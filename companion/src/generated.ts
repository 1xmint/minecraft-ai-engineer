import { createHash, randomUUID } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { rotatePosition } from "./geometry.js";
import type {
  Blueprint,
  DesignRequest,
  Position,
  Rotation,
} from "./types.js";
import { RequestError, integer, object, position, text } from "./types.js";

export interface Behavior {
  schemaVersion: 1;
  function: "buffer";
  risingGameTicks: number;
  fallingGameTicks: number;
}
export interface BehaviorProposal {
  behavior: Behavior | null;
  explanation: string;
}
export interface GeneratedGeometry {
  name: string;
  explanation: string;
  blocks: { pos: Position; state: string }[];
  input: Position;
  output: Position;
}
export interface GeneratedRequest extends DesignRequest {
  behavior: Behavior;
}

const strictObject = (value: unknown, allowed: string[]): Record<string, unknown> => {
  const v = object(value);
  if (Object.keys(v).some((key) => !allowed.includes(key)))
    throw new RequestError("Unknown generated data field.");
  return v;
};
const nullableBehaviorSchema = {
  anyOf: [
    { type: "null" },
    {
      type: "object",
      additionalProperties: false,
      required: ["schemaVersion", "function", "risingGameTicks", "fallingGameTicks"],
      properties: {
        schemaVersion: { const: 1 },
        function: { const: "buffer" },
        risingGameTicks: { type: "integer", minimum: 1, maximum: 128 },
        fallingGameTicks: { type: "integer", minimum: 1, maximum: 128 },
      },
    },
  ],
} as const;
export const BEHAVIOR_PROPOSAL_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["behavior", "explanation"],
  properties: {
    behavior: nullableBehaviorSchema,
    explanation: { type: "string", minLength: 1, maxLength: 4000 },
  },
} as const;
export const GENERATED_GEOMETRY_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["name", "explanation", "blocks", "input", "output"],
  properties: {
    name: { type: "string", minLength: 1, maxLength: 256 },
    explanation: { type: "string", minLength: 1, maxLength: 4000 },
    blocks: {
      type: "array",
      minItems: 1,
      maxItems: 4096,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["pos", "state"],
        properties: {
          pos: {
            type: "object",
            additionalProperties: false,
            required: ["x", "y", "z"],
            properties: Object.fromEntries(["x", "y", "z"].map((axis) => [axis, { type: "integer", minimum: -64, maximum: 64 }])),
          },
          state: { type: "string", minLength: 1, maxLength: 512 },
        },
      },
    },
    input: { $ref: "#/$defs/position" },
    output: { $ref: "#/$defs/position" },
  },
  $defs: {
    position: {
      type: "object",
      additionalProperties: false,
      required: ["x", "y", "z"],
      properties: Object.fromEntries(["x", "y", "z"].map((axis) => [axis, { type: "integer", minimum: -64, maximum: 64 }])),
    },
  },
} as const;

export function validateBehavior(value: unknown): Behavior {
  const v = strictObject(value, ["schemaVersion", "function", "risingGameTicks", "fallingGameTicks"]);
  if (v.schemaVersion !== 1 || v.function !== "buffer")
    throw new RequestError("Unsupported generated behavior.");
  return {
    schemaVersion: 1,
    function: "buffer",
    risingGameTicks: integer(v.risingGameTicks, "risingGameTicks", 1, 128),
    fallingGameTicks: integer(v.fallingGameTicks, "fallingGameTicks", 1, 128),
  };
}
export function behaviorDigest(value: unknown): string {
  const b = validateBehavior(value);
  return createHash("sha256")
    .update(`buffer-v1|${b.risingGameTicks}|${b.fallingGameTicks}`, "utf8")
    .digest("hex");
}
export function validateBehaviorProposal(value: unknown): BehaviorProposal {
  const v = strictObject(value, ["behavior", "explanation"]);
  return {
    behavior: v.behavior === null ? null : validateBehavior(v.behavior),
    explanation: text(v.explanation, "behavior explanation", 4000),
  };
}

