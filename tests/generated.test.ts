import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import {
  validateBehavior,
  behaviorDigest,
  makeGeneratedBlueprint,
  validateGeneratedBlueprint,
} from "../companion/src/generated.js";
import { plannerConfig } from "../companion/src/provider.js";
import type { DesignRequest } from "../companion/src/types.js";

const input: DesignRequest = {
  request: "A lamp following a lever: on after 12 game ticks, off after 16.",
  projectId: "11111111-1111-4111-8111-111111111111",
  worldId: "world-a",
  dimension: "minecraft:overworld",
  anchor: { x: 32, y: 100, z: 0 },
  rotation: 0,
  maxSize: { x: 4, y: 2, z: 1 },
};
const behavior = {
  schemaVersion: 1,
  function: "buffer",
  risingGameTicks: 12,
  fallingGameTicks: 16,
};
const geometry = () => ({
  name: "Generated delayed lamp",
  explanation: "Two serial repeaters carry the signal to the output.",
  blocks: [
    ...[0, 1, 2, 3].map((x) => ({
      pos: { x, y: 0, z: 0 },
      state: "minecraft:stone",
    })),
    {
      pos: { x: 0, y: 1, z: 0 },
      state: "minecraft:lever[face=floor,facing=east,powered=false]",
    },
    {
      pos: { x: 1, y: 1, z: 0 },
      state:
        "minecraft:repeater[facing=west,delay=3,locked=false,powered=false]",
    },
    {
      pos: { x: 2, y: 1, z: 0 },
      state:
        "minecraft:repeater[facing=west,delay=3,locked=false,powered=false]",
    },
    { pos: { x: 3, y: 1, z: 0 }, state: "minecraft:redstone_lamp[lit=false]" },
  ],
  input: { x: 0, y: 1, z: 0 },
  output: { x: 3, y: 1, z: 0 },
});

test("generated geometry uses an independently supplied behavior and derives its own bounded metadata", () => {
  const accepted = validateBehavior(behavior);
  const source = geometry();
  const blueprint = makeGeneratedBlueprint(input, source, accepted);
  assert.equal(blueprint.provenance.kind, "generated");
  assert.deepEqual(blueprint.resources, {
    "minecraft:stone": 4,
    "minecraft:lever": 1,
    "minecraft:repeater": 2,
    "minecraft:redstone_lamp": 1,
  });
  assert.equal(blueprint.blocks.length, 8);
  assert.deepEqual(blueprint.bounds, {
    min: { x: 0, y: 0, z: 0 },
    max: { x: 3, y: 1, z: 0 },
  });
  assert.equal(blueprint.verification, "untested");
  assert.equal(
    (blueprint.provenance as { behaviorDigest?: string }).behaviorDigest,
    behaviorDigest(accepted),
  );
  assert.deepEqual(validateGeneratedBlueprint(blueprint), blueprint);
  source.blocks[0]!.state = "minecraft:diamond_block";
  assert.equal(blueprint.blocks[0]!.state, "minecraft:stone");
});

test("timing intent rejects unknown fields, wrong units, unbounded values and model-chosen tests", () => {
  for (const bad of [
    { ...behavior, risingGameTicks: -1 },
    { ...behavior, fallingGameTicks: 129 },
    { ...behavior, risingGameTicks: 1.5 },
    { ...behavior, schemaVersion: 99 },
    { ...behavior, function: "clock" },
    { ...behavior, tests: [] },
    { ...behavior, fallingRedstoneTicks: 8 },
  ])
    assert.throws(() => validateBehavior(bad));
  assert.equal(
    validateBehavior({ ...behavior, risingGameTicks: 0 }).risingGameTicks,
    0,
  );
  assert.notEqual(
    behaviorDigest(validateBehavior(behavior)),
    behaviorDigest(validateBehavior({ ...behavior, risingGameTicks: 10 })),
  );
  assert.throws(() =>
    makeGeneratedBlueprint(
      input,
      { ...geometry(), tests: [{ passed: true }] },
      behavior,
    ),
  );
  assert.throws(() =>
    makeGeneratedBlueprint(
      input,
      { ...geometry(), verification: "passed" },
      behavior,
    ),
  );
});

test("generated data refuses unsupported states, extra inputs, invalid supports and delegated overflow", () => {
  const badState = geometry();
  badState.blocks[0]!.state = "minecraft:command_block";
  assert.throws(() => makeGeneratedBlueprint(input, badState, behavior));
  const extraInput = geometry();
  extraInput.blocks[7]!.state =
    "minecraft:lever[face=floor,facing=east,powered=false]";
  assert.throws(() => makeGeneratedBlueprint(input, extraInput, behavior));
  const unsupported = geometry();
  unsupported.blocks.splice(1, 1);
  assert.throws(() => makeGeneratedBlueprint(input, unsupported, behavior));
  assert.throws(() =>
    makeGeneratedBlueprint(
      { ...input, maxSize: { x: 3, y: 2, z: 1 } },
      geometry(),
      behavior,
    ),
  );
  const escaped = geometry();
  escaped.blocks[0]!.pos.x = 65;
  assert.throws(() => makeGeneratedBlueprint(input, escaped, behavior));
  const duplicate = geometry();
  duplicate.blocks.push({ ...duplicate.blocks[0]! });
  assert.throws(() => makeGeneratedBlueprint(input, duplicate, behavior));
});

