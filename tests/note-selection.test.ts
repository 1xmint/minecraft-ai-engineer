import test from "node:test";
import assert from "node:assert/strict";
import {
  currentNotes,
  DISCUSSION_NOTE_LIMIT,
  noteWords,
} from "../companion/src/discussion.js";
import type {
  ProjectKnowledge,
  ProjectNote,
} from "../companion/src/project-knowledge.js";

const OWNER = "11111111-1111-4111-8111-111111111111";
const MEMBER = "22222222-2222-4222-8222-222222222222";
function note(
  revision: number,
  text: string,
  extra: { rationale?: string; supersedes?: number; authorId?: string } = {},
): ProjectNote {
  return Object.freeze({
    schemaVersion: 2,
    kind: "decision",
    text,
    ...(extra.rationale !== undefined ? { rationale: extra.rationale } : {}),
    ...(extra.supersedes !== undefined ? { supersedes: extra.supersedes } : {}),
    id: `note-${revision}`,
    revision,
    authorId: extra.authorId ?? OWNER,
    createdAt: revision,
    source: "owner-authored",
    writtenAtRevision: revision - 1,
  }) as ProjectNote;
}
function knowledge(notes: ProjectNote[]): ProjectKnowledge {
  return {
    schemaVersion: 1,
    revision: notes.length,
    notes,
    hasEarlier: false,
  };
}
// Ten filler notes push past the limit of eight; the interesting notes are the oldest.
function project(): ProjectKnowledge {
  const notes = [
    note(1, "use an XOR gate for the sum bit", {
      rationale: "matches the truth table",
      authorId: MEMBER,
    }),
    note(2, "keep the carry lamps on the left"),
    note(3, "put the clock behind the wall"),
  ];
  for (let i = 4; i <= 13; i++)
    notes.push(note(i, `filler requirement number${i}`));
  return knowledge(notes);
}
const texts = (k: ProjectKnowledge, message?: string, component?: string) =>
  currentNotes(k, message, component).notes.map((n) => n.text);

test("question words do not make every note relevant; short circuit terms still count", () => {
  assert.deepEqual(
    [...noteWords("What does this do? Why is that so?")],
    [],
    "Bare question words select nothing",
  );
  assert.deepEqual([...noteWords("the XOR and LED hub")].sort(), [
    "and",
    "hub",
    "led",
    "xor",
  ]);
  assert.ok(noteWords("carry lamps").has("lamp"), "A plural folds to match");
  assert.ok(noteWords("glass").has("glass"), "A double-s word is not cut");

  const k = project();
  assert.deepEqual(
    texts(k, "what does this do?"),
    texts(k),
    "A question with no subject gets the newest notes, as with no question",
  );
  assert.ok(
    texts(k, "why the xor?").includes("use an XOR gate for the sum bit"),
  );
  assert.ok(!texts(k).includes("use an XOR gate for the sum bit"));
});

test("the request and the selected component change which older notes are offered", () => {
  const k = project();
  assert.ok(
    texts(k, "where do the carry lamps go?").includes(
      "keep the carry lamps on the left",
    ),
  );
  assert.ok(
    texts(k, "explain this", "clock").includes("put the clock behind the wall"),
    "The selected component's id selects notes naming it",
  );
  // A literal reason is searchable too, and authorship survives selection.
  const byReason = currentNotes(k, "does it follow the truth table?").notes;
  const xor = byReason.find((n) => n.revision === 1);
  assert.equal(xor?.authorId, MEMBER);
  assert.equal(
    xor?.schemaVersion === 2 ? xor.rationale : undefined,
    "matches the truth table",
  );
});

test("more shared words rank first, the bound holds, and stored order is kept", () => {
  const notes = [note(1, "red lamp row"), note(2, "red lamp")];
  for (let i = 3; i <= 20; i++) notes.push(note(i, `red filler${i}`));
  const k = knowledge(notes);
  const chosen = currentNotes(k, "red lamp row").notes;
  assert.equal(chosen.length, DISCUSSION_NOTE_LIMIT);
  assert.equal(chosen[0]!.revision, 1, "Three shared words beat two");
  assert.equal(chosen[1]!.revision, 2, "Two shared words beat one");
  assert.deepEqual(
    chosen.map((n) => n.revision),
    [1, 2, 15, 16, 17, 18, 19, 20],
    "Ties go to the newest, and the result keeps stored order",
  );
  assert.equal(currentNotes(k, "red lamp row").omitted, true);
});

test("a replaced note is never current, however well it matches", () => {
  const notes = [note(1, "use repeaters for the clock")];
  for (let i = 2; i <= 11; i++) notes.push(note(i, `filler${i}`));
  notes.push(
    note(12, "use comparators for the clock", {
      rationale: "smaller",
      supersedes: 1,
    }),
  );
  const result = currentNotes(knowledge(notes), "why repeaters for the clock?");
  assert.ok(result.notes.every((n) => n.revision !== 1));
  assert.ok(result.notes.some((n) => n.revision === 12));
  assert.equal(result.replaced, 1);
  // At or under the limit nothing is ranked or dropped.
  const small = knowledge([note(1, "a"), note(2, "b", { supersedes: 1 })]);
  assert.deepEqual(
    currentNotes(small, "a").notes.map((n) => n.revision),
    [2],
  );
  assert.equal(currentNotes(small, "a").omitted, false);
});
