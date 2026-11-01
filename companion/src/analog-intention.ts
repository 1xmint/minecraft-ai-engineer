import { rotatePosition } from "./geometry.js";
import type { Position, Rotation } from "./types.js";

/** Identity-bound recipe data only; this type carries no world authority or pass claim. */
export interface AnalogIntention {
  schemaVersion: 1;
  id: string;
  projectId: string;
  revision: number;
  worldId: string;
  dimension: string;
  name: string;
  explanation: string;
  anchor: Position;
  rotation: Rotation;
  stages: {
    id: string;
    title: string;
    explanation: string;
    prerequisites: string[];
  }[];
  blocks: {
    pos: Position;
    state: string;
    stage: "foundation" | "selectors" | "transfer";
    component: "root";
    prerequisites: string[];
  }[];
  resources: Record<string, number>;
  bounds: { min: Position; max: Position };
  ports: { name: string; pos: Position; kind: "input" | "output" }[];
  tests: { id: string; description: string }[];
  analog: {
    schemaVersion: 1;
    function: "comparator-transfer";
    mode: "compare" | "subtract";
    sampleStartGameTicks: 8;
    stableSamples: 3;
  };
  provenance: {
    kind: "curated";
    template: "comparator-compare" | "comparator-subtract";
    minecraft: "26.3";
    catalogRevision: 1;
  };
  verification: "untested";
}

export interface AnalogIntentionInput {
  id: string;
  revision: number;
  projectId: string;
  worldId: string;
  dimension: string;
  anchor: Position;
  rotation: Rotation;
  mode: "compare" | "subtract";
}

