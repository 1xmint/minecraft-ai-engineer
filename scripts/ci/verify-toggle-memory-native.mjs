import { createHash } from "node:crypto";
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

// Independently hand-work the coupled state change immediately after each native
// edge. These are observed native states, not a Java encoder/decoder round trip.
const orientations = {
  NONE: {
    angle: 0,
    facing: "north",
    bulb: { x: 12, y: 2, z: 12 },
    lever: { x: 11, y: 2, z: 12 },
  },
  CLOCKWISE_90: {
    angle: 90,
    facing: "east",
    bulb: { x: 4, y: 2, z: 12 },
    lever: { x: 4, y: 2, z: 11 },
  },
  CLOCKWISE_180: {
    angle: 180,
    facing: "south",
    bulb: { x: 4, y: 2, z: 4 },
    lever: { x: 5, y: 2, z: 4 },
  },
  COUNTERCLOCKWISE_90: {
    angle: 270,
    facing: "west",
    bulb: { x: 12, y: 2, z: 4 },
    lever: { x: 12, y: 2, z: 5 },
  },
};
for (const record of records) {
  const orientation = orientations[record.rotation];
  const intended = createToggleMemoryIntention({
    ...context,
    id: identity,
    revision: 1,
    anchor: orientation.bulb,
    rotation: orientation.angle,
  });
  const sourceDigest = createHash("sha256")
    .update(JSON.stringify(intended))
    .digest("hex");
  assert.equal(record.edges.length, 4);
  let input = false,
    bit = record.initial;
  const frame = (on, stored) => ({
    lever: `minecraft:lever[face=floor,facing=${orientation.facing},powered=${on}]`,
    bulb: `minecraft:waxed_copper_bulb[lit=${stored},powered=${on}]`,
  });
  const origin = record.samples[0].gameTick - 16;
  record.edges.forEach((row, index) => {
    const phase = index + 1,
      requested = phase === 1 || phase === 3;
    assert.equal(row.phase, phase);
    assert.equal(row.gameTick, origin + 8 + phase * 24);
    const afterBit = requested ? !bit : bit;
    const before = frame(input, bit),
      expectedAfter = frame(requested, afterBit);
    assert.deepEqual(row.transition, {
      schemaVersion: 1,
      kind: "memory-input-transition",
      ...context,
      blueprintId: identity,
      revision: 1,
      sourceDigest,
      lever: orientation.lever,
      bulb: orientation.bulb,
      before,
      requestedInput: requested,
      expectedAfter,
    });
    assert.deepEqual(row.observedAfter, expectedAfter);
    input = requested;
    bit = afterBit;
  });
  assert.equal(input, false);
  assert.equal(bit, record.initial);
  const log = record.inputJournal;
  assert.deepEqual(
    Object.keys(log).sort(),
    [
      "schemaVersion",
      "kind",
      "id",
      "intention",
      "initial",
      "startedTick",
      "stage",
      "events",
    ].sort(),
  );
  assert.equal(log.schemaVersion, 1);
  assert.equal(log.kind, "memory-input-journal");
  assert.match(
    log.id,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u,
  );
  assert.deepEqual(log.intention, intended);
  assert.deepEqual(log.initial, frame(false, record.initial));
  assert.ok(
    Number.isSafeInteger(log.startedTick) &&
      log.startedTick >= 0 &&
      log.startedTick <= record.edges[0].gameTick,
  );
  assert.equal(log.stage, "RESTORED");
  assert.equal(log.events.length, 9);
  record.edges.forEach((edge, index) => {
    assert.deepEqual(log.events[index * 2], {
      stage: "PREPARED",
      step: index + 1,
      tick: edge.gameTick,
      transition: edge.transition,
    });
    assert.deepEqual(log.events[index * 2 + 1], {
      stage: "OBSERVED",
      step: index + 1,
      tick: edge.gameTick,
      observed: edge.observedAfter,
    });
  });
  assert.deepEqual(log.events[8], {
    stage: "RESTORED",
    step: 4,
    tick: origin + 154,
    observed: frame(false, record.initial),
  });
}
console.log(
  JSON.stringify({
    kind: "memory-input-transition-native-replay",
    nativeEdges: 32,
    restoresInitialPair: true,
    executionAuthority: false,
  }),
);

console.log(
  JSON.stringify({
    kind: "memory-input-journal-native-replay",
    journals: 8,
    preparedBeforeEdges: 32,
    observedAfterEdges: 32,
    restoredPairs: 8,
    functionalPass: false,
    reloadedExecution: false,
  }),
);

// Replay the production-shaped read-only sampler independently of its Java
// implementation and compare it with the original native fixture observations.
const samplerMarker = "ENGINEER_MEMORY_STATE_SAMPLES ";
const samplerRecords = lines
  .filter((line) => line.includes(samplerMarker))
  .map((line) =>
    JSON.parse(line.slice(line.indexOf(samplerMarker) + samplerMarker.length)),
  );
assert.equal(samplerRecords.length, 8);
const samplerIdentities = new Set();
for (const record of samplerRecords) {
  assert.deepEqual(
    Object.keys(record).sort(),
    [
      "schemaVersion",
      "kind",
      "intention",
      "rotation",
      "initial",
      "startedTick",
      "stage",
      "sampleCount",
      "samples",
      "restored",
    ].sort(),
  );
  assert.equal(record.schemaVersion, 1);
  assert.equal(record.kind, "memory-state-samples");
  assert.equal(record.stage, "COMPLETE");
  assert.equal(record.sampleCount, 96);
  assert.equal(record.samples.length, 96);
  const orientation = orientations[record.rotation];
  assert.ok(orientation);
  assert.equal(record.intention.rotation, orientation.angle);
  assert.deepEqual(
    readToggleMemoryIntention(record.intention, context),
    record.intention,
  );
  const frame = (stored) => ({
    lever: `minecraft:lever[face=floor,facing=${orientation.facing},powered=false]`,
    bulb: `minecraft:waxed_copper_bulb[lit=${stored},powered=false]`,
  });
  const initial = record.initial.bulb === frame(true).bulb;
  assert.deepEqual(record.initial, frame(initial));
  assert.deepEqual(record.restored, frame(initial));
  const key = `${record.rotation}:${initial}`;
  assert.ok(!samplerIdentities.has(key));
  samplerIdentities.add(key);
  const native = records.find(
    (entry) => entry.rotation === record.rotation && entry.initial === initial,
  );
  assert.ok(native);
  assert.equal(record.startedTick, native.samples[0].gameTick - 8);
  assert.ok(
    Number.isSafeInteger(record.startedTick) && record.startedTick >= 0,
  );
  record.samples.forEach((sample, index) => {
    const phase = Math.floor(index / 16),
      elapsedTicks = 8 + (index % 16);
    const stored = phase === 1 || phase === 2 ? !initial : initial;
    assert.deepEqual(sample, {
      gameTick: record.startedTick + phase * 24 + elapsedTicks,
      phase,
      elapsedTicks,
      input: phase === 1 || phase === 3,
      stored,
      comparatorPowered: stored,
      signal: stored ? 15 : 0,
    });
    const { comparatorPowered, ...shared } = sample;
    assert.deepEqual(shared, native.samples[index]);
  });
}
console.log(
  JSON.stringify({
    kind: "memory-state-sampler-native-replay",
    records: 8,
    settledSamples: 768,
    functionalPass: false,
  }),
);
