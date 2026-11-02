import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createConnectedIntention,
  readConnectedIntention,
} from "../companion/src/connected-intention.js";
const id = "db1175eb-1559-4cca-a740-d63f224a4068";
const context = {
  projectId: id,
  worldId: "test",
  dimension: "minecraft:overworld",
};
const input = {
  ...context,
  id,
  revision: 1,
  anchor: { x: 24, y: 2, z: 24 },
  rotation: 0 as const,
};
test("canonical connected source rejects altered geometry, topology and authority", () => {
  const source = createConnectedIntention(input);
  assert.deepEqual(readConnectedIntention(source, context), source);
  const mutations = [
    (v: any) => {
      v.owner = id;
    },
    (v: any) => {
      v.verification = "passed";
    },
    (v: any) => {
      v.blocks[0].state = "minecraft:air";
    },
    (v: any) => {
      v.connected.ports[0].role = "signal_input";
    },
    (v: any) => {
      v.connected.nets.pop();
    },
    (v: any) => {
      v.graphDigest = "0".repeat(64);
    },
  ];
  for (const mutate of mutations) {
    const forged = structuredClone(source);
    mutate(forged);
    assert.throws(() => readConnectedIntention(forged, context));
  }
  assert.throws(() =>
    readConnectedIntention(source, { ...context, worldId: "other" }),
  );
  assert.throws(() =>
    readConnectedIntention(source, {
      ...context,
      projectId: "00000000-0000-0000-0000-000000000000",
    }),
  );
});
test("connected source bounds cover every rotated cell and exports are independent", () => {
  for (const rotation of [0, 90, 180, 270] as const) {
    assert.equal(
      createConnectedIntention({ ...input, rotation }).blocks.length,
      436,
    );
    assert.throws(() =>
      createConnectedIntention({
        ...input,
        rotation,
        anchor: { x: 29999984, y: 2047, z: 29999984 },
      }),
    );
  }
  const source = createConnectedIntention(input);
  source.blocks[0]!.state = "minecraft:air";
  assert.notEqual(
    createConnectedIntention(input).blocks[0]!.state,
    "minecraft:air",
  );
});
