import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  createFullAdderIntention,
  readFullAdderIntention,
} from "../../dist/companion/src/full-adder-intention.js";

const path = process.argv[2];
if (!path) throw new Error("Provide the current native GameTest log.");
const lines = (await readFile(path, "utf8")).split(/\r?\n/u);
function extract(marker) {
  const found = lines.filter((line) => line.includes(marker));
  assert.equal(found.length, 1, `Expected exactly one current-run ${marker}`);
  return JSON.parse(found[0].slice(found[0].indexOf(marker) + marker.length));
}
const identity = "0d3574ee-7b0f-4d8b-b38f-cef756e319ca";
const context = {
  worldId: "test",
  dimension: "minecraft:overworld",
  projectId: identity,
};
const native = extract("ENGINEER_FULL_ADDER_INTENTION ");
const expected = createFullAdderIntention({
  ...context,
  id: identity,
  revision: 1,
  anchor: { x: 0, y: 80, z: 0 },
  rotation: 0,
});
assert.deepEqual(
  native,
  expected,
  "Native and companion source contracts must match every cell/state/stage/pin and metadata field.",
);
assert.deepEqual(readFullAdderIntention(native, context), expected);
const definition = extract("ENGINEER_FULL_ADDER_DEFINITION ");
assert.equal(definition.graphDigest, expected.graphDigest);
assert.deepEqual(definition.graph, expected.connected);
assert.deepEqual(
  definition.geometry,
  expected.blocks.map(({ prerequisites, ...cell }) => cell),
);
console.log(
  JSON.stringify({
    schemaVersion: 1,
    kind: "full-adder-native-source-parity",
    graphDigest: expected.graphDigest,
    cells: expected.blocks.length,
    pins: expected.connected.ports.length,
    verification: expected.verification,
  }),
);
