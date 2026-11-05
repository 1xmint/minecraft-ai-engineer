import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { readFile, readdir } from "node:fs/promises";
import { join } from "node:path";

const directory = process.argv[2];
const nbt = createRequire(import.meta.url)("prismarine-nbt");
const files = (await readdir(directory)).filter(
  (name) => name.includes(".craft-") && name.endsWith(".json"),
);
assert.equal(files.length, 1);
const record = JSON.parse(await readFile(join(directory, files[0]), "utf8"));
assert.equal(record.kind, "native-oak-planks-v1");
assert.equal(record.stage, "COMPLETE");
assert.equal(record.step, 4);
assert.equal(record.steps.length, 4);
assert.ok(
  Number.isInteger(record.sourceHotbar) &&
    record.sourceHotbar >= 0 &&
    record.sourceHotbar < 9,
);
const log = { id: "minecraft:oak_log", count: 1 };
const planks = { id: "minecraft:oak_planks", count: 4 };
const frame = (inventoryStack, gridStack, cursor) => {
  const inventory = Array.from({ length: 43 }, () => ({}));
  inventory[record.sourceHotbar] = inventoryStack;
  return {
    inventory,
    menu: { schemaVersion: 1, inputs: [gridStack, {}, {}, {}], cursor },
    selected: record.initial.selected,
  };
};
const expected = [
  frame(log, {}, {}),
  frame({}, {}, log),
  frame({}, log, {}),
  frame({}, {}, planks),
  frame(planks, {}, {}),
];
assert.deepEqual(record.initial, expected[0]);
const events = ["NEW"];
for (let step = 1; step <= 4; step++) {
  const row = record.steps[step - 1];
  assert.deepEqual(row, {
    step,
    status: "OBSERVED",
    before: expected[step - 1],
    expectedAfter: expected[step],
    observedAfter: expected[step],
  });
  events.push(`PREPARED:${step}`, `OBSERVED:${step}`);
}
events.push("COMPLETE");
assert.deepEqual(record.events, events);
const players = [];
for (const phase of ["crafted", "crafted-restarted"]) {
  const bytes = await readFile(join(directory, `worker-menu-${phase}.dat`));
  assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
  const player = nbt.simplify((await nbt.parse(bytes)).parsed);
  assert.deepEqual(player.Inventory, [
    { Slot: record.sourceHotbar, ...planks },
  ]);
  assert.deepEqual(player.engineer_worker_menu, expected[4].menu);
  for (const stack of Object.values(player.equipment ?? {}))
    assert.deepEqual(stack, {});
  players.push(player);
}
assert.deepEqual(players[0].UUID, players[1].UUID);
const uuid = Buffer.alloc(16);
Array.from(players[0].UUID).forEach((part, index) =>
  uuid.writeInt32BE(part, index * 4),
);
assert.equal(uuid.toString("hex"), record.worker.replaceAll("-", ""));
console.log(
  JSON.stringify({
    kind: "native-oak-crafting",
    clicks: 4,
    consumedLogs: 1,
    retainedPlanks: 4,
    savedWorldRestart: true,
    resultStored: false,
  }),
);

// Independently hand-work each interrupted native return, including the
// already-settled fourth click. No production snapshot/recipe code is imported.
for (let stopped = 1; stopped <= 4; stopped++) {
  const location = join(directory, `craft-interrupted-${stopped}`);
  const names = (await readdir(location)).filter(
    (name) => name.includes(".craft-") && name.endsWith(".json"),
  );
  assert.equal(names.length, 1);
  const history = JSON.parse(await readFile(join(location, names[0]), "utf8"));
  assert.equal(history.kind, "native-oak-planks-v1");
  assert.equal(history.stage, "UNCERTAIN");
  assert.equal(history.step, stopped);
  assert.equal(history.steps.length, stopped);
  assert.equal(history.sourceHotbar, record.sourceHotbar);
  assert.equal(history.initial.selected, record.initial.selected);
  assert.deepEqual(history.initial, expected[0]);
  assert.deepEqual(history.outcome, expected[stopped]);
  const sequence = ["NEW"];
  for (let step = 1; step <= stopped; step++) {
    assert.deepEqual(history.steps[step - 1], {
      step,
      status: "OBSERVED",
      before: expected[step - 1],
      expectedAfter: expected[step],
      observedAfter: expected[step],
    });
    sequence.push(`PREPARED:${step}`, `OBSERVED:${step}`);
  }
  sequence.push("UNCERTAIN");
  assert.deepEqual(history.events, sequence);
  for (const suffix of ["", "-restarted"]) {
    const bytes = await readFile(
      join(directory, `worker-menu-craft-interrupted-${stopped}${suffix}.dat`),
    );
    assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
    const player = nbt.simplify((await nbt.parse(bytes)).parsed);
    assert.deepEqual(
      player.Inventory,
      stopped === 4 ? [{ Slot: history.sourceHotbar, ...planks }] : [],
    );
    assert.deepEqual(player.engineer_worker_menu, expected[stopped].menu);
    for (const stack of Object.values(player.equipment ?? {}))
      assert.deepEqual(stack, {});
    const identity = Buffer.alloc(16);
    Array.from(player.UUID).forEach((part, index) =>
      identity.writeInt32BE(part, index * 4),
    );
    assert.equal(identity.toString("hex"), history.worker.replaceAll("-", ""));
  }
}
console.log(
  JSON.stringify({
    kind: "native-crafting-interruptions",
    stoppedAfterClicks: [1, 2, 3, 4],
    savedWorldRestart: true,
    automaticRetry: false,
    compensation: false,
  }),
);

