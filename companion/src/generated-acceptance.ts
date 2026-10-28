/** Data-only contract for the manually selected generated-circuit acceptance.
 * This binds a request and response; it is not authentication or authorization.
 */
import { createHash } from "node:crypto";
import { gatewayContext, type GatewayContext } from "./gateway-types.js";
import {
  makeGeneratedBlueprint,
  validateBehavior,
  validateBehaviorProposal,
  validateGeneratedGeometry,
  type Behavior,
  type BehaviorProposal,
  type GeneratedGeometry,
  type GeneratedRequest,
} from "./generated.js";
import {
  identifier,
  integer,
  object,
  RequestError,
  text,
  type DesignRequest,
} from "./types.js";
import type {
  AcceptanceProvenance,
  AcceptanceRun,
} from "./acceptance-exchange.js";

const TTL = 15 * 60_000;
export const GENERATED_ACCEPTANCE_REQUEST =
  "Make a lever-controlled lamp that turns on after 12 game ticks and off after 16 game ticks. Teach me its construction.";
export const GENERATED_ACCEPTANCE_SCENARIO =
  "generated-buffer-owner-confirmation-v1";
export const GENERATED_ACCEPTANCE_BEHAVIOR: Behavior = Object.freeze({
  schemaVersion: 1,
  function: "buffer",
  risingGameTicks: 12,
  fallingGameTicks: 16,
});

export type GeneratedAcceptanceStage = "behavior" | "geometry";
export interface GeneratedAcceptanceRequest {
  schemaVersion: 1;
  run: AcceptanceRun;
  stage: GeneratedAcceptanceStage;
  context: GatewayContext;
  worldId: string;
  nonce: string;
  createdAt: number;
  expiresAt: number;
  behavior: Behavior | null;
  digest: string;
}
export interface GeneratedAcceptanceResponse {
  schemaVersion: 1;
  requestDigest: string;
  stage: GeneratedAcceptanceStage;
  result: BehaviorProposal | GeneratedGeometry;
  provenance: AcceptanceProvenance;
  digest: string;
}
export type GeneratedAcceptanceInput = DesignRequest | GeneratedRequest;

