import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFile, readdir } from "node:fs/promises";
import { join } from "node:path";

const directory = process.argv[2];
const nbt = createRequire(import.meta.url)("prismarine-nbt");
const files = (await readdir(directory)).filter(
  (name) => name.includes(".craft-") && name.endsWith(".json"),
);
assert.equal(files.length, 1);
const record = JSON.parse(await readFile(join(directory, files[0]), "utf8"));
assert.equal(record.kind, "native-oak-planks-v1");
assert.equal(record.stage, "COMPLETE");
assert.equal(record.step, 4);
assert.equal(record.steps.length, 4);
assert.ok(
  Number.isInteger(record.sourceHotbar) &&
    record.sourceHotbar >= 0 &&
    record.sourceHotbar < 9,
);
const log = { id: "minecraft:oak_log", count: 1 };
const planks = { id: "minecraft:oak_planks", count: 4 };
const frame = (inventoryStack, gridStack, cursor) => {
  const inventory = Array.from({ length: 43 }, () => ({}));
  inventory[record.sourceHotbar] = inventoryStack;
  return {
    inventory,
    menu: { schemaVersion: 1, inputs: [gridStack, {}, {}, {}], cursor },
    selected: record.initial.selected,
  };
};
const expected = [
  frame(log, {}, {}),
  frame({}, {}, log),
  frame({}, log, {}),
  frame({}, {}, planks),
  frame(planks, {}, {}),
];
assert.deepEqual(record.initial, expected[0]);
const events = ["NEW"];
for (let step = 1; step <= 4; step++) {
  const row = record.steps[step - 1];
  assert.deepEqual(row, {
    step,
    status: "OBSERVED",
    before: expected[step - 1],
    expectedAfter: expected[step],
    observedAfter: expected[step],
  });
  events.push(`PREPARED:${step}`, `OBSERVED:${step}`);
}
events.push("COMPLETE");
assert.deepEqual(record.events, events);
const players = [];
for (const phase of ["crafted", "crafted-restarted"]) {
  const bytes = await readFile(join(directory, `worker-menu-${phase}.dat`));
  assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
  const player = nbt.simplify((await nbt.parse(bytes)).parsed);
  assert.deepEqual(player.Inventory, [
    { Slot: record.sourceHotbar, ...planks },
  ]);
  assert.deepEqual(player.engineer_worker_menu, expected[4].menu);
  for (const stack of Object.values(player.equipment ?? {}))
    assert.deepEqual(stack, {});
  players.push(player);
}
assert.deepEqual(players[0].UUID, players[1].UUID);
const uuid = Buffer.alloc(16);
Array.from(players[0].UUID).forEach((part, index) =>
  uuid.writeInt32BE(part, index * 4),
);
assert.equal(uuid.toString("hex"), record.worker.replaceAll("-", ""));
console.log(
  JSON.stringify({
    kind: "native-oak-crafting",
    clicks: 4,
    consumedLogs: 1,
    retainedPlanks: 4,
    savedWorldRestart: true,
    resultStored: false,
  }),
);
