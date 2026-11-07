import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { readToggleMemoryIntention } from "../../dist/companion/src/toggle-memory-intention.js";

const document = JSON.parse(await readFile(process.argv[2], "utf8"));
assert.ok(
  process.argv[3] === undefined || process.argv[3] === "--functional-record",
);
const functional = process.argv[3] === "--functional-record";
const record = functional ? document.trace : document;
if (functional) {
  assert.deepEqual(
    Object.keys(document).sort(),
    [
      "schemaVersion",
      "kind",
      "id",
      "projectId",
      "revision",
      "component",
      "assignmentBasis",
      "assignmentGeneration",
      "controllerId",
      "authorizationRevision",
      "outcome",
      "trace",
    ].sort(),
  );
  assert.equal(document.schemaVersion, 1);
  assert.equal(document.kind, "native-memory-functional-trial");
  assert.match(
    document.id,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u,
  );
  assert.equal(document.component, "root");
  assert.equal(document.outcome, "passed");
  assert.equal(document.controllerId, record.controller);
  assert.equal(document.projectId, record.observations.intention.projectId);
  assert.equal(document.revision, record.observations.intention.revision);
  assert.ok(Number.isSafeInteger(document.revision) && document.revision > 0);
  assert.match(document.assignmentBasis, /^[0-9a-f]{64}$/u);
  for (const value of [
    document.assignmentGeneration,
    document.authorizationRevision,
  ]) {
    assert.ok(Number.isSafeInteger(value) && value >= 0);
  }
}
assert.deepEqual(
  Object.keys(record).sort(),
  [
    "schemaVersion",
    "kind",
    "controller",
    "observations",
    "inputJournal",
  ].sort(),
);
assert.equal(record.schemaVersion, 1);
assert.equal(record.kind, "memory-timed-trial");
assert.match(
  record.controller,
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u,
);
const trace = record.observations,
  journal = record.inputJournal;
const source = trace.intention;
assert.deepEqual(
  readToggleMemoryIntention(source, {
    worldId: source.worldId,
    dimension: source.dimension,
    projectId: source.projectId,
  }),
  source,
);
assert.equal(source.verification, "untested");
assert.equal(trace.schemaVersion, 1);
assert.equal(trace.kind, "memory-state-samples");
assert.equal(trace.stage, "COMPLETE");
assert.equal(trace.sampleCount, 96);
assert.equal(trace.samples.length, 96);
assert.ok(Number.isSafeInteger(trace.startedTick) && trace.startedTick >= 0);
const orientation = {
  0: { name: "NONE", facing: "north", dx: -1, dz: 0 },
  90: { name: "CLOCKWISE_90", facing: "east", dx: 0, dz: -1 },
  180: { name: "CLOCKWISE_180", facing: "south", dx: 1, dz: 0 },
  270: { name: "COUNTERCLOCKWISE_90", facing: "west", dx: 0, dz: 1 },
}[source.rotation];
assert.ok(orientation);
assert.equal(trace.rotation, orientation.name);
const frame = (input, bit) => ({
  lever: `minecraft:lever[face=floor,facing=${orientation.facing},powered=${input}]`,
  bulb: `minecraft:waxed_copper_bulb[lit=${bit},powered=${input}]`,
});
const initial = trace.initial.bulb === frame(false, true).bulb;
assert.deepEqual(trace.initial, frame(false, initial));
assert.deepEqual(trace.restored, trace.initial);
trace.samples.forEach((sample, index) => {
  const phase = Math.floor(index / 16),
    elapsedTicks = 8 + (index % 16);
  const stored = phase === 1 || phase === 2 ? !initial : initial;
  assert.deepEqual(sample, {
    gameTick: trace.startedTick + phase * 24 + elapsedTicks,
    phase,
    elapsedTicks,
    input: phase === 1 || phase === 3,
    stored,
    comparatorPowered: stored,
    signal: stored ? 15 : 0,
  });
});
assert.equal(journal.schemaVersion, 1);
assert.equal(journal.kind, "memory-input-journal");
assert.match(journal.id, /^[0-9a-f-]{36}$/u);
assert.deepEqual(journal.intention, source);
assert.deepEqual(journal.initial, trace.initial);
assert.equal(journal.startedTick, trace.startedTick);
assert.equal(journal.stage, "RESTORED");
assert.equal(journal.events.length, 9);
const sourceDigest = createHash("sha256")
  .update(JSON.stringify(source))
  .digest("hex");
const lever = {
  x: source.anchor.x + orientation.dx,
  y: source.anchor.y,
  z: source.anchor.z + orientation.dz,
};
let input = false,
  bit = initial;
for (let index = 0; index < 4; index++) {
  const requested = index % 2 === 0,
    nextBit = requested ? !bit : bit;
  const tick = trace.startedTick + (index + 1) * 24;
  const after = frame(requested, nextBit);
  assert.deepEqual(journal.events[index * 2], {
    stage: "PREPARED",
    step: index + 1,
    tick,
    transition: {
      schemaVersion: 1,
      kind: "memory-input-transition",
      worldId: source.worldId,
      dimension: source.dimension,
      projectId: source.projectId,
      blueprintId: source.id,
      revision: source.revision,
      sourceDigest,
      lever,
      bulb: source.anchor,
      before: frame(input, bit),
      requestedInput: requested,
      expectedAfter: after,
    },
  });
  assert.deepEqual(journal.events[index * 2 + 1], {
    stage: "OBSERVED",
    step: index + 1,
    tick,
    observed: after,
  });
  input = requested;
  bit = nextBit;
}
assert.deepEqual(journal.events[8], {
  stage: "RESTORED",
  step: 4,
  tick: trace.startedTick + 143,
  observed: trace.initial,
});
console.log(
  JSON.stringify({
    kind: functional
      ? "memory-functional-native-replay"
      : "memory-timed-native-replay",
    settledSamples: 96,
    nativeEdges: 4,
    restoredOriginalPair: true,
    functionalPass: false,
  }),
);

if (functional) {
  console.log(
    JSON.stringify({
      recordedOutcome: "passed",
      grantsCurrentPass: false,
      note: "Offline replay checks saved observations; current authority is established only by the live server run.",
    }),
  );
}
