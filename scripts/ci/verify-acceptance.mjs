// Reads the acceptance run's persisted companion data and coordination
// receipts. Read-only; uses no build output and no network.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";

if (process.env.CI !== "true") {
  console.error("Run this acceptance verifier on GitHub CI.");
  process.exit(64);
}
const [databasePath, coord] = process.argv.slice(2);
assert.ok(
  databasePath && coord,
  "usage: verify-acceptance.mjs <projects.sqlite> <coordination>",
);
const read = (name) => readFileSync(join(coord, name), "utf8").trim();
const json = (name) => JSON.parse(read(name));

const receipts = [
  "project",
  "alice-uuid",
  "bob-uuid",
  "world",
  "server.json",
  "snapshot-before.json",
  "snapshot-after.json",
  "snapshot-restart.json",
  "bob-reads.done",
  "pause-a.done",
  "resume-a.done",
  "late-a-resumed",
  "bob-late.done",
  "pause-b.done",
  "resume-b.done",
  "late-b.done",
  "eve-denied.done",
  "companion-restart.done",
  "gen2-ready",
  "bob-gen2.done",
  "alice.done",
  "bob.done",
  "eve.done",
];
for (const receipt of receipts)
  assert.ok(existsSync(join(coord, receipt)), `missing receipt ${receipt}`);

const project = read("project");
const alice = read("alice-uuid");
const bob = read("bob-uuid");
const server = json("server.json");
assert.equal(
  server.generation,
  2,
  "The last published server is the reopened saved world",
);
assert.equal(server.worldId, read("world"), "The same saved world reopened");
const restart = json("companion-restart.done");
assert.notEqual(
  restart.oldPid,
  restart.newPid,
  "A distinct companion process served the reopened world",
);

const db = new DatabaseSync(databasePath, { readOnly: true });
const notes = db
  .prepare(
    "SELECT document FROM gateway_project_notes WHERE server_id=? AND project_id=? ORDER BY note_revision",
  )
  .all(server.serverId, project)
  .map((row) => JSON.parse(row.document));
assert.deepEqual(
  notes.map((note) => [note.revision, note.kind, note.text]),
  [
    [1, "goal", "Keep the entrance within six blocks."],
    [2, "decision", "Use a visible lamp for the output."],
  ],
  "Only the owner's two notes exist; the editor's attempted note and every read created nothing",
);
for (const note of notes) {
  assert.equal(
    note.authorId,
    alice,
    "Notes stay attributed to Alice across the restart",
  );
  assert.equal(note.source, "owner-authored");
}

const row = db
  .prepare("SELECT * FROM gateway_projects WHERE server_id=? AND project_id=?")
  .get(server.serverId, project);
assert.ok(row, "The project is mirrored in the companion database");
assert.equal(row.world_id, server.worldId);
assert.equal(
  row.synced_epoch,
  server.serverEpoch,
  "The mirror was resynchronized by the reopened server",
);
const members = JSON.parse(row.members);
assert.deepEqual(
  members
    .filter((member) => member.role === "owner")
    .map((member) => member.actorId),
  [alice],
  "Ownership never moved",
);
assert.ok(
  members.every((member) => member.actorId !== bob),
  "Bob's revocation persisted",
);

const auditKinds = db
  .prepare(
    "SELECT kind, COUNT(*) AS count FROM gateway_audit WHERE server_id=? AND project_id=? GROUP BY kind ORDER BY kind",
  )
  .all(server.serverId, project)
  .map((entry) => ({ kind: entry.kind, count: Number(entry.count) }));
assert.ok(auditKinds.length > 0, "The project has an audit trail");
const planningStarts =
  auditKinds.find((entry) => entry.kind === "planning-start")?.count ?? 0;
assert.equal(planningStarts, 0, "No read-only question started planning work");

const before = json("snapshot-before.json");
const after = json("snapshot-after.json");
const reopened = json("snapshot-restart.json");
assert.deepEqual(
  after,
  before,
  "The editor reads, both late replies and the denied player changed no blocks, inventory, ownership or verification",
);
for (const key of [
  "blocksSha256",
  "nonAirBlocks",
  "owner",
  "verified",
  "journal",
  "designRevision",
]) {
  assert.deepEqual(
    reopened[key],
    before[key],
    `Saved-world restart kept ${key}`,
  );
}
assert.equal(
  reopened.inventories.Alice,
  before.inventories.Alice,
  "Alice's inventory survived the restart unchanged",
);
assert.ok(
  before.nonAirBlocks > 0,
  "The snapshot covers real placed fixture blocks",
);
const emptyHash =
  "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
for (const name of ["Alice", "Bob"])
  assert.notEqual(
    before.inventories[name],
    emptyHash,
    `${name} snapshot covers a real, non-empty inventory`,
  );

console.log(
  JSON.stringify(
    {
      project,
      serverId: server.serverId,
      worldId: server.worldId,
      notes: notes.map((note) => ({
        revision: note.revision,
        kind: note.kind,
        authorId: note.authorId,
        source: note.source,
      })),
      members: members.map((member) => ({
        actorId: member.actorId,
        role: member.role,
      })),
      auditKinds,
      companionRestart: restart,
      snapshotBefore: before,
      snapshotAfter: after,
      snapshotRestart: reopened,
    },
    null,
    2,
  ),
);
