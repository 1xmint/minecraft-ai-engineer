import type { Blueprint } from "./types.js";
import type { ProjectKnowledge } from "./project-knowledge.js";
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

/** Only supplied intention and literal shared notes. Never a private inventory or a fresh observation. */
export function discussionGrounding(
  name: string,
  revision: number,
  blueprint: Blueprint | undefined,
  knowledge: ProjectKnowledge,
  componentId?: string,
) {
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
            }
          : null,
      },
    },
  ];
  for (const note of knowledge.notes.slice(-8))
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
          .filter((port) =>
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
      earlierNotesOmitted: knowledge.hasEarlier || knowledge.notes.length > 8,
      sources,
    }),
  });
}
