import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createToggleMemoryIntention,
  readToggleMemoryIntention,
} from "../companion/src/toggle-memory-intention.js";

const identity = {
  id: "db1175eb-1559-4cca-a740-d63f224a4068",
  projectId: "9a71b5bd-2073-4c67-9cd8-60d66f43cb6a",
  revision: 3,
  worldId: "test",
  dimension: "minecraft:overworld",
  anchor: { x: 24, y: 2, z: 24 },
  rotation: 0 as const,
};
const context = {
  worldId: identity.worldId,
  dimension: identity.dimension,
  projectId: identity.projectId,
};

test("canonical toggle-memory intentions describe only the intended mechanism", () => {
  for (const rotation of [0, 90, 180, 270] as const) {
    const value = createToggleMemoryIntention({ ...identity, rotation });
    assert.deepEqual(readToggleMemoryIntention(value, context), value);
    assert.equal(value.blocks.length, 8);
    assert.equal(value.stages.length, 4);
    assert.equal(value.ports.length, 3);
    assert.deepEqual(value.resources, {
      "minecraft:stone": 4,
      "minecraft:waxed_copper_bulb": 1,
      "minecraft:lever": 1,
      "minecraft:comparator": 1,
      "minecraft:redstone": 1,
    });
    assert.equal(value.memory.function, "toggle-memory");
    assert.equal(value.memory.initialBit, "observed-at-test-start");
    assert.deepEqual(value.memory.phaseInputs, [
      false,
      true,
      false,
      true,
      false,
      false,
    ]);
    assert.equal(value.verification, "untested");
    assert.deepEqual(value.bounds, {
      min: { x: -1, y: -1, z: 0 },
      max: { x: 2, y: 0, z: 0 },
    });
  }
});

test("saved toggle-memory intent rejects identity, geometry and authority changes", () => {
  const value = createToggleMemoryIntention(identity);
  const bad: unknown[] = [];
  for (const field of ["ownerId", "permissions", "serverEpoch", "passed"])
    bad.push({ ...structuredClone(value), [field]: true });
  bad.push({ ...structuredClone(value), verification: "passed" });
  const geometry = structuredClone(value);
  geometry.blocks[0]!.state = "minecraft:chest";
  bad.push(geometry);
  const stages = structuredClone(value);
  stages.stages[0]!.title = "Changed";
  bad.push(stages);
  const resources = structuredClone(value);
  resources.resources["minecraft:stone"] = 5;
  bad.push(resources);
  const memory = structuredClone(value);
  memory.memory.phaseInputs[0] = true;
  bad.push(memory);
  const owner = structuredClone(value);
  Object.assign(owner.memory, {
    ownerId: "00000000-0000-0000-0000-000000000000",
  });
  bad.push(owner);
  for (const candidate of bad)
    assert.throws(() => readToggleMemoryIntention(candidate, context));
  assert.throws(() =>
    readToggleMemoryIntention(value, { ...context, worldId: "other" }),
  );
  assert.throws(() =>
    readToggleMemoryIntention(value, {
      ...context,
      dimension: "minecraft:the_nether",
    }),
  );
  assert.throws(() =>
    readToggleMemoryIntention(value, {
      ...context,
      projectId: "00000000-0000-0000-0000-000000000000",
    }),
  );
});

test("toggle-memory identities, positions and rotated footprint are bounded", () => {
  for (const revision of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, 2147483648])
    assert.throws(() => createToggleMemoryIntention({ ...identity, revision }));
  for (const id of ["not-a-uuid", "DB1175EB-1559-4CCA-A740-D63F224A4068"])
    assert.throws(() => createToggleMemoryIntention({ ...identity, id }));
  assert.throws(() =>
    createToggleMemoryIntention({ ...identity, projectId: "invalid" }),
  );
  for (const worldId of ["", "has space", "x".repeat(257)])
    assert.throws(() => createToggleMemoryIntention({ ...identity, worldId }));
  for (const dimension of [
    "Overworld",
    "minecraft:",
    "minecraft:" + "x".repeat(241),
  ])
    assert.throws(() =>
      createToggleMemoryIntention({ ...identity, dimension }),
    );
  for (const rotation of [-90, 45, 360, 90.5])
    assert.throws(() =>
      createToggleMemoryIntention({ ...identity, rotation: rotation as 0 }),
    );
  assert.throws(() =>
    createToggleMemoryIntention({
      ...identity,
      anchor: { x: Number.MAX_SAFE_INTEGER, y: 2, z: 24 },
    }),
  );
  assert.throws(() =>
    createToggleMemoryIntention({
      ...identity,
      anchor: { x: 24, y: -2048, z: 24 },
    }),
  );
  assert.throws(() =>
    createToggleMemoryIntention({
      ...identity,
      anchor: { x: MAX_HORIZONTAL - 1, y: 2, z: 24 },
    }),
  );
  assert.throws(() =>
    createToggleMemoryIntention({
      ...identity,
      anchor: { x: 24, y: 2, z: MAX_HORIZONTAL - 1 },
      rotation: 90,
    }),
  );
});

const MAX_HORIZONTAL = 29_999_984;

test("toggle-memory results do not alias input or catalog data", () => {
  const anchor = { ...identity.anchor };
  const value = createToggleMemoryIntention({ ...identity, anchor });
  anchor.x++;
  assert.equal(value.anchor.x, identity.anchor.x);
  value.blocks[0]!.state = "tampered";
  const fresh = createToggleMemoryIntention(identity);
  assert.equal(fresh.blocks[0]!.state, "minecraft:stone");
  assert.throws(() => readToggleMemoryIntention(value, context));
});
