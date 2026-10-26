import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";

if (process.env.CI !== "true")
  throw new Error("This verifier only reads the disposable CI database");
const [, , database, coordination] = process.argv;
const server = JSON.parse(readFileSync(`${coordination}/server.json`, "utf8"));
const project = readFileSync(`${coordination}/project`, "utf8").trim();
const db = new DatabaseSync(database, { readOnly: true });
try {
  const row = db
    .prepare(
      "SELECT * FROM gateway_projects WHERE server_id=? AND project_id=?",
    )
    .get(server.serverId, project);
  assert.ok(
    row,
    "Actual Minecraft project must exist in the companion database",
  );
  assert.equal(row.world_id, server.worldId);
  assert.equal(row.synced_epoch, server.serverEpoch);
  const members = JSON.parse(row.members);
  assert.equal(members.filter((member) => member.role === "owner").length, 1);
  assert.ok(members.some((member) => member.role === "editor"));
  assert.ok(members.some((member) => member.role === "viewer"));
  const blueprint = db
    .prepare(
      "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
    )
    .get(server.serverId, project, row.project_revision);
  assert.equal(JSON.parse(blueprint.document).blocks.length, 6);
  const passes = db
    .prepare(
      "SELECT COUNT(*) AS count FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:%'",
    )
    .get(server.serverId, project);
  assert.ok(
    passes.count >= 2,
    "Both actual functional passes must be recorded durably across the dedicated-server restart",
  );
  const observations = db
    .prepare(
      "SELECT document FROM gateway_observations WHERE server_id=? AND project_id=? ORDER BY id",
    )
    .all(server.serverId, project)
    .map((row) => JSON.parse(row.document));
  assert.ok(
    observations.length >= 2,
    "Actual before-build and after-restart inspections must be durable",
  );
  assert.ok(
    observations[0].blocks.every((block) => block.state === "minecraft:air"),
  );
  assert.ok(
    observations
      .at(-1)
      .blocks.some((block) => block.state.startsWith("minecraft:repeater")),
    "Restart inspection must contain the real built repeater",
  );
  console.log(
    JSON.stringify({
      serverId: server.serverId,
      serverEpoch: server.serverEpoch,
      projectId: project,
      revision: row.project_revision,
      recordedFunctionalPasses: passes.count,
      recordedWorldInspections: observations.length,
    }),
  );
} finally {
  db.close();
}
