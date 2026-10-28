/** Data-only contract for the manually selected, synthetic CI acceptance exchange.
 * Not authentication: transport must separately verify GitHub workflow/run/actor provenance.
 * No endpoint, provider invocation, file access or default CI inference is enabled here.
 */
import { createHash } from "node:crypto";
import { gatewayContext, type GatewayContext } from "./gateway-types.js";
import { validatePlan } from "./provider.js";
import {
  identifier,
  integer,
  object,
  RequestError,
  text,
  type DesignRequest,
  type Plan,
} from "./types.js";

const TTL = 15 * 60_000;
const REQUEST =
  "Build a delayed lamp with a lever and a one-tick repeater. Teach me its construction.";
export interface AcceptanceRun {
  repository: string;
  workflow: string;
  sourceSha: string;
  runId: string;
  runAttempt: number;
  scenario: string;
}
export interface AcceptanceRequest {
  schemaVersion: 1;
  run: AcceptanceRun;
  context: GatewayContext;
  worldId: string;
  nonce: string;
  createdAt: number;
  expiresAt: number;
  digest: string;
}
export interface AcceptanceProvenance {
  model: string;
  sdkVersion: string;
  inputTokens: number;
  cachedInputTokens: number;
  outputTokens: number;
}
export interface AcceptanceResponse {
  schemaVersion: 1;
  requestDigest: string;
  plan: Plan;
  provenance: AcceptanceProvenance;
  digest: string;
}
function keys(v: Record<string, unknown>, allowed: string[]): void {
  if (Object.keys(v).some((k) => !allowed.includes(k)))
    throw new RequestError("Unknown synthetic acceptance field.");
}
function bounded(value: unknown, max: number): Record<string, unknown> {
  const v = object(value);
  if (JSON.stringify(v).length > max)
    throw new RequestError("Synthetic acceptance envelope too large.");
  return v;
}
function hex(value: unknown, length: number): string {
  if (
    typeof value !== "string" ||
    value.length !== length ||
    !/^[a-f0-9]+$/u.test(value)
  )
    throw new RequestError("Invalid acceptance digest or identity.");
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
    v.scenario !== "signal-lamp-client-handoff-v1"
  )
    throw new RequestError("Unrecognized synthetic acceptance scenario.");
  if (typeof v.runId !== "string" || !/^[1-9][0-9]{0,19}$/u.test(v.runId))
    throw new RequestError("Invalid acceptance run ID.");
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
    throw new RequestError("Synthetic acceptance needs a connected actor.");
  return result;
}
function payload(value: unknown): Omit<AcceptanceRequest, "digest"> {
  const v = bounded(value, 4096);
  keys(v, [
    "schemaVersion",
    "run",
    "context",
    "worldId",
    "nonce",
    "createdAt",
    "expiresAt",
    "digest",
  ]);
  if (v.schemaVersion !== 1)
    throw new RequestError("Unsupported acceptance schema.");
  const createdAt = integer(
    v.createdAt,
    "createdAt",
    0,
    Number.MAX_SAFE_INTEGER - TTL,
  );
  if (v.expiresAt !== createdAt + TTL)
    throw new RequestError("Invalid acceptance expiry.");
  return {
    schemaVersion: 1,
    run: runIdentity(v.run),
    context: frame(v.context),
    worldId: identifier(v.worldId, "worldId"),
    nonce: hex(v.nonce, 64),
    createdAt,
    expiresAt: createdAt + TTL,
  };
}
export function createAcceptanceRequest(value: unknown): AcceptanceRequest {
  const v = object(value);
  keys(v, ["run", "context", "worldId", "nonce", "createdAt"]);
  const createdAt = integer(
    v.createdAt,
    "createdAt",
    0,
    Number.MAX_SAFE_INTEGER - TTL,
  );
  const body = payload({ ...v, schemaVersion: 1, expiresAt: createdAt + TTL });
  return { ...body, digest: digest(body) };
}
export function readAcceptanceRequest(
  value: unknown,
  expectedRun: unknown,
  now: number,
): AcceptanceRequest & { input: DesignRequest; memory: string } {
  const body = payload(value),
    v = object(value);
  if (
    JSON.stringify(body.run) !== JSON.stringify(runIdentity(expectedRun)) ||
    hex(v.digest, 64) !== digest(body)
  )
    throw new RequestError("Acceptance request identity changed.", 409);
  integer(now, "now", 0, Number.MAX_SAFE_INTEGER);
  if (now < body.createdAt || now >= body.expiresAt)
    throw new RequestError(
      "Acceptance request expired or clock moved backward.",
      409,
    );
  return {
    ...body,
    digest: v.digest as string,
    input: {
      request: REQUEST,
      worldId: body.worldId,
      dimension: "minecraft:overworld",
      projectId: body.context.projectId,
      anchor: { x: 0, y: 100, z: 0 },
      rotation: 0,
      maxSize: { x: 3, y: 2, z: 1 },
    },
    memory: JSON.stringify({
      project: {
        name: "Engineering project",
        projectRevision: body.context.projectRevision,
      },
      tasks: [],
    }),
  };
}
function planValue(value: unknown): Plan {
  const v = object(value);
  keys(v, ["template", "explanation", "parameters", "rotation"]);
  if (v.parameters !== undefined) keys(object(v.parameters), ["delay"]);
  return validatePlan(v);
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
    sdkVersion: v.sdkVersion,
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
export function createAcceptanceResponse(
  request: unknown,
  plan: unknown,
  provider: unknown,
): AcceptanceResponse {
  const v = object(request),
    body = readAcceptanceRequest(
      request,
      v.run,
      integer(v.createdAt, "createdAt", 0, Number.MAX_SAFE_INTEGER),
    );
  const result = {
    schemaVersion: 1 as const,
    requestDigest: body.digest,
    plan: planValue(plan),
    provenance: provenance(provider),
  };
  return { ...result, digest: digest(result) };
}
export function readAcceptanceResponse(
  value: unknown,
  request: unknown,
  now: number,
): AcceptanceResponse {
  const expected = object(request),
    current = readAcceptanceRequest(request, expected.run, now);
  const v = bounded(value, 8192);
  keys(v, ["schemaVersion", "requestDigest", "plan", "provenance", "digest"]);
  if (v.schemaVersion !== 1 || hex(v.requestDigest, 64) !== current.digest)
    throw new RequestError(
      "Acceptance response belongs to another request.",
      409,
    );
  const result = {
    schemaVersion: 1 as const,
    requestDigest: current.digest,
    plan: planValue(v.plan),
    provenance: provenance(v.provenance),
  };
  if (hex(v.digest, 64) !== digest(result))
    throw new RequestError("Acceptance response changed after receipt.", 409);
  return { ...result, digest: v.digest as string };
}
