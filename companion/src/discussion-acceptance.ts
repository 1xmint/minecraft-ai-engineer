/** Bounded data-only exchange for one manually selected synthetic conversation. */
import { createHash } from "node:crypto";
import { gatewayContext, type GatewayContext } from "./gateway-types.js";
import { integer, object, text, RequestError } from "./types.js";
import { readProjectNote } from "./project-knowledge.js";
import { makeReferenceBlueprint } from "./blueprint.js";
import {
  discussionGrounding,
  validateDiscussionReply,
  type DiscussionReply,
} from "./discussion.js";
import type {
  AcceptanceRun,
  AcceptanceProvenance,
} from "./acceptance-exchange.js";
export const DISCUSSION_ACCEPTANCE_SCENARIO =
  "project-discussion-owner-notes-v1";
export const DISCUSSION_ACCEPTANCE_MESSAGE =
  "Why did we choose a visible lamp for this entrance, and what size constraint should I preserve? Explain the recorded requirements without claiming that the circuit was built or tested.";
const OWNER = "10920508-d5d8-3eed-93d2-92f193afe7d7",
  TTL = 15 * 60_000;
function exact(
  value: unknown,
  fields: string[],
  max = 16384,
): Record<string, unknown> {
  const v = object(value);
  if (
    JSON.stringify(v).length > max ||
    Object.keys(v).length !== fields.length ||
    Object.keys(v).some((key) => !fields.includes(key))
  )
    throw new RequestError("Invalid synthetic discussion fields or size.");
  return v;
}
function hex(value: unknown, length: number): string {
  if (
    typeof value !== "string" ||
    value.length !== length ||
    !/^[a-f0-9]+$/u.test(value)
  )
    throw new RequestError("Invalid discussion digest.");
  return value;
}
const digest = (value: unknown) =>
  createHash("sha256").update(JSON.stringify(value)).digest("hex");
