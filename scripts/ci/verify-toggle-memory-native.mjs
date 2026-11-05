import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
const marker = "ENGINEER_TOGGLE_MEMORY_NATIVE ";
const lines = (await readFile(process.argv[2], "utf8")).split(/\r?\n/u);
const records = lines
  .filter((line) => line.includes(marker))
  .map((line) => JSON.parse(line.slice(line.indexOf(marker) + marker.length)));
assert.equal(records.length, 8);
const identities = new Set();
for (const record of records) {
  assert.equal(record.kind, "toggle-memory-native-feasibility");
  assert.equal(typeof record.initial, "boolean");
  assert.ok(
    ["NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"].includes(
      record.rotation,
    ),
  );
  const key = `${record.rotation}:${record.initial}`;
  assert.ok(!identities.has(key));
  identities.add(key);
  assert.equal(record.samples.length, 96);
  let origin;
  record.samples.forEach((sample, index) => {
    const phase = Math.floor(index / 16),
      elapsed = 8 + (index % 16);
    assert.equal(sample.phase, phase);
    assert.equal(sample.elapsedTicks, elapsed);
    assert.ok(Number.isSafeInteger(sample.gameTick) && sample.gameTick >= 0);
    origin ??= sample.gameTick - phase * 24 - elapsed;
    assert.equal(sample.gameTick, origin + phase * 24 + elapsed);
    assert.equal(sample.input, phase === 1 || phase === 3);
    const bit = phase === 1 || phase === 2 ? !record.initial : record.initial;
    assert.equal(sample.stored, bit);
    assert.equal(sample.signal, bit ? 15 : 0);
  });
}
console.log(
  JSON.stringify({
    kind: "toggle-memory-native-replay",
    rotations: 4,
    initialStates: 2,
    phases: 6,
    settledSamples: 768,
    productionContract: false,
  }),
);

const { createToggleMemoryIntention, readToggleMemoryIntention } =
  await import("../../dist/companion/src/toggle-memory-intention.js");
const intentionLines = lines.filter((line) =>
  line.includes("ENGINEER_TOGGLE_MEMORY_INTENTION "),
);
assert.equal(intentionLines.length, 1);
const intentionMarker = "ENGINEER_TOGGLE_MEMORY_INTENTION ";
const nativeIntention = JSON.parse(
  intentionLines[0].slice(
    intentionLines[0].indexOf(intentionMarker) + intentionMarker.length,
  ),
);
const identity = "d0c177a4-75a5-436c-9440-554ad92f9d62";
const context = {
  worldId: "test",
  dimension: "minecraft:overworld",
  projectId: identity,
};
const expected = createToggleMemoryIntention({
  ...context,
  id: identity,
  revision: 1,
  anchor: { x: 0, y: 80, z: 0 },
  rotation: 0,
});
assert.deepEqual(nativeIntention, expected);
assert.deepEqual(readToggleMemoryIntention(nativeIntention, context), expected);
console.log(
  JSON.stringify({
    kind: "toggle-memory-native-source-parity",
    cells: expected.blocks.length,
    verification: expected.verification,
    admission: "preview-build-only",
    automatedTesting: false,
  }),
);
