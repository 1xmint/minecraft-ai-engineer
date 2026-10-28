/** Manual synthetic CI bridge; real subscription credentials remain on the operator PC. */
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
  createGeneratedAcceptanceRequest,
  readGeneratedAcceptanceRequest,
  readGeneratedAcceptanceResponse,
} from "./generated-acceptance.js";
import type { AcceptanceRun } from "./acceptance-exchange.js";
import type { GatewayContext } from "./gateway-types.js";
import type {
  BehaviorProposal,
  GeneratedGeometry,
  GeneratedRequest,
} from "./generated.js";
import {
  RequestError,
  type DesignRequest,
  type PlanningProvider,
} from "./types.js";
export class GeneratedSyntheticAcceptanceProvider implements PlanningProvider {
  private readonly directory: string;
  private used = new Set<string>();
  private proposed?: BehaviorProposal;
  private basis: Readonly<GatewayContext> | undefined;
  constructor(
    private readonly options: { directory: string; run: AcceptanceRun },
  ) {
    if (
      process.env.CI !== "true" ||
      process.env.GITHUB_ACTIONS !== "true" ||
      process.env.MCE_LIVE_ACCEPTANCE !== "true" ||
      process.env.MCE_GENERATED_ACCEPTANCE !== "true"
    )
      throw new RequestError("Manual generated acceptance only.");
    this.directory = resolve(options.directory);
    mkdirSync(this.directory, { recursive: true, mode: 0o700 });
  }
  private refuse(): never {
    writeFileSync(
      join(this.directory, "unexpected-inference"),
      "Unexpected generated acceptance call",
      { mode: 0o600 },
    );
    throw new RequestError(
      "Only the two exact owner-confirmed synthetic stages are supported.",
      503,
    );
  }
  async plan(): Promise<never> {
    return this.refuse();
  }
  async proposeBehavior(
    input: DesignRequest,
    signal: AbortSignal,
    memory?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<BehaviorProposal> {
    const result = (await this.exchange(
      "behavior",
      input,
      signal,
      memory,
      authority,
    )) as BehaviorProposal;
    this.proposed = result;
    this.basis = authority;
    return result;
  }
  async generateGeometry(
    input: GeneratedRequest,
    signal: AbortSignal,
    memory?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<GeneratedGeometry> {
    if (
      !this.proposed?.behavior ||
      !this.basis ||
      !isDeepStrictEqual(this.proposed.behavior, input.behavior) ||
      !authority
    )
      return this.refuse();
    for (const key of [
      "serverId",
      "serverEpoch",
      "actorId",
      "projectId",
      "projectRevision",
      "authorizationRevision",
      "connectionId",
    ] as const)
      if (this.basis[key] !== authority[key]) return this.refuse();
    return (await this.exchange(
      "geometry",
      input,
      signal,
      memory,
      authority,
    )) as GeneratedGeometry;
  }
  private async exchange(
    stage: "behavior" | "geometry",
    input: DesignRequest | GeneratedRequest,
    signal: AbortSignal,
    memory: string | undefined,
    authority: Readonly<GatewayContext> | undefined,
  ): Promise<BehaviorProposal | GeneratedGeometry> {
    if (signal.aborted) throw new RequestError("Acceptance cancelled.", 503);
    const directory = join(this.directory, stage);
    if (
      this.used.has(stage) ||
      existsSync(join(directory, "request.json")) ||
      !authority ||
      !Object.isFrozen(authority) ||
      (stage === "behavior" && this.used.size !== 0)
    )
      return this.refuse();
    const request = createGeneratedAcceptanceRequest({
      run: this.options.run,
      stage,
      context: authority,
      worldId: input.worldId,
      nonce: randomBytes(32).toString("hex"),
      createdAt: Date.now(),
      behavior:
        stage === "geometry" ? (input as GeneratedRequest).behavior : null,
    });
    const expected = readGeneratedAcceptanceRequest(
      request,
      this.options.run,
      Date.now(),
    );
    if (!isDeepStrictEqual(input, expected.input) || memory !== expected.memory)
      return this.refuse();
    this.used.add(stage);
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    const write = (name: string, value: unknown) => {
      const target = join(directory, name);
      writeFileSync(target + ".tmp", JSON.stringify(value), { mode: 0o600 });
      renameSync(target + ".tmp", target);
    };
    write("request.json", request);
    while (true) {
      if (signal.aborted) throw new RequestError("Acceptance cancelled.", 503);
      readGeneratedAcceptanceRequest(request, this.options.run, Date.now());
      const file = join(directory, "response.json");
      if (existsSync(file)) {
        const stat = statSync(file);
        if (!stat.isFile() || stat.size > 8192)
          throw new RequestError("Acceptance response too large.");
        const result = readGeneratedAcceptanceResponse(
          JSON.parse(readFileSync(file, "utf8")),
          request,
          Date.now(),
        );
        if (signal.aborted)
          throw new RequestError("Acceptance cancelled.", 503);
        write("accepted.json", {
          schemaVersion: 1,
          stage,
          requestDigest: request.digest,
          resultDigest: result.digest,
          acceptedAt: Date.now(),
          provenance: result.provenance,
        });
        return result.result;
      }
      await setTimeout(500, undefined, { signal });
    }
  }
}