function runIdentity(value: unknown): AcceptanceRun {
  const v = exact(value, [
    "repository",
    "workflow",
    "sourceSha",
    "runId",
    "runAttempt",
    "scenario",
  ]);
  if (
    v.repository !== "1xmint/minecraft-ai-engineer" ||
    v.workflow !== ".github/workflows/live-acceptance.yml" ||
    v.scenario !== DISCUSSION_ACCEPTANCE_SCENARIO ||
    typeof v.runId !== "string" ||
    !/^[1-9][0-9]{0,19}$/u.test(v.runId)
  )
    throw new RequestError("Unknown discussion acceptance run.");
  return {
    repository: v.repository,
    workflow: v.workflow,
    sourceSha: hex(v.sourceSha, 40),
    runId: v.runId,
    runAttempt: integer(v.runAttempt, "runAttempt", 1, 1000),
    scenario: v.scenario,
  };
}
function frame(value: unknown): GatewayContext {
  const v = exact(value, [
    "serverId",
    "serverEpoch",
    "actorId",
    "projectId",
    "projectRevision",
    "authorizationRevision",
    "requestId",
    "connectionId",
  ]);
  const c = gatewayContext(v);
  if (c.actorId !== OWNER || !c.connectionId)
    throw new RequestError(
      "Discussion acceptance requires the connected synthetic owner.",
    );
  return c;
}
function grounding(memory: unknown, context: GatewayContext) {
  const source = text(memory, "synthetic discussion context", 12000),
    value = object(JSON.parse(source));
  if (!Array.isArray(value.sources))
    throw new RequestError("Missing synthetic project sources.");
  const notes = value.sources
    .filter((item) => object(item).kind === "owner-authored-requirement")
    .map((item) => readProjectNote(object(item).data));
  if (
    notes.length !== 2 ||
    new Set(notes.map((note) => note.id)).size !== 2 ||
    notes.some(
      (note, index) => note.authorId !== OWNER || note.revision !== index + 1,
    ) ||
    notes[0]!.kind !== "goal" ||
    notes[0]!.text !== "Keep the entrance within six blocks." ||
    notes[1]!.kind !== "decision" ||
    notes[1]!.text !== "Use a visible lamp for the output."
  )
    throw new RequestError(
      "Only fixed synthetic owner notes may enter this proof.",
    );
  const blueprint = makeReferenceBlueprint({
    request: "reference",
    worldId: "synthetic",
    dimension: "minecraft:overworld",
    projectId: context.projectId,
    anchor: { x: 500, y: 100, z: 0 },
    rotation: 0,
  });
  const expected = discussionGrounding(
    blueprint.name,
    context.projectRevision,
    blueprint,
    { schemaVersion: 1, revision: 2, notes, hasEarlier: false },
    "root",
  );
  if (source !== expected.text)
    throw new RequestError(
      "Discussion proof contains unexpected project data.",
    );
  return expected;
}
export interface DiscussionAcceptanceRequest {
  schemaVersion: 1;
  stage: "discussion";
  run: AcceptanceRun;
  context: GatewayContext;
  grounding: string;
  nonce: string;
  createdAt: number;
  expiresAt: number;
  digest: string;
}
function payload(value: unknown): Omit<DiscussionAcceptanceRequest, "digest"> {
  const v = exact(value, [
    "schemaVersion",
    "stage",
    "run",
    "context",
    "grounding",
    "nonce",
    "createdAt",
    "expiresAt",
    "digest",
  ]);
  if (v.schemaVersion !== 1 || v.stage !== "discussion")
    throw new RequestError("Invalid discussion acceptance schema/stage.");
  const createdAt = integer(
      v.createdAt,
      "createdAt",
      0,
      Number.MAX_SAFE_INTEGER - TTL,
    ),
    context = frame(v.context);
  if (v.expiresAt !== createdAt + TTL)
    throw new RequestError("Invalid discussion expiry.");
  grounding(v.grounding, context);
  return {
    schemaVersion: 1,
    stage: "discussion",
    run: runIdentity(v.run),
    context,
    grounding: v.grounding as string,
    nonce: hex(v.nonce, 64),
    createdAt,
    expiresAt: createdAt + TTL,
  };
}
export function createDiscussionAcceptanceRequest(
  value: unknown,
): DiscussionAcceptanceRequest {
  const v = exact(value, ["run", "context", "grounding", "nonce", "createdAt"]),
    createdAt = integer(
      v.createdAt,
      "createdAt",
      0,
      Number.MAX_SAFE_INTEGER - TTL,
    );
  const body = payload({
    ...v,
    schemaVersion: 1,
    stage: "discussion",
    expiresAt: createdAt + TTL,
    digest: "",
  });
  return { ...body, digest: digest(body) };
}
export function readDiscussionAcceptanceRequest(
  value: unknown,
  expectedRun: unknown,
  now: number,
) {
  const body = payload(value),
    v = object(value);
  if (
    JSON.stringify(body.run) !== JSON.stringify(runIdentity(expectedRun)) ||
    hex(v.digest, 64) !== digest(body)
  )
    throw new RequestError("Discussion request identity changed.", 409);
  integer(now, "now", 0, Number.MAX_SAFE_INTEGER);
  if (now < body.createdAt || now >= body.expiresAt)
    throw new RequestError("Discussion request expired.", 409);
  return {
    ...body,
    digest: v.digest as string,
    input: { message: DISCUSSION_ACCEPTANCE_MESSAGE, componentId: "root" },
    memory: body.grounding,
  };
}
function provenance(value: unknown): AcceptanceProvenance {
  const v = exact(value, [
    "model",
    "sdkVersion",
    "inputTokens",
    "cachedInputTokens",
    "outputTokens",
  ]);
  if (v.sdkVersion !== "0.164.0")
    throw new RequestError("Unreviewed discussion SDK.");
  const inputTokens = integer(
      v.inputTokens,
      "inputTokens",
      0,
      Number.MAX_SAFE_INTEGER,
    ),
    cachedInputTokens = integer(
      v.cachedInputTokens,
      "cachedInputTokens",
      0,
      inputTokens,
    );
  return {
    model: text(v.model, "model", 96),
    sdkVersion: v.sdkVersion,
    inputTokens,
    cachedInputTokens,
    outputTokens: integer(
      v.outputTokens,
      "outputTokens",
      0,
      Number.MAX_SAFE_INTEGER,
    ),
  };
}
export interface DiscussionAcceptanceResponse {
  schemaVersion: 1;
  stage: "discussion";
  requestDigest: string;
  result: DiscussionReply;
  provenance: AcceptanceProvenance;
  digest: string;
}
export function createDiscussionAcceptanceResponse(
  request: unknown,
  result: unknown,
  provider: unknown,
): DiscussionAcceptanceResponse {
  const v = object(request),
    current = readDiscussionAcceptanceRequest(
      request,
      v.run,
      integer(v.createdAt, "createdAt", 0, Number.MAX_SAFE_INTEGER),
    );
  const sources = grounding(current.grounding, current.context);
  const reply = validateDiscussionReply(result, sources.availableBasis);
  const required = [...sources.availableBasis].filter((id) =>
    id.startsWith("note:"),
  );
  if (
    reply.kind !== "explanation" ||
    required.some((id) => !reply.basis.includes(id))
  )
    throw new RequestError(
      "The fixed discussion proof requires an explanation citing both owner requirements.",
    );
  const body = {
    schemaVersion: 1 as const,
    stage: "discussion" as const,
    requestDigest: current.digest,
    result: reply,
    provenance: provenance(provider),
  };
  return { ...body, digest: digest(body) };
}
export function readDiscussionAcceptanceResponse(
  value: unknown,
  request: unknown,
  now: number,
): DiscussionAcceptanceResponse {
  const req = object(request),
    current = readDiscussionAcceptanceRequest(request, req.run, now),
    v = exact(
      value,
      [
        "schemaVersion",
        "stage",
        "requestDigest",
        "result",
        "provenance",
        "digest",
      ],
      8192,
    );
  if (
    v.schemaVersion !== 1 ||
    v.stage !== "discussion" ||
    hex(v.requestDigest, 64) !== current.digest
  )
    throw new RequestError(
      "Discussion response belongs to another request.",
      409,
    );
  const body = createDiscussionAcceptanceResponse(
    request,
    v.result,
    v.provenance,
  );
  if (hex(v.digest, 64) !== body.digest)
    throw new RequestError("Discussion response changed.", 409);
  return body;
}