test("a persisted generated source cannot rewrite the accepted behavior digest or derived metadata", () => {
  const blueprint = makeGeneratedBlueprint(input, geometry(), behavior);
  const forged = structuredClone(blueprint) as unknown as Record<
    string,
    unknown
  >;
  (forged.provenance as Record<string, unknown>).behaviorDigest = "0".repeat(
    64,
  );
  assert.throws(() => validateGeneratedBlueprint(forged));
  const resources = structuredClone(blueprint);
  resources.resources["minecraft:stone"] = 999;
  assert.throws(() => validateGeneratedBlueprint(resources));
  assert.throws(() =>
    validateGeneratedBlueprint({ ...blueprint, verification: "passed" }),
  );
});

test("generated initial states cannot hide powered or locked inputs behind a passing-looking preview", () => {
  for (const [index, state] of [
    [4, "minecraft:lever[face=floor,facing=east,powered=true]"],
    [4, "minecraft:lever"],
    [5, "minecraft:repeater[delay=3,facing=west,locked=true,powered=false]"],
    [5, "minecraft:repeater[delay=3,facing=west,locked=false,powered=true]"],
    [7, "minecraft:redstone_lamp[lit=true]"],
  ] as const) {
    const raw = geometry();
    raw.blocks[index]!.state = state;
    assert.throws(() => makeGeneratedBlueprint(input, raw, behavior));
  }
});

test("all generated planning stages preserve isolated subscription-only no-tool configuration", () => {
  for (const mode of ["catalog", "behavior", "geometry"] as const) {
    const config = plannerConfig(mode);
    assert.equal(config.forced_login_method, "chatgpt");
    assert.equal(config.model_provider, "openai");
    assert.equal(config.web_search, "disabled");
    for (const value of Object.values(
      config.features as Record<string, unknown>,
    ))
      assert.equal(value, false);
    assert.deepEqual(config.notify, []);
  }
  assert.match(
    String(plannerConfig("behavior").developer_instructions),
    /no geometry/iu,
  );
  assert.match(
    String(plannerConfig("geometry").developer_instructions),
    /accepted behavior/iu,
  );
});

test("model geometry schema constrains actual delegated volume, rotations and canonical block states", async () => {
  const { generatedGeometrySchema } =
    await import("../companion/src/generated.js");
  for (const rotation of [0, 90, 180, 270] as const) {
    const schema = generatedGeometrySchema({
      ...input,
      rotation,
      maxSize: { x: 4, y: 2, z: 1 },
    });
    assert.equal(schema.properties.blocks.maxItems, 8);
    const pos = schema.properties.blocks.items.properties.pos.properties;
    assert.equal(
      pos.x.maximum - pos.x.minimum + 1,
      rotation === 90 || rotation === 270 ? 1 : 4,
    );
    assert.equal(pos.y.maximum - pos.y.minimum + 1, 2);
    assert.equal(
      pos.z.maximum - pos.z.minimum + 1,
      rotation === 90 || rotation === 270 ? 4 : 1,
    );
    assert.ok(
      schema.properties.blocks.items.properties.state.enum.includes(
        "minecraft:stone",
      ),
    );
    assert.ok(
      !schema.properties.blocks.items.properties.state.enum.includes("stone"),
    );
    assert.ok(
      !schema.properties.blocks.items.properties.state.enum.some(
        (state) =>
          state.includes("powered=true") || state.includes("command_block"),
      ),
    );
    assert.ok(
      pos.x.maximum < 64 && pos.z.maximum < 64,
      "The observed 65-block model layout cannot satisfy the delegated output schema",
    );
  }
  const large = generatedGeometrySchema({
    ...input,
    rotation: 0,
    maxSize: { x: 128, y: 1, z: 1 },
  }).properties.blocks.items.properties.pos.properties.x;
  assert.equal(large.maximum - large.minimum + 1, 128);
  assert.ok(
    large.minimum >= -64 && large.maximum <= 64,
    "Large workspaces retain their representable extent",
  );
  assert.throws(() =>
    generatedGeometrySchema({ ...input, maxSize: { x: 0, y: 2, z: 1 } }),
  );
  assert.throws(() => generatedGeometrySchema({ ...input, rotation: 45 as 0 }));
});

test("recorded reversed live geometry remains untested despite valid bounds and states", () => {
  const recorded = JSON.parse(
    readFileSync(
      new URL(
        "../../tests/fixtures/live-structural-reversed.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  const blueprint = makeGeneratedBlueprint(
    { ...input, maxSize: { x: 5, y: 2, z: 1 } },
    recorded.geometry,
    behavior,
  );
  assert.equal(blueprint.verification, "untested");
  assert.equal(recorded.observed.verdict, "failed");
  assert.equal(recorded.observed.observedOutputAtExpectedEdge, false);
  assert.equal(
    blueprint.blocks.filter((b) =>
      b.state.startsWith("minecraft:repeater[delay=2,facing=east"),
    ).length,
    3,
  );
  assert.equal(validateGeneratedBlueprint(blueprint).verification, "untested");
});
