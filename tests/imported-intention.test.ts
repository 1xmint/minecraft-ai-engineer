import assert from "node:assert/strict";
import { test } from "node:test";
import { readImportedIntention } from "../companion/src/imported-intention.js";
import {
  serverBlueprint,
  type GatewayContext,
} from "../companion/src/gateway-types.js";

const id = "db1175eb-1559-4cca-a740-d63f224a4068";
const context = {
  worldId: "test",
  dimension: "minecraft:overworld",
  projectId: id,
};
function source() {
  return {
    schemaVersion: 1,
    id,
    projectId: id,
    revision: 2,
    worldId: "test",
    dimension: "minecraft:overworld",
    name: "Imported design 7",
    explanation:
      "Imported geometry only. No functional behavior or verification is claimed.",
    anchor: { x: 10, y: 100, z: 10 },
    rotation: 90,
    verification: "untested",
    provenance: {
      kind: "schematic",
      designId: 7,
      digest: "a".repeat(64),
      sourceSha256: "b".repeat(64),
    },
    stages: [
      {
        id: "design",
        title: "Imported design 7",
        explanation:
          "Geometry imported from a schematic file. It is untrusted and untested until it is built and checked in game.",
        prerequisites: [],
      },
    ],
    blocks: [
      {
        pos: { x: 0, y: 0, z: 0 },
        state: "minecraft:stone",
        stage: "design",
        component: "root",
        prerequisites: [],
      },
      {
        pos: { x: 0, y: 1, z: 0 },
        state: "minecraft:lever[face=floor,facing=east,powered=false]",
        stage: "design",
        component: "root",
        prerequisites: [],
      },
      {
        pos: { x: 1, y: 1, z: 0 },
        state: "minecraft:redstone_lamp[lit=false]",
        stage: "design",
        component: "root",
        prerequisites: [],
      },
    ],
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 1, y: 1, z: 0 } },
    resources: {
      "minecraft:stone": 1,
      "minecraft:lever": 1,
      "minecraft:redstone_lamp": 1,
    },
    ports: [],
    tests: [],
  };
}
test("imported admission retains geometry without authority or behavior", () => {
  const value = source();
  assert.deepEqual(readImportedIntention(value, context), value);
  const mutate: ((value: any) => void)[] = [
    (v) => {
      v.owner = id;
    },
    (v) => {
      v.verification = "passed:other-design";
    },
    (v) => {
      v.ports = [{ name: "a", pos: { x: 0, y: 1, z: 0 }, kind: "input" }];
    },
    (v) => {
      v.tests = [{ id: "fake", description: "working" }];
    },
    (v) => {
      v.resources["minecraft:stone"] = 99;
    },
    (v) => {
      v.bounds.max.x = 0;
    },
    (v) => {
      v.blocks[0].state = "minecraft:chest";
    },
    (v) => {
      v.blocks[1].state = "minecraft:lever[facing=up]";
    },
    (v) => {
      v.blocks[1].pos = { ...v.blocks[0].pos };
    },
    (v) => {
      v.anchor.y = 2047;
    },
  ];
  for (const change of mutate) {
    const bad = structuredClone(value);
    change(bad);
    assert.throws(() => readImportedIntention(bad, context));
  }
  assert.throws(() =>
    readImportedIntention(value, { ...context, worldId: "other" }),
  );
  assert.throws(() =>
    readImportedIntention(value, {
      ...context,
      projectId: "00000000-0000-0000-0000-000000000000",
    }),
  );
});
test("server gateway still requires the exact current revision for imported geometry", () => {
  const gateway: GatewayContext = {
    serverId: "server",
    serverEpoch: "epoch",
    actorId: id,
    projectId: id,
    projectRevision: 2,
    authorizationRevision: 1,
    requestId: "request",
  };
  assert.deepEqual(
    serverBlueprint(source(), gateway, context.worldId, context.dimension),
    source(),
  );
  assert.throws(() =>
    serverBlueprint(
      source(),
      { ...gateway, projectRevision: 3 },
      context.worldId,
      context.dimension,
    ),
  );
});
