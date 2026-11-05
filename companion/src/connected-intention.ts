import { isDeepStrictEqual } from "node:util";
import { readFullAdderIntention } from "./full-adder-intention.js";
import definition from "../../catalog/half-adder-definition.json" with { type: "json" };
import recipe from "../../catalog/half-adder.json" with { type: "json" };
import { rotatePosition } from "./geometry.js";
import type { Blueprint } from "./types.js";
import {
  integer,
  object,
  position,
  type Position,
  type Rotation,
} from "./types.js";

export interface ConnectedIntentionInput {
  id: string;
  projectId: string;
  revision: number;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
}
export interface ConnectedIntention extends Blueprint {
  connected: typeof definition.graph;
  graphDigest: string;
}
const uuid = (value: unknown): string => {
  if (
    typeof value !== "string" ||
    !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u.test(
      value,
    )
  )
    throw new TypeError("Expected canonical UUID");
  return value;
};

/** Intended geometry only. No execution admission, ownership or live verification. */
export function createConnectedIntention(
  input: ConnectedIntentionInput,
): ConnectedIntention {
  const stageIds = [
    "foundation",
    "controls",
    "logic",
    "connections",
    "outputs",
  ];
  const titles = [
    "Support the circuit",
    "Add shared inputs",
    "Build the logic gates",
    "Connect the signal routes",
    "Add both result lamps",
  ];
  const lessons = [
    "Place all stone supports before adding devices.",
    "Only A and B are external controls. Internal inputs receive signals from wiring.",
    "Four NAND gates compute the sum; the carry inverter reads the first NAND output.",
    "Connect the exact buffered routes. Separate circuits can interfere even when individual gates work.",
    "Both SUM and CARRY must pass every input row. A matching shape or one correct lamp is insufficient.",
  ];
  const resources: Record<string, number> = {};
  for (const cell of definition.geometry) {
    const block = cell.state.split("[")[0]!;
    const item =
      block === "minecraft:redstone_wire"
        ? "minecraft:redstone"
        : block === "minecraft:redstone_wall_torch"
          ? "minecraft:redstone_torch"
          : block;
    resources[item] = (resources[item] ?? 0) + 1;
  }
  if (
    recipe.id !== "half-adder" ||
    recipe.minecraft !== "26.3" ||
    recipe.revision !== 1
  )
    throw new TypeError("Unsupported connected recipe");
  const id = uuid(input.id),
    projectId = uuid(input.projectId);
  const revision = integer(input.revision, "revision", 1, 2147483647);
  if (
    typeof input.worldId !== "string" ||
    !/^[a-zA-Z0-9_.:-]{1,256}$/u.test(input.worldId) ||
    typeof input.dimension !== "string" ||
    input.dimension.length > 256 ||
    !/^[a-z0-9_.-]+:[a-z0-9_./-]{1,240}$/u.test(input.dimension)
  )
    throw new TypeError("Invalid connected world identity");
  const anchor = position(input.anchor);
  if (![0, 90, 180, 270].includes(input.rotation))
    throw new TypeError("Invalid rotation");
  for (const cell of definition.geometry) {
    const relative = rotatePosition(cell.pos, input.rotation);
    const x = anchor.x + relative.x,
      y = anchor.y + relative.y,
      z = anchor.z + relative.z;
    if (
      Math.abs(x) > 29999984 ||
      Math.abs(z) > 29999984 ||
      y < -2048 ||
      y > 2047
    )
      throw new TypeError("Connected source exceeds supported world bounds");
  }
  return {
    schemaVersion: 1,
    stages: stageIds.map((id, index) => ({
      id,
      title: titles[index]!,
      explanation: lessons[index]!,
      prerequisites: index > 0 ? [stageIds[index - 1]!] : [],
    })),
    resources,
    id,
    projectId,
    revision,
    worldId: input.worldId,
    dimension: input.dimension,
    anchor,
    rotation: input.rotation,
    name: "Connected one-bit half-adder",
    explanation:
      "Two shared external controls drive four NAND gates and a carry inverter. SUM is A XOR B; CARRY is A AND B. Internal signal pins are observed, never stimulated. Geometry alone does not prove function.",
    blocks: structuredClone(definition.geometry).map((cell) => {
      const index = stageIds.indexOf(cell.stage);
      return {
        ...cell,
        prerequisites: index > 0 ? [stageIds[index - 1]!] : [],
      };
    }),
    ports: definition.graph.ports
      .filter((pin) => pin.role === "external_control" || pin.role === "result")
      .map((pin) => ({
        name: pin.id,
        pos: { ...pin.pos },
        kind: pin.role === "external_control" ? "input" : "output",
      })),
    tests: [
      {
        id: "connected-half-adder",
        description:
          "Observe all twenty pins for eight forward/reverse input cases at consecutive game ticks and restore both original external controls. No latency claim.",
      },
    ],
    connected: structuredClone(definition.graph),
    graphDigest: definition.graphDigest,
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 45, y: 1, z: 36 } },
    provenance: {
      kind: "curated",
      template: "half-adder",
      minecraft: "26.3",
      catalogRevision: 1,
    },
    verification: "untested",
  };
}

export function readConnectedIntention(
  value: unknown,
  context: { worldId: string; dimension: string; projectId: string },
) {
  const source = object(value);
  if (object(source.connected).function === "full-adder")
    return readFullAdderIntention(value, context);
  const expected = createConnectedIntention({
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
      "Connected source differs from canonical graph, geometry or current project identity",
    );
  return expected;
}
