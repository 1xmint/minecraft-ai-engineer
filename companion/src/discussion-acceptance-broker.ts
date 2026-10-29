/** Manual CI bridge only. This process never loads the SDK or credentials. */
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
import { setTimeout } from "node:timers/promises";
import { isDeepStrictEqual } from "node:util";
import {
  createDiscussionAcceptanceRequest,
  readDiscussionAcceptanceRequest,
  readDiscussionAcceptanceResponse,
  DISCUSSION_ACCEPTANCE_SCENARIO,
} from "./discussion-acceptance.js";
import type { AcceptanceRun } from "./acceptance-exchange.js";
import type { GatewayContext } from "./gateway-types.js";
import type { DiscussionRequest, DiscussionReply } from "./discussion.js";
import { RequestError, type PlanningProvider } from "./types.js";
export class DiscussionSyntheticAcceptanceProvider implements PlanningProvider {
  private used = false;
  private readonly directory: string;
  constructor(
    private readonly options: { directory: string; run: AcceptanceRun },
  ) {
    if (
      process.env.CI !== "true" ||
      process.env.GITHUB_ACTIONS !== "true" ||
      process.env.MCE_LIVE_ACCEPTANCE !== "true" ||
      process.env.MCE_DISCUSSION_ACCEPTANCE !== "true" ||
      options.run.scenario !== DISCUSSION_ACCEPTANCE_SCENARIO
    )
      throw new RequestError("Manual synthetic discussion proof only.");
    this.directory = resolve(options.directory, "discussion");
    mkdirSync(this.directory, { recursive: true, mode: 0o700 });
  }
  private refuse(): never {
    writeFileSync(
      join(this.directory, "..", "unexpected-inference"),
      "Unexpected discussion acceptance call",
      { mode: 0o600 },
    );
    throw new RequestError(
      "Only one exact synthetic owner discussion is supported.",
      503,
    );
  }
  async plan(): Promise<never> {
    return this.refuse();
  }
  async discuss(
    input: DiscussionRequest,
    signal: AbortSignal,
    memory: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<DiscussionReply> {
    if (signal.aborted)
      throw new RequestError("Discussion acceptance cancelled.", 503);
    if (
      this.used ||
      existsSync(join(this.directory, "request.json")) ||
      !authority ||
      !Object.isFrozen(authority)
    )
      return this.refuse();
    const request = createDiscussionAcceptanceRequest({
      run: this.options.run,
      context: authority,
      grounding: memory,
      nonce: randomBytes(32).toString("hex"),
      createdAt: Date.now(),
    });
    const expected = readDiscussionAcceptanceRequest(
      request,
      this.options.run,
      Date.now(),
    );
    if (!isDeepStrictEqual(input, expected.input) || memory !== expected.memory)
      return this.refuse();
    this.used = true;
    const write = (name: string, value: unknown) => {
      const file = join(this.directory, name);
      writeFileSync(file + ".tmp", JSON.stringify(value), { mode: 0o600 });
      renameSync(file + ".tmp", file);
    };
    write("request.json", request);
    while (true) {
      if (signal.aborted)
        throw new RequestError("Discussion acceptance cancelled.", 503);
      readDiscussionAcceptanceRequest(request, this.options.run, Date.now());
      const file = join(this.directory, "response.json");
      if (existsSync(file)) {
        const stat = statSync(file);
        if (!stat.isFile() || stat.size > 8192)
          throw new RequestError("Invalid discussion response file.");
        const result = readDiscussionAcceptanceResponse(
          JSON.parse(readFileSync(file, "utf8")),
          request,
          Date.now(),
        );
        if (signal.aborted)
          throw new RequestError("Discussion acceptance cancelled.", 503);
        write("accepted.json", {
          schemaVersion: 1,
          stage: "discussion",
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
