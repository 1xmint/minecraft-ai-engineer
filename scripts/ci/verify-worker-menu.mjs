import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

// Independent native-NBT reader; no production menu snapshot codec is imported.
const nbt = createRequire(import.meta.url)("prismarine-nbt");
const directory = process.argv[2];
const empty = (stack) => assert.deepEqual(stack, {});
const dirt = (stack) => {
  assert.equal(stack.id, "minecraft:dirt");
  assert.equal(stack.count ?? 1, 1);
  assert.deepEqual(
    Object.keys(stack).filter(
      (key) => !["id", "count", "components"].includes(key),
    ),
    [],
  );
  assert.deepEqual(stack.components ?? {}, {});
};
const records = [];
const phases = ["grid", "grid-fault", "cursor", "cursor-fault", "settled"];
for (const phase of phases) {
  const bytes = await readFile(join(directory, `worker-menu-${phase}.dat`));
  assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
  const { parsed } = await nbt.parse(bytes);
  const player = nbt.simplify(parsed);
  const menu = player.engineer_worker_menu;
  assert.deepEqual(Object.keys(menu).sort(), [
    "cursor",
    "inputs",
    "schemaVersion",
  ]);
  assert.equal(menu.schemaVersion, 1);
  assert.equal(menu.inputs.length, 4);
  if (phase.startsWith("grid")) {
    assert.deepEqual(player.Inventory, []);
    dirt(menu.inputs[0]);
    menu.inputs.slice(1).forEach(empty);
    empty(menu.cursor);
  } else if (phase.startsWith("cursor")) {
    assert.deepEqual(player.Inventory, []);
    menu.inputs.forEach(empty);
    dirt(menu.cursor);
  } else {
    assert.equal(player.Inventory.length, 1);
    const { Slot, ...stack } = player.Inventory[0];
    assert.equal(Slot, 0);
    dirt(stack);
    menu.inputs.forEach(empty);
    empty(menu.cursor);
  }
  records.push(player);
}
for (const player of records) assert.deepEqual(records[0].UUID, player.UUID);
for (const player of records)
  Object.values(player.equipment ?? {}).forEach(empty);
assert.equal(records[0].UUID.length, 4);
const uuidBytes = Buffer.alloc(16);
Array.from(records[0].UUID).forEach((part, index) =>
  uuidBytes.writeInt32BE(part, index * 4),
);
const hex = uuidBytes.toString("hex");
assert.equal(
  `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`,
  (await readFile(join(directory, "physical-mine-prepared"), "utf8")).trim(),
);
console.log(
  JSON.stringify({
    schemaVersion: 1,
    kind: "native-worker-menu-recovery",
    phases,
    retainedNativeDirt: 1,
    resultStored: false,
  }),
);
