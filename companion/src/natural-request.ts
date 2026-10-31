import { object, text, RequestError } from "./types.js";

export const NATURAL_ACTIONS = [
  "status",
  "explain",
  "timing",
  "materials",
  "teach",
  "next",
  "repeat",
  "pause_lesson",
  "diagnose",
  "build",
  "resume",
  "test",
  "repair",
  "undo",
  "stop",
] as const;
export type NaturalAction = (typeof NATURAL_ACTIONS)[number];

/** Trusted advertised choices, not grants. Dispatch must recheck live authority. */
export type NaturalCapabilities = ReadonlyMap<
  NaturalAction,
  ReadonlySet<string | null>
>;
export interface NaturalReply {
  readonly schemaVersion: 1;
  readonly kind: "action" | "clarification" | "unsupported";
  readonly action: NaturalAction | null;
  readonly componentId: string | null;
  readonly text: string;
  readonly basis: readonly string[];
}

export const NATURAL_REPLY_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["schemaVersion", "kind", "action", "componentId", "text", "basis"],
  properties: {
    schemaVersion: { type: "integer", const: 1 },
    kind: { type: "string", enum: ["action", "clarification", "unsupported"] },
    action: {
      anyOf: [{ type: "string", enum: NATURAL_ACTIONS }, { type: "null" }],
    },
    componentId: {
      anyOf: [
        { type: "string", pattern: "^[a-z][a-z0-9_-]{0,31}$" },
        { type: "null" },
      ],
    },
    text: { type: "string", minLength: 1, maxLength: 800 },
    basis: {
      type: "array",
      maxItems: 12,
      items: { type: "string", minLength: 1, maxLength: 128 },
    },
  },
} as const;

/** Model prose and references never become commands, consent, or world evidence. */
export function validateNaturalReply(
  value: unknown,
  capabilities: NaturalCapabilities,
  availableBasis: ReadonlySet<string>,
): NaturalReply {
  const v = object(value);
  const keys = [
    "schemaVersion",
    "kind",
    "action",
    "componentId",
    "text",
    "basis",
  ];
  if (
    Object.keys(v).length !== keys.length ||
    Object.keys(v).some((key) => !keys.includes(key)) ||
    v.schemaVersion !== 1 ||
    !["action", "clarification", "unsupported"].includes(String(v.kind))
  )
    throw new RequestError("Invalid natural request reply.");
  if (!Array.isArray(v.basis) || v.basis.length > 12)
    throw new RequestError("Invalid natural request references.");
  const basis = v.basis.map((id) => text(id, "natural request reference", 128));
  if (
    new Set(basis).size !== basis.length ||
    basis.some((id) => !availableBasis.has(id))
  )
    throw new RequestError(
      "Natural request cited unavailable project knowledge.",
    );
  if (v.kind === "action") {
    if (
      typeof v.action !== "string" ||
      !NATURAL_ACTIONS.includes(v.action as NaturalAction) ||
      (v.componentId !== null &&
        (typeof v.componentId !== "string" ||
          !/^[a-z][a-z0-9_-]{0,31}$/u.test(v.componentId))) ||
      !capabilities
        .get(v.action as NaturalAction)
        ?.has(v.componentId as string | null) ||
      basis.length === 0
    )
      throw new RequestError(
        "Natural request selected an unavailable action or target.",
      );
  } else if (v.action !== null || v.componentId !== null) {
    throw new RequestError(
      "A clarification cannot select an action or target.",
    );
  }
  return Object.freeze({
    schemaVersion: 1,
    kind: v.kind as NaturalReply["kind"],
    action: v.action as NaturalAction | null,
    componentId: v.componentId as string | null,
    text: text(v.text, "natural request response", 800),
    basis: Object.freeze(basis),
  });
}
