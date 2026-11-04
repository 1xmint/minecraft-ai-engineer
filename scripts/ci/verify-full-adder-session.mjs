import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import definition from "../../catalog/full-adder-definition.json" with { type: "json" };
import { readFullAdderIntention } from "../../dist/companion/src/full-adder-intention.js";

const [database, coordination] = process.argv.slice(2);
if (!database || !coordination)
  throw new Error(
    "Provide the actual closed SQLite database and coordination folder.",
  );
const project = readFileSync(
  `${coordination}/full-adder-project`,
  "utf8",
).trim();
const server = JSON.parse(readFileSync(`${coordination}/server.json`, "utf8"));
assert.equal(server.generation, 2);
const db = new DatabaseSync(database, { readOnly: true });
const intended = db
  .prepare(
    "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? ORDER BY revision DESC LIMIT 1",
  )
  .get(server.serverId, project);
assert.ok(intended);
const source = readFullAdderIntention(JSON.parse(intended.document), {
  worldId: server.worldId,
  dimension: "minecraft:overworld",
  projectId: project,
});
assert.equal(source.verification, "untested");
assert.equal(source.blocks.length, 3544);
function keys(value, expected) {
  assert.deepEqual(Object.keys(value).sort(), [...expected].sort());
}
function expected(vector) {
  const values = {
    "a:control": !!(vector & 4),
    "a:out": !!(vector & 4),
    "b:control": !!(vector & 2),
    "b:out": !!(vector & 2),
    "cin:control": !!(vector & 1),
    "cin:out": !!(vector & 1),
  };
  for (const node of definition.graph.nodes)
    if (node.kind === "nand") {
      const [a, b] = node.inputs.map((pin) => {
        assert.equal(typeof values[pin], "boolean");
        return values[pin];
      });
      values[`${node.id}:in0`] = a;
      values[`${node.id}:in1`] = b;
      values[`${node.id}:out`] = !(a && b);
    }
  const count =
    Number(!!(vector & 4)) + Number(!!(vector & 2)) + Number(!!(vector & 1));
  values["sum:result"] = !!(count & 1);
  values["carry:result"] = count >= 2;
  assert.equal(values["sum:out"], values["sum:result"]);
  assert.equal(values["carry:out"], values["carry:result"]);
  return values;
}
function cases(rows) {
  assert.equal(rows.length, 16);
  let previous = -1;
  for (let index = 0; index < 16; index++) {
    const row = rows[index],
      vector = index < 8 ? index : 15 - index;
    keys(row, [
      "schemaVersion",
      "function",
      "graphDigest",
      "vector",
      "startGameTick",
      "settleGameTicks",
      "stableSamples",
      "samples",
      "verdict",
      "firstMismatch",
    ]);
    assert.equal(row.schemaVersion, 2);
    assert.equal(row.function, "full-adder");
    assert.equal(row.graphDigest, definition.graphDigest);
    assert.equal(row.vector, vector);
    assert.equal(row.settleGameTicks, 256);
    assert.equal(row.stableSamples, 8);
    assert.equal(row.verdict, "passed");
    assert.equal(row.firstMismatch, null);
    assert.ok(
      Number.isSafeInteger(row.startGameTick) &&
        row.startGameTick > previous &&
        row.startGameTick <= Number.MAX_SAFE_INTEGER - 263,
    );
    assert.equal(row.samples.length, 8);
    const wanted = expected(vector);
    for (let offset = 0; offset < 8; offset++) {
      const frame = row.samples[offset];
      keys(frame, ["gameTick", "elapsedTicks", "ports"]);
      assert.equal(frame.gameTick, row.startGameTick + 256 + offset);
      assert.equal(frame.elapsedTicks, 256 + offset);
      keys(
        frame.ports,
        definition.graph.ports.map((pin) => pin.id),
      );
      for (const pin of definition.graph.ports) {
        const actual = frame.ports[pin.id];
        if (pin.physical === "wire") {
          assert.ok(Number.isInteger(actual) && actual >= 0 && actual <= 15);
          assert.equal(actual > 0, wanted[pin.id]);
        } else {
          assert.equal(typeof actual, "boolean");
          assert.equal(actual, wanted[pin.id]);
        }
      }
    }
    previous = row.startGameTick + 263;
  }
}
const passes = db
  .prepare(
    "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:full-adder:16 cases@%' ORDER BY id",
  )
  .all(server.serverId, project);
const epochs = new Set();
let firstEnd = -1,
  latestStart = -1;
for (const raw of passes) {
  const entry = JSON.parse(raw.document),
    checkpoint = entry.checkpoint,
    observed = checkpoint.observed;
  assert.equal(checkpoint.status, "COMPLETED");
  assert.equal(entry.context.projectId, project);
  assert.equal(entry.context.projectRevision, source.revision);
  assert.deepEqual(observed.testInputs, []);
  assert.equal(observed.testScope.projectId, project);
  assert.equal(observed.testScope.blueprintId, source.id);
  assert.equal(observed.testScope.blueprintRevision, source.revision);
  assert.equal(observed.testScope.component, "root");
  assert.equal(observed.testScope.basisVersion, 2);
  assert.equal(observed.testScope.basis, observed.componentBasis);
  assert.deepEqual(observed.lastPassedScope, observed.testScope);
  assert.equal(observed.assistantChanges, 3544);
  cases(observed.testEvidence);
  epochs.add(entry.context.serverEpoch);
  const begin = observed.testEvidence[0].startGameTick,
    end = observed.testEvidence[15].startGameTick + 263;
  if (firstEnd < 0) firstEnd = end;
  if (entry.context.serverEpoch === server.serverEpoch) latestStart = begin;
}
assert.equal(
  epochs.size,
  2,
  "Actual observations must pass before and after the same saved-world restart",
);
assert.ok(
  latestStart > firstEnd,
  "The new epoch must record fresh later clocks, not replay the first suite",
);
const task = db
  .prepare(
    "SELECT observed,verified FROM gateway_tasks WHERE server_id=? AND project_id=? ORDER BY rowid DESC LIMIT 1",
  )
  .get(server.serverId, project);
assert.ok(task);
const final = JSON.parse(task.observed);
assert.equal(final.assistantChanges, 0);
assert.deepEqual(final.testInputs, []);
assert.ok(!JSON.parse(task.verified).startsWith("passed:"));
const actions = db
  .prepare(
    "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='interpretation-reply' ORDER BY id",
  )
  .all(server.serverId, project)
  .map((raw) => JSON.parse(raw.document).reply.action);
assert.deepEqual(actions, [
  "build",
  "resume",
  "resume",
  "test",
  "test",
  "test",
  "test",
  "undo",
  "undo",
]);
db.close();
console.log(
  JSON.stringify({
    schemaVersion: 1,
    kind: "full-adder-saved-session-proof",
    project,
    graphDigest: definition.graphDigest,
    cells: 3544,
    pins: 35,
    passedEpochs: epochs.size,
    freshClockAfterRestart: true,
    finalAssistantChanges: 0,
    conversationActions: actions,
  }),
);
