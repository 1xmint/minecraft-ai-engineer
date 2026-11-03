import { actorId } from "./gateway-types.js";
import { integer, object, RequestError, text } from "./types.js";
export const PROJECT_NOTE_KINDS = Object.freeze([
  "goal",
  "constraint",
  "preference",
  "decision",
] as const);
export type ProjectNoteKind = (typeof PROJECT_NOTE_KINDS)[number];
/** Version 1 is frozen: stored rows and planning text keep exactly these keys. */
export interface ProjectNoteInputV1 {
  readonly schemaVersion: 1;
  readonly kind: ProjectNoteKind;
  readonly text: string;
}
/** Optional keys are absent, never null: the mod's JSON writer drops nulls. */
export interface ProjectNoteInputV2 {
  readonly schemaVersion: 2;
  readonly kind: ProjectNoteKind;
  readonly text: string;
  readonly rationale?: string;
  readonly supersedes?: number;
}
export type ProjectNoteInput = ProjectNoteInputV1 | ProjectNoteInputV2;
interface NoteProvenance {
  readonly id: string;
  readonly revision: number;
  readonly authorId: string;
  readonly createdAt: number;
  readonly source: "owner-authored";
}
export type ProjectNote =
  | (ProjectNoteInputV1 & NoteProvenance)
  | (ProjectNoteInputV2 &
      NoteProvenance & {
        /** Knowledge revision the owner saw when writing this note. */
        readonly writtenAtRevision: number;
      });
export interface ProjectKnowledge {
  readonly schemaVersion: 1;
  readonly revision: number;
  readonly notes: readonly ProjectNote[];
  readonly hasEarlier: boolean;
}
const BASE = ["schemaVersion", "kind", "text"] as const;
const OPTIONAL = ["rationale", "supersedes"] as const;
const PROVENANCE = [
  "id",
  "revision",
  "authorId",
  "createdAt",
  "source",
] as const;
function exact(
  value: Record<string, unknown>,
  required: readonly string[],
  optional: readonly string[] = [],
) {
  if (
    required.some((key) => !(key in value)) ||
    Object.keys(value).some(
      (key) => !required.includes(key) && !optional.includes(key),
    )
  )
    throw new RequestError("Invalid project-note fields.");
}
function content(value: Record<string, unknown>): ProjectNoteInput {
  if (
    (value.schemaVersion !== 1 && value.schemaVersion !== 2) ||
    !PROJECT_NOTE_KINDS.some((kind) => kind === value.kind)
  )
    throw new RequestError("Unsupported project-note kind or version.");
  const kind = value.kind as ProjectNoteKind;
  const literal = text(value.text, "literal project note", 512);
  if (value.schemaVersion === 1)
    return { schemaVersion: 1, kind, text: literal };
  return {
    schemaVersion: 2,
    kind,
    text: literal,
    ...("rationale" in value
      ? { rationale: text(value.rationale, "note reason", 512) }
      : {}),
    ...("supersedes" in value
      ? {
          supersedes: integer(
            value.supersedes,
            "replaced note",
            1,
            Number.MAX_SAFE_INTEGER,
          ),
        }
      : {}),
  };
}
/** Human text is retained verbatim and labelled as a note, never observed/verified state. */
export function validateProjectNote(value: unknown): ProjectNoteInput {
  const v = object(value);
  exact(v, BASE, v.schemaVersion === 2 ? OPTIONAL : []);
  return Object.freeze(content(v));
}
export function readProjectNote(value: unknown): ProjectNote {
  const v = object(value);
  const v2 = v.schemaVersion === 2;
  exact(
    v,
    v2
      ? [...BASE, ...PROVENANCE, "writtenAtRevision"]
      : [...BASE, ...PROVENANCE],
    v2 ? OPTIONAL : [],
  );
  if (v.source !== "owner-authored")
    throw new RequestError("Unknown project-note source.");
  return Object.freeze({
    ...content(v),
    ...(v2
      ? {
          writtenAtRevision: integer(
            v.writtenAtRevision,
            "note written-at revision",
            0,
            Number.MAX_SAFE_INTEGER,
          ),
        }
      : {}),
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
  }) as ProjectNote;
}
