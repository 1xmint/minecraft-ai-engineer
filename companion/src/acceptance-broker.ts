/** CI-only data bridge. The production companion never selects this provider. */
import {
  mkdirSync,
  existsSync,
  readFileSync,
  writeFileSync,
  renameSync,
  statSync,
} from "node:fs";
import { join, resolve } from "node:path";
import { randomBytes } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { setTimeout } from "node:timers/promises";
import {
  createAcceptanceRequest,
  readAcceptanceRequest,
  readAcceptanceResponse,
  type AcceptanceRun,
} from "./acceptance-exchange.js";
import type { GatewayContext } from "./gateway-types.js";
import {
  RequestError,
  integer,
  type DesignRequest,
  type Plan,
  type PlanningProvider,
} from "./types.js";

export class SyntheticAcceptanceProvider implements PlanningProvider {
  private used = false;
  private readonly directory: string;
  private readonly clock: () => number;
  private readonly pollMs: number;
  constructor(
    private readonly options: {
      directory: string;
      run: AcceptanceRun;
      clock?: () => number;
      pollMs?: number;
    },
  ) {
    if (process.env.CI !== "true")
      throw new RequestError("Synthetic acceptance broker is CI-only.");
    this.directory = resolve(options.directory);
    this.clock = options.clock ?? Date.now;
    this.pollMs = integer(options.pollMs ?? 500, "pollMs", 1, 1000);
    mkdirSync(this.directory, { recursive: true, mode: 0o700 });
  }
  private write(name: string, value: unknown): void {
    const target = join(this.directory, name),
      temporary = target + ".tmp";
    writeFileSync(temporary, JSON.stringify(value), { mode: 0o600 });
    renameSync(temporary, target);
  }
  private refuse(): never {
    this.used = true;
    writeFileSync(
      join(this.directory, "unexpected-inference"),
      "Unexpected synthetic inference attempt",
      { mode: 0o600 },
    );
    throw new RequestError(
      "Only one exact synthetic acceptance request is supported.",
      503,
    );
  }
  async plan(
    input: DesignRequest,
    signal: AbortSignal,
    memory?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<Plan> {
    if (signal.aborted) throw new RequestError("Acceptance cancelled.", 503);
    if (
      this.used ||
      existsSync(join(this.directory, "request.json")) ||
      !authority ||
      !Object.isFrozen(authority)
    )
      return this.refuse();
    const request = createAcceptanceRequest({
      run: this.options.run,
      context: authority,
      worldId: input.worldId,
      nonce: randomBytes(32).toString("hex"),
      createdAt: this.clock(),
    });
    const expected = readAcceptanceRequest(
      request,
      this.options.run,
      this.clock(),
    );
    if (!isDeepStrictEqual(input, expected.input) || memory !== expected.memory)
      return this.refuse();
    this.used = true;
    this.write("request.json", request);
    try {
      while (true) {
        if (signal.aborted)
          throw new RequestError("Acceptance cancelled.", 503);
        readAcceptanceRequest(request, this.options.run, this.clock());
        const responseFile = join(this.directory, "response.json");
        if (existsSync(responseFile)) {
          const stat = statSync(responseFile);
          if (!stat.isFile() || stat.size > 8192)
            throw new RequestError("Acceptance response too large.");
          const result = readAcceptanceResponse(
            JSON.parse(readFileSync(responseFile, "utf8")),
            request,
            this.clock(),
          );
          if (signal.aborted)
            throw new RequestError("Acceptance cancelled.", 503);
          this.write("accepted.json", {
            schemaVersion: 1,
            requestDigest: request.digest,
            resultDigest: result.digest,
            acceptedAt: this.clock(),
            provenance: result.provenance,
          });
          return result.plan;
        }
        await setTimeout(this.pollMs, undefined, { signal });
      }
    } catch (error) {
      if (signal.aborted) throw new RequestError("Acceptance cancelled.", 503);
      throw error;
    }
  }
}
