import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { isDeepStrictEqual } from "node:util";
import { readToggleMemoryIntention } from "../../dist/companion/src/toggle-memory-intention.js";

const marker = "ENGINEER_MEMORY_RECOVERY_PLAN ";
const records = (await readFile(process.argv[2], "utf8"))
  .split(/\r?\n/u)
  .filter((line) => line.includes(marker))
  .map((line) => JSON.parse(line.slice(line.indexOf(marker) + marker.length)));
assert.equal(records.length, 32);
const identities = new Set();
const hash = (value) =>
  createHash("sha256").update(JSON.stringify(value)).digest("hex");
for (const { plan, originRecord: journal, pending } of records) {
  assert.equal(plan.schemaVersion, 1);
  assert.equal(plan.kind, "memory-recovery-plan");
  assert.equal(plan.executionAuthority, false);
  assert.equal(plan.functionalPass, false);
  const source = plan.intention;
  assert.deepEqual(
    readToggleMemoryIntention(source, {
      worldId: source.worldId,
      dimension: source.dimension,
      projectId: source.projectId,
    }),
    source,
  );
  assert.equal(source.verification, "untested");
  assert.deepEqual(journal.intention, source);
  assert.equal(journal.id, plan.originJournalId);
  assert.equal(journal.stage, plan.originStage);
  assert.equal(hash(journal), plan.originRecordDigest);
  const { birthDigest, ...birth } = pending;
  assert.equal(hash(birth), birthDigest);
  assert.equal(plan.pendingBirthDigest, birthDigest);
  assert.deepEqual(pending.intention, source);
  assert.equal(pending.journalId, journal.id);
  assert.equal(pending.startedTick, journal.startedTick);
  assert.deepEqual(pending.initial, journal.initial);
  assert.deepEqual(plan.initialTarget, journal.initial);
  const orientation = {
    0: { facing: "north", dx: -1, dz: 0 },
    90: { facing: "east", dx: 0, dz: -1 },
    180: { facing: "south", dx: 1, dz: 0 },
    270: { facing: "west", dx: 0, dz: 1 },
  }[source.rotation];
  assert.ok(orientation);
  const frame = (input, bit) => ({
    lever: `minecraft:lever[face=floor,facing=${orientation.facing},powered=${input}]`,
    bulb: `minecraft:waxed_copper_bulb[lit=${bit},powered=${input}]`,
  });
  const initial = plan.initialTarget.bulb === frame(false, true).bulb;
  assert.deepEqual(plan.initialTarget, frame(false, initial));
  const possible = [
    frame(false, initial),
    frame(true, !initial),
    frame(false, !initial),
    frame(true, initial),
  ];
  const phase = possible.findIndex((value) =>
    isDeepStrictEqual(value, plan.current),
  );
  assert.ok(phase >= 0);
  const key = `${source.rotation}:${initial}:${phase}`;
  assert.ok(!identities.has(key));
  identities.add(key);
  assert.ok(
    journal.stage === (phase === 0 ? "NEW" : "OBSERVED") ||
      journal.stage === "INTERRUPTED",
  );
  const interrupted = journal.stage === "INTERRUPTED";
  assert.equal(journal.events.length, phase * 2 + (interrupted ? 1 : 0));
  let input = false,
    bit = initial,
    previousTick = journal.startedTick;
  const lever = {
    x: source.anchor.x + orientation.dx,
    y: source.anchor.y,
    z: source.anchor.z + orientation.dz,
  };
  for (let index = 0; index < phase; index++) {
    const prepared = journal.events[index * 2],
      observed = journal.events[index * 2 + 1];
    const requested = index % 2 === 0,
      nextBit = requested ? !bit : bit;
    assert.ok(
      Number.isSafeInteger(prepared.tick) && prepared.tick >= previousTick,
    );
    assert.deepEqual(prepared, {
      stage: "PREPARED",
      step: index + 1,
      tick: prepared.tick,
      transition: {
        schemaVersion: 1,
        kind: "memory-input-transition",
        worldId: source.worldId,
        dimension: source.dimension,
        projectId: source.projectId,
        blueprintId: source.id,
        revision: source.revision,
        sourceDigest: hash(source),
        lever,
        bulb: source.anchor,
        before: frame(input, bit),
        requestedInput: requested,
        expectedAfter: frame(requested, nextBit),
      },
    });
    assert.deepEqual(observed, {
      stage: "OBSERVED",
      step: index + 1,
      tick: prepared.tick,
      observed: frame(requested, nextBit),
    });
    previousTick = prepared.tick;
    input = requested;
    bit = nextBit;
  }
  if (interrupted) {
    const terminal = journal.events.at(-1);
    assert.ok(terminal.tick >= previousTick);
    assert.deepEqual(terminal, {
      stage: "INTERRUPTED",
      step: phase,
      tick: terminal.tick,
      observed: plan.current,
    });
  }
  const expectedInputs = [[], [false, true, false], [true, false], [false]][
    phase
  ];
  assert.deepEqual(plan.requestedInputs, expectedInputs);
  const expectedFrames = [];
  for (const requested of expectedInputs) {
    if (requested) bit = !bit;
    input = requested;
    expectedFrames.push(frame(input, bit));
  }
  assert.deepEqual(plan.expectedFrames, expectedFrames);
  assert.deepEqual(frame(input, bit), plan.initialTarget);
}
console.log(
  JSON.stringify({
    kind: "memory-recovery-plan-native-replay",
    records: 32,
    initialStates: 2,
    rotations: 4,
    currentStates: 4,
    executionAuthority: false,
    functionalPass: false,
  }),
);
