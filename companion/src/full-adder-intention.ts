import { createHash } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import definition from "../../catalog/full-adder-definition.json" with { type: "json" };
import type { ConnectedIntentionInput } from "./connected-intention.js";
import { rotatePosition } from "./geometry.js";
import {
  integer,
  object,
  position,
  type Blueprint,
  type Rotation,
} from "./types.js";

export interface FullAdderIntention extends Blueprint {
  connected: typeof definition.graph;
  graphDigest: string;
}
function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object")
    return `{${Object.keys(value)
      .sort()
      .map(
        (key) =>
          `${JSON.stringify(key)}:${canonical((value as Record<string, unknown>)[key])}`,
      )
      .join(",")}}`;
  return JSON.stringify(value);
}
const digest = createHash("sha256")
  .update(
    `connected-full-adder-v2|minecraft=26.3|${canonical(definition.graph)}|${canonical(definition.geometry)}`,
  )
  .digest("hex");
if (
  definition.schemaVersion !== 2 ||
  definition.minecraft !== "26.3" ||
  definition.graph.function !== "full-adder" ||
  definition.geometry.length !== 3544 ||
  definition.graph.ports.length !== 35 ||
  digest !== definition.graphDigest
)
  throw new TypeError("Unsupported canonical full-adder definition");
function uuid(value: unknown): string {
  if (
    typeof value !== "string" ||
    !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u.test(
      value,
    )
  )
    throw new TypeError("Expected canonical UUID");
  return value;
}

/** Exact intended data only; neither owner authority nor a functional pass. */
export function createFullAdderIntention(
  input: ConnectedIntentionInput,
): FullAdderIntention {
  const id = uuid(input.id),
    projectId = uuid(input.projectId),
    revision = integer(input.revision, "revision", 1, 2147483647);
  if (
    typeof input.worldId !== "string" ||
    !/^[a-zA-Z0-9_.:-]{1,256}$/u.test(input.worldId) ||
    typeof input.dimension !== "string" ||
    input.dimension.length > 256 ||
    !/^[a-z0-9_.-]+:[a-z0-9_./-]{1,240}$/u.test(input.dimension)
  )
    throw new TypeError("Invalid full-adder world identity");
  const anchor = position(input.anchor);
  if (![0, 90, 180, 270].includes(input.rotation))
    throw new TypeError("Invalid rotation");
  const resources: Record<string, number> = {};
  for (const cell of definition.geometry) {
    const relative = rotatePosition(cell.pos, input.rotation),
      x = anchor.x + relative.x,
      y = anchor.y + relative.y,
      z = anchor.z + relative.z;
    if (
      Math.abs(x) > 29999984 ||
      Math.abs(z) > 29999984 ||
      y < -2048 ||
      y > 2047
    )
      throw new TypeError("Full-adder source exceeds supported world bounds");
    const block = cell.state.split("[")[0]!,
      item =
        block === "minecraft:redstone_wire"
          ? "minecraft:redstone"
          : block === "minecraft:redstone_wall_torch"
            ? "minecraft:redstone_torch"
            : block;
    resources[item] = (resources[item] ?? 0) + 1;
  }
  const order = ["foundation", "controls", "logic", "connections", "outputs"];
  const titles = [
    "Support the circuit",
    "Add three shared inputs",
    "Build nine NAND gates",
    "Connect insulated signal routes",
    "Add both result lamps",
  ];
  const lessons = [
    "Place all stone and glass supports before adding devices.",
    "Only A, B and carry-in are external controls. Internal inputs receive signals from wiring.",
    "Nine NAND gates compute sum and carry together.",
    "Build the exact buffered trunks and insulated overhead routes. Nearby circuits can interfere.",
    "Both SUM and CARRY must pass every input row at every observed pin. Matching geometry is insufficient.",
  ];
  return {
    schemaVersion: 1,
    id,
    projectId,
    revision,
    worldId: input.worldId,
    dimension: input.dimension,
    anchor,
    rotation: input.rotation,
    name: "Connected one-bit full-adder",
    explanation:
      "Three shared external controls drive nine NAND gates. SUM is the low bit of A plus B plus carry-in; CARRY is true when at least two inputs are true. Internal signal pins are observed, never stimulated. Geometry alone does not prove function.",
    stages: order.map((stage, index) => ({
      id: stage,
      title: titles[index]!,
      explanation: lessons[index]!,
      prerequisites: index > 0 ? [order[index - 1]!] : [],
    })),
    blocks: structuredClone(definition.geometry).map((cell) => {
      const index = order.indexOf(cell.stage);
      return { ...cell, prerequisites: index > 0 ? [order[index - 1]!] : [] };
    }),
    connected: structuredClone(definition.graph),
    graphDigest: digest,
    ports: definition.graph.ports
      .filter((pin) => pin.role === "external_control" || pin.role === "result")
      .map((pin) => ({
        name: pin.id,
        pos: { ...pin.pos },
        kind: pin.role === "external_control" ? "input" : "output",
      })),
    tests: [
      {
        id: "connected-full-adder",
        description:
          "Observe all35 pins for16 forward/reverse input cases at consecutive settled game ticks and restore all three original external controls. No latency claim.",
      },
    ],
    resources,
    bounds: { min: { x: -48, y: 0, z: -36 }, max: { x: 50, y: 3, z: 42 } },
    provenance: {
      kind: "curated",
      template: "full-adder",
      minecraft: "26.3",
      catalogRevision: 1,
    },
    verification: "untested",
  };
}
export function readFullAdderIntention(
  value: unknown,
  context: { worldId: string; dimension: string; projectId: string },
): FullAdderIntention {
  const source = object(value);
  const expected = createFullAdderIntention({
    id: uuid(source.id),
    projectId: uuid(source.projectId),
    revision: integer(source.revision, "revision", 1, 2147483647),
    worldId: source.worldId as string,
    dimension: source.dimension as string,
    anchor: position(source.anchor),
    rotation: source.rotation as Rotation,
  });
  if (
    !isDeepStrictEqual(value, expected) ||
    expected.worldId !== context.worldId ||
    expected.dimension !== context.dimension ||
    expected.projectId !== context.projectId
  )
    throw new TypeError(
      "Full-adder source differs from canonical graph, geometry or current project identity",
    );
  return expected;
}
