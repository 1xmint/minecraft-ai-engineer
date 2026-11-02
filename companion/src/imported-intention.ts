import { isDeepStrictEqual } from "node:util";
import materials from "../../catalog/imported-materials.json" with { type: "json" };
import { parseBlockState } from "./schematics/index.js";
import { rotatePosition } from "./geometry.js";
import {
  object,
  integer,
  position,
  text,
  type Blueprint,
  type Rotation,
} from "./types.js";

function identifier(value: unknown, pattern: RegExp): string {
  if (typeof value !== "string" || !pattern.test(value))
    throw new TypeError("Invalid imported identity");
  return value;
}
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u;
const SHA = /^[a-f0-9]{64}$/u;
const horizontal = ["north", "east", "south", "west"],
  bool = ["true", "false"];
const properties: Record<string, Record<string, readonly string[]>> = {
  "minecraft:stone": {},
  "minecraft:redstone_lamp": { lit: bool },
  "minecraft:lever": {
    face: ["floor", "wall", "ceiling"],
    facing: horizontal,
    powered: bool,
  },
  "minecraft:repeater": {
    delay: ["1", "2", "3", "4"],
    facing: horizontal,
    locked: bool,
    powered: bool,
  },
  "minecraft:redstone_wall_torch": { facing: horizontal, lit: bool },
};

if (materials.schemaVersion !== 1 || materials.minecraft !== "26.3")
  throw new TypeError("Unsupported imported material snapshot");
for (const id of materials.ids) properties[id] = {};
/** Exact server-normalized geometry. Descriptive hashes grant no authority or behavior. */
export function readImportedIntention(
  value: unknown,
  context: { worldId: string; dimension: string; projectId: string },
): Blueprint {
  const source = object(value),
    provenance = object(source.provenance);
  const id = identifier(source.id, UUID),
    projectId = identifier(source.projectId, UUID);
  const revision = integer(source.revision, "revision", 1, 2147483647);
  const designId = integer(provenance.designId, "designId", 1, 2147483647);
  if (
    source.worldId !== context.worldId ||
    source.dimension !== context.dimension ||
    projectId !== context.projectId ||
    ![0, 90, 180, 270].includes(source.rotation as number)
  )
    throw new TypeError("Imported server context differs");
  const rotation = source.rotation as Rotation,
    anchor = position(source.anchor);
  if (
    !Array.isArray(source.blocks) ||
    source.blocks.length < 1 ||
    source.blocks.length > 4096
  )
    throw new TypeError("Invalid imported block count");
  const seen = new Set<string>(),
    resources: Record<string, number> = {};
  const blocks = source.blocks.map((value) => {
    const raw = object(value),
      pos = position(raw.pos),
      state = text(raw.state, "block state", 512);
    if (Math.max(Math.abs(pos.x), Math.abs(pos.y), Math.abs(pos.z)) > 64)
      throw new TypeError("Imported extent exceeds64");
    const key = `${pos.x},${pos.y},${pos.z}`;
    if (seen.has(key)) throw new TypeError("Duplicate imported position");
    seen.add(key);
    const parsed = parseBlockState(state),
      allowed = properties[parsed.name];
    if (
      !allowed ||
      Object.entries(parsed.properties).some(
        ([key, value]) => !allowed[key]?.includes(value),
      )
    )
      throw new TypeError(
        "Imported state is outside the supported build library",
      );
    const relative = rotatePosition(pos, rotation),
      x = anchor.x + relative.x,
      y = anchor.y + relative.y,
      z = anchor.z + relative.z;
    if (
      Math.abs(x) > 29999984 ||
      Math.abs(z) > 29999984 ||
      y < -2048 ||
      y > 2047
    )
      throw new TypeError("Imported placement exceeds world bounds");
    const item =
      parsed.name === "minecraft:redstone_wall_torch"
        ? "minecraft:redstone_torch"
        : parsed.name;
    resources[item] = (resources[item] ?? 0) + 1;
    return {
      pos,
      state,
      stage: "design",
      component: "root",
      prerequisites: [],
    };
  });
  const extent = (fn: (...values: number[]) => number) => ({
    x: fn(...blocks.map((b) => b.pos.x)),
    y: fn(...blocks.map((b) => b.pos.y)),
    z: fn(...blocks.map((b) => b.pos.z)),
  });
  const expected: Blueprint = {
    schemaVersion: 1,
    id,
    projectId,
    revision,
    worldId: context.worldId,
    dimension: context.dimension,
    name: `Imported design ${designId}`,
    explanation:
      "Imported geometry only. No functional behavior or verification is claimed.",
    anchor,
    rotation,
    verification: "untested",
    blocks,
    stages: [
      {
        id: "design",
        title: `Imported design ${designId}`,
        explanation:
          "Geometry imported from a schematic file. It is untrusted and untested until it is built and checked in game.",
        prerequisites: [],
      },
    ],
    bounds: { min: extent(Math.min), max: extent(Math.max) },
    resources,
    ports: [],
    tests: [],
    provenance: {
      kind: "schematic",
      designId,
      digest: identifier(provenance.digest, SHA),
      sourceSha256: identifier(provenance.sourceSha256, SHA),
    },
  };
  if (!isDeepStrictEqual(value, expected))
    throw new TypeError(
      "Imported source contains altered geometry metadata or behavior claims",
    );
  return expected;
}
