import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { MemoryStore } from "../companion/src/memory.js";
import { discussionGrounding } from "../companion/src/discussion.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import {
  readProjectNote,
  validateNoteSearch,
  validateProjectNote,
} from "../companion/src/project-knowledge.js";
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
    { schemaVersion: 3 },
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
test("version 2 notes keep a literal reason and an optional replacement; absent keys stay absent", () => {
  const decision = {
    schemaVersion: 2,
    kind: "decision",
    text: "use repeaters",
  };
  const reasoned = validateProjectNote({
    ...decision,
    rationale: " they stay visible ",
    supersedes: 1,
  });
  assert.equal(reasoned.schemaVersion, 2);
  assert.equal(
    "rationale" in reasoned && reasoned.rationale,
    " they stay visible ",
  );
  assert.equal("supersedes" in reasoned && reasoned.supersedes, 1);
  const plain = validateProjectNote(decision);
  assert.equal("rationale" in plain, false);
  assert.equal("supersedes" in plain, false);
  for (const patch of [
    { rationale: "" },
    { rationale: null },
    { rationale: "x".repeat(513) },
    { supersedes: 0 },
    { supersedes: null },
    { supersedes: "1" },
    { writtenAtRevision: 4 },
  ])
    assert.throws(() => validateProjectNote({ ...decision, ...patch }));
  assert.throws(() =>
    validateProjectNote({ ...note, rationale: "v1 has none" }),
  );
});
test("stored version 1 notes read back with exactly their original keys", () => {
  const stored = {
    ...note,
    id: OWNER,
    revision: 1,
    authorId: OWNER,
    createdAt: 1,
    source: "owner-authored",
  };
  assert.deepEqual(Object.keys(readProjectNote(stored)), Object.keys(stored));
  assert.throws(() => readProjectNote({ ...stored, writtenAtRevision: 0 }));
  const v2 = { ...stored, schemaVersion: 2, writtenAtRevision: 0 };
  assert.equal(readProjectNote(v2).schemaVersion, 2);
  const { writtenAtRevision: _dropped, ...missing } = v2;
  assert.throws(() => readProjectNote(missing));
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
const decision = (text: string, extra: Record<string, unknown> = {}) => ({
  schemaVersion: 2,
  kind: "decision",
  text,
  ...extra,
});
test("replacing a note keeps both in history and names the replacement", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    const first = store.gateway.rememberNote(
      frame(),
      decision("use repeaters", { rationale: "they stay visible" }),
      0,
    );
    assert.equal(first.schemaVersion === 2 && first.writtenAtRevision, 0);
    assert.equal(store.gateway.replacedBy(frame(), 1), undefined);
    const second = store.gateway.rememberNote(
      frame(),
      decision("use comparators", { rationale: "smaller", supersedes: 1 }),
      1,
    );
    assert.equal(second.schemaVersion === 2 && second.supersedes, 1);
    assert.equal(store.gateway.replacedBy(frame(), 1), 2);
    const refusals: [Record<string, unknown>, RegExp][] = [
      [
        decision("again", { supersedes: 1 }),
        /already replaced by #2; replace #2 instead/,
      ],
      [decision("missing", { supersedes: 9 }), /No note #9 in this project/],
      [
        { ...decision("wrong kind", { supersedes: 2 }), kind: "goal" },
        /is a decision/,
      ],
    ];
    for (const [input, message] of refusals) {
      assert.throws(
        () => store.gateway.rememberNote(frame(), input, 2),
        message,
      );
      assert.equal(store.gateway.knowledge(frame()).revision, 2);
    }
    assert.throws(
      () =>
        store.gateway.rememberNote(
          frame({ actorId: EDITOR }),
          decision("editor", { supersedes: 2 }),
          2,
        ),
      /Only the project owner/,
    );
    const other = frame({ projectId: "project-b" });
    sync(store, other);
    assert.throws(
      () =>
        store.gateway.rememberNote(
          other,
          decision("cross", { supersedes: 1 }),
          0,
        ),
      /No note #1 in this project/,
    );
    // Opening the second project's service context retires every old mirror.
    assert.throws(
      () => store.gateway.knowledge(frame()),
      /fresh server-owned synchronization/,
    );
    sync(store);
    const history = store.gateway.knowledge(frame()).notes;
    assert.deepEqual(
      history.map((n) => n.text),
      ["use repeaters", "use comparators"],
    );
  } finally {
    store.close();
  }
});
test("planning context marks replaced notes and stays byte-identical for version 1 notes", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    store.gateway.rememberNote(frame(), note, 0);
    store.gateway.rememberNote(frame(), { ...note, kind: "decision" }, 1);
    const knowledge = store.gateway.knowledge(frame());
    assert.equal(
      store.gateway.reasoning(frame()).text,
      JSON.stringify({
        projectContext: store.gateway.context(frame()),
        knowledge: {
          revision: 2,
          source:
            "owner-authored requirements; not observed or verified world state",
          notes: knowledge.notes.slice(-8),
          hasEarlier: false,
        },
      }),
    );
    store.gateway.rememberNote(
      frame(),
      decision("replacement", { supersedes: 2 }),
      2,
    );
    const context = JSON.parse(store.gateway.reasoning(frame()).text);
    assert.deepEqual(context.knowledge.replaced, [2]);
    assert.deepEqual(
      context.knowledge.notes[0],
      JSON.parse(JSON.stringify(knowledge.notes[0])),
    );
  } finally {
    store.close();
  }
});
test("a note claiming a pass changes no task, verification or blueprint state", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    const tasks = store.gateway.context(frame());
    const design = store.gateway.blueprint(frame());
    store.gateway.rememberNote(
      frame(),
      decision("verified: the clock passed every check", {
        rationale: "tested last week",
      }),
      0,
    );
    assert.equal(store.gateway.context(frame()), tasks);
    assert.deepEqual(store.gateway.blueprint(frame()), design);
    assert.match(
      JSON.parse(store.gateway.reasoning(frame()).text).knowledge.source,
      /not observed or verified world state/,
    );
  } finally {
    store.close();
  }
});
test("a reason and a replacement survive a restart and still refuse a second replacement", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-knowledge-v2-")),
    file = join(dir, "memory.sqlite");
  let store = new MemoryStore(file);
  try {
    sync(store);
    store.gateway.rememberNote(
      frame(),
      decision("use repeaters", { rationale: "they stay visible" }),
      0,
    );
    const replacement = store.gateway.rememberNote(
      frame(),
      decision("use comparators", { rationale: "smaller", supersedes: 1 }),
      1,
    );
    store.close();
    store = new MemoryStore(file);
    const c = frame({ serverEpoch: "epoch-two" });
    sync(store, c);
    assert.deepEqual(store.gateway.knowledge(c).notes[1], replacement);
    assert.equal(store.gateway.replacedBy(c, 1), 2);
    assert.throws(
      () =>
        store.gateway.rememberNote(c, decision("again", { supersedes: 1 }), 2),
      /already replaced by #2/,
    );
    assert.deepEqual(
      JSON.parse(store.gateway.reasoning(c).text).knowledge.replaced,
      [1],
    );
  } finally {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
const reference = (text: string) => ({
  schemaVersion: 2,
  kind: "reference",
  text,
});
/** Notes 1-5: a goal, a decision replaced by note 3, its replacement, a reference and a preference. */
function history(store: MemoryStore) {
  store.gateway.rememberNote(
    frame(),
    { ...note, text: "Keep the entrance within six blocks." },
    0,
  );
  store.gateway.rememberNote(
    frame(),
    decision("use repeaters", { rationale: "they stay visible" }),
    1,
  );
  store.gateway.rememberNote(
    frame(),
    decision("use comparators", { rationale: "smaller", supersedes: 2 }),
    2,
  );
  store.gateway.rememberNote(
    frame(),
    reference("Copy the 100% tested clock_a layout"),
    3,
  );
  store.gateway.rememberNote(
    frame(),
    { schemaVersion: 2, kind: "preference", text: "Keep wiring visible" },
    4,
  );
}
const revisions = (entries: readonly { note: { revision: number } }[]) =>
  entries.map((entry) => entry.note.revision);
test("references are version 2 notes; version 1 stays frozen to its four kinds", () => {
  assert.equal(validateProjectNote(reference("Docs page 3")).kind, "reference");
  assert.throws(() =>
    validateProjectNote({ ...reference("x"), schemaVersion: 1 }),
  );
  for (const search of [
    {},
    { text: " " },
    { text: "x".repeat(65) },
    { kind: "verified" },
    { changes: false },
  ])
    assert.throws(() => validateNoteSearch(search));
  assert.deepEqual(validateNoteSearch({ text: "  lamp " }), { text: "lamp" });
});
test("show and find read the whole history and mark current and replaced notes", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    history(store);
    const replaced = store.gateway.showNote(frame(), 2);
    assert.equal(replaced.knowledgeRevision, 5);
    assert.equal(replaced.entry?.note.text, "use repeaters");
    assert.equal(replaced.entry?.note.authorId, OWNER);
    assert.equal(replaced.entry?.replacedBy, 3);
    assert.equal(replaced.entry?.replaces, undefined);
    const current = store.gateway.showNote(frame(), 3).entry!;
    assert.equal(current.replacedBy, undefined);
    assert.equal(current.replaces?.revision, 2);
    assert.equal(current.replaces?.text, "use repeaters");
    assert.equal(store.gateway.showNote(frame(), 9).entry, null);
    assert.throws(() => store.gateway.showNote(frame(), 0));
    const find = (search: Record<string, unknown>) =>
      store.gateway.findNotes(frame(), validateNoteSearch(search));
    assert.deepEqual(revisions(find({ text: "VISIBLE" }).entries), [5, 2]);
    assert.deepEqual(revisions(find({ text: "%" }).entries), [4]);
    assert.deepEqual(revisions(find({ text: "_" }).entries), [4]);
    assert.deepEqual(revisions(find({ text: "clock%a" }).entries), []);
    assert.deepEqual(
      revisions(find({ text: "decision" }).entries),
      [],
      "Search never matches stored field names",
    );
    assert.deepEqual(revisions(find({ kind: "decision" }).entries), [3, 2]);
    assert.deepEqual(revisions(find({ changes: true }).entries), [3]);
    assert.equal(find({ changes: true }).entries[0]!.replaces?.revision, 2);
    assert.deepEqual(revisions(find({ kind: "reference" }).entries), [4]);
    const none = find({ text: "torches" });
    assert.deepEqual(none.entries, []);
    assert.equal(none.more, 0);
    for (let i = 5; i < 40; i++)
      store.gateway.rememberNote(
        frame(),
        { ...note, kind: "preference", text: `Preference ${i + 1}` },
        i,
      );
    assert.equal(store.gateway.knowledge(frame()).hasEarlier, true);
    assert.equal(
      store.gateway.showNote(frame(), 1).entry?.note.text,
      "Keep the entrance within six blocks.",
      "Notes older than the read window stay retrievable by number",
    );
    assert.deepEqual(revisions(find({ text: "six blocks" }).entries), [1]);
    const many = find({ kind: "preference" });
    assert.equal(many.entries.length, 8);
    assert.equal(many.more, 36 - 8);
    assert.deepEqual(revisions(many.entries).slice(0, 2), [40, 39]);
  } finally {
    store.close();
  }
});
test("retrieval is fenced to current members of the selected project", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    history(store);
    assert.equal(
      store.gateway.showNote(frame({ actorId: EDITOR }), 1).entry?.note.text,
      "Keep the entrance within six blocks.",
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
      { ...note, text: "Private entrance plan for project b." },
      0,
    );
    assert.deepEqual(
      revisions(
        store.gateway.findNotes(
          frame(),
          validateNoteSearch({ text: "entrance" }),
        ).entries,
      ),
      [1],
      "Search never crosses into another project",
    );
    for (const c of [
      frame({ actorId: EVE }),
      frame({ projectId: "project-b" }),
      frame({ projectId: "unknown-project" }),
    ]) {
      assert.throws(() => store.gateway.showNote(c, 1));
      assert.throws(() =>
        store.gateway.findNotes(c, validateNoteSearch({ text: "entrance" })),
      );
    }
    store.gateway.sync({
      context: frame({ authorizationRevision: 2 }),
      worldId: "world-a",
      dimension: "minecraft:overworld",
      name: "Shared engineering",
      members: [members[0]!],
      isOperator: true,
      blueprint,
    });
    for (const c of [
      frame({ actorId: EDITOR, authorizationRevision: 2 }),
      frame({ actorId: EDITOR }),
    ]) {
      assert.throws(() => store.gateway.showNote(c, 1));
      assert.throws(() =>
        store.gateway.findNotes(c, validateNoteSearch({ kind: "goal" })),
      );
    }
    assert.equal(
      store.gateway.showNote(frame({ authorizationRevision: 2 }), 1).entry?.note
        .revision,
      1,
    );
  } finally {
    store.close();
  }
});
test("refused writes leave nothing to show or find", () => {
  const store = new MemoryStore(":memory:");
  try {
    sync(store);
    history(store);
    const refused = decision("Use torches for the delay");
    const attempts: [GatewayContext, Record<string, unknown>, number][] = [
      [frame({ actorId: EDITOR }), refused, 5],
      [frame({ actorId: EVE }), refused, 5],
      [frame(), refused, 4],
      [frame(), { ...refused, supersedes: 2 }, 5],
      [frame(), { ...refused, kind: "goal", supersedes: 3 }, 5],
      [frame(), { ...refused, supersedes: 9 }, 5],
      [frame(), { ...refused, schemaVersion: 1, kind: "reference" }, 5],
    ];
    for (const [c, value, expected] of attempts)
      assert.throws(() => store.gateway.rememberNote(c, value, expected));
    assert.equal(store.gateway.knowledge(frame()).revision, 5);
    assert.equal(store.gateway.showNote(frame(), 6).entry, null);
    assert.deepEqual(
      store.gateway.findNotes(frame(), validateNoteSearch({ text: "torches" }))
        .entries,
      [],
    );
  } finally {
    store.close();
  }
});
test("show and find return the same records after a restart", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-knowledge-find-")),
    file = join(dir, "memory.sqlite");
  let store = new MemoryStore(file);
  try {
    sync(store);
    history(store);
    const shown = store.gateway.showNote(frame(), 3);
    const found = store.gateway.findNotes(
      frame(),
      validateNoteSearch({ kind: "decision" }),
    );
    store.close();
    store = new MemoryStore(file);
    const c = frame({ serverEpoch: "epoch-two" });
    assert.throws(
      () => store.gateway.showNote(c, 3),
      "Retrieval waits for a fresh server-owned sync",
    );
    sync(store, c);
    assert.deepEqual(store.gateway.showNote(c, 3), shown);
    assert.deepEqual(
      store.gateway.findNotes(c, validateNoteSearch({ kind: "decision" })),
      found,
    );
    assert.equal(store.gateway.showNote(c, 2).entry?.replacedBy, 3);
  } finally {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
test("replaced notes never reach discussion or planning as current requirements, even after a restart", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-knowledge-current-")),
    file = join(dir, "memory.sqlite");
  let store = new MemoryStore(file);
  try {
    sync(store);
    store.gateway.rememberNote(
      frame(),
      decision("use repeaters for the clock"),
      0,
    );
    // Ten more current notes push past the bound of eight.
    for (let i = 1; i <= 10; i++)
      store.gateway.rememberNote(frame(), decision(`filler marker${i}`), i);
    const replacement = store.gateway.rememberNote(
      frame(),
      decision("use comparators for the clock  <keep spacing>", {
        rationale: "smaller",
        supersedes: 1,
      }),
      11,
    );
    store.gateway.rememberNote(
      frame(),
      { schemaVersion: 2, kind: "goal", text: "keep the lamp row visible" },
      12,
    );
    store.close();
    store = new MemoryStore(file);
    const c = frame({ serverEpoch: "epoch-two" });
    sync(store, c);
    const knowledge = store.gateway.knowledge(c);
    const grounding = (message?: string) =>
      discussionGrounding("Shared", 1, blueprint, knowledge, "root", message);
    const sources = (message?: string) =>
      JSON.parse(grounding(message).text).sources.filter(
        (source: { kind: string }) =>
          source.kind === "owner-authored-requirement",
      ) as { id: string; data: { revision: number; text: string } }[];

    const asked = sources("why does the lamp row stay visible?");
    assert.equal(asked.length, 8, "Bounded to eight notes");
    assert.ok(
      asked.every((source) => source.data.revision !== 1),
      "The replaced note is never offered as a current requirement",
    );
    assert.ok(
      asked.some(
        (source) =>
          source.data.text === "use comparators for the clock  <keep spacing>",
      ),
      "The replacement keeps its literal text and its author",
    );
    assert.ok(
      asked.some((source) => source.data.text === "keep the lamp row visible"),
    );
    // A question about an older current note pulls it in ahead of the newest notes.
    const filler = sources("what did marker2 say?");
    assert.ok(filler.some((source) => source.data.text === "filler marker2"));
    assert.ok(
      !sources().some((source) => source.data.text === "filler marker2"),
    );
    assert.deepEqual(
      filler.map((source) => source.data.revision),
      [...filler.map((source) => source.data.revision)].sort((a, b) => a - b),
      "Chosen notes keep their stored order",
    );
    assert.equal(
      grounding("what did marker2 say?").text,
      grounding("what did marker2 say?").text,
      "The same question gives byte-identical context",
    );
    const context = JSON.parse(grounding().text);
    assert.equal(context.replacedNotesOmitted, 1);
    assert.equal(context.earlierNotesOmitted, true);
    assert.equal(
      grounding().availableBasis.has(
        `note:${knowledge.notes.find((n) => n.revision === 1)!.id}`,
      ),
      false,
      "A reply may not cite the replaced note as a basis",
    );

    const planning = JSON.parse(store.gateway.reasoning(c).text).knowledge;
    assert.deepEqual(planning.replaced, [1]);
    assert.ok(
      planning.notes.every((n: { revision: number }) => n.revision !== 1),
    );
    assert.ok(
      planning.notes.some(
        (n: { revision: number }) => n.revision === replacement.revision,
      ),
    );
    assert.match(planning.replacedNotes, /history only/);
    const requestedPlanning = JSON.parse(
      store.gateway.reasoning(c, "build around filler marker2").text,
    ).knowledge;
    assert.equal(requestedPlanning.notes.length, 8);
    assert.ok(
      requestedPlanning.notes.some(
        (n: { text: string }) => n.text === "filler marker2",
      ),
      "The planning request retrieves a relevant older current note after restart",
    );
    assert.ok(
      requestedPlanning.notes.every(
        (n: { revision: number }) => n.revision !== 1,
      ),
      "Request relevance cannot revive a replaced requirement",
    );
    assert.throws(
      () => store.gateway.reasoning({ ...c, actorId: EVE }, "marker2"),
      "A planning subject grants no access to an unrelated player's private notes",
    );
    // The replaced note is still history: show reads it back with its replacement named.
    assert.equal(store.gateway.replacedBy(c, 1), replacement.revision);
  } finally {
    store.close();
    rmSync(dir, { recursive: true, force: true });
  }
});
