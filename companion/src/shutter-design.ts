import raw from "../../catalog/shutter.json" with { type: "json" };
import { randomUUID } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { rotatePosition } from "./geometry.js";
import {
  object,
  text,
  integer,
  position,
  RequestError,
  type Blueprint,
  type DesignRequest,
  type Position,
  type Rotation,
} from "./types.js";

export interface ShutterMetadata {
  schemaVersion: 1;
  function: "shutter";
  piston: Position;
  payload: Position;
  input: "input";
  output: "aperture";
  off: "open";
  on: "closed";
  settleGameTicks: 8;
  stableGameTicks: 8;
}
export type ShutterDesign = Blueprint & { mechanical: ShutterMetadata };
export const SHUTTER_OPTION = Object.freeze({
  template: "sticky-shutter" as const,
  name: raw.name,
  explanation: raw.explanation,
  maxDelay: 1,
});
if (
  raw.schemaVersion !== 1 ||
  raw.minecraft !== "26.3" ||
  raw.revision !== 1 ||
  raw.id !== "sticky-shutter" ||
  raw.mechanical.function !== "shutter" ||
  raw.mechanical.settleGameTicks !== 8 ||
  raw.mechanical.stableGameTicks !== 8 ||
  raw.mechanical.off !== "open" ||
  raw.mechanical.on !== "closed"
)
  throw new Error("Unsupported mechanical recipe revision");
const uuid = (value: unknown, name: string) => {
  const id = text(value, name, 36);
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(id)
  )
    throw new RequestError(`Invalid ${name}.`);
  return id;
};
function rotation(value: unknown): Rotation {
  if (typeof value !== "number" || ![0, 90, 180, 270].includes(value))
    throw new RequestError("Invalid shutter rotation.");
  return value as Rotation;
}
function worldPos(value: unknown): Position {
  const p = position(value);
  if (p.y > 2047)
    throw new RequestError("Shutter exceeds supported world height.");
  return p;
}
function corners(angle: Rotation) {
  return [0, 3].flatMap((x) =>
    [0, 1].map((y) => rotatePosition({ x, y, z: 0 }, angle)),
  );
}
function extent(angle: Rotation) {
  const points = corners(angle);
  return {
    min: {
      x: Math.min(...points.map((p) => p.x)),
      y: 0,
      z: Math.min(...points.map((p) => p.z)),
    },
    max: {
      x: Math.max(...points.map((p) => p.x)),
      y: 1,
      z: Math.max(...points.map((p) => p.z)),
    },
  };
}
function canonical(value: {
  id: string;
  revision: number;
  projectId: string;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
}): ShutterDesign {
  const anchor = worldPos(value.anchor),
    angle = rotation(value.rotation);
  for (const point of corners(angle))
    worldPos({
      x: anchor.x + point.x,
      y: anchor.y + point.y,
      z: anchor.z + point.z,
    });
  const worldId = text(value.worldId, "worldId", 256),
    dimension = text(value.dimension, "dimension", 256);
  if (
    !/^[a-zA-Z0-9_.:-]+$/u.test(worldId) ||
    !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/u.test(dimension)
  )
    throw new RequestError("Invalid shutter world identity.");
  return {
    schemaVersion: 1,
    id: uuid(value.id, "blueprint id"),
    revision: integer(value.revision, "revision", 1, 2147483647),
    projectId: uuid(value.projectId, "project id"),
    worldId,
    dimension,
    name: raw.name,
    explanation: raw.explanation,
    anchor,
    rotation: angle,
    blocks: raw.blocks.map((block) => ({
      ...block,
      pos: position(block.pos),
      prerequisites: [...block.prerequisites],
    })),
    stages: raw.stages.map((stage) => ({
      ...stage,
      prerequisites: [...stage.prerequisites],
    })),
    bounds: structuredClone(raw.bounds),
    resources: structuredClone(raw.resources),
    ports: raw.ports.map((port) => ({
      ...port,
      pos: position(port.pos),
      kind: port.kind as "input" | "output",
    })),
    tests: structuredClone(raw.tests),
    provenance: {
      kind: "curated",
      template: raw.id,
      minecraft: "26.3",
      catalogRevision: raw.revision,
    },
    mechanical: structuredClone(raw.mechanical) as ShutterMetadata,
    verification: "untested",
  };
}
/** Input anchor is the minimum of the delegated workspace, including the aperture's reserved air. */
export function makeShutterDesign(
  input: DesignRequest,
  identity: { id?: string; revision?: number } = {},
): ShutterDesign {
  const angle = rotation(input.rotation),
    box = extent(angle),
    min = worldPos(input.anchor);
  if (input.maxSize)
    for (const axis of ["x", "y", "z"] as const)
      if (
        integer(input.maxSize[axis], "workspace size", 1, 129) <
        box.max[axis] - box.min[axis] + 1
      )
        throw new RequestError(
          "The shutter and its movement space do not fit.",
          422,
        );
  return canonical({
    id: identity.id ?? randomUUID(),
    revision: identity.revision ?? 1,
    projectId: input.projectId ?? randomUUID(),
    worldId: input.worldId,
    dimension: input.dimension,
    anchor: { x: min.x - box.min.x, y: min.y, z: min.z - box.min.z },
    rotation: angle,
  });
}
/** Exact recipe-derived metadata, not a permissive registry expansion or functional verification. */
export function readShutterDesign(value: unknown): ShutterDesign {
  const v = object(value);
  if (JSON.stringify(v).length > 32768)
    throw new RequestError("Shutter design exceeds its bounded schema.");
  const expected = canonical({
    id: uuid(v.id, "blueprint id"),
    revision: integer(v.revision, "revision", 1, 2147483647),
    projectId: uuid(v.projectId, "project id"),
    worldId: text(v.worldId, "worldId", 256),
    dimension: text(v.dimension, "dimension", 256),
    anchor: worldPos(v.anchor),
    rotation: rotation(v.rotation),
  });
  if (!isDeepStrictEqual(v, expected))
    throw new RequestError(
      "Shutter geometry, behavior or metadata differs from its canonical recipe.",
    );
  return expected;
}
export function shutterFrame(value: unknown) {
  const design = readShutterDesign(value),
    absolute = (p: Position) => {
      const r = rotatePosition(p, design.rotation);
      return worldPos({
        x: design.anchor.x + r.x,
        y: design.anchor.y + r.y,
        z: design.anchor.z + r.z,
      });
    };
  return {
    piston: absolute(design.mechanical.piston),
    payload: absolute(design.mechanical.payload),
    input: absolute(design.ports[0]!.pos),
    aperture: absolute(design.ports[1]!.pos),
    facing: (["east", "south", "west", "north"] as const)[
      design.rotation / 90
    ]!,
  };
}
