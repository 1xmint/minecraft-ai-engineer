import assert from "node:assert/strict";
import { test } from "node:test";
import { randomUUID } from "node:crypto";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { AddressInfo } from "node:net";
import { DatabaseSync } from "node:sqlite";
import { MemoryStore } from "../companion/src/memory.js";
import {
  createCompanionServer,
  type ServerOptions,
} from "../companion/src/server.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import type { Blueprint, PlanningProvider } from "../companion/src/types.js";
import type {
  GatewayContext,
  ProjectMember,
} from "../companion/src/gateway-types.js";
import { PlanningQueue } from "../companion/src/planning-queue.js";

const OWNER = "11111111-1111-4111-8111-111111111111",
  EDITOR = "22222222-2222-4222-8222-222222222222",
  EVE = "33333333-3333-4333-8333-333333333333";
const TOKEN = "gateway-only-test-token-0123456789abcdef";
const members: ProjectMember[] = [
  { actorId: OWNER, role: "owner", canPlan: true },
  { actorId: EDITOR, role: "editor", canPlan: false },
];
const design = {
  request: "Build a delayed lamp",
  projectId: "project-a",
  worldId: "world-a",
  dimension: "minecraft:overworld",
  anchor: { x: 0, y: 100, z: 0 },
  rotation: 0 as const,
};
const blueprint = makeReferenceBlueprint(design);
const context = (changes: Partial<GatewayContext> = {}): GatewayContext => ({
  serverId: "server-a",
  serverEpoch: "epoch-one",
  actorId: OWNER,
  projectId: "project-a",
  projectRevision: 1,
  authorizationRevision: 1,
  requestId: randomUUID(),
  ...changes,
});
const plan: PlanningProvider = {
  async plan() {
    return {
      template: "signal-lamp",
      explanation:
        "A lever controls a repeater and lamp. Functional verification is separate.",
    };
  },
};

