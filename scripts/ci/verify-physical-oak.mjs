import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const directory = process.argv[2];
const files = readdirSync(directory).filter((name) => name.endsWith(".json"));
assert.deepEqual(files, ["oak.json"]);
const record = JSON.parse(readFileSync(join(directory, files[0]), "utf8"));
assert.deepEqual(
  Object.keys(record).sort(),
  [
    "schemaVersion",
    "world",
    "project",
    "worker",
    "dimension",
    "source",
    "inventoryBefore",
    "stage",
    "events",
    "material",
    "sourceState",
    "expectedItem",
    "expectedCount",
    "drop",
    "inventorySaved",
  ].sort(),
);
assert.equal(record.schemaVersion, 3);
assert.equal(record.material, "oak-log");
assert.equal(record.sourceState, "minecraft:oak_log[axis=x]");
assert.equal(record.expectedItem, "minecraft:oak_log");
assert.equal(record.expectedCount, 1);
assert.equal(record.stage, "INVENTORY_SAVED");
assert.deepEqual(record.events, [
  "NEW",
  "MINE_PREPARED",
  "DROP_OBSERVED",
  "PICKUP_PREPARED",
  "INVENTORY_SAVED",
]);
assert.deepEqual(record.source, { x: 2631, y: 101, z: 4 });
assert.equal(record.dimension, "minecraft:overworld");
for (const field of ["worker", "project", "drop"])
  assert.match(record[field], /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
assert.equal(record.inventoryBefore.length, record.inventorySaved.length);
assert.equal(record.inventoryBefore.filter((stack) => stack?.id).length, 0);
const retained = record.inventorySaved.filter((stack) => stack?.id);
assert.equal(retained.length, 1);
assert.equal(retained[0].id, "minecraft:oak_log");
assert.equal(retained[0].count ?? 1, 1);
console.log(
  "Oak journal: exact source state, one native log, ordered intents and saved inventory verified.",
);
