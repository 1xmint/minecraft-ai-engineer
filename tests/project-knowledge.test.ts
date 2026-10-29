import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { MemoryStore } from "../companion/src/memory.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import { validateProjectNote } from "../companion/src/project-knowledge.js";
import type {
  GatewayContext,
  ProjectMember,
} from "../companion/src/gateway-types.js";
const OWNER = "11111111-1111-4111-8111-111111111111",
  EDITOR = "22222222-2222-4222-8222-222222222222",
  EVE = "33333333-3333-4333-8333-333333333333";
const members: ProjectMember[] = [
  { actorId: OWNER, role: "owner", canPlan: true },
  { actorId: EDITOR, role: "editor", canPlan: false },
];
const frame = (changes: Partial<GatewayContext> = {}): GatewayContext => ({
  serverId: "knowledge-server",
  serverEpoch: "epoch-one",
  projectId: "project-a",
  projectRevision: 1,
  authorizationRevision: 1,
  actorId: OWNER,
  connectionId: "44444444-4444-4444-8444-444444444444",
  requestId: randomUUID(),
  ...changes,
});
const blueprint = makeReferenceBlueprint({
  request: "reference",
  projectId: "project-a",
  worldId: "world-a",
  dimension: "minecraft:overworld",
  anchor: { x: 0, y: 100, z: 0 },
  rotation: 0,
});
function sync(store: MemoryStore, c = frame(), people = members) {
  store.gateway.open(
    c.serverId,
    c.serverEpoch,
    c.serverEpoch === "epoch-one" ? 1 : 2,
  );
  store.gateway.sync({
    context: c,
    worldId: "world-a",
    dimension: "minecraft:overworld",
    name: "Shared engineering",
    members: people,
    isOperator: true,
    blueprint,
  });
}
const note = {
  schemaVersion: 1,
  kind: "goal",
  text: "Fit the entrance inside six blocks; this is a requirement, not observed geometry.",
};
test("project notes preserve literal human text and cannot supply authorship or world claims", () => {
  const input = { ...note, text: "  Prefer stone\nfor the supports.  " };
  const accepted = validateProjectNote(input);
  input.text = "changed";
  assert.equal(accepted.text, "  Prefer stone\nfor the supports.  ");
  assert.equal(Object.isFrozen(accepted), true);
  for (const patch of [
    { kind: "verified" },
    { schemaVersion: 2 },
    { text: " " },
    { text: "x".repeat(513) },
    { text: "\u0000bad" },
    { authorId: EVE },
    { revision: 8 },
    { verified: "passed" },
    { inventory: [] },
    { authority: true },
  ])
    assert.throws(() => validateProjectNote({ ...note, ...patch }));
});
test("only the current connected owner can append; readers, optimistic revisions and replay remain fenced", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    const initial = store.gateway.blueprint(frame());
    const before = store.gateway.context(frame());
    const noConnection = frame();
    delete noConnection.connectionId;
    for (const c of [
      noConnection,
      frame({ actorId: EDITOR }),
      frame({ actorId: EVE }),
      frame({ authorizationRevision: 2 }),
    ])
      assert.throws(() => store.gateway.rememberNote(c, note, 0));
    assert.equal(store.gateway.knowledge(frame()).revision, 0);
    const c = frame();
    const saved = store.gateway.rememberNote(c, note, 0);
    assert.equal(saved.authorId, OWNER);
    assert.equal(saved.revision, 1);
    assert.equal(saved.source, "owner-authored");
    assert.equal(Object.isFrozen(saved), true);
    assert.throws(() => store.gateway.rememberNote(frame(), note, 0));
    assert.throws(() =>
      store.gateway.rememberNote(c, { ...note, kind: "decision" }, 1),
    );
    assert.equal(store.gateway.knowledge(frame()).revision, 1);
    assert.deepEqual(
      store.gateway.knowledge(frame({ actorId: EDITOR })).notes,
      [saved],
    );
    assert.throws(() =>
      store.gateway.knowledge(frame({ projectId: "other-project" })),
    );
    const other = frame({
      projectId: "project-b",
      actorId: EVE,
      connectionId: "55555555-5555-4555-8555-555555555555",
    });
    store.gateway.sync({
      context: other,
      worldId: "world-a",
      dimension: "minecraft:overworld",
      name: "Other owner's project",
      members: [{ actorId: EVE, role: "owner", canPlan: true }],
      isOperator: true,
      blueprint: makeReferenceBlueprint({
        request: "other",
        projectId: "project-b",
        worldId: "world-a",
        dimension: "minecraft:overworld",
        anchor: { x: 20, y: 100, z: 0 },
        rotation: 0,
      }),
    });
    store.gateway.rememberNote(
      { ...other, requestId: randomUUID() },
      { ...note, text: "Other project only." },
      0,
    );
    assert.throws(() =>
      store.gateway.knowledge(frame({ projectId: "project-b" })),
    );
    assert.throws(() => store.gateway.knowledge(frame({ actorId: EVE })));

    assert.deepEqual(
      store.gateway.blueprint(frame()),
      initial,
      "Knowledge does not relabel accepted geometry",
    );
    assert.equal(
      store.gateway.context(frame()),
      before,
      "Planning context is unchanged until versioned conversation integration is implemented",
    );
  } finally {
    store.close();
  }
});
test("literal goals and decisions persist through service/epoch changes without restoring authority", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-knowledge-")),
    file = join(dir, "memory.sqlite");
  let store = new MemoryStore(file);
  try {
    sync(store);
    const first = store.gateway.rememberNote(frame(), note, 0);
    const second = store.gateway.rememberNote(
      frame(),
      {
        ...note,
        kind: "decision",
        text: "Keep the previous blueprint until a separately authorized revision.",
      },
      1,
    );
    store.close();
    store = new MemoryStore(file);
    const c = frame({ serverEpoch: "epoch-two" });
    sync(store, c);
    const snapshot = store.gateway.knowledge(c);
    assert.equal(snapshot.revision, 2);
    assert.deepEqual(snapshot.notes, [first, second]);
    assert.equal(snapshot.hasEarlier, false);
    assert.equal(Object.isFrozen(snapshot.notes), true);
    assert.throws(() => store.gateway.knowledge(frame()));
    store.gateway.sync({
      context: { ...c, authorizationRevision: 2, requestId: randomUUID() },
      worldId: "world-a",
      dimension: "minecraft:overworld",
      name: "Shared engineering",
      members: [members[0]!],
      isOperator: true,
      blueprint,
    });
    assert.throws(() =>
      store.gateway.knowledge(
        frame({
          serverEpoch: "epoch-two",
          actorId: EDITOR,
          authorizationRevision: 2,
        }),
      ),
    );
    assert.deepEqual(
      store.gateway.knowledge({ ...c, authorizationRevision: 2 }).notes,
      [first, second],
    );
    const db = new DatabaseSync(file, { readOnly: true });
    try {
      const recorded = db
        .prepare(
          "SELECT document FROM gateway_project_notes ORDER BY note_revision",
        )
        .all();
      assert.equal(recorded.length, 2);
      assert.equal(JSON.parse(String(recorded[0]!.document)).authorId, OWNER);
    } finally {
      db.close();
    }
  } finally {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  }
});

