import type { Blueprint } from "./types.js";
import type { ProjectKnowledge } from "./project-knowledge.js";
import { readShutterDesign } from "./shutter-design.js";
import { object, text, RequestError } from "./types.js";

export interface DiscussionRequest {
  readonly message: string;
  readonly componentId?: string;
}
export interface DiscussionReply {
  readonly schemaVersion: 1;
  readonly kind: "explanation" | "clarification" | "unsupported";
  readonly text: string;
  readonly basis: readonly string[];
}
export const DISCUSSION_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["schemaVersion", "kind", "text", "basis"],
  properties: {
    schemaVersion: { type: "integer", const: 1 },
    kind: {
      type: "string",
      enum: ["explanation", "clarification", "unsupported"],
    },
    text: { type: "string", minLength: 1, maxLength: 2000 },
    basis: {
      type: "array",
      maxItems: 12,
      items: { type: "string", maxLength: 128 },
    },
  },
} as const;
export function validateDiscussionReply(
  value: unknown,
  availableBasis?: ReadonlySet<string>,
): DiscussionReply {
  const v = object(value);
  if (
    Object.keys(v).length !== 4 ||
    Object.keys(v).some(
      (key) => !["schemaVersion", "kind", "text", "basis"].includes(key),
    ) ||
    v.schemaVersion !== 1 ||
    typeof v.kind !== "string" ||
    !["explanation", "clarification", "unsupported"].includes(String(v.kind))
  )
    throw new RequestError("Invalid read-only discussion reply.");
  if (!Array.isArray(v.basis) || v.basis.length > 12)
    throw new RequestError("Invalid discussion references.");
  const basis = v.basis.map((id) => text(id, "discussion reference", 128));
  if (
    new Set(basis).size !== basis.length ||
    (availableBasis && basis.some((id) => !availableBasis.has(id)))
  )
    throw new RequestError("Discussion cited unavailable project knowledge.");
  return Object.freeze({
    schemaVersion: 1,
    kind: v.kind as DiscussionReply["kind"],
    text: text(v.text, "discussion answer", 2000),
    basis: Object.freeze(basis),
  });
}

export const DISCUSSION_NOTE_LIMIT = 8;
// Question words that say nothing about which note is meant. Short circuit terms such as
// "and", "xor", "led" and "hub" stay meaningful.
const IGNORED = new Set(
  (
    "the for you are was why how did does what this that with have has can our its not but " +
    "from into about when where which who will would should could there their they them then " +
    "than these those your use used make made need just also some more most very much like " +
    "want tell show explain note notes project engineer part doing done say said do is it we"
  ).split(" "),
);
/** Literal words of three or more letters; a plural "s" is folded so "lamps" matches "lamp". */
export function noteWords(value: string): ReadonlySet<string> {
  const found = new Set<string>();
  for (const raw of value.toLowerCase().match(/[a-z0-9]+/g) ?? []) {
    if (raw.length < 3 || IGNORED.has(raw)) continue;
    found.add(
      raw.length > 4 && raw.endsWith("s") && !raw.endsWith("ss")
        ? raw.slice(0, -1)
        : raw,
    );
  }
  return found;
}
/**
 * Current notes only: a note another note replaced is history, never a current requirement.
 * When more than the limit are current, notes sharing more literal words with the question or the
 * selected component's id come first (ties: newest first), then the newest of the rest; the chosen
 * notes keep their stored order. Selection only orders what is offered; it never changes a note.
 */
