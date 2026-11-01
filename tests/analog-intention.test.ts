import {
  serverBlueprint,
  type GatewayContext,
} from "../companion/src/gateway-types.js";
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  makeAnalogIntention,
  validateAnalogIntention,
} from "../companion/src/analog-intention.js";

const identity = {
  id: "db1175eb-1559-4cca-a740-d63f224a4068",
  projectId: "9a71b5bd-2073-4c67-9cd8-60d66f43cb6a",
  revision: 3,
  worldId: "test",
  dimension: "minecraft:overworld",
  anchor: { x: 24, y: 2, z: 24 },
  rotation: 0 as const,
  mode: "compare" as const,
};
const context = {
  worldId: identity.worldId,
  dimension: identity.dimension,
  projectId: identity.projectId,
};

test("canonical analog intentions describe physical hardware without a pass", () => {
  for (const mode of ["compare", "subtract"] as const)
    for (const rotation of [0, 90, 180, 270] as const) {
      const value = makeAnalogIntention({ ...identity, mode, rotation });
      assert.deepEqual(validateAnalogIntention(value, context), value);
      assert.equal(value.blocks.length, 139);
      assert.equal(value.ports.length, 46);
      assert.deepEqual(value.resources, {
        "minecraft:stone": 47,
        "minecraft:lever": 45,
        "minecraft:redstone": 46,
        "minecraft:comparator": 1,
      });
      assert.equal(value.verification, "untested");
      assert.deepEqual(value.bounds, {
        min: { x: -15, y: -2, z: -15 },
        max: { x: 1, y: 0, z: 15 },
      });
      assert.deepEqual(
        value.blocks.find((block) =>
          block.state.startsWith("minecraft:comparator["),
        )?.pos,
        { x: 0, y: 0, z: 0 },
      );
      assert.deepEqual(
        value.ports.find((port) => port.name === "output")?.pos,
        { x: 1, y: 0, z: 0 },
      );
      assert.equal(
        value.ports.filter((port) => port.kind === "input").length,
        45,
      );
    }
});

test("saved analog intent rejects changed geometry, contract and imported authority", () => {
  const value = makeAnalogIntention(identity);
  const bad: unknown[] = [];
  for (const field of ["ownerId", "permissions", "serverEpoch", "passed"])
    bad.push({ ...structuredClone(value), [field]: true });
  bad.push({ ...structuredClone(value), verification: "passed" });
  const geometry = structuredClone(value);
  geometry.blocks[0]!.state =
    "minecraft:chest[facing=north,type=single,waterlogged=false]";
  bad.push(geometry);
  const changedMode = structuredClone(value);
  changedMode.analog.mode = "subtract";
  bad.push(changedMode);
  const ports = structuredClone(value);
  ports.ports[0]!.pos.x++;
  bad.push(ports);
  const sparse = structuredClone(value);
  delete sparse.blocks[0];
  bad.push(sparse);
  for (const candidate of bad)
    assert.throws(() => validateAnalogIntention(candidate, context));
  assert.throws(() =>
    validateAnalogIntention(value, { ...context, worldId: "other" }),
  );
  assert.throws(() =>
    validateAnalogIntention(value, {
      ...context,
      dimension: "minecraft:the_nether",
    }),
  );
  assert.throws(() =>
    validateAnalogIntention(value, { ...context, projectId: identity.id }),
  );
});

test("analog factory rejects malformed identities and complete apparatus escaping world bounds", () => {
  for (const revision of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])
    assert.throws(() => makeAnalogIntention({ ...identity, revision }));
  for (const anchor of [
    { x: 29_999_984, y: 2, z: 0 },
    { x: 0, y: -2048, z: 0 },
    { x: 0, y: 2, z: 29_999_984 },
  ])
    assert.throws(() => makeAnalogIntention({ ...identity, anchor }));
  assert.throws(() => makeAnalogIntention({ ...identity, id: "owner" }));
  assert.throws(() =>
    makeAnalogIntention({ ...identity, worldId: "bad world" }),
  );
});

test("companion accepts only current-context canonical analog server snapshots", () => {
  const value = makeAnalogIntention(identity);
  const gateway: GatewayContext = {
    serverId: "server",
    serverEpoch: "epoch",
    actorId: identity.id,
    projectId: identity.projectId,
    projectRevision: 3,
    authorizationRevision: 1,
    requestId: "request",
  };
  assert.deepEqual(
    serverBlueprint(value, gateway, identity.worldId, identity.dimension),
    value,
  );
  assert.throws(() =>
    serverBlueprint(
      value,
      { ...gateway, projectRevision: 4 },
      identity.worldId,
      identity.dimension,
    ),
  );
  assert.throws(() =>
    serverBlueprint(
      value,
      { ...gateway, projectId: identity.id },
      identity.worldId,
      identity.dimension,
    ),
  );
  const changed = structuredClone(value);
  changed.blocks[0]!.state = "minecraft:chest";
  assert.throws(() =>
    serverBlueprint(changed, gateway, identity.worldId, identity.dimension),
  );
});