export interface AnalogIntentionContext {
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

function canonicalUuid(value: unknown, name: string): string {
  if (typeof value !== "string" || !UUID.test(value))
    return fail(`Invalid canonical ${name} UUID.`);
  return value;
}

function text(value: unknown, pattern: RegExp, name: string): string {
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
    return fail("Invalid analog rotation.");
  return value as Rotation;
}

function deepFreeze<T>(value: T): T {
  if (value !== null && typeof value === "object" && !Object.isFrozen(value)) {
    for (const child of Object.values(value)) deepFreeze(child);
    Object.freeze(value);
  }
  return value;
}

function design(input: AnalogIntentionInput): AnalogIntention {
  const id = canonicalUuid(input.id, "intention id");
  const projectId = canonicalUuid(input.projectId, "project id");
  const revision = safeInteger(input.revision, "revision");
  if (revision < 1 || revision > 2_147_483_647)
    return fail("Invalid revision.");
  const worldId = text(input.worldId, WORLD_ID, "world id");
  const dimensionId = dimension(input.dimension);
  const anchor = position(input.anchor, "anchor");
  const angle = rotation(input.rotation);
  if (input.mode !== "compare" && input.mode !== "subtract")
    return fail("Unknown analog comparator mode.");

  const relativeBlocks: AnalogIntention["blocks"] = [];
  const resources: Record<string, number> = {};
  const add = (
    pos: Position,
    state: string,
    stage: AnalogIntention["blocks"][number]["stage"],
    prerequisites: string[],
  ) => {
    relativeBlocks.push({
      pos,
      state,
      stage,
      component: "root",
      prerequisites,
    });
    const bracket = state.indexOf("[");
    const resource = bracket < 0 ? state : state.slice(0, bracket);
    resources[resource] = (resources[resource] ?? 0) + 1;
  };
  const wirePos = (bank: number, index: number): Position => {
    if (bank === 0) return { x: index - 15, y: 0, z: 0 };
    if (bank === 1) return { x: 0, y: 0, z: index - 15 };
    return { x: 0, y: 0, z: index + 1 };
  };
  const wireState = (bank: number) =>
    bank === 0
      ? "minecraft:redstone_wire[east=side,north=none,power=0,south=none,west=side]"
      : "minecraft:redstone_wire[east=none,north=side,power=0,south=side,west=none]";

  // The Java recipe emits all 47 supports first, followed by each lever/wire pair.
  for (let bank = 0; bank < 3; bank++)
    for (let index = 0; index < 15; index++) {
      const wire = wirePos(bank, index);
      add({ x: wire.x, y: -1, z: wire.z }, "minecraft:stone", "foundation", []);
    }
  add({ x: 0, y: -1, z: 0 }, "minecraft:stone", "foundation", []);
  add({ x: 1, y: -1, z: 0 }, "minecraft:stone", "foundation", []);
  for (let bank = 0; bank < 3; bank++)
    for (let index = 0; index < 15; index++) {
      const wire = wirePos(bank, index);
      add(
        { x: wire.x, y: -2, z: wire.z },
        "minecraft:lever[face=ceiling,facing=north,powered=false]",
        "selectors",
        ["foundation"],
      );
      add(wire, wireState(bank), "transfer", ["foundation", "selectors"]);
    }
  add(
    { x: 0, y: 0, z: 0 },
    `minecraft:comparator[facing=west,mode=${input.mode},powered=false]`,
    "transfer",
    ["foundation", "selectors"],
  );
  add({ x: 1, y: 0, z: 0 }, wireState(0), "transfer", [
    "foundation",
    "selectors",
  ]);

  const stages: AnalogIntention["stages"] = [
    {
      id: "foundation",
      title: "Support the circuit",
      explanation:
        "Place the stone supports before attaching ceiling levers or dust.",
      prerequisites: [],
    },
    {
      id: "selectors",
      title: "Add physical strength controls",
      explanation:
        "Each bank uses one powered ceiling lever at a selected distance; all levers off means zero.",
      prerequisites: ["foundation"],
    },
    {
      id: "transfer",
      title: "Connect the comparator",
      explanation:
        "Place the comparator in the intended mode and connect the unpowered dust. Functional testing is still required.",
      prerequisites: ["foundation", "selectors"],
    },
  ];
  const ports: AnalogIntention["ports"] = [];
  for (let bank = 0; bank < 3; bank++)
    for (let index = 0; index < 15; index++) {
      const wire = wirePos(bank, index);
      ports.push({
        name: `bank-${bank}-${index}`,
        pos: { x: wire.x, y: -2, z: wire.z },
        kind: "input",
      });
    }
  ports.push({ name: "output", pos: { x: 1, y: 0, z: 0 }, kind: "output" });

  // Check the entire canonical footprint, not only the comparator anchor.
  for (const cell of relativeBlocks) {
    const rotated = rotatePosition(cell.pos, angle);
    position(
      {
        x: anchor.x + rotated.x,
        y: anchor.y + rotated.y,
        z: anchor.z + rotated.z,
      },
      "analog block position",
    );
  }

  return deepFreeze({
    schemaVersion: 1,
    id,
    projectId,
    revision,
    worldId,
    dimension: dimensionId,
    name: `Comparator ${input.mode} workbench`,
    explanation:
      "A native comparator with three independent physical strength selectors. Compare passes the rear strength when it is at least both side strengths; subtract removes the stronger side from the rear, clamped at zero. Settled numeric tests do not measure latency.",
    anchor,
    rotation: angle,
    stages,
    blocks: relativeBlocks.map((block) => ({
      ...block,
      pos: { ...block.pos },
      prerequisites: [...block.prerequisites],
      state: block.state,
    })),
    resources,
    bounds: {
      min: { x: -15, y: -2, z: -15 },
      max: { x: 1, y: 0, z: 15 },
    },
    ports,
    tests: [
      {
        id: "analog-transfer",
        description:
          "Measure actual rear, both sides, comparator mode and output wire at consecutive settled game ticks; restore every original selector state.",
      },
    ],
    analog: {
      schemaVersion: 1,
      function: "comparator-transfer",
      mode: input.mode,
      sampleStartGameTicks: 8,
      stableSamples: 3,
    },
    provenance: {
      kind: "curated",
      template: `comparator-${input.mode}`,
      minecraft: "26.3",
      catalogRevision: 1,
    },
    verification: "untested",
  });
}

/** Create a detached, immutable intention from its complete explicit identity and design inputs. */
export function makeAnalogIntention(
  input: AnalogIntentionInput,
): AnalogIntention {
  return design({
    ...input,
    anchor: position(input.anchor, "anchor"),
  });
}

function equalCanonical(actual: unknown, expected: unknown): boolean {
  if (actual === expected) return true;
  if (actual === null || expected === null || typeof actual !== typeof expected)
    return false;
  if (Array.isArray(expected)) {
    if (
      !Array.isArray(actual) ||
      actual.length !== expected.length ||
      Object.keys(actual).length !== expected.length
    )
      return false;
    for (let index = 0; index < expected.length; index++) {
      if (
        !Object.hasOwn(actual, index) ||
        !equalCanonical(actual[index], expected[index])
      )
        return false;
    }
    return true;
  }
  if (typeof expected === "object") {
    if (
      typeof actual !== "object" ||
      actual === null ||
      Array.isArray(actual) ||
      (Object.getPrototypeOf(actual) !== Object.prototype &&
        Object.getPrototypeOf(actual) !== null)
    )
      return false;
    const actualRecord = actual as Record<string, unknown>;
    const expectedRecord = expected as Record<string, unknown>;
    const actualKeys = Object.keys(actualRecord).sort();
    const expectedKeys = Object.keys(expectedRecord).sort();
    return (
      actualKeys.length === expectedKeys.length &&
      actualKeys.every(
        (key, index) =>
          key === expectedKeys[index] &&
          equalCanonical(actualRecord[key], expectedRecord[key]),
      )
    );
  }
  return false;
}

/** Regenerate canonical data and require a separately supplied current identity context. */
export function validateAnalogIntention(
  value: unknown,
  context: AnalogIntentionContext,
): AnalogIntention {
  const source = record(value, "analog intention");
  const supplied = record(context, "analog intention context");
  const anchor = position(source.anchor, "anchor");
  const analog = record(source.analog, "analog contract");
  const expected = design({
    id: canonicalUuid(source.id, "intention id"),
    revision: safeInteger(source.revision, "revision"),
    projectId: canonicalUuid(source.projectId, "project id"),
    worldId: text(source.worldId, WORLD_ID, "world id"),
    dimension: dimension(source.dimension),
    anchor,
    rotation: rotation(source.rotation),
    mode: analog.mode as "compare" | "subtract",
  });
  if (
    !equalCanonical(source, expected) ||
    supplied.worldId !== expected.worldId ||
    supplied.dimension !== expected.dimension ||
    supplied.projectId !== expected.projectId
  )
    return fail(
      "Analog intention differs from canonical geometry, contract or current identity.",
    );
  return expected;
}
