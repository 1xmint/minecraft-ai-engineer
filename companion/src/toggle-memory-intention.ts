import { isDeepStrictEqual } from "node:util";
import definition from "../../catalog/toggle-memory.json" with { type: "json" };
import { rotatePosition } from "./geometry.js";
import type { Position, Rotation } from "./types.js";

export type ToggleMemoryIntention = typeof definition & {
  schemaVersion: 1;
  id: string;
  projectId: string;
  revision: number;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
};

export interface ToggleMemoryIntentionInput {
  id: string;
  projectId: string;
  revision: number;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
}

export interface ToggleMemoryIntentionContext {
  worldId: string;
  dimension: string;
  projectId: string;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
const WORLD_ID = /^[a-zA-Z0-9_.:-]{1,256}$/u;
const DIMENSION = /^[a-z0-9_.-]+:[a-z0-9_./-]{1,240}$/u;
const MAX_HORIZONTAL = 29_999_984;
const MIN_Y = -2048;
const MAX_Y = 2047;

function fail(message: string): never {
  throw new TypeError(message);
}

function record(value: unknown, name: string): Record<string, unknown> {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    (Object.getPrototypeOf(value) !== Object.prototype &&
      Object.getPrototypeOf(value) !== null)
  )
    return fail(`Invalid ${name}.`);
  return value as Record<string, unknown>;
}

function uuid(value: unknown, name: string): string {
  if (typeof value !== "string" || !UUID.test(value))
    return fail(`Invalid ${name} UUID.`);
  return value;
}

function identityText(value: unknown, pattern: RegExp, name: string): string {
  if (typeof value !== "string" || !pattern.test(value))
    return fail(`Invalid ${name}.`);
  return value;
}

function dimension(value: unknown): string {
  if (typeof value !== "string" || value.length > 256 || !DIMENSION.test(value))
    return fail("Invalid dimension.");
  return value;
}

function safeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value))
    return fail(`Invalid ${name}.`);
  return value;
}

function position(value: unknown, name: string): Position {
  const input = record(value, name);
  if (
    !Object.keys(input).every((key) => ["x", "y", "z"].includes(key)) ||
    !["x", "y", "z"].every((key) => Object.hasOwn(input, key))
  )
    return fail(`Invalid ${name}.`);
  const result = {
    x: safeInteger(input.x, `${name}.x`),
    y: safeInteger(input.y, `${name}.y`),
    z: safeInteger(input.z, `${name}.z`),
  };
  if (
    Math.abs(result.x) > MAX_HORIZONTAL ||
    Math.abs(result.z) > MAX_HORIZONTAL ||
    result.y < MIN_Y ||
    result.y > MAX_Y
  )
    return fail(`${name} exceeds supported world bounds.`);
  return result;
}

function rotation(value: unknown): Rotation {
  if (typeof value !== "number" || ![0, 90, 180, 270].includes(value))
    return fail("Invalid rotation.");
  return value as Rotation;
}

function validateFootprint(anchor: Position, angle: Rotation): void {
  for (const cell of definition.blocks) {
    const relative = rotatePosition(cell.pos, angle);
    const x = anchor.x + relative.x;
    const y = anchor.y + relative.y;
    const z = anchor.z + relative.z;
    if (
      Math.abs(x) > MAX_HORIZONTAL ||
      Math.abs(z) > MAX_HORIZONTAL ||
      y < MIN_Y ||
      y > MAX_Y
    )
      fail("Toggle-memory source exceeds supported world bounds.");
  }
}

/** Exact intended data only; this contract carries no owner authority or functional pass. */
export function createToggleMemoryIntention(
  input: ToggleMemoryIntentionInput,
): ToggleMemoryIntention {
  const id = uuid(input.id, "intention"),
    projectId = uuid(input.projectId, "project"),
    revision = safeInteger(input.revision, "revision");
  if (revision < 1 || revision > 2_147_483_647) fail("Invalid revision.");
  const worldId = identityText(input.worldId, WORLD_ID, "world id");
  const dimensionId = dimension(input.dimension);
  const anchor = position(input.anchor, "anchor");
  const angle = rotation(input.rotation);
  validateFootprint(anchor, angle);
  return {
    schemaVersion: 1,
    id,
    projectId,
    revision,
    worldId,
    dimension: dimensionId,
    anchor,
    rotation: angle,
    ...structuredClone(definition),
  };
}

/** Regenerate the canonical recipe and require the current identity context. */
export function readToggleMemoryIntention(
  value: unknown,
  context: ToggleMemoryIntentionContext,
): ToggleMemoryIntention {
  const source = record(value, "toggle-memory intention");
  const anchor = position(source.anchor, "anchor");
  const expected = createToggleMemoryIntention({
    id: uuid(source.id, "intention"),
    projectId: uuid(source.projectId, "project"),
    revision: safeInteger(source.revision, "revision"),
    worldId: identityText(source.worldId, WORLD_ID, "world id"),
    dimension: dimension(source.dimension),
    anchor,
    rotation: rotation(source.rotation),
  });
  const supplied = record(context, "toggle-memory context");
  if (
    !isDeepStrictEqual(value, expected) ||
    supplied.worldId !== expected.worldId ||
    supplied.dimension !== expected.dimension ||
    supplied.projectId !== expected.projectId
  )
    fail(
      "Toggle-memory source differs from canonical intention or current identity.",
    );
  return expected;
}