function keys(v: Record<string, unknown>, allowed: string[]): void {
  if (Object.keys(v).some((key) => !allowed.includes(key)))
    throw new RequestError("Unknown generated acceptance field.");
}
function bounded(value: unknown, max: number): Record<string, unknown> {
  const v = object(value);
  if (JSON.stringify(v).length > max)
    throw new RequestError("Generated acceptance envelope too large.");
  return v;
}
function hex(value: unknown, length: number): string {
  if (
    typeof value !== "string" ||
    value.length !== length ||
    !/^[a-f0-9]+$/u.test(value)
  )
    throw new RequestError("Invalid generated acceptance digest or identity.");
  return value;
}
function digest(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}
function runIdentity(value: unknown): AcceptanceRun {
  const v = object(value);
  keys(v, [
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
    v.scenario !== GENERATED_ACCEPTANCE_SCENARIO
  )
    throw new RequestError("Unrecognized generated acceptance scenario.");
  if (typeof v.runId !== "string" || !/^[1-9][0-9]{0,19}$/u.test(v.runId))
    throw new RequestError("Invalid acceptance run ID.");
  return {
    repository: "1xmint/minecraft-ai-engineer",
    workflow: ".github/workflows/live-acceptance.yml",
    sourceSha: hex(v.sourceSha, 40),
    runId: v.runId,
    runAttempt: integer(v.runAttempt, "runAttempt", 1, 1000),
    scenario: GENERATED_ACCEPTANCE_SCENARIO,
  };
}
function frame(value: unknown): GatewayContext {
  const v = object(value);
  keys(v, [
    "serverId",
    "serverEpoch",
    "actorId",
    "projectId",
    "projectRevision",
    "authorizationRevision",
    "requestId",
    "connectionId",
  ]);
  const result = gatewayContext(v);
  if (!result.connectionId)
    throw new RequestError("Generated acceptance needs a connected actor.");
  return result;
}
function stage(value: unknown): GeneratedAcceptanceStage {
  if (value !== "behavior" && value !== "geometry")
    throw new RequestError("Invalid generated acceptance stage.");
  return value;
}
function requestBehavior(
  value: unknown,
  requestStage: GeneratedAcceptanceStage,
): Behavior | null {
  if (requestStage === "behavior") {
    if (value !== null)
      throw new RequestError(
        "Behavior-stage requests must have null behavior.",
      );
    return null;
  }
  const result = validateBehavior(value);
  if (JSON.stringify(result) !== JSON.stringify(GENERATED_ACCEPTANCE_BEHAVIOR))
    throw new RequestError(
      "Geometry stage requires the fixed accepted behavior.",
    );
  return result;
}
function payload(value: unknown): Omit<GeneratedAcceptanceRequest, "digest"> {
  const v = bounded(value, 4096);
  keys(v, [
    "schemaVersion",
    "run",
    "stage",
    "context",
    "worldId",
    "nonce",
    "createdAt",
    "expiresAt",
    "behavior",
    "digest",
  ]);
  if (v.schemaVersion !== 1)
    throw new RequestError("Unsupported generated acceptance schema.");
  const createdAt = integer(
    v.createdAt,
    "createdAt",
    0,
    Number.MAX_SAFE_INTEGER - TTL,
  );
  if (v.expiresAt !== createdAt + TTL)
    throw new RequestError("Invalid generated acceptance expiry.");
  const requestStage = stage(v.stage);
  return {
    schemaVersion: 1,
    run: runIdentity(v.run),
    stage: requestStage,
    context: frame(v.context),
    worldId: identifier(v.worldId, "worldId"),
    nonce: hex(v.nonce, 64),
    createdAt,
    expiresAt: createdAt + TTL,
    behavior: requestBehavior(v.behavior, requestStage),
  };
}
export function createGeneratedAcceptanceRequest(
  value: unknown,
): GeneratedAcceptanceRequest {
  const v = object(value);
  keys(v, [
    "run",
    "stage",
    "context",
    "worldId",
    "nonce",
    "createdAt",
    "behavior",
  ]);
  const createdAt = integer(
    v.createdAt,
    "createdAt",
    0,
    Number.MAX_SAFE_INTEGER - TTL,
  );
  const body = payload({
    ...v,
    schemaVersion: 1,
    expiresAt: createdAt + TTL,
  });
  return { ...body, digest: digest(body) };
}
export function readGeneratedAcceptanceRequest(
  value: unknown,
  expectedRun: unknown,
  now: number,
): GeneratedAcceptanceRequest & {
  input: GeneratedAcceptanceInput;
  memory: string;
} {
  const body = payload(value),
    v = object(value),
    expected = runIdentity(expectedRun);
  if (
    JSON.stringify(body.run) !== JSON.stringify(expected) ||
    hex(v.digest, 64) !== digest(body)
  )
    throw new RequestError(
      "Generated acceptance request identity changed.",
      409,
    );
  integer(now, "now", 0, Number.MAX_SAFE_INTEGER);
  if (now < body.createdAt || now >= body.expiresAt)
    throw new RequestError(
      "Generated acceptance expired or clock moved backward.",
      409,
    );
  const base: DesignRequest = {
    request: GENERATED_ACCEPTANCE_REQUEST,
    worldId: body.worldId,
    dimension: "minecraft:overworld",
    projectId: body.context.projectId,
    anchor: { x: 200, y: 100, z: 0 },
    rotation: 0,
    maxSize: { x: 4, y: 2, z: 1 },
  };
  const input: GeneratedAcceptanceInput =
    body.stage === "geometry"
      ? { ...base, behavior: GENERATED_ACCEPTANCE_BEHAVIOR }
      : base;
  return {
    ...body,
    digest: v.digest as string,
    input,
    memory: JSON.stringify({
      project: {
        name: "Engineering project",
        projectRevision: body.context.projectRevision,
      },
      tasks: [],
    }),
  };
}
function resultValue(
  value: unknown,
  requestStage: GeneratedAcceptanceStage,
): BehaviorProposal | GeneratedGeometry {
  if (requestStage === "behavior") return validateBehaviorProposal(value);
  const geometry = validateGeneratedGeometry(value);
  // Run all server-derived bounds, support, state, and fit checks without
  // replacing the raw geometry sent by the model in the acceptance receipt.
  makeGeneratedBlueprint(
    {
      request: GENERATED_ACCEPTANCE_REQUEST,
      worldId: "acceptance-world",
      dimension: "minecraft:overworld",
      projectId: "acceptance-project",
      anchor: { x: 200, y: 100, z: 0 },
      rotation: 0,
      maxSize: { x: 4, y: 2, z: 1 },
    },
    value,
    GENERATED_ACCEPTANCE_BEHAVIOR,
  );
  return geometry;
}
function provenance(value: unknown): AcceptanceProvenance {
  const v = object(value);
  keys(v, [
    "model",
    "sdkVersion",
    "inputTokens",
    "cachedInputTokens",
    "outputTokens",
  ]);
  const model = text(v.model, "model", 96);
  if (!/^[a-z0-9][a-z0-9._-]*$/u.test(model) || v.sdkVersion !== "0.164.0")
    throw new RequestError("Invalid local provider provenance.");
  const inputTokens = integer(
    v.inputTokens,
    "inputTokens",
    0,
    Number.MAX_SAFE_INTEGER,
  );
  return {
    model,
    sdkVersion: "0.164.0",
    inputTokens,
    cachedInputTokens: integer(
      v.cachedInputTokens,
      "cachedInputTokens",
      0,
      inputTokens,
    ),
    outputTokens: integer(
      v.outputTokens,
      "outputTokens",
      0,
      Number.MAX_SAFE_INTEGER,
    ),
  };
}
export function createGeneratedAcceptanceResponse(
  request: unknown,
  result: unknown,
  provider: unknown,
): GeneratedAcceptanceResponse {
  const v = object(request),
    body = readGeneratedAcceptanceRequest(
      request,
      v.run,
      integer(v.createdAt, "createdAt", 0, Number.MAX_SAFE_INTEGER),
    );
  const canonical = {
    schemaVersion: 1 as const,
    requestDigest: body.digest,
    stage: body.stage,
    result: resultValue(result, body.stage),
    provenance: provenance(provider),
  };
  return { ...canonical, digest: digest(canonical) };
}
export function readGeneratedAcceptanceResponse(
  value: unknown,
  request: unknown,
  now: number,
): GeneratedAcceptanceResponse {
  const expected = object(request),
    current = readGeneratedAcceptanceRequest(request, expected.run, now),
    v = bounded(value, 8192);
  keys(v, [
    "schemaVersion",
    "requestDigest",
    "stage",
    "result",
    "provenance",
    "digest",
  ]);
  if (
    v.schemaVersion !== 1 ||
    v.stage !== current.stage ||
    hex(v.requestDigest, 64) !== current.digest
  )
    throw new RequestError(
      "Generated response belongs to another request or stage.",
      409,
    );
  const canonical = {
    schemaVersion: 1 as const,
    requestDigest: current.digest,
    stage: current.stage,
    result: resultValue(v.result, current.stage),
    provenance: provenance(v.provenance),
  };
  if (hex(v.digest, 64) !== digest(canonical))
    throw new RequestError(
      "Generated acceptance response changed after receipt.",
      409,
    );
  return { ...canonical, digest: v.digest as string };
}
