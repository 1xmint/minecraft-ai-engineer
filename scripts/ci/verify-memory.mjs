import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import {
  validateGeneratedBlueprint,
  behaviorDigest,
} from "../../dist/companion/src/generated.js";

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
  const catalogEvidence = [];
  for (const template of ["or-gate", "not-gate"]) {
    const row = db
      .prepare(
        "SELECT document FROM gateway_audit WHERE server_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.verified') LIKE ? ORDER BY id DESC LIMIT 1",
      )
      .get(server.serverId, `passed:${template}:%`);
    assert.ok(row, `Actual ${template} functional evidence must reach SQLite`);
    const report = JSON.parse(row.document);
    const intended = db
      .prepare(
        "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
      )
      .get(
        server.serverId,
        report.context.projectId,
        report.context.projectRevision,
      );
    assert.equal(
      JSON.parse(intended.document).provenance.template,
      template,
      "Evidence belongs to the exact immutable mechanism revision that was tested",
    );
    const vectors = report.checkpoint.observed.testEvidence;
    assert.ok(vectors.length >= 3);
    for (const [index, vector] of vectors.entries()) {
      assert.equal(
        vector.outputs.light,
        template === "or-gate"
          ? Boolean(vector.inputs.a || vector.inputs.b)
          : !vector.inputs.enable,
      );
      if (index) assert.ok(vector.tick > vectors[index - 1].tick);
    }
    assert.equal(
      new Set(
        vectors.map((v) =>
          template === "or-gate"
            ? `${v.inputs.a}${v.inputs.b}`
            : String(v.inputs.enable),
        ),
      ).size,
      template === "or-gate" ? 4 : 2,
    );
    catalogEvidence.push(template);
  }
  assert.equal(
    readFileSync(`${coordination}/repair-recovery.done`, "utf8").trim(),
    "passed",
  );
  const repairProject = readFileSync(
    `${coordination}/repair-project`,
    "utf8",
  ).trim();
  const repairReports = db
    .prepare(
      "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' ORDER BY id",
    )
    .all(server.serverId, repairProject)
    .map((entry) => JSON.parse(entry.document));
  const testedDelays = new Set();
  for (const report of repairReports.filter((entry) =>
    entry.checkpoint.verified.startsWith("passed:signal-lamp:"),
  )) {
    const intended = db
      .prepare(
        "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
      )
      .get(server.serverId, repairProject, report.context.projectRevision);
    const design = JSON.parse(intended.document);
    assert.equal(design.provenance.template, "signal-lamp");
    testedDelays.add(design.parameters.delay);
    assert.ok(report.checkpoint.observed.testEvidence.length >= 3);
    for (const vector of report.checkpoint.observed.testEvidence)
      assert.equal(vector.outputs.light, vector.inputs.enable);
  }
  assert.deepEqual(
    [...testedDelays].sort(),
    [1, 4],
    "Both repaired delay revisions have actual immutable functional evidence",
  );
  assert.ok(
    repairReports.some(
      (report) =>
        report.context.serverEpoch === server.serverEpoch &&
        report.checkpoint.observed.assistantChanges === 5 &&
        report.checkpoint.observed.undoRequested === false,
    ),
    "Third server boot records the completed undo without erasing earlier operation groups",
  );
  assert.equal(
    readFileSync(`${coordination}/components-independent.done`, "utf8").trim(),
    "passed",
  );
  assert.equal(
    readFileSync(`${coordination}/companion-recovery.done`, "utf8").trim(),
    "passed",
  );
  const componentProject = readFileSync(
    `${coordination}/components-project`,
    "utf8",
  ).trim();
  assert.equal(
    readFileSync(`${coordination}/components-recovery.done`, "utf8").trim(),
    "passed",
  );
  const restoredComponents = db
    .prepare(
      "SELECT synced_epoch FROM gateway_projects WHERE server_id=? AND project_id=?",
    )
    .get(server.serverId, componentProject);
  assert.equal(
    restoredComponents.synced_epoch,
    server.serverEpoch,
    "Actual composed project was selected and synchronized after the third server boot",
  );
  for (const component of ["a", "b", "c"]) {
    const entry = db
      .prepare(
        "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.observed.component')=? AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:signal-lamp:%' ORDER BY id DESC LIMIT 1",
      )
      .get(server.serverId, componentProject, component);
    assert.ok(
      entry,
      `Actual composed component ${component} functional evidence must reach SQLite`,
    );
    const report = JSON.parse(entry.document);
    assert.equal(report.checkpoint.taskId, `${componentProject}:${component}`);
    assert.match(report.checkpoint.observed.componentBasis, /^[a-f0-9]{64}$/u);
    assert.ok(report.checkpoint.observed.assignmentGeneration >= 1);
    const intended = JSON.parse(
      db
        .prepare(
          "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
        )
        .get(server.serverId, componentProject, report.context.projectRevision)
        .document,
    );
    assert.equal(intended.provenance.template, "composition");
    assert.equal(
      intended.components.nodes.find((node) => node.id === component).template,
      "signal-lamp",
    );
    const vectors = report.checkpoint.observed.testEvidence;
    assert.equal(vectors.length, 3);
    assert.deepEqual(
      vectors.map((vector) => vector.inputs.enable),
      [false, true, false],
    );
    for (const vector of vectors)
      assert.equal(vector.outputs.light, vector.inputs.enable);
  }
  assert.equal(
    readFileSync(`${coordination}/component-revisions.done`, "utf8").trim(),
    "passed",
  );
  const revisionProject = readFileSync(
    `${coordination}/component-revisions-project`,
    "utf8",
  ).trim();
  for (const component of ["a", "b", "c"]) {
    const entry = db
      .prepare(
        "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.observed.component')=? AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:signal-lamp:%' ORDER BY id DESC LIMIT 1",
      )
      .get(server.serverId, revisionProject, component);
    assert.ok(
      entry,
      `Actual revision-project component ${component} must have functional evidence`,
    );
    const report = JSON.parse(entry.document);
    const observed = report.checkpoint.observed;
    const scope = observed.testScope ?? observed.lastPassedScope;
    assert.ok(scope, "A pass must retain its captured test identity");
    assert.equal(scope.projectId, revisionProject);
    assert.equal(scope.component, component);
    assert.equal(scope.basisVersion, 2);
    assert.match(scope.basis, /^[a-f0-9]{64}$/u);
    let evidence = observed.testEvidence;
    if (component === "a") {
      // A remains valid after unrelated B changes, but its vectors belong to the old intent.
      assert.ok(scope.blueprintRevision < report.context.projectRevision);
      assert.equal(observed.componentBasis, scope.basis);
      assert.equal(observed.testScope, undefined);
      assert.equal(evidence.length, 0);
      const historical = db
        .prepare(
          "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.observed.component')='a' AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:signal-lamp:%' ORDER BY id DESC",
        )
        .all(server.serverId, revisionProject)
        .map((row) => JSON.parse(row.document))
        .find((candidate) => {
          const captured = candidate.checkpoint.observed.testScope;
          return (
            captured &&
            Object.keys(scope).every((key) => captured[key] === scope[key]) &&
            candidate.context.projectRevision === scope.blueprintRevision &&
            candidate.checkpoint.observed.testEvidence.length === 3
          );
        });
      assert.ok(
        historical,
        "A's historical pass must resolve to real vectors under its original intent",
      );
      evidence = historical.checkpoint.observed.testEvidence;
    } else {
      assert.equal(scope.blueprintRevision, report.context.projectRevision);
    }
    assert.equal(evidence.length, 3);
    assert.deepEqual(
      evidence.map((vector) => vector.inputs.enable),
      [false, true, false],
    );
    const intended = JSON.parse(
      db
        .prepare(
          "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
        )
        .get(server.serverId, revisionProject, scope.blueprintRevision)
        .document,
    );
    assert.equal(scope.blueprintId, intended.id);
    const leaf = intended.components.nodes.find(
      (node) => node.id === component,
    );
    assert.equal(leaf.template, "signal-lamp");
    if (component === "b") {
      assert.equal(leaf.parameters.delay, 4);
      assert.ok(evidence.at(-1).elapsedTicks >= 56);
    }
    for (const vector of evidence)
      assert.equal(vector.outputs.light, vector.inputs.enable);
  }
  assert.equal(
    readFileSync(`${coordination}/targeted-perception.done`, "utf8").trim(),
    "passed",
  );
  const perceptionProject = readFileSync(
    `${coordination}/targeted-perception-project`,
    "utf8",
  ).trim();
  assert.equal(perceptionProject, revisionProject);
  const owner = JSON.parse(
    db
      .prepare(
        "SELECT members FROM gateway_projects WHERE server_id=? AND project_id=?",
      )
      .get(server.serverId, perceptionProject).members,
  ).find((member) => member.role === "owner").actorId;
  for (const kind of ["block", "view"]) {
    const entry = db
      .prepare(
        "SELECT document FROM gateway_observations WHERE server_id=? AND project_id=? AND json_extract(document,'$.perception.kind')=? ORDER BY id DESC LIMIT 1",
      )
      .get(server.serverId, perceptionProject, kind);
    assert.ok(
      entry,
      `Actual ${kind} perception must survive the server/companion protocol`,
    );
    const captured = JSON.parse(entry.document);
    assert.equal(captured.perception.schemaVersion, 1);
    assert.equal(captured.perception.actorId, owner);
    assert.equal(captured.blocks.length, 7);
    assert.ok(Number.isFinite(captured.perception.player.position.x));
    assert.ok(
      Math.abs(
        Math.hypot(
          ...["x", "y", "z"].map(
            (axis) => captured.perception.player.look[axis],
          ),
        ) - 1,
      ) < 0.01,
    );
    if (kind === "block") {
      assert.deepEqual(captured.perception.target, { x: 109, y: 101, z: 0 });
      assert.ok(
        captured.blocks
          .find(
            (block) =>
              block.pos.x === 109 && block.pos.y === 101 && block.pos.z === 0,
          )
          .state.includes("delay=4"),
      );
      assert.equal(captured.perception.differences.changed, 0);
    } else
      assert.ok(
        ["north", "south", "east", "west", "up", "down"].includes(
          captured.perception.face,
        ),
      );
  }
  for (const marker of [
    "inventory-native.done",
    "inventory-bob-private.done",
    "inventory-revocation.done",
    "inventory-recovery.done",
    "undo-context-failure.done",
    "undo-context-recovery.done",
    "player-client-construction.done",
    "lesson-followup.done",
    "lesson-bob.done",
    "lesson-denied.done",
    "lesson-recovery.done",
  ])
    assert.equal(
      readFileSync(`${coordination}/${marker}`, "utf8").trim(),
      "passed",
    );
  const inventoryCaptures = db
    .prepare(
      "SELECT count(*) AS count FROM gateway_requests WHERE server_id=? AND kind='inventory'",
    )
    .get(server.serverId).count;
  assert.ok(
    inventoryCaptures >= 7,
    "Actual private own/double-chest/crafter/other-actor/restart captures must reach the companion",
  );
  for (const table of db
    .prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
    )
    .all()) {
    for (const persisted of db
      .prepare(
        'SELECT * FROM "' + String(table.name).replaceAll('"', '""') + '"',
      )
      .all()) {
      const data = JSON.stringify(persisted);
      assert.ok(
        !data.includes("minecraft:diamond"),
        "The real other-actor private item ID cannot enter durable project data",
      );
      assert.ok(
        !data.includes("inventory-private-secret"),
        "Custom item names never enter companion payloads or persistence",
      );
    }
  }
  if (process.env.MCE_LIVE_ACCEPTANCE !== "true") {
    for (const name of [
      "generated-confirmation.done",
      "player-generated-section.done",
      "generated-confirmation-recovery.done",
      "generated-wrong-timing.done",
      "generated-confirmation-save-failure.done",
      "generated-bob.done",
      "generated-bob-revoked.done",
    ])
      assert.equal(
        readFileSync(`${coordination}/${name}`, "utf8").trim(),
        "passed",
      );
    const generatedId = readFileSync(
      `${coordination}/generated-project`,
      "utf8",
    ).trim();
    const generated = db
      .prepare(
        "SELECT * FROM gateway_projects WHERE server_id=? AND project_id=?",
      )
      .get(server.serverId, generatedId);
    assert.ok(
      generated,
      "Confirmed generated intent reaches durable project memory",
    );
    const source = validateGeneratedBlueprint(
      JSON.parse(
        db
          .prepare(
            "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
          )
          .get(server.serverId, generatedId, generated.project_revision)
          .document,
      ),
    );
    assert.deepEqual(source.behavior, {
      schemaVersion: 1,
      function: "buffer",
      risingGameTicks: 12,
      fallingGameTicks: 16,
    });
    const digest = behaviorDigest(source.behavior);
    const report = JSON.parse(
      db
        .prepare(
          "SELECT document FROM gateway_audit WHERE server_id=? AND project_id=? AND kind='checkpoint' AND json_extract(document,'$.checkpoint.verified') LIKE 'passed:generated-buffer:%' ORDER BY id DESC LIMIT 1",
        )
        .get(server.serverId, generatedId).document,
    );
    const rows = report.checkpoint.observed.testEvidence;
    assert.equal(rows.length, 3);
    assert.equal(
      report.checkpoint.observed.testScope.blueprintRevision,
      source.revision,
    );
    assert.deepEqual(
      rows[0].baselineSamples,
      Array.from({ length: 9 }, (_, elapsedTicks) => ({
        elapsedTicks,
        observed: false,
      })),
    );
    for (const [index, on, ticks] of [
      [1, true, 12],
      [2, false, 16],
    ]) {
      const row = rows[index],
        trace = row.trace;
      assert.equal(row.behaviorDigest, digest);
      assert.equal(trace.verdict, "passed");
      assert.equal(trace.expectedGameTicks, ticks);
      assert.equal(trace.transitionTick, ticks);
      assert.equal(trace.stableGameTicks, 8);
      assert.deepEqual(
        trace.samples,
        Array.from({ length: ticks + 9 }, (_, elapsedTicks) => ({
          elapsedTicks,
          observed: elapsedTicks < ticks ? !on : on,
        })),
      );
    }
    assert.equal(
      generated.synced_epoch,
      server.serverEpoch,
      "Generated restart synchronizes against the current server epoch",
    );
  }
  console.log(
    JSON.stringify({
      serverId: server.serverId,
      serverEpoch: server.serverEpoch,
      projectId: project,
      revision: row.project_revision,
      recordedFunctionalPasses: passes.count,
      recordedWorldInspections: observations.length,
      recordedCatalogMechanisms: catalogEvidence,
      repairedDelays: [...testedDelays].sort(),
      recoveredRepairProject: repairProject,
      testedProjectComponents: ["a", "b", "c"],
      revisedComponent: "b:delay4",
      targetedPerception: ["block", "view"],
      ephemeralPrivateInventories: inventoryCaptures,
    }),
  );
} finally {
  db.close();
}