const conversationGather = JSON.parse(
  await readFile(
    join(directory, "oak-conversation-gather-records", "oak.json"),
    "utf8",
  ),
);
for (const suffix of ["", "-restarted"]) {
  const bytes = await readFile(
    join(directory, `worker-menu-craft-conversation${suffix}.dat`),
  );
  assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
  const player = nbt.simplify((await nbt.parse(bytes)).parsed);
  assert.deepEqual(player.Inventory, [{ Slot: 0, ...planks }]);
  assert.deepEqual(player.engineer_worker_menu, expected[4].menu);
  for (const stack of Object.values(player.equipment ?? {}))
    assert.deepEqual(stack, {});
  const identity = Buffer.alloc(16);
  Array.from(player.UUID).forEach((part, index) =>
    identity.writeInt32BE(part, index * 4),
  );
  assert.equal(
    identity.toString("hex"),
    conversationGather.worker.replaceAll("-", ""),
  );
}
console.log(
  JSON.stringify({
    kind: "conversational-native-crafting",
    retainedPlanks: 4,
    savedWorldRestart: true,
  }),
);

for (let stopped = 1; stopped <= 4; stopped++) {
  const originalDirectory = join(directory, `craft-interrupted-${stopped}`);
  const originalNames = (await readdir(originalDirectory)).filter(
    (name) => name.includes(".craft-") && name.endsWith(".json"),
  );
  assert.equal(originalNames.length, 1);
  const originalBytes = await readFile(
    join(originalDirectory, originalNames[0]),
  );
  const original = JSON.parse(originalBytes.toString("utf8"));
  const recoveryDirectory = join(directory, `craft-recovered-${stopped}`);
  const names = (await readdir(recoveryDirectory)).filter(
    (name) => name.includes(".resource-recovery-") && name.endsWith(".json"),
  );
  assert.equal(names.length, 1);
  const recovery = JSON.parse(
    await readFile(join(recoveryDirectory, names[0]), "utf8"),
  );
  assert.equal(recovery.kind, "native-oak-resource-recovery-v1");
  assert.equal(recovery.stage, "COMPLETE");
  for (const field of [
    "world",
    "dimension",
    "project",
    "owner",
    "worker",
    "sourceHotbar",
  ])
    assert.equal(recovery[field], original[field]);
  assert.equal(recovery.craftOperation, original.operation);
  assert.equal(
    recovery.craftSha256,
    createHash("sha256").update(originalBytes).digest("hex"),
  );
  const finalFrame = expected[stopped < 3 ? 0 : 4];
  assert.deepEqual(recovery.initial, expected[stopped]);
  assert.deepEqual(recovery.expectedFinal, finalFrame);
  assert.deepEqual(recovery.savedFinal, finalFrame);
  const movement =
    stopped === 1
      ? [[36 + record.sourceHotbar, expected[1], expected[0]]]
      : stopped === 2
        ? [
            [1, expected[2], expected[1]],
            [36 + record.sourceHotbar, expected[1], expected[0]],
          ]
        : stopped === 3
          ? [[36 + record.sourceHotbar, expected[3], expected[4]]]
          : [];
  assert.equal(recovery.clicks, movement.length);
  assert.equal(recovery.step, movement.length);
  assert.equal(recovery.steps.length, movement.length);
  const events = ["NEW"];
  movement.forEach(([slot, before, after], index) => {
    const step = index + 1;
    assert.notEqual(slot, 0);
    assert.deepEqual(recovery.steps[index], {
      step,
      slot,
      status: "OBSERVED",
      before,
      expectedAfter: after,
      observedAfter: after,
    });
    events.push(`PREPARED:${step}`, `OBSERVED:${step}`);
  });
  events.push("COMPLETE");
  assert.deepEqual(recovery.events, events);
  for (const suffix of ["", "-restarted"]) {
    const bytes = await readFile(
      join(directory, `worker-menu-craft-recovered-${stopped}${suffix}.dat`),
    );
    assert.ok(bytes.length > 0 && bytes.length < 1024 * 1024);
    const player = nbt.simplify((await nbt.parse(bytes)).parsed);
    assert.deepEqual(player.Inventory, [
      { Slot: record.sourceHotbar, ...(stopped < 3 ? log : planks) },
    ]);
    assert.deepEqual(player.engineer_worker_menu, finalFrame.menu);
    for (const stack of Object.values(player.equipment ?? {}))
      assert.deepEqual(stack, {});
    const identity = Buffer.alloc(16);
    Array.from(player.UUID).forEach((part, index) =>
      identity.writeInt32BE(part, index * 4),
    );
    assert.equal(identity.toString("hex"), original.worker.replaceAll("-", ""));
  }
}
console.log(
  JSON.stringify({
    kind: "native-crafting-resource-recovery",
    cases: 4,
    originalHistoryPreserved: true,
    resultClicks: 0,
    savedWorldRestart: true,
  }),
);
