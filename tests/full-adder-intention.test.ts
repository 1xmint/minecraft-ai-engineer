import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createFullAdderIntention,
  readFullAdderIntention,
} from "../companion/src/full-adder-intention.js";
import { readConnectedIntention } from "../companion/src/connected-intention.js";
import {
  serverBlueprint,
  type GatewayContext,
} from "../companion/src/gateway-types.js";
const id = "0d3574ee-7b0f-4d8b-b38f-cef756e319ca";
const context = {
  worldId: "test",
  dimension: "minecraft:overworld",
  projectId: id,
};
const input = {
  ...context,
  id,
  revision: 1,
  anchor: { x: 0, y: 80, z: 0 },
  rotation: 0 as const,
};
test("full-adder preview is exact bounded intended geometry, never authority or a pass", () => {
  const source = createFullAdderIntention(input);
  assert.equal(source.blocks.length, 3544);
  assert.equal(source.connected.ports.length, 35);
  assert.equal(source.stages.length, 5);
  assert.equal(source.ports.length, 5);
  assert.equal(source.ports.filter((pin) => pin.kind === "input").length, 3);
  assert.equal(source.verification, "untested");
  assert.equal(
    source.graphDigest,
    "8a2b25ed743c42b936e101dc19368a35b740fb48a0dc72781712d3152dc100be",
  );
  assert.equal(
    Object.values(source.resources).reduce((a, b) => a + b, 0),
    3544,
  );
  assert.ok(
    source.blocks.every(
      (cell) => Math.abs(cell.pos.x) <= 64 && Math.abs(cell.pos.z) <= 64,
    ),
  );
  assert.deepEqual(readFullAdderIntention(source, context), source);
  assert.deepEqual(readConnectedIntention(source, context), source);
  const gateway: GatewayContext = {
    serverId: "server",
    serverEpoch: "epoch",
    actorId: id,
    projectId: id,
    projectRevision: 1,
    authorizationRevision: 1,
    requestId: "request",
  };
  assert.deepEqual(
    serverBlueprint(source, gateway, context.worldId, context.dimension),
    source,
  );
  assert.throws(() =>
    serverBlueprint(
      source,
      { ...gateway, projectRevision: 2 },
      context.worldId,
      context.dimension,
    ),
  );
  assert.throws(() =>
    readFullAdderIntention(source, { ...context, worldId: "other" }),
  );
  assert.throws(() =>
    readFullAdderIntention(source, {
      ...context,
      dimension: "minecraft:the_nether",
    }),
  );
  assert.throws(() =>
    readFullAdderIntention(source, {
      ...context,
      projectId: "00112233-4455-6677-8899-aabbccddeeff",
    }),
  );
});
test("full-adder source rejects edited geometry, graph, evidence labels and authority fields", () => {
  const source = createFullAdderIntention(input);
  for (const fault of [
    "owner",
    "permission",
    "verification",
    "geometry",
    "graph",
    "digest",
    "bounds",
    "resource",
    "stage",
  ]) {
    const bad = structuredClone(source);
    switch (fault) {
      case "owner":
        Object.assign(bad, { owner: id });
        break;
      case "permission":
        Object.assign(bad, { permissions: ["BUILD"] });
        break;
      case "verification":
        Object.assign(bad, { verification: "passed" });
        break;
      case "geometry":
        bad.blocks[0]!.state = "minecraft:chest";
        break;
      case "graph":
        bad.connected.ports[0]!.role = "signal_input";
        break;
      case "digest":
        bad.graphDigest = "0".repeat(64);
        break;
      case "bounds":
        bad.bounds.max.x = 64;
        break;
      case "resource":
        bad.resources["minecraft:stone"] = 0;
        break;
      case "stage":
        bad.stages[0]!.title = "Already verified";
        break;
    }
    assert.throws(() => readFullAdderIntention(bad, context), fault);
  }
  source.blocks[0]!.state = "minecraft:chest";
  source.connected.ports[0]!.pos.x = 0;
  assert.notEqual(
    createFullAdderIntention(input).blocks[0]!.state,
    "minecraft:chest",
  );
  assert.notEqual(createFullAdderIntention(input).connected.ports[0]!.pos.x, 0);
});
test("full-adder rotations retain the bounded source and reject world-coordinate overflow", () => {
  for (const rotation of [0, 90, 180, 270] as const) {
    const source = createFullAdderIntention({ ...input, rotation });
    assert.deepEqual(readFullAdderIntention(source, context), source);
    assert.throws(() =>
      createFullAdderIntention({
        ...input,
        rotation,
        anchor: { x: 29999984, y: 80, z: 29999984 },
      }),
    );
  }
  assert.throws(() => createFullAdderIntention({ ...input, revision: 0 }));
  assert.throws(() =>
    createFullAdderIntention({ ...input, id: id.toUpperCase() }),
  );
  assert.throws(() =>
    createFullAdderIntention({ ...input, anchor: { x: 0, y: 2047, z: 0 } }),
  );
});
