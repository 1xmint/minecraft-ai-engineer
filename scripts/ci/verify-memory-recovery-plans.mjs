import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { isDeepStrictEqual } from "node:util";
import { readToggleMemoryIntention } from "../../dist/companion/src/toggle-memory-intention.js";

const marker = "ENGINEER_MEMORY_RECOVERY_PLAN ";
const lines = (await readFile(process.argv[2], "utf8")).split(/\r?\n/u);
const records = lines
  .filter((line) => line.includes(marker))
  .map((line) => JSON.parse(line.slice(line.indexOf(marker) + marker.length)));
assert.equal(records.length, 32);
const identities = new Set();
const hash = (value) =>
  createHash("sha256").update(JSON.stringify(value)).digest("hex");
function verifyPlan({ plan, originRecord: journal, pending }, identities) {
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
  return { source, phase, initial, frame, lever };
}
for (const record of records) verifyPlan(record, identities);
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

const recoveryMarker = "ENGINEER_MEMORY_RECOVERY_JOURNAL ";
const recoveryRecords = lines
  .filter((line) => line.includes(recoveryMarker))
  .map((line) =>
    JSON.parse(
      line.slice(line.indexOf(recoveryMarker) + recoveryMarker.length),
    ),
  );
assert.equal(recoveryRecords.length, 32);
const recoveryIdentities = new Set();
let recoveryEdges = 0;
for (const wrapper of recoveryRecords) {
  const context = verifyPlan(wrapper, recoveryIdentities);
  const { recovery, plan, pending, originRecord } = wrapper;
  recoveryEdges += verifyRecovery(
    recovery,
    plan,
    pending,
    originRecord,
    context,
  );
}
function verifyRecovery(
  recovery,
  plan,
  pending,
  originRecord,
  context,
  terminalStage = "RESTORED",
  observedCount = plan.requestedInputs.length,
) {
  const { source, initial, frame, lever } = context;
  const phases = [
    frame(false, initial),
    frame(true, !initial),
    frame(false, !initial),
    frame(true, initial),
  ];
  const phase = phases.findIndex((value) =>
    isDeepStrictEqual(value, plan.current),
  );
  assert.ok(phase >= 0);
  const proposedInputs = [[], [false, true, false], [true, false], [false]][
    phase
  ];
  assert.deepEqual(plan.requestedInputs, proposedInputs);
  let proposedInput = phase === 1 || phase === 3,
    proposedBit = phase === 1 || phase === 2 ? !initial : initial;
  const proposedFrames = [];
  for (const requested of proposedInputs) {
    if (requested) proposedBit = !proposedBit;
    proposedInput = requested;
    proposedFrames.push(frame(proposedInput, proposedBit));
  }
  assert.deepEqual(plan.expectedFrames, proposedFrames);
  assert.deepEqual(frame(proposedInput, proposedBit), plan.initialTarget);
  assert.equal(plan.executionAuthority, false);
  assert.equal(plan.functionalPass, false);
  assert.equal(plan.pendingBirthDigest, pending.birthDigest);
  assert.equal(plan.originJournalId, originRecord.id);
  assert.equal(plan.originRecordDigest, hash(originRecord));
  assert.equal(plan.originStage, originRecord.stage);
  assert.deepEqual(plan.initialTarget, pending.initial);
  assert.deepEqual(plan.intention, source);
  const { birthDigest, stage, events, ...birth } = recovery;
  assert.equal(hash(birth), birthDigest);
  assert.equal(recovery.schemaVersion, 2);
  assert.equal(recovery.kind, "memory-recovery-journal");
  assert.equal(recovery.originKind, originRecord.kind);
  assert.match(
    recovery.id,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u,
  );
  assert.match(
    recovery.controllerId,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u,
  );
  assert.equal(recovery.projectId, source.projectId);
  assert.deepEqual(recovery.intention, source);
  assert.equal(recovery.rootBirthDigest, pending.birthDigest);
  assert.equal(recovery.originJournalId, originRecord.id);
  assert.equal(recovery.originRecordDigest, hash(originRecord));
  assert.deepEqual(recovery.plan, plan);
  assert.deepEqual(recovery.initial, plan.current);
  assert.deepEqual(recovery.target, plan.initialTarget);
  assert.deepEqual(recovery.requestedInputs, plan.requestedInputs);
  assert.deepEqual(recovery.expectedFrames, plan.expectedFrames);
  assert.equal(stage, terminalStage);
  assert.ok(
    Number.isSafeInteger(recovery.startedTick) &&
      recovery.startedTick >= originRecord.startedTick,
  );
  assert.ok(observedCount >= 0 && observedCount <= plan.requestedInputs.length);
  assert.equal(events.length, observedCount * 2 + 1);
  let input = phase === 1 || phase === 3,
    bit = phase === 1 || phase === 2 ? !initial : initial;
  let previousTick = recovery.startedTick;
  plan.requestedInputs.slice(0, observedCount).forEach((requested, index) => {
    const prepared = events[index * 2],
      observed = events[index * 2 + 1];
    assert.ok(
      Number.isSafeInteger(prepared.tick) && prepared.tick >= previousTick,
    );
    const nextBit = requested ? !bit : bit,
      after = frame(requested, nextBit);
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
        expectedAfter: after,
      },
    });
    assert.deepEqual(observed, {
      stage: "OBSERVED",
      step: index + 1,
      tick: prepared.tick,
      observed: after,
    });
    input = requested;
    bit = nextBit;
    previousTick = prepared.tick;
  });
  const final = events.at(-1);
  assert.ok(Number.isSafeInteger(final.tick) && final.tick >= previousTick);
  assert.deepEqual(final, {
    stage: terminalStage,
    step: observedCount,
    tick: final.tick,
    observed: frame(input, bit),
  });
  if (terminalStage === "RESTORED")
    assert.deepEqual(frame(input, bit), plan.initialTarget);
  return observedCount;
}
assert.equal(recoveryEdges, 48);
console.log(
  JSON.stringify({
    kind: "memory-recovery-journal-native-replay",
    journals: 32,
    nativeRecoveryEdges: 48,
    originalPairsRestored: 32,
    loadedExecution: false,
    functionalPass: false,
  }),
);

