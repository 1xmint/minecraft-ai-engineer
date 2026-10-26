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
export interface DesignRequest {
  request: string;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
  projectId?: string;
  maxSize?: Position;
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
  stages: { id: string; title: string; explanation: string }[];
  bounds: Bounds;
  resources: Record<string, number>;
  ports: { name: string; pos: Position; kind: "input" | "output" }[];
  tests: { id: string; description: string }[];
  provenance: { kind: "curated"; template: string; minecraft: "26.3" };
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
export interface Observation {
  worldId: string;
  dimension: string;
  region: Bounds;
  blocks: { pos: Position; state: string }[];
  tick: number;
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
  template: "signal-lamp" | "unsupported";
  explanation: string;
}
export interface PlanningProvider {
  plan(
    input: DesignRequest,
    signal: AbortSignal,
    context?: string,
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
