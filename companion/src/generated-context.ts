import {
  actorId,
  gatewayContext,
  type GatewayContext,
} from "./gateway-types.js";
import { behaviorDigest, type Behavior } from "./generated.js";
import {
  integer,
  object,
  position,
  RequestError,
  type DesignRequest,
  type Position,
  type Rotation,
} from "./types.js";

export interface GeneratedAcceptance {
  schemaVersion: 1;
  proposalId: string;
  behaviorDigest: string;
  basis: Omit<GatewayContext, "requestId"> & { connectionId: string };
  worldId: string;
  dimension: string;
  corner1: Position;
  corner2: Position;
  rotation: Rotation;
  expiresAt: number;
}
function exact(value: unknown, fields: string[]): Record<string, unknown> {
  const v = object(value);
  if (
    Object.keys(v).length !== fields.length ||
    Object.keys(v).some((key) => !fields.includes(key))
  )
    throw new RequestError("Invalid generated acceptance fields.");
  return v;
}
function corner(value: unknown): Position {
  return position(exact(value, ["x", "y", "z"]));
}
/** Consistency checks for native-fenced consent; a nonce/digest is never authorization. */
export function validateGeneratedAcceptance(
  value: unknown,
  context: GatewayContext,
  input: DesignRequest,
  behavior: Behavior,
  now = Date.now(),
): GeneratedAcceptance {
  const v = exact(value, [
    "schemaVersion",
    "proposalId",
    "behaviorDigest",
    "basis",
    "worldId",
    "dimension",
    "corner1",
    "corner2",
    "rotation",
    "expiresAt",
  ]);
  if (v.schemaVersion !== 1)
    throw new RequestError("Unsupported generated acceptance version.");
  const proposalId = actorId(v.proposalId);
  const rawBasis = exact(v.basis, [
    "serverId",
    "serverEpoch",
    "actorId",
    "projectId",
    "projectRevision",
    "authorizationRevision",
    "connectionId",
  ]);
  const parsed = gatewayContext({ ...rawBasis, requestId: context.requestId });
  if (!context.connectionId || !parsed.connectionId)
    throw new RequestError(
      "Generated consent requires its current native connection.",
      403,
    );
  for (const field of [
    "serverId",
    "serverEpoch",
    "actorId",
    "projectId",
    "projectRevision",
    "authorizationRevision",
    "connectionId",
  ] as const)
    if (parsed[field] !== context[field])
      throw new RequestError(
        "Generated consent belongs to a stale or different actor/project connection.",
        409,
      );
  if (
    v.worldId !== input.worldId ||
    v.dimension !== input.dimension ||
    v.rotation !== input.rotation ||
    v.behaviorDigest !== behaviorDigest(behavior)
  )
    throw new RequestError(
      "Generated consent no longer matches its behavior, world or orientation.",
      409,
    );
  const expiresAt = integer(
    v.expiresAt,
    "acceptance expiry",
    1,
    Number.MAX_SAFE_INTEGER,
  );
  if (expiresAt <= now || expiresAt > now + 300_000)
    throw new RequestError(
      "Generated consent expired or exceeds its confirmation window.",
      409,
    );
  const corner1 = corner(v.corner1),
    corner2 = corner(v.corner2);
  const min = {
    x: Math.min(corner1.x, corner2.x),
    y: Math.min(corner1.y, corner2.y),
    z: Math.min(corner1.z, corner2.z),
  };
  const size = {
    x: Math.abs(corner1.x - corner2.x) + 1,
    y: Math.abs(corner1.y - corner2.y) + 1,
    z: Math.abs(corner1.z - corner2.z) + 1,
  };
  if (
    !input.maxSize ||
    (["x", "y", "z"] as const).some(
      (axis) =>
        input.anchor[axis] !== min[axis] || input.maxSize![axis] !== size[axis],
    )
  )
    throw new RequestError(
      "Generated consent no longer matches its delegated workspace.",
      409,
    );
  const { requestId: _request, ...basis } = parsed;
  return {
    schemaVersion: 1,
    proposalId,
    behaviorDigest: behaviorDigest(behavior),
    basis: { ...basis, connectionId: parsed.connectionId },
    worldId: input.worldId,
    dimension: input.dimension,
    corner1,
    corner2,
    rotation: input.rotation,
    expiresAt,
  };
}
