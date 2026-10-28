import assert from "node:assert/strict";
import { test } from "node:test";
import { validateObservation } from "../companion/src/types.js";

const observation = () => ({
  worldId: "world",
  dimension: "minecraft:overworld",
  tick: 50,
  region: { min: { x: 108, y: 100, z: -1 }, max: { x: 110, y: 102, z: 1 } },
  blocks: [
    {
      pos: { x: 109, y: 101, z: 0 },
      state:
        "minecraft:repeater[delay=4,facing=west,locked=false,powered=false]",
    },
  ],
  perception: {
    schemaVersion: 1,
    kind: "block",
    actorId: "11111111-1111-4111-8111-111111111111",
    player: {
      position: { x: 109.5, y: 102, z: 4 },
      eye: { x: 109.5, y: 103.62, z: 4 },
      look: { x: 0, y: -0.6, z: -0.8 },
    },
    target: { x: 109, y: 101, z: 0 },
    differences: { missing: 0, changed: 0, unplanned: 0 },
  },
});

test("targeted server perception retains bounded actual player/view facts without world text", () => {
  const source = observation();
  const parsed = validateObservation(source) as unknown as {
    perception: unknown;
  };
  assert.deepEqual(parsed.perception, source.perception);
  const view = {
    ...source,
    perception: { ...source.perception, kind: "view", face: "south" },
  };
  assert.deepEqual(
    (validateObservation(view) as unknown as { perception: unknown })
      .perception,
    view.perception,
  );
});

test("perception rejects malformed vectors, uncovered targets, arbitrary inventory data and unknown schemas", () => {
  for (const alter of [
    (value: ReturnType<typeof observation>) => {
      value.perception.schemaVersion = 99;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.kind = "inventory";
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.actorId = "world-sign-text";
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.player.position.x = Infinity;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.player.eye.y = NaN;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.player.look.x = 2;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.player.look = { x: 0, y: 0, z: 0 };
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.target.x = 111;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.target.x = 108;
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.differences.missing = -1;
    },
    (value: ReturnType<typeof observation>) => {
      Object.assign(value.perception, { inventory: [{ nbt: "private" }] });
    },
    (value: ReturnType<typeof observation>) => {
      Object.assign(value.perception.player, { otherPlayers: ["private"] });
    },
    (value: ReturnType<typeof observation>) => {
      value.perception.kind = "view";
      Object.assign(value.perception, { face: "up;run command" });
    },
  ]) {
    const value = observation();
    alter(value);
    assert.throws(() => validateObservation(value));
  }
  const legacy = observation();
  delete (legacy as Partial<typeof legacy>).perception;
  assert.equal(
    validateObservation(legacy).tick,
    50,
    "Ordinary unscoped observations retain wire compatibility",
  );
});