type ParsedState = { name: string; state: string; props: Record<string, string> };
function parseState(value: unknown): ParsedState {
  const raw = text(value, "block state", 512);
  const match = /^(minecraft:(?:stone|lever|repeater|redstone_lamp|redstone_wall_torch))(?:\[([^\]]*)\])?$/u.exec(raw);
  if (!match) throw new RequestError("Unsupported generated block state.");
  const name = match[1]!, props: Record<string, string> = {};
  if (match[2] !== undefined) for (const pair of match[2].split(",")) {
    const p = /^([a-z]+)=([a-z0-9_]+)$/u.exec(pair);
    if (!p || props[p[1]!] !== undefined) throw new RequestError("Invalid or duplicate block property.");
    props[p[1]!] = p[2]!;
  }
  const allowed: Record<string, string[]> = {
    "minecraft:stone": [], "minecraft:lever": ["face", "facing", "powered"],
    "minecraft:repeater": ["delay", "facing", "locked", "powered"],
    "minecraft:redstone_lamp": ["lit"], "minecraft:redstone_wall_torch": ["facing", "lit"],
  };
  if (Object.keys(props).some((key) => !allowed[name]!.includes(key))) throw new RequestError("Unknown block state property.");
  const directions = ["north", "east", "south", "west"];
  if (name === "minecraft:stone" && Object.keys(props).length) throw new RequestError("Stone has no properties.");
  if (name === "minecraft:lever") {
    const face = props.face ?? "floor", facing = props.facing ?? "north", powered = props.powered ?? "false";
    if (!["floor", "ceiling", "wall"].includes(face) || !directions.includes(facing) || !["true", "false"].includes(powered)) throw new RequestError("Invalid lever state.");
    Object.assign(props, { face, facing, powered });
  }
  if (name === "minecraft:repeater") {
    const delay = props.delay ?? "1", facing = props.facing ?? "north", locked = props.locked ?? "false", powered = props.powered ?? "false";
    if (!/^[1-4]$/u.test(delay) || !directions.includes(facing) || !["true", "false"].includes(locked) || !["true", "false"].includes(powered)) throw new RequestError("Invalid repeater state.");
    Object.assign(props, { delay, facing, locked, powered });
  }
  if (name === "minecraft:redstone_lamp") {
    const lit = props.lit ?? "false";
    if (!["true", "false"].includes(lit)) throw new RequestError("Invalid lamp state.");
    props.lit = lit;
  }
  if (name === "minecraft:redstone_wall_torch") {
    const facing = props.facing, lit = props.lit ?? "true";
    if (!facing || !directions.includes(facing) || !["true", "false"].includes(lit)) throw new RequestError("Invalid wall torch state.");
    Object.assign(props, { facing, lit });
  }
  const state = Object.keys(props).length ? `${name}[${Object.entries(props).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => `${k}=${v}`).join(",")}]` : name;
  return { name, state, props };
}
function rawPosition(value: unknown): Position {
  const v = strictObject(value, ["x", "y", "z"]);
  return { x: integer(v.x, "x", -64, 64), y: integer(v.y, "y", -64, 64), z: integer(v.z, "z", -64, 64) };
}
function validateGeometry(value: unknown): GeneratedGeometry {
  const v = strictObject(value, ["name", "explanation", "blocks", "input", "output"]);
  if (!Array.isArray(v.blocks) || v.blocks.length < 1 || v.blocks.length > 4096) throw new RequestError("Invalid generated block list.");
  const seen = new Set<string>(), blocks = v.blocks.map((entry) => {
    const b = strictObject(entry, ["pos", "state"]), pos = rawPosition(b.pos), key = `${pos.x},${pos.y},${pos.z}`;
    if (seen.has(key)) throw new RequestError("Duplicate generated block position.");
    seen.add(key); return { pos, state: parseState(b.state).state };
  });
  const input = rawPosition(v.input), output = rawPosition(v.output), at = (p: Position) => blocks.find((b) => b.pos.x === p.x && b.pos.y === p.y && b.pos.z === p.z);
  const lever = at(input), lamp = at(output);
  if (!lever || !lever.state.startsWith("minecraft:lever[") || !lamp || !lamp.state.startsWith("minecraft:redstone_lamp[")) throw new RequestError("Generated ports must identify their unique lever and lamp blocks.");
  if (blocks.filter((b) => b.state.startsWith("minecraft:lever[")).length !== 1 || blocks.filter((b) => b.state.startsWith("minecraft:redstone_lamp[")).length !== 1) throw new RequestError("Generated geometry must contain exactly one lever and lamp.");
  const support = new Set(blocks.filter((b) => b.state === "minecraft:stone").map((b) => `${b.pos.x},${b.pos.y},${b.pos.z}`));
  for (const b of blocks) {
    const s = parseState(b.state), { x, y, z } = b.pos;
    let required: Position | undefined;
    if (s.name === "minecraft:repeater") required = { x, y: y - 1, z };
    else if (s.name === "minecraft:lever") {
      if (s.props.face === "floor") required = { x, y: y - 1, z };
      else if (s.props.face === "ceiling") required = { x, y: y + 1, z };
      else required = offset({ x, y, z }, s.props.facing!, -1);
    } else if (s.name === "minecraft:redstone_wall_torch") required = offset({ x, y, z }, s.props.facing!, -1);
    if (required && !support.has(`${required.x},${required.y},${required.z}`)) throw new RequestError("Generated mechanism is missing required stone support.");
  }
  return { name: text(v.name, "generated name", 256), explanation: text(v.explanation, "generated explanation", 4000), blocks, input, output };
}
function offset(p: Position, direction: string, amount: number): Position {
  const d: Record<string, Position> = { north: { x: 0, y: 0, z: -1 }, east: { x: 1, y: 0, z: 0 }, south: { x: 0, y: 0, z: 1 }, west: { x: -1, y: 0, z: 0 } };
  return { x: p.x + d[direction]!.x * amount, y: p.y, z: p.z + d[direction]!.z * amount };
}
export function validateGeneratedGeometry(value: unknown): GeneratedGeometry { return validateGeometry(value); }
function getBounds(blocks: { pos: Position }[]) {
  return { min: { x: Math.min(...blocks.map((b) => b.pos.x)), y: Math.min(...blocks.map((b) => b.pos.y)), z: Math.min(...blocks.map((b) => b.pos.z)) }, max: { x: Math.max(...blocks.map((b) => b.pos.x)), y: Math.max(...blocks.map((b) => b.pos.y)), z: Math.max(...blocks.map((b) => b.pos.z)) } };
}
function derive(input: DesignRequest, geom: GeneratedGeometry, behavior: Behavior, identity?: { id: string; revision: number; projectId: string }): Blueprint {
  const rotated = getBounds(geom.blocks.map((b) => ({ pos: rotatePosition(b.pos, input.rotation) }))), size = { x: rotated.max.x - rotated.min.x + 1, y: rotated.max.y - rotated.min.y + 1, z: rotated.max.z - rotated.min.z + 1 };
  if (input.maxSize && (size.x > input.maxSize.x || size.y > input.maxSize.y || size.z > input.maxSize.z)) throw new RequestError("Generated mechanism does not fit the selected dimensions.", 422);
  const anchor = position({ x: input.anchor.x - rotated.min.x, y: input.anchor.y - rotated.min.y, z: input.anchor.z - rotated.min.z });
  for (const b of geom.blocks) { const p = rotatePosition(b.pos, input.rotation), abs = position({ x: anchor.x + p.x, y: anchor.y + p.y, z: anchor.z + p.z }); if (abs.y > 2047) throw new RequestError("Generated mechanism exceeds supported world bounds."); }
  const support = geom.blocks.filter((b) => b.state === "minecraft:stone").sort(sortXYZ), devices = geom.blocks.filter((b) => b.state !== "minecraft:stone").sort(sortXYZ);
  const blocks: Blueprint["blocks"] = [...support.map((b) => ({ ...b, pos: { ...b.pos }, stage: "support", component: "root", prerequisites: [] })), ...devices.map((b) => ({ ...b, pos: { ...b.pos }, stage: "devices", component: "root", prerequisites: ["support"] }))];
  const resources: Record<string, number> = {};
  for (const b of blocks) { const base = b.state.split("[", 1)[0]!, item = base === "minecraft:redstone_wall_torch" ? "minecraft:redstone_torch" : base; resources[item] = (resources[item] ?? 0) + 1; }
  return {
    schemaVersion: 1, id: identity?.id ?? randomUUID(), revision: identity?.revision ?? 1, projectId: identity?.projectId ?? input.projectId ?? randomUUID(), worldId: input.worldId, dimension: input.dimension,
    name: geom.name, explanation: geom.explanation, anchor, rotation: input.rotation, blocks,
    stages: [{ id: "support", title: "Build the support", explanation: "Place the stone supports for the generated mechanism.", prerequisites: [] }, { id: "devices", title: "Install the devices", explanation: "Place the lever, repeaters and output lamp.", prerequisites: ["support"] }],
    bounds: getBounds(blocks), resources, ports: [{ name: "input", pos: { ...geom.input }, kind: "input" }, { name: "output", pos: { ...geom.output }, kind: "output" }],
    tests: [{ id: "buffer-timing", description: `Measure the lamp transition after input edges: rising ${behavior.risingGameTicks} game ticks and falling ${behavior.fallingGameTicks} game ticks; confirm stable off/on/off behavior.` }],
    provenance: { kind: "generated", minecraft: "26.3", generatorRevision: 1, behaviorDigest: behaviorDigest(behavior) }, behavior: { ...behavior }, verification: "untested",
  };
}
function sortXYZ(a: { pos: Position }, b: { pos: Position }): number { return a.pos.x - b.pos.x || a.pos.y - b.pos.y || a.pos.z - b.pos.z; }
export function makeGeneratedBlueprint(input: DesignRequest, geometry: unknown, behavior: unknown): Blueprint { return derive(input, validateGeometry(geometry), validateBehavior(behavior)); }
export function validateGeneratedBlueprint(value: unknown): Blueprint {
  const v = strictObject(value, ["schemaVersion", "id", "revision", "projectId", "worldId", "dimension", "name", "explanation", "anchor", "rotation", "blocks", "stages", "bounds", "resources", "ports", "tests", "provenance", "behavior", "verification"]);
  if (v.schemaVersion !== 1 || v.verification !== "untested" || ![0, 90, 180, 270].includes(Number(v.rotation))) throw new RequestError("Invalid generated blueprint envelope.");
  const p = strictObject(v.provenance, ["kind", "minecraft", "generatorRevision", "behaviorDigest"]);
  if (p.kind !== "generated" || p.minecraft !== "26.3" || p.generatorRevision !== 1) throw new RequestError("Invalid generated provenance.");
  const behavior = validateBehavior(v.behavior);
  if (p.behaviorDigest !== behaviorDigest(behavior)) throw new RequestError("Generated behavior digest mismatch.");
  const rawBlocks = v.blocks;
  if (!Array.isArray(rawBlocks)) throw new RequestError("Invalid generated blueprint blocks.");
  if (!Array.isArray(v.ports)) throw new RequestError("Invalid generated blueprint ports.");
  const ports = v.ports.map((entry) => strictObject(entry, ["name", "pos", "kind"]));
  const inputPort = ports.find((port) => port.name === "input");
  const outputPort = ports.find((port) => port.name === "output");
  const geometry = validateGeometry({
    name: v.name,
    explanation: v.explanation,
    blocks: rawBlocks.map((b) => {
      const x = strictObject(b, ["pos", "state", "stage", "component", "prerequisites"]);
      if (typeof x.stage !== "string" || typeof x.component !== "string" || !Array.isArray(x.prerequisites)) throw new RequestError("Invalid derived block metadata.");
      return { pos: x.pos, state: x.state };
    }),
    input: inputPort?.pos,
    output: outputPort?.pos,
  });
  const id = text(v.id, "blueprint id", 128), projectId = text(v.projectId, "projectId", 128), revision = integer(v.revision, "revision", 1, Number.MAX_SAFE_INTEGER), anchor = position(v.anchor), rotation = Number(v.rotation) as Rotation;
  const min = getBounds(geometry.blocks.map((b) => ({ pos: rotatePosition(b.pos, rotation) })).map((b) => ({ pos: b.pos })));
  const input: DesignRequest = { request: "", worldId: text(v.worldId, "worldId", 128), dimension: text(v.dimension, "dimension", 128), anchor: { x: anchor.x + min.min.x, y: anchor.y + min.min.y, z: anchor.z + min.min.z }, rotation, projectId };
  const expected = derive(input, geometry, behavior, { id, revision, projectId });
  if (!inputPort || !outputPort || !isDeepStrictEqual(expected, v)) throw new RequestError("Generated blueprint derived fields were changed.");
  return expected;
}
