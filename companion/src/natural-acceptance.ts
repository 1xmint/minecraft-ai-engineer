/** Bounded data-only exchange for one manually selected synthetic interpretation. */
import { createHash } from "node:crypto";
import { gatewayContext, type GatewayContext } from "./gateway-types.js";
import { integer, object, text, RequestError } from "./types.js";
import { readProjectNote } from "./project-knowledge.js";
import { makeReferenceBlueprint } from "./blueprint.js";
import { discussionGrounding } from "./discussion.js";
import {
  naturalCapabilities,
  validateNaturalReply,
  type NaturalReply,
} from "./natural-request.js";
import type {
  AcceptanceRun,
  AcceptanceProvenance,
} from "./acceptance-exchange.js";
export const NATURAL_ACCEPTANCE_SCENARIO =
  "natural-engineering-owner-action-v1";
export const NATURAL_ACCEPTANCE_MESSAGE =
  "Why is this part not working? Inspect the selected entrance component and tell me what needs fixing. Do not change blocks.";
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
    throw new RequestError(
      "Invalid synthetic natural interpretation fields or size.",
    );
  return v;
}
function hex(value: unknown, length: number): string {
  if (
    typeof value !== "string" ||
    value.length !== length ||
    !/^[a-f0-9]+$/u.test(value)
  )
    throw new RequestError("Invalid natural interpretation digest.");
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
    v.scenario !== NATURAL_ACCEPTANCE_SCENARIO ||
    typeof v.runId !== "string" ||
    !/^[1-9][0-9]{0,19}$/u.test(v.runId)
  )
    throw new RequestError("Unknown natural interpretation acceptance run.");
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
      "Natural acceptance requires the connected synthetic owner.",
    );
  return c;
}
function grounding(memory: unknown, context: GatewayContext) {
  const source = text(
      memory,
      "synthetic natural interpretation context",
      12000,
    ),
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
    throw new RequestError("Natural proof contains unexpected project data.");
  return { ...expected, capabilities: naturalCapabilities(blueprint, "root") };
}
export interface NaturalAcceptanceRequest {
  schemaVersion: 1;
  stage: "natural";
  run: AcceptanceRun;
  context: GatewayContext;
  grounding: string;
  nonce: string;
  createdAt: number;
  expiresAt: number;
  digest: string;
}
function payload(value: unknown): Omit<NaturalAcceptanceRequest, "digest"> {
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
  if (v.schemaVersion !== 1 || v.stage !== "natural")
    throw new RequestError(
      "Invalid natural interpretation acceptance schema/stage.",
    );
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
    stage: "natural",
    run: runIdentity(v.run),
    context,
    grounding: v.grounding as string,
    nonce: hex(v.nonce, 64),
    createdAt,
    expiresAt: createdAt + TTL,
  };
}
export function createNaturalAcceptanceRequest(
  value: unknown,
): NaturalAcceptanceRequest {
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
    stage: "natural",
    expiresAt: createdAt + TTL,
    digest: "",
  });
  return { ...body, digest: digest(body) };
}
export function readNaturalAcceptanceRequest(
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
    throw new RequestError("Natural request identity changed.", 409);
  integer(now, "now", 0, Number.MAX_SAFE_INTEGER);
  if (now < body.createdAt || now >= body.expiresAt)
    throw new RequestError("Natural request expired.", 409);
  return {
    ...body,
    digest: v.digest as string,
    input: { message: NATURAL_ACCEPTANCE_MESSAGE },
    capabilities: grounding(body.grounding, body.context).capabilities,
    availableBasis: grounding(body.grounding, body.context).availableBasis,
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
export interface NaturalAcceptanceResponse {
  schemaVersion: 1;
  stage: "natural";
  requestDigest: string;
  result: NaturalReply;
  provenance: AcceptanceProvenance;
  digest: string;
}
export function createNaturalAcceptanceResponse(
  request: unknown,
  result: unknown,
  provider: unknown,
): NaturalAcceptanceResponse {
  const v = object(request),
    current = readNaturalAcceptanceRequest(
      request,
      v.run,
      integer(v.createdAt, "createdAt", 0, Number.MAX_SAFE_INTEGER),
    );
  const sources = grounding(current.grounding, current.context);
  const reply = validateNaturalReply(
    result,
    sources.capabilities,
    sources.availableBasis,
  );
  if (
    reply.kind !== "action" ||
    reply.action !== "diagnose" ||
    reply.componentId !== "root"
  )
    throw new RequestError(
      "The fixed natural proof requires diagnosis of the selected component, without world delegation.",
    );
  const body = {
    schemaVersion: 1 as const,
    stage: "natural" as const,
    requestDigest: current.digest,
    result: reply,
    provenance: provenance(provider),
  };
  return { ...body, digest: digest(body) };
}
export function readNaturalAcceptanceResponse(
  value: unknown,
  request: unknown,
  now: number,
): NaturalAcceptanceResponse {
  const req = object(request),
    current = readNaturalAcceptanceRequest(request, req.run, now),
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
    v.stage !== "natural" ||
    hex(v.requestDigest, 64) !== current.digest
  )
    throw new RequestError("Natural response belongs to another request.", 409);
  const body = createNaturalAcceptanceResponse(request, v.result, v.provenance);
  if (hex(v.digest, 64) !== body.digest)
    throw new RequestError("Natural response changed.", 409);
  return body;
}
