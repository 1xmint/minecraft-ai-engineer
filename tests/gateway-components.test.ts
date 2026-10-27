import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { DatabaseSync } from "node:sqlite";
import { test } from "node:test";
import { GatewayMemory } from "../companion/src/gateway-memory.js";
import { makeComponentBlueprint } from "../companion/src/components.js";
import type { GatewayContext } from "../companion/src/gateway-types.js";
import type { ComponentNode, Observation } from "../companion/src/types.js";

const owner = "11111111-1111-4111-8111-111111111111";
function fixture() {
  const db = new DatabaseSync(":memory:");
  db.exec("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  let memory = new GatewayMemory(db);
  let revision = 1,
    authorization = 1,
    sequence = 0;
  const context = (): GatewayContext => ({
    serverId: "server",
    serverEpoch: "epoch",
    actorId: owner,
    projectId: "project",
    projectRevision: revision,
    authorizationRevision: authorization,
    requestId: randomUUID(),
  });
  const nodes: ComponentNode[] = [
    { id: "root", kind: "group" },
    ...["a", "b", "c"].map((id, index): ComponentNode => ({
      id,
      parent: "root",
      kind: "mechanism",
      template: "signal-lamp",
      anchor: { x: index * 8, y: 0, z: 0 },
      rotation: 0,
      parameters: { delay: 1 },
      dependsOn: id === "c" ? ["b"] : [],
    })),
  ];
  const sync = () => {
    const blueprint = makeComponentBlueprint(
      {
        projectId: "project",
        worldId: "world",
        dimension: "minecraft:overworld",
        request: "Three bounded components",
        anchor: { x: 0, y: 100, z: 0 },
        rotation: 0,
      },
      nodes,
    );
    blueprint.revision = revision;
    memory.sync({
      context: context(),
      worldId: "world",
      dimension: blueprint.dimension,
      name: "Components",
      members: [{ actorId: owner, role: "owner", canPlan: true }],
      blueprint,
      isOperator: false,
    });
  };
  const pass = (component: string, taskId = `project:${component}`) =>
    memory.checkpoint(
      context(),
      {
        worldId: "world",
        dimension: "minecraft:overworld",
        projectId: "project",
        blueprintRevision: revision,
        taskId,
        status: "COMPLETED",
        detail: `Actual ${component} test`,
        observed: { component },
        verified: `passed:${component}:3 vectors`,
      },
      ++sequence,
    );
  const observe = (x: number, state: string) => {
    const pos = { x, y: 100, z: 0 };
    const observation: Observation = {
      worldId: "world",
      dimension: "minecraft:overworld",
      region: { min: pos, max: pos },
      blocks: [{ pos, state }],
      tick: ++sequence,
    };
    return memory.observe(context(), observation, sequence);
  };
  const rows = () =>
    Object.fromEntries(
      (
        db
          .prepare(
            "SELECT task_id,status,verified,project_revision,observed FROM gateway_tasks ORDER BY task_id",
          )
          .all() as {
          task_id: string;
          status: string;
          verified: string | null;
          project_revision: number;
          observed: string | null;
        }[]
      ).map((row) => [row.task_id, row]),
    );
  memory.open("server", "epoch", 1);
  sync();
  return {
    db,
    nodes,
    sync,
    pass,
    observe,
    rows,
    revise: () => {
      revision++;
      sync();
    },
    revoke: () => {
      authorization++;
      sync();
    },
    reopen: () => {
      memory = new GatewayMemory(db);
      memory.open("server", "epoch", 1);
      sync();
    },
  };
}

test("a canonical leaf revision preserves unrelated historical results and invalidates transitive dependants", () => {
  const f = fixture();
  try {
    for (const id of ["a", "b", "c"]) f.pass(id);
    const original = f.rows()["project:a"]!;
    const b = f.nodes.find((node) => node.id === "b")!;
    assert.equal(b.kind, "mechanism");
    if (b.kind === "mechanism") b.parameters.delay = 4;
    f.revise();
    const rows = f.rows();
    assert.deepEqual(
      rows["project:a"],
      original,
      "A's original report must not be relabelled under the new whole-project revision",
    );
    for (const id of ["b", "c"]) {
      assert.equal(rows[`project:${id}`]!.status, "PAUSED");
      assert.equal(rows[`project:${id}`]!.verified, null);
    }
    f.pass("b");
    f.pass("c");
    f.revoke();
    for (const row of Object.values(f.rows())) {
      assert.equal(row.status, "PAUSED");
      assert.equal(
        row.verified,
        null,
        "ACL changes still invalidate every component",
      );
    }
  } finally {
    f.db.close();
  }
});

test("partial world observations retain coordinate history and stale only affected read closures", () => {
  const f = fixture();
  try {
    f.observe(0, "minecraft:stone");
    f.observe(8, "minecraft:stone");
    for (const id of ["a", "b", "c"]) f.pass(id);
    assert.equal(f.observe(0, "minecraft:stone").changed, 0);
    assert.equal(
      f.observe(8, "minecraft:air").changed,
      1,
      "A partial observation of A cannot erase the previous B observation",
    );
    let rows = f.rows();
    assert.equal(rows["project:a"]!.status, "COMPLETED");
    assert.equal(rows["project:b"]!.verified, null);
    assert.equal(rows["project:c"]!.verified, null, "C reads prerequisite B");
    f.pass("b");
    f.pass("c");
    f.observe(6, "minecraft:air");
    assert.equal(f.observe(6, "minecraft:stone").changed, 1);
    rows = f.rows();
    assert.equal(rows["project:a"]!.status, "COMPLETED");
    assert.equal(
      rows["project:b"]!.verified,
      null,
      "B's two-block isolation margin is a read dependency",
    );
    assert.equal(rows["project:c"]!.verified, null);
    f.reopen();
    for (const row of Object.values(f.rows())) {
      assert.equal(row.status, "PAUSED");
      assert.equal(
        row.verified,
        null,
        "A service restart never imports authority or a current-world pass",
      );
    }
  } finally {
    f.db.close();
  }
});

test("legacy unscoped task rows fail closed when intent or observed geometry changes", () => {
  const f = fixture();
  try {
    f.pass("a", "legacy-task");
    f.revise();
    assert.equal(f.rows()["legacy-task"]!.verified, null);
    f.observe(30, "minecraft:air");
    f.pass("a", "legacy-task");
    f.observe(30, "minecraft:stone");
    assert.equal(f.rows()["legacy-task"]!.verified, null);
  } finally {
    f.db.close();
  }
});