test("failed persistence rolls back both note revision and replay consumption", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    const c = frame();
    const audit = store.gateway.audit;
    store.gateway.audit = () => {
      throw new Error("synthetic audit failure");
    };
    assert.throws(
      () => store.gateway.rememberNote(c, note, 0),
      /synthetic audit failure/,
    );
    store.gateway.audit = audit;
    assert.equal(store.gateway.knowledge(frame()).revision, 0);
    assert.equal(
      store.gateway.rememberNote(c, note, 0).revision,
      1,
      "Uncommitted request can be retried after rollback",
    );
  } finally {
    store.close();
  }
});
test("stored row identity corruption cannot be read or extended as legitimate knowledge", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-knowledge-corrupt-")),
    file = join(dir, "memory.sqlite");
  const store = new MemoryStore(file);
  try {
    sync(store);
    store.gateway.rememberNote(frame(), note, 0);
    const db = new DatabaseSync(file);
    try {
      db.prepare(
        "UPDATE gateway_project_notes SET document=json_set(document,'$.revision',2)",
      ).run();
    } finally {
      db.close();
    }
    assert.throws(() => store.gateway.knowledge(frame()), /inconsistent/);
    assert.throws(
      () => store.gateway.rememberNote(frame(), note, 1),
      /inconsistent/,
    );
    const read = new DatabaseSync(file, { readOnly: true });
    try {
      assert.equal(
        read.prepare("SELECT revision FROM gateway_knowledge_revisions").get()!
          .revision,
        1,
      );
      assert.equal(
        read
          .prepare("SELECT COUNT(*) AS count FROM gateway_project_notes")
          .get()!.count,
        1,
      );
    } finally {
      read.close();
    }
  } finally {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  }
});

test("bounded reads retain older notes durably instead of truncating project history", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    for (let index = 0; index < 33; index++)
      store.gateway.rememberNote(
        frame(),
        { ...note, text: `Explicit requirement ${index + 1}` },
        index,
      );
    const page = store.gateway.knowledge(frame());
    assert.equal(page.revision, 33);
    assert.equal(page.notes.length, 32);
    assert.equal(page.notes[0]!.revision, 2);
    assert.equal(page.notes.at(-1)!.revision, 33);
    assert.equal(page.hasEarlier, true);
    assert.throws(() => store.gateway.knowledge(frame(), 33));
  } finally {
    store.close();
  }
});

test("reasoning snapshots include bounded literal requirements and reject obsolete knowledge versions", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    const empty = store.gateway.reasoning(frame());
    assert.equal(empty.text, store.gateway.context(frame()));
    assert.equal(empty.knowledgeRevision, 0);
    store.gateway.assertReasoningCurrent(frame(), 0);
    for (let i = 0; i < 10; i++)
      store.gateway.rememberNote(
        frame(),
        { ...note, text: `Requirement ${i + 1}` },
        i,
      );
    assert.throws(() =>
      store.gateway.assertReasoningCurrent(frame(), empty.knowledgeRevision),
    );
    const snapshot = store.gateway.reasoning(frame());
    assert.equal(snapshot.knowledgeRevision, 10);
    const context = JSON.parse(snapshot.text);
    assert.equal(context.knowledge.notes.length, 8);
    assert.equal(context.knowledge.notes[0].revision, 3);
    assert.equal(context.knowledge.notes[7].text, "Requirement 10");
    assert.equal(context.knowledge.hasEarlier, true);
    assert.match(context.knowledge.source, /not observed or verified/);
    assert.equal(
      context.knowledge.notes.every(
        (n: { authorId: string }) => n.authorId === OWNER,
      ),
      true,
    );
    store.gateway.assertReasoningCurrent(frame(), snapshot.knowledgeRevision);
    store.gateway.rememberNote(
      frame(),
      { ...note, text: "New requirement" },
      10,
    );
    assert.throws(() =>
      store.gateway.assertReasoningCurrent(frame(), snapshot.knowledgeRevision),
    );
    assert.equal(
      JSON.parse(snapshot.text).knowledge.revision,
      10,
      "Captured context never silently changes underneath a model call",
    );
  } finally {
    store.close();
  }
});
