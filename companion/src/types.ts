import type {
  Behavior,
  BehaviorProposal,
  GeneratedGeometry,
  GeneratedRequest,
} from "./generated.js";
import type { GatewayContext } from "./gateway-types.js";
export interface Position {
  x: number;
  y: number;
  z: number;
}
export type Rotation = 0 | 90 | 180 | 270;
export interface Bounds {
  min: Position;
  max: Position;
}
export interface ComponentGroupNode {
  id: string;
  parent?: string;
  kind: "group";
}
export interface ComponentMechanismNode {
  id: string;
  parent: string;
  kind: "mechanism";
  template: Exclude<Plan["template"], "unsupported">;
  anchor: Position;
  rotation: Rotation;
  parameters: { delay: number };
  dependsOn: string[];
}
export type ComponentNode = ComponentGroupNode | ComponentMechanismNode;
export interface ComponentTree {
  schemaVersion: 1;
  nodes: ComponentNode[];
}
export interface DesignRequest {
  request: string;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
  projectId?: string;
  maxSize?: Position;
  /** Server-derived bound for a revision of an existing canonical design. */
  revision?: { template: string; rotation: Rotation };
}
export interface Blueprint {
  schemaVersion: 1;
  id: string;
  revision: number;
  projectId: string;
  worldId: string;
  dimension: string;
  name: string;
  explanation: string;
  anchor: Position;
  rotation: Rotation;
  blocks: {
    pos: Position;
    state: string;
    stage: string;
    component: string;
    prerequisites: string[];
  }[];
  stages: {
    id: string;
    title: string;
    explanation: string;
    prerequisites?: string[];
  }[];
  bounds: Bounds;
  resources: Record<string, number>;
  ports: { name: string; pos: Position; kind: "input" | "output" }[];
  tests: { id: string; description: string }[];
  provenance:
    | {
        kind: "curated";
        template: string;
        minecraft: "26.3";
        catalogRevision?: number;
      }
    | {
        kind: "generated";
        minecraft: "26.3";
        generatorRevision: 1 | 2;
        behaviorDigest: string;
        template?: never;
        catalogRevision?: never;
      };
  behavior?: Behavior;
  components?: ComponentTree;
  parameters?: { delay: number };
  verification: "untested";
}
export const TASK_STATES = [
  "PENDING",
  "PLANNING",
  "READY",
  "EXECUTING",
  "VERIFYING",
  "COMPLETED",
  "PAUSED",
  "BLOCKED",
  "FAILED",
  "CANCELLED",
] as const;
export type TaskState = (typeof TASK_STATES)[number];
export interface TargetedPerception {
  schemaVersion: 1;
  kind: "block" | "view";
  actorId: string;
  player: { position: Position; eye: Position; look: Position };
  target: Position;
  face?: "north" | "south" | "east" | "west" | "up" | "down";
  differences: { missing: number; changed: number; unplanned: number };
}
export interface Observation {
  worldId: string;
  dimension: string;
  region: Bounds;
  blocks: { pos: Position; state: string }[];
  tick: number;
  perception?: TargetedPerception;
}
export interface Checkpoint {
  worldId: string;
  dimension: string;
  projectId: string;
  taskId: string;
  blueprintRevision?: number;
  status: TaskState;
  detail: string;
  observed?: unknown;
  verified?: unknown;
}
export interface Plan {
  template: "signal-lamp" | "not-gate" | "or-gate" | "unsupported";
  explanation: string;
  parameters?: { delay: number };
  rotation?: Rotation;
}
export interface PlanningProvider {
  proposeBehavior?(
    input: DesignRequest,
    signal: AbortSignal,
    context?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<BehaviorProposal>;
  generateGeometry?(
    input: GeneratedRequest,
    signal: AbortSignal,
    context?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<GeneratedGeometry>;
  plan(
    input: DesignRequest,
    signal: AbortSignal,
    context?: string,
    authority?: Readonly<GatewayContext>,
  ): Promise<Plan>;
}
export class RequestError extends Error {
  constructor(
    message: string,
    public readonly status = 400,
  ) {
    super(message);
  }
}
export function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new RequestError("Expected a JSON object.");
  return value as Record<string, unknown>;
}
export function text(value: unknown, name: string, max = 256): string {
  if (
    typeof value !== "string" ||
    !value.trim() ||
    value.length > max ||
    /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/u.test(value)
  )
    throw new RequestError(`Invalid ${name}.`);
  return value;
}
export function identifier(value: unknown, name: string): string {
  const result = text(value, name, 128);
  if (!/^[a-zA-Z0-9_.:-]+$/u.test(result))
    throw new RequestError(`Invalid ${name}.`);
  return result;
}
export function integer(
  value: unknown,
  name: string,
  min: number,
  max: number,
): number {
  if (
    !Number.isSafeInteger(value) ||
    Number(value) < min ||
    Number(value) > max
  )
    throw new RequestError(`Invalid ${name}.`);
  return Number(value);
}
export function position(value: unknown): Position {
  const v = object(value);
  return {
    x: integer(v.x, "x", -29_999_984, 29_999_984),
    y: integer(v.y, "y", -2048, 2048),
    z: integer(v.z, "z", -29_999_984, 29_999_984),
  };
}
export function bounds(value: unknown): Bounds {
  const v = object(value),
    min = position(v.min),
    max = position(v.max);
  if (
    min.x > max.x ||
    min.y > max.y ||
    min.z > max.z ||
    (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1) > 4096
  )
    throw new RequestError(
      "Region must be ordered and contain at most 4096 blocks.",
    );
  return { min, max };
}
export function inside(pos: Position, region: Bounds): boolean {
  return (
    pos.x >= region.min.x &&
    pos.x <= region.max.x &&
    pos.y >= region.min.y &&
    pos.y <= region.max.y &&
    pos.z >= region.min.z &&
    pos.z <= region.max.z
  );
}
export function validateDesignRequest(value: unknown): DesignRequest {
  const v = object(value),
    rotation = v.rotation ?? 0;
  if (
    ![0, 90, 180, 270].includes(Number(rotation)) ||
    typeof rotation !== "number"
  )
    throw new RequestError("Rotation must be 0, 90, 180 or 270.");
  const result: DesignRequest = {
    request: text(v.request, "request", 4000),
    worldId: identifier(v.worldId, "worldId"),
    dimension: identifier(v.dimension, "dimension"),
    anchor: position(v.anchor),
    rotation: rotation as Rotation,
  };
  if (v.projectId !== undefined)
    result.projectId = identifier(v.projectId, "projectId");
  if (v.maxSize !== undefined) {
    const s = object(v.maxSize);
    result.maxSize = {
      x: integer(s.x, "size x", 1, 128),
      y: integer(s.y, "size y", 1, 128),
      z: integer(s.z, "size z", 1, 128),
    };
  }
  return result;
}
function perceptionKeys(
  value: Record<string, unknown>,
  allowed: string[],
): void {
  if (Object.keys(value).some((key) => !allowed.includes(key)))
    throw new RequestError("Unknown targeted perception field.");
}
function finiteVector(value: unknown, maximum: number): Position {
  const v = object(value);
  perceptionKeys(v, ["x", "y", "z"]);
  for (const axis of ["x", "y", "z"] as const)
    if (
      typeof v[axis] !== "number" ||
      !Number.isFinite(v[axis]) ||
      Math.abs(v[axis]) > maximum
    )
      throw new RequestError("Invalid perception vector.");
  return { x: v.x as number, y: v.y as number, z: v.z as number };
}
function validatePerception(
  value: unknown,
  region: Bounds,
  blocks: Observation["blocks"],
): TargetedPerception {
  const v = object(value);
  perceptionKeys(v, [
    "schemaVersion",
    "kind",
    "actorId",
    "player",
    "target",
    "face",
    "differences",
  ]);
  if (v.schemaVersion !== 1 || (v.kind !== "block" && v.kind !== "view"))
    throw new RequestError("Unsupported targeted perception schema or kind.");
  const actor = text(v.actorId, "perception actor", 36).toLowerCase();
  if (
    !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u.test(
      actor,
    )
  )
    throw new RequestError("Invalid perception actor UUID.");
  const self = object(v.player);
  perceptionKeys(self, ["position", "eye", "look"]);
  const player = {
    position: finiteVector(self.position, 29_999_984),
    eye: finiteVector(self.eye, 29_999_984),
    look: finiteVector(self.look, 1),
  };
  if (
    Math.abs(player.position.y) > 2048 ||
    Math.abs(player.eye.y) > 2052 ||
    Math.abs(player.position.x - player.eye.x) > 0.01 ||
    Math.abs(player.position.z - player.eye.z) > 0.01 ||
    player.eye.y - player.position.y < 0 ||
    player.eye.y - player.position.y > 4 ||
    Math.abs(Math.hypot(player.look.x, player.look.y, player.look.z) - 1) > 0.01
  )
    throw new RequestError(
      "Invalid actual player pose or normalized view direction.",
    );
  perceptionKeys(object(v.target), ["x", "y", "z"]);
  const target = position(v.target);
  if (
    !inside(target, region) ||
    blocks.length > 7 ||
    !blocks.some(
      (block) =>
        block.pos.x === target.x &&
        block.pos.y === target.y &&
        block.pos.z === target.z,
    )
  )
    throw new RequestError(
      "Perception target must be in its bounded state sample.",
    );
  const raw = object(v.differences);
  perceptionKeys(raw, ["missing", "changed", "unplanned"]);
  const differences = {
    missing: integer(raw.missing, "missing", 0, blocks.length),
    changed: integer(raw.changed, "changed", 0, blocks.length),
    unplanned: integer(raw.unplanned, "unplanned", 0, blocks.length),
  };
  if (
    differences.missing + differences.changed + differences.unplanned >
    blocks.length
  )
    throw new RequestError("Perception difference counts exceed the sample.");
  const faces = ["north", "south", "east", "west", "up", "down"];
  if (
    v.kind === "view" ? !faces.includes(String(v.face)) : v.face !== undefined
  )
    throw new RequestError("Invalid observed hit face.");
  return {
    schemaVersion: 1,
    kind: v.kind,
    actorId: actor,
    player,
    target,
    differences,
    ...(v.kind === "view"
      ? { face: v.face as NonNullable<TargetedPerception["face"]> }
      : {}),
  };
}
export function validateObservation(value: unknown): Observation {
  const v = object(value),
    region = bounds(v.region);
  if (!Array.isArray(v.blocks) || v.blocks.length > 4096)
    throw new RequestError("Invalid observation blocks.");
  const seen = new Set<string>();
  const blocks = v.blocks.map((entry: unknown) => {
    const b = object(entry),
      pos = position(b.pos),
      key = `${pos.x},${pos.y},${pos.z}`;
    if (!inside(pos, region) || seen.has(key))
      throw new RequestError(
        "Observation contains duplicate or out-of-region blocks.",
      );
    seen.add(key);
    const state = text(b.state, "block state", 512);
    if (!/^minecraft:[a-z0-9_]+(?:\[[a-z0-9_=,]+\])?$/u.test(state))
      throw new RequestError("Invalid block state syntax.");
    return { pos, state };
  });
  return {
    worldId: identifier(v.worldId, "worldId"),
    dimension: identifier(v.dimension, "dimension"),
    region,
    blocks,
    tick: integer(v.tick, "tick", 0, Number.MAX_SAFE_INTEGER),
    ...(v.perception === undefined
      ? {}
      : { perception: validatePerception(v.perception, region, blocks) }),
  };
}
export function validateCheckpoint(value: unknown): Checkpoint {
  const v = object(value);
  if (!TASK_STATES.includes(v.status as TaskState))
    throw new RequestError("Invalid task status.");
  if (v.verified !== undefined && v.blueprintRevision === undefined)
    throw new RequestError(
      "Functional evidence requires its blueprintRevision.",
    );
  return {
    worldId: identifier(v.worldId, "worldId"),
    dimension: identifier(v.dimension, "dimension"),
    projectId: identifier(v.projectId, "projectId"),
    taskId: identifier(v.taskId, "taskId"),
    ...(v.blueprintRevision === undefined
      ? {}
      : {
          blueprintRevision: integer(
            v.blueprintRevision,
            "blueprintRevision",
            1,
            Number.MAX_SAFE_INTEGER,
          ),
        }),
    status: v.status as TaskState,
    detail: text(v.detail, "detail", 4000),
    ...(v.observed === undefined ? {} : { observed: v.observed }),
    ...(v.verified === undefined ? {} : { verified: v.verified }),
  };
}