async function fixture(
  provider = plan,
  db = ":memory:",
  limits: Partial<
    Pick<ServerOptions, "actorPlansPerMinute" | "serverPlansPerHour">
  > = {},
) {
  const store = new MemoryStore(db);
  const server = createCompanionServer({
    token: TOKEN,
    store,
    provider,
    ...limits,
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const post = (route: string, value: unknown) =>
    fetch(`${base}${route}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(value),
    });
  const get = (route: string) =>
    fetch(`${base}${route}`, { headers: { Authorization: `Bearer ${TOKEN}` } });
  const open = (
    epoch = "epoch-one",
    serverId = "server-a",
    serverGeneration = epoch === "epoch-two" ? 2 : 1,
  ) =>
    post("/v2/server/open", { serverId, serverEpoch: epoch, serverGeneration });
  const sync = (c = context(), changes: Record<string, unknown> = {}) =>
    post("/v2/project", {
      context: c,
      worldId: design.worldId,
      dimension: design.dimension,
      name: "Private lamp",
      members,
      blueprint,
      ...changes,
    });
  const close = async () => {
    const done = new Promise<void>((resolve) => server.close(() => resolve()));
    server.closeAllConnections();
    await done;
    store.close();
  };
  return { store, server, base, post, get, open, sync, close };
}
async function json<T>(response: Response): Promise<T> {
  return (await response.json()) as T;
}

test("durable boot generations fence delayed unseen boots without retiring the live server", async () => {
  const directory = mkdtempSync(join(tmpdir(), "mce-boot-generation-"));
  const file = join(directory, "memory.sqlite");
  let f = await fixture(plan, file);
  try {
    const ready = await f.open("epoch-two", "server-a", 2);
    assert.equal(ready.status, 200);
    assert.equal(
      (await json<{ serverGeneration: number }>(ready)).serverGeneration,
      2,
    );
    await f.sync(context({ serverEpoch: "epoch-two" }));
    const read = () =>
      f.post("/v2/project/read", {
        context: context({ serverEpoch: "epoch-two" }),
      });
    assert.equal(
      (await f.open("unseen-delayed-first-boot", "server-a", 1)).status,
      409,
    );
    assert.equal(
      (await f.open("different-epoch-same-generation", "server-a", 2)).status,
      409,
    );
    assert.equal((await f.open("epoch-two", "server-a", 3)).status, 409);
    assert.equal(
      (await read()).status,
      200,
      "rejected registration must not retire or invalidate current projects",
    );
    for (const serverGeneration of [
      undefined,
      0,
      -1,
      1.5,
      Number.MAX_SAFE_INTEGER + 1,
    ]) {
      assert.equal(
        (
          await f.post("/v2/server/open", {
            serverId: "server-a",
            serverEpoch: "invalid-boot",
            serverGeneration,
          })
        ).status,
        400,
      );
    }
    await f.close();
    f = await fixture(plan, file);
    assert.equal(
      (await f.open("unseen-delayed-first-boot", "server-a", 1)).status,
      409,
      "service restart preserves the generation fence",
    );
    assert.equal(
      (await f.open("epoch-two", "server-a", 2)).status,
      200,
      "the actual current boot can reopen after companion restart",
    );
    assert.equal(
      (await read()).status,
      409,
      "a service restart still requires a fresh project mirror",
    );
    assert.equal(
      (await f.sync(context({ serverEpoch: "epoch-two" }))).status,
      200,
    );
    assert.equal((await read()).status, 200);
    assert.equal((await f.open("epoch-three", "server-a", 3)).status, 200);
    assert.equal((await f.open("epoch-two", "server-a", 2)).status, 409);
    assert.equal(
      (await f.sync(context({ serverEpoch: "epoch-three" }))).status,
      200,
    );
  } finally {
    await f.close();
    rmSync(directory, { recursive: true, force: true });
  }
});

test("gateway registration, membership and identity reject access before inference", async () => {
  let calls = 0;
  const f = await fixture({
    async plan() {
      calls++;
      return plan.plan(design, new AbortController().signal);
    },
  });
  try {
    assert.equal(
      (
        await fetch(`${f.base}/v2/server/open`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{}",
        })
      ).status,
      401,
    );
    assert.equal(
      (await f.sync()).status,
      409,
      "service restart begins with no live registration",
    );
    assert.equal((await f.open()).status, 200);
    assert.equal((await f.sync()).status, 200);
    assert.equal(
      (
        await f.post("/v2/project/read", {
          context: context({ actorId: EDITOR }),
        })
      ).status,
      200,
    );
    assert.equal(
      (await f.post("/v2/project/read", { context: context({ actorId: EVE }) }))
        .status,
      403,
    );
    const listed = await json<{ projects: unknown[] }>(
      await f.get(
        `/v2/projects?serverId=server-a&serverEpoch=epoch-one&actorId=${EVE}&worldId=world-a`,
      ),
    );
    assert.deepEqual(listed.projects, []);
    for (const c of [context({ actorId: EVE }), context({ actorId: EDITOR })])
      assert.equal(
        (
          await f.post("/v2/design", {
            context: c,
            ...design,
            modelAuthorized: true,
            isOperator: true,
          })
        ).status,
        403,
      );
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          modelAuthorized: true,
        })
      ).status,
      403,
      "technical membership is not operator permission",
    );
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          modelAuthorized: "true",
          isOperator: true,
        })
      ).status,
      400,
    );
    assert.equal(
      (
        await f.post("/v2/project/read", {
          context: { ...context(), actorId: "forged-name" },
        })
      ).status,
      400,
    );
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          projectId: "another-project",
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      409,
    );
    assert.equal(calls, 0);
  } finally {
    await f.close();
  }
});

test("model proposals cannot advance authority until Fabric accepts a new revision", async () => {
  const f = await fixture();
  try {
    await f.open();
    await f.sync();
    const c = context();
    const response = await f.post("/v2/design", {
      context: c,
      ...design,
      modelAuthorized: true,
      isOperator: true,
    });
    assert.equal(response.status, 200);
    const result = await json<{
      context: GatewayContext;
      blueprint: Blueprint;
    }>(response);
    assert.deepEqual(result.context, c);
    assert.equal(result.blueprint.revision, 2);
    assert.equal(result.blueprint.verification, "untested");
    const stored = await json<{ blueprint: Blueprint }>(
      await f.post("/v2/project/read", { context: context() }),
    );
    assert.equal(stored.blueprint.revision, 1);
    assert.equal(
      (
        await f.post("/v2/design", {
          context: c,
          ...design,
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      409,
      "replayed inference request does not spend quota twice",
    );
    assert.equal(
      (
        await f.sync(context({ projectRevision: 2 }), {
          blueprint: result.blueprint,
        })
      ).status,
      200,
    );
    assert.equal(
      (await f.post("/v2/project/read", { context: context() })).status,
      409,
    );
    assert.equal(
      (
        await f.sync(context({ projectRevision: 2 }), {
          blueprint: {
            ...result.blueprint,
            name: "changed without a revision",
          },
        })
      ).status,
      409,
    );
  } finally {
    await f.close();
  }
});

test("revision planning derives its constraints from canonical memory, ignoring client hints", async () => {
  const provider: PlanningProvider = {
    async plan(input) {
      assert.deepEqual(input.revision, {
        template: "signal-lamp",
        rotation: 0,
      });
      assert.equal(input.rotation, 0);
      return {
        template: "signal-lamp",
        explanation: "Keep construction and increase its repeater delay.",
        parameters: { delay: 4 },
      };
    },
  };
  const f = await fixture(provider);
  try {
    await f.open();
    await f.sync();
    const response = await f.post("/v2/design", {
      context: context(),
      ...design,
      rotation: 180,
      revision: { template: "or-gate", rotation: 180 },
      revisionOnly: true,
      modelAuthorized: true,
      isOperator: true,
    });
    assert.equal(response.status, 200);
    const result = await json<{ blueprint: Blueprint }>(response);
    assert.equal(result.blueprint.provenance.template, "signal-lamp");
    assert.equal(result.blueprint.rotation, 0);
    assert.equal(result.blueprint.parameters?.delay, 4);
    const stored = await json<{ blueprint: Blueprint }>(
      await f.post("/v2/project/read", { context: context() }),
    );
    assert.equal(stored.blueprint.revision, 1);
    assert.equal(stored.blueprint.parameters?.delay, 1);
  } finally {
    await f.close();
  }
});

test("revocation cancels active inference and rejects its late reply", async () => {
  let entered!: () => void,
    release!: () => void,
    calls = 0;
  const started = new Promise<void>((resolve) => {
      entered = resolve;
    }),
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
  const f = await fixture({
    async plan() {
      calls++;
      entered();
      await gate;
      return {
        template: "signal-lamp",
        explanation: "A late proposal must not be accepted.",
      };
    },
  });
  try {
    await f.open();
    await f.sync();
    const pending = f.post("/v2/design", {
      context: context(),
      ...design,
      modelAuthorized: true,
      isOperator: true,
    });
    await started;
    const revoked = members.map((m) => ({ ...m, canPlan: false }));
    assert.equal(
      (
        await f.sync(context({ authorizationRevision: 2 }), {
          members: revoked,
        })
      ).status,
      200,
    );
    release();
    assert.equal((await pending).status, 503);
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context({ authorizationRevision: 2 }),
          ...design,
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      403,
    );
    assert.equal(calls, 1);
  } finally {
    release();
    await f.close();
  }
});

test("epochs cannot be replayed and revocation needs a distinct authorization revision", async () => {
  const f = await fixture();
  try {
    await f.open();
    await f.sync();
    assert.equal(
      (
        await f.sync(context(), {
          members: [{ actorId: OWNER, role: "owner", canPlan: true }],
        })
      ).status,
      409,
    );
    assert.equal(
      (
        await f.sync(context({ authorizationRevision: 2, actorId: EDITOR }), {
          members: [{ actorId: OWNER, role: "owner", canPlan: true }],
        })
      ).status,
      403,
    );
    assert.equal(
      (
        await f.sync(context({ authorizationRevision: 2 }), {
          members: [{ actorId: OWNER, role: "owner", canPlan: true }],
        })
      ).status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/project/read", {
          context: context({ actorId: EDITOR, authorizationRevision: 2 }),
        })
      ).status,
      403,
    );
    assert.equal((await f.open("epoch-two")).status, 200);
    assert.equal(
      (
        await f.post("/v2/project/read", {
          context: context({ authorizationRevision: 2 }),
        })
      ).status,
      409,
    );
    assert.equal(
      (await f.open()).status,
      409,
      "retired epochs cannot reclaim a namespace",
    );
    assert.equal(
      (
        await f.post("/v2/project/read", {
          context: context({
            serverEpoch: "epoch-two",
            authorizationRevision: 2,
          }),
        })
      ).status,
      409,
      "fresh project mirror required after restart",
    );
    assert.equal(
      (
        await f.sync(
          context({ serverEpoch: "epoch-two", authorizationRevision: 2 }),
          { members: [{ actorId: OWNER, role: "owner", canPlan: true }] },
        )
      ).status,
      200,
    );
  } finally {
    await f.close();
  }
});

test("server namespaces isolate copied project UUIDs", async () => {
  const f = await fixture();
  try {
    await f.open();
    await f.sync();
    await f.open("epoch-b", "server-b");
    assert.equal(
      (
        await f.sync(
          context({
            serverId: "server-b",
            serverEpoch: "epoch-b",
            actorId: EVE,
          }),
          { members: [{ actorId: EVE, role: "owner", canPlan: false }] },
        )
      ).status,
      200,
    );
    const list = async (serverId: string, serverEpoch: string, actor: string) =>
      json<{ projects: { serverId: string }[] }>(
        await f.get(
          `/v2/projects?serverId=${serverId}&serverEpoch=${serverEpoch}&actorId=${actor}&worldId=world-a`,
        ),
      );
    assert.deepEqual((await list("server-a", "epoch-one", EVE)).projects, []);
    assert.equal(
      (await list("server-b", "epoch-b", EVE)).projects[0]?.serverId,
      "server-b",
    );
  } finally {
    await f.close();
  }
});

test("durable gateway memory, evidence invalidation and quota survive service restart", async () => {
  const directory = mkdtempSync(join(tmpdir(), "mce-gateway-")),
    file = join(directory, "projects.sqlite");
  let f = await fixture(plan, file, { actorPlansPerMinute: 1 });
  try {
    await f.open();
    await f.sync();
    const report = {
      reportSequence: 1,
      worldId: design.worldId,
      dimension: design.dimension,
      projectId: design.projectId,
      taskId: "task-one",
      blueprintRevision: 1,
      status: "COMPLETED",
      detail: "Server tick test report",
      verified: { passed: true, ticks: [8, 16, 24] },
    };
    assert.equal(
      (await f.post("/v2/checkpoint", { context: context(), ...report }))
        .status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          ...report,
          blueprintRevision: 2,
        })
      ).status,
      409,
    );
    const region = { min: { x: 0, y: 100, z: 0 }, max: { x: 0, y: 100, z: 0 } };
    const observation = {
      worldId: design.worldId,
      dimension: design.dimension,
      region,
      blocks: [{ pos: region.min, state: "minecraft:stone" }],
      tick: 1,
    };
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 2,
          ...observation,
        })
      ).status,
      200,
    );
    const changed = await json<{ changed: number }>(
      await f.post("/v2/observe", {
        context: context(),
        reportSequence: 3,
        ...observation,
        tick: 2,
        blocks: [{ pos: region.min, state: "minecraft:air" }],
      }),
    );
    assert.equal(changed.changed, 1);
    const reader = new DatabaseSync(file, { readOnly: true });
    assert.equal(
      (
        reader
          .prepare(
            "SELECT verified FROM gateway_tasks WHERE task_id='task-one'",
          )
          .get() as { verified: unknown }
      ).verified,
      null,
    );
    reader.close();
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      429,
    );
    await f.close();
    f = await fixture(plan, file, { actorPlansPerMinute: 1 });
    assert.equal(
      (await f.post("/v2/project/read", { context: context() })).status,
      409,
    );
    await f.open();
    assert.equal(
      (await f.post("/v2/project/read", { context: context() })).status,
      409,
    );
    await f.sync();
    const restored = await json<{ blueprint: Blueprint }>(
      await f.post("/v2/project/read", { context: context() }),
    );
    assert.equal(restored.blueprint.id, blueprint.id);
    assert.equal(
      (
        await f.post("/v2/design", {
          context: context(),
          ...design,
          modelAuthorized: true,
          isOperator: true,
        })
      ).status,
      429,
      "restarting the service cannot reset account protection counters",
    );
  } finally {
    await f.close();
    rmSync(directory, { recursive: true, force: true });
  }
});

test("same-revision reports cannot arrive backwards or resurrect evidence across either route", async () => {
  const directory = mkdtempSync(join(tmpdir(), "mce-report-order-"));
  const file = join(directory, "projects.sqlite");
  let f = await fixture(plan, file);
  const report = {
    worldId: design.worldId,
    dimension: design.dimension,
    projectId: design.projectId,
    taskId: "ordered-task",
    blueprintRevision: 1,
    status: "COMPLETED",
    detail: "Latest actual server test passed",
    verified: { passed: true, ticks: [8, 16, 24] },
  };
  const readTask = () => {
    const reader = new DatabaseSync(file, { readOnly: true });
    try {
      return reader
        .prepare(
          "SELECT status,verified FROM gateway_tasks WHERE task_id='ordered-task'",
        )
        .get() as { status: string; verified: string | null };
    } finally {
      reader.close();
    }
  };
  const region = { min: { x: 0, y: 100, z: 0 }, max: { x: 0, y: 100, z: 0 } };
  const observation = {
    worldId: design.worldId,
    dimension: design.dimension,
    region,
    blocks: [{ pos: region.min, state: "minecraft:stone" }],
    tick: 25,
  };
  try {
    await f.open();
    await f.sync();
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 2,
          ...report,
        })
      ).status,
      200,
    );
    // This was captured before the completed report, but its HTTP chain arrives
    // afterwards with a fresh request ID and otherwise identical authority.
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 1,
          ...report,
          status: "EXECUTING",
          detail: "Older placement progress",
          verified: "untested",
        })
      ).status,
      409,
    );
    assert.equal(readTask().status, "COMPLETED");
    assert.deepEqual(JSON.parse(readTask().verified!), report.verified);
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 2,
          ...report,
        })
      ).status,
      409,
    );
    for (const sequence of [
      undefined,
      0,
      -1,
      1.5,
      Number.MAX_SAFE_INTEGER + 1,
    ]) {
      assert.equal(
        (
          await f.post("/v2/checkpoint", {
            context: context(),
            reportSequence: sequence,
            ...report,
          })
        ).status,
        400,
      );
    }
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 3,
          ...observation,
        })
      ).status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 4,
          ...observation,
          tick: 26,
          blocks: [{ pos: region.min, state: "minecraft:air" }],
        })
      ).status,
      200,
    );
    assert.equal(readTask().status, "PAUSED");
    assert.equal(readTask().verified, null);
    // Checkpoints and observations deliberately share one ordering boundary:
    // an old pass cannot restore evidence invalidated by a later observation.
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 3,
          ...report,
        })
      ).status,
      409,
    );
    assert.equal(readTask().verified, null);
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 5,
          ...report,
        })
      ).status,
      200,
    );
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 4,
          ...observation,
        })
      ).status,
      409,
    );
    assert.deepEqual(JSON.parse(readTask().verified!), report.verified);

    await f.close();
    f = await fixture(plan, file);
    await f.open();
    await f.sync();
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 4,
          ...report,
        })
      ).status,
      409,
      "service restart must not reset the current server epoch's ordering guard",
    );
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 6,
          ...report,
          status: "PAUSED",
          verified: "untested",
        })
      ).status,
      200,
    );
    await f.open("epoch-two");
    await f.sync(context({ serverEpoch: "epoch-two" }));
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context({ serverEpoch: "epoch-two" }),
          reportSequence: 1,
          ...report,
        })
      ).status,
      200,
      "a freshly authenticated Minecraft boot has an independent report sequence",
    );
    assert.equal(
      (
        await f.post("/v2/checkpoint", {
          context: context(),
          reportSequence: 7,
          ...report,
        })
      ).status,
      409,
      "a higher sequence cannot revive retired server authority",
    );
  } finally {
    await f.close();
    rmSync(directory, { recursive: true, force: true });
  }
});

test("the bounded planning queue is fair, serial and cancellation aware", async () => {
  const queue = new PlanningQueue(),
    trace: string[] = [];
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let concurrent = 0,
    maximum = 0;
  const work =
    (name: string, wait = false) =>
    async () => {
      concurrent++;
      maximum = Math.max(maximum, concurrent);
      trace.push(name);
      if (wait) await gate;
      concurrent--;
      return name;
    };
  const a0 = queue.run("a", new AbortController().signal, work("a0", true));
  const a1 = queue.run("a", new AbortController().signal, work("a1"));
  const b = queue.run("b", new AbortController().signal, work("b"));
  const cancelled = new AbortController(),
    c = queue.run("c", cancelled.signal, work("c"));
  const rejected = assert.rejects(c);
  cancelled.abort();
  await rejected;
  await assert.rejects(
    queue.run("legacy", new AbortController().signal, work("legacy"), false),
  );
  release();
  assert.deepEqual(await Promise.all([a0, a1, b]), ["a0", "a1", "b"]);
  assert.equal(maximum, 1);
  assert.deepEqual(trace, ["a0", "b", "a1"]);
});

test("targeted observations bind self perception to the authenticated gateway actor before claiming report order", async () => {
  const f = await fixture();
  try {
    await f.open();
    await f.sync();
    const pos = { x: 0, y: 100, z: 0 };
    const observation = {
      worldId: design.worldId,
      dimension: design.dimension,
      tick: 1,
      region: { min: pos, max: pos },
      blocks: [{ pos, state: "minecraft:stone" }],
      perception: {
        schemaVersion: 1,
        kind: "block",
        actorId: EDITOR,
        player: {
          position: { x: 0.5, y: 102, z: 4 },
          eye: { x: 0.5, y: 103.62, z: 4 },
          look: { x: 0, y: -0.6, z: -0.8 },
        },
        target: pos,
        differences: { missing: 0, changed: 0, unplanned: 0 },
      },
    };
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 1,
          ...observation,
        })
      ).status,
      403,
      "An otherwise-valid request cannot attribute another player's position to its authenticated actor",
    );
    observation.perception.actorId = OWNER;
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context(),
          reportSequence: 1,
          ...observation,
        })
      ).status,
      200,
      "The rejected actor mismatch did not claim report order",
    );
    assert.equal(
      (
        await f.post("/v2/observe", {
          context: context({ actorId: EVE }),
          reportSequence: 2,
          ...observation,
        })
      ).status,
      403,
    );
  } finally {
    await f.close();
  }
});
