import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const directory = process.argv[2];
const files = readdirSync(directory).filter((name) => name.endsWith(".json"));
assert.equal(files.length, 1, "Exactly one successful fixture transaction");
const record = JSON.parse(readFileSync(join(directory, files[0]), "utf8"));
assert.equal(record.schemaVersion, 3);
assert.equal(record.material, "dirt");
assert.equal(record.sourceState, "minecraft:dirt");
assert.equal(record.expectedItem, "minecraft:dirt");
assert.equal(record.expectedCount, 1);
assert.equal(record.stage, "INVENTORY_SAVED");
assert.deepEqual(record.events, [
  "NEW",
  "MINE_PREPARED",
  "DROP_OBSERVED",
  "PICKUP_PREPARED",
  "INVENTORY_SAVED",
]);
assert.deepEqual(record.source, { x: 2421, y: 101, z: 5 });
for (const field of ["worker", "project", "drop"])
  assert.match(record[field], /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
assert.equal(record.dimension, "minecraft:overworld");
assert.ok(Array.isArray(record.inventoryBefore));
assert.equal(record.inventorySaved.length, record.inventoryBefore.length);
assert.notDeepEqual(record.inventorySaved, record.inventoryBefore);
assert.equal(
  record.inventorySaved.filter((stack) => stack?.id === "minecraft:dirt")
    .length,
  1,
);
for (const key of ["permissions", "lease", "ticket", "verified", "connection"])
  assert.equal(Object.hasOwn(record, key), false);
console.log(
  "Physical gather record: ordered intents, native drop identity and saved inventory verified.",
);