const chainMarker = "ENGINEER_MEMORY_RECOVERY_CHAIN ";
const chains = lines
  .filter((line) => line.includes(chainMarker))
  .map((line) =>
    JSON.parse(line.slice(line.indexOf(chainMarker) + chainMarker.length)),
  );
assert.ok(chains.length > 0, "Native recovery checkpoint chain proof must run");
for (const chain of chains) {
  const {
    pendingRoot,
    originRecord,
    firstRecovery,
    firstRecoveryCheckpoint,
    recoveryCheckpoint,
    secondPlan,
    secondRecovery,
  } = chain;
  const context = verifyPlan(
    { pending: pendingRoot, originRecord, plan: firstRecovery.plan },
    new Set(),
  );
  assert.equal(
    verifyRecovery(
      firstRecovery,
      firstRecovery.plan,
      pendingRoot,
      originRecord,
      context,
      "INTERRUPTED",
      1,
    ),
    1,
  );
  for (const [checkpoint, journal] of [
    [firstRecoveryCheckpoint, firstRecovery],
    [recoveryCheckpoint, secondRecovery],
  ]) {
    assert.deepEqual(checkpoint, {
      schemaVersion: 1,
      kind: "pending-memory-recovery",
      projectId: context.source.projectId,
      rootBirthDigest: pendingRoot.birthDigest,
      journalId: journal.id,
      journalBirthDigest: journal.birthDigest,
    });
  }
  assert.deepEqual(secondPlan.current, firstRecovery.events.at(-1).observed);
  assert.deepEqual(secondPlan.requestedInputs, [true, false]);
  assert.equal(secondPlan.originJournalId, firstRecovery.id);
  assert.equal(secondPlan.originRecordDigest, hash(firstRecovery));
  assert.notEqual(secondRecovery.id, firstRecovery.id);
  assert.equal(
    verifyRecovery(
      secondRecovery,
      secondPlan,
      pendingRoot,
      firstRecovery,
      context,
    ),
    2,
  );
}
console.log(
  JSON.stringify({
    kind: "memory-recovery-checkpoint-native-replay",
    chains: chains.length,
    latestProgressSelected: true,
    loadedExecution: false,
    functionalPass: false,
  }),
);