export function currentNotes(
  knowledge: ProjectKnowledge,
  message?: string,
  componentId?: string,
) {
  const replaced = new Set(
    knowledge.notes.flatMap((note) =>
      note.schemaVersion === 2 && note.supersedes !== undefined
        ? [note.supersedes]
        : [],
    ),
  );
  const current = knowledge.notes.filter(
    (note) => !replaced.has(note.revision),
  );
  if (current.length <= DISCUSSION_NOTE_LIMIT)
    return {
      notes: current,
      replaced: knowledge.notes.length - current.length,
      omitted: false,
    };
  const asked = noteWords(`${message ?? ""} ${componentId ?? ""}`);
  const shared = (note: (typeof current)[number]) =>
    [
      ...noteWords(
        `${note.text} ${note.schemaVersion === 2 ? (note.rationale ?? "") : ""}`,
      ),
    ].filter((word) => asked.has(word)).length;
  const newestFirst = [...current].reverse();
  const scored = newestFirst
    .map((note, order) => ({ note, order, score: shared(note) }))
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score || a.order - b.order)
    .map((entry) => entry.note);
  const kept = new Set(
    [...new Set([...scored, ...newestFirst])].slice(0, DISCUSSION_NOTE_LIMIT),
  );
  return {
    notes: current.filter((note) => kept.has(note)),
    replaced: knowledge.notes.length - current.length,
    omitted: true,
  };
}

/** Only supplied intention and literal shared notes. Never a private inventory or a fresh observation. */
export function discussionGrounding(
  name: string,
  revision: number,
  blueprint: Blueprint | undefined,
  knowledge: ProjectKnowledge,
  componentId?: string,
  message?: string,
) {
  const notes = currentNotes(knowledge, message, componentId);
  // Fabric exposes one logical root for standalone mechanisms; block-group labels
  // inside a catalog recipe are not separately selectable project components.
  const ids = blueprint
    ? (blueprint.components?.nodes.map((node) => node.id) ?? ["root"])
    : [];
  if (componentId !== undefined && !ids.includes(componentId))
    throw new RequestError(
      "Selected discussion component is unavailable.",
      409,
    );
  const sources: { id: string; kind: string; data: unknown }[] = [
    {
      id: "project",
      kind: "intended-design",
      data: {
        name,
        revision,
        design: blueprint
          ? {
              name: blueprint.name,
              explanation: blueprint.explanation.slice(0, 2000),
              behavior: blueprint.behavior ?? null,
              componentIds: ids.slice(0, 32),
              hasMoreComponents: ids.length > 32,
              provenance: blueprint.provenance,
              ...(blueprint.provenance.kind === "curated" &&
              blueprint.provenance.template === "sticky-shutter"
                ? { mechanical: readShutterDesign(blueprint).mechanical }
                : {}),
            }
          : null,
      },
    },
  ];
  for (const note of notes.notes)
    sources.push({
      id: `note:${note.id}`,
      kind: "owner-authored-requirement",
      data: note,
    });
  if (blueprint && componentId !== undefined) {
    const blocks = blueprint.blocks.filter(
      (block) => !blueprint.components || block.component === componentId,
    );
    sources.push({
      id: `component:${componentId}`,
      kind: "intended-component",
      data: {
        id: componentId,
        definition:
          blueprint.components?.nodes.find((node) => node.id === componentId) ??
          null,
        blocks: blocks.slice(0, 64),
        hasMoreBlocks: blocks.length > 64,
        ports: blueprint.ports
          .filter(
            (port) =>
              !blueprint.components ||
              blocks.some(
                (block) =>
                  block.pos.x === port.pos.x &&
                  block.pos.y === port.pos.y &&
                  block.pos.z === port.pos.z,
              ),
          )
          .slice(0, 16),
      },
    });
  }
  return Object.freeze({
    knowledgeRevision: knowledge.revision,
    availableBasis: new Set(sources.map((source) => source.id)),
    text: JSON.stringify({
      schemaVersion: 1,
      projectRevision: revision,
      knowledgeRevision: knowledge.revision,
      selectedComponent: componentId ?? null,
      worldEvidence: "not-inspected",
      earlierNotesOmitted: knowledge.hasEarlier || notes.omitted,
      // Absent when nothing was replaced, so recorded contexts stay byte-identical.
      ...(notes.replaced
        ? {
            replacedNotesOmitted: notes.replaced,
            replacedNotes: "history only; not current requirements",
          }
        : {}),
      sources,
    }),
  });
}
