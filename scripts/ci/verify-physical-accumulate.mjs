import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Two conversation gathers into one companion: dirt first, then oak while still
// holding the dirt. Each journal must show exactly one slot gaining one item.
const directory = process.argv[2];
const files = readdirSync(directory).filter((name) => name.endsWith(".json")).sort();
assert.deepEqual(files, ["1-dirt.json", "2-oak.json"]);
const read = (name) => JSON.parse(readFileSync(join(directory, name), "utf8"));
const changed = (record) =>
  record.inventoryBefore
    .map((stack, slot) => [slot, stack, record.inventorySaved[slot]])
    .filter(([, before, after]) => JSON.stringify(before) !== JSON.stringify(after));
const occupied = (stacks) => stacks.filter((stack) => stack?.id);
const [dirt, oak] = files.map(read);
for (const [record, item] of [
  [dirt, "minecraft:dirt"],
  [oak, "minecraft:oak_log"],
]) {
  assert.equal(record.schemaVersion, 3);
  assert.equal(record.stage, "INVENTORY_SAVED");
  assert.equal(record.expectedItem, item);
  assert.equal(record.expectedCount, 1);
  assert.equal(record.inventoryBefore.length, record.inventorySaved.length);
  const diff = changed(record);
  assert.equal(diff.length, 1, "exactly one slot changes per gather");
  assert.equal(diff[0][2].id, item);
  assert.equal(diff[0][2].count ?? 1, (diff[0][1]?.count ?? 0) + 1);
  for (const key of ["permissions", "lease", "ticket", "verified", "connection"])
    assert.equal(Object.hasOwn(record, key), false);
}
assert.equal(occupied(dirt.inventoryBefore).length, 0);
assert.deepEqual(oak.inventoryBefore, dirt.inventorySaved, "oak gather starts from the saved dirt inventory");
assert.deepEqual(
  occupied(oak.inventorySaved).map((stack) => stack.id).sort(),
  ["minecraft:dirt", "minecraft:oak_log"],
);
console.log(
  "Accumulating gather: dirt kept, one oak added in one slot, both journals conserve exactly.",
);
