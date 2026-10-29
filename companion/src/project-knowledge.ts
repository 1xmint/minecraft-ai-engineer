import { actorId } from "./gateway-types.js";
import { integer, object, RequestError, text } from "./types.js";
export const PROJECT_NOTE_KINDS = Object.freeze([
  "goal",
  "constraint",
  "preference",
  "decision",
] as const);
export interface ProjectNoteInput {
  readonly schemaVersion: 1;
  readonly kind: (typeof PROJECT_NOTE_KINDS)[number];
  readonly text: string;
}
export interface ProjectNote extends ProjectNoteInput {
  readonly id: string;
  readonly revision: number;
  readonly authorId: string;
  readonly createdAt: number;
  readonly source: "owner-authored";
}
export interface ProjectKnowledge {
  readonly schemaVersion: 1;
  readonly revision: number;
  readonly notes: readonly ProjectNote[];
  readonly hasEarlier: boolean;
}
function exact(value: Record<string, unknown>, keys: readonly string[]) {
  if (
    Object.keys(value).length !== keys.length ||
    Object.keys(value).some((key) => !keys.includes(key))
  )
    throw new RequestError("Invalid project-note fields.");
}
function content(value: Record<string, unknown>): ProjectNoteInput {
  if (
    value.schemaVersion !== 1 ||
    !PROJECT_NOTE_KINDS.some((kind) => kind === value.kind)
  )
    throw new RequestError("Unsupported project-note kind or version.");
  return {
    schemaVersion: 1,
    kind: value.kind as ProjectNoteInput["kind"],
    text: text(value.text, "literal project note", 512),
  };
}
/** Human text is retained verbatim and labelled as a note, never observed/verified state. */
export function validateProjectNote(value: unknown): ProjectNoteInput {
  const v = object(value);
  exact(v, ["schemaVersion", "kind", "text"]);
  return Object.freeze(content(v));
}
export function readProjectNote(value: unknown): ProjectNote {
  const v = object(value);
  exact(v, [
    "schemaVersion",
    "kind",
    "text",
    "id",
    "revision",
    "authorId",
    "createdAt",
    "source",
  ]);
  if (v.source !== "owner-authored")
    throw new RequestError("Unknown project-note source.");
  return Object.freeze({
    ...content(v),
    id: actorId(v.id),
    revision: integer(v.revision, "note revision", 1, Number.MAX_SAFE_INTEGER),
    authorId: actorId(v.authorId),
    createdAt: integer(
      v.createdAt,
      "note creation time",
      0,
      Number.MAX_SAFE_INTEGER,
    ),
    source: "owner-authored",
  });
}
