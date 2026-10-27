import raw from "../../catalog/mechanisms.json" with { type: "json" };
import { randomUUID } from "node:crypto";
import { rotatePosition } from "./geometry.js";
import {
  type Blueprint,
  type DesignRequest,
  type Plan,
  type Position,
  type Rotation,
  RequestError,
  identifier,
  integer,
  object,
  position,
  text,
} from "./types.js";

export interface Mechanism {
  id: Exclude<Plan["template"], "unsupported">;
  name: string;
  explanation: string;
  maxDelay: number;
  settleTicks: number;
  blocks: Omit<Blueprint["blocks"][number], "prerequisites">[];
  stages: Required<Blueprint["stages"][number]>[];
  ports: Blueprint["ports"];
  cases: {
    inputs: Record<string, boolean>;
    outputs: Record<string, boolean>;
  }[];
}
const ids = new Set(["signal-lamp", "not-gate", "or-gate"]);
function array(value: unknown, name: string, max: number): unknown[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > max)
    throw new RequestError(`Invalid catalog ${name}.`);
  return value;
}
function booleanMap(value: unknown, names: string[]): Record<string, boolean> {
  const v = object(value);
  if (
    Object.keys(v).length !== names.length ||
    names.some((name) => typeof v[name] !== "boolean")
  )
    throw new RequestError(
      "Catalog case must specify every port exactly once.",
    );
  return Object.fromEntries(names.map((name) => [name, v[name] as boolean]));
}
function readMechanism(value: unknown): Mechanism {
  const v = object(value),
    id = identifier(v.id, "mechanism");
  if (!ids.has(id)) throw new RequestError("Unknown catalog mechanism.");
  const stages: Mechanism["stages"] = [],
    seenStages = new Set<string>();
  for (const item of array(v.stages, "stages", 64)) {
    const s = object(item),
      name = identifier(s.id, "stage"),
      requires = s.prerequisites;
    if (
      seenStages.has(name) ||
      !Array.isArray(requires) ||
      requires.some((p) => typeof p !== "string" || !seenStages.has(p)) ||
      new Set(requires).size !== requires.length
    )
      throw new RequestError(
        "Catalog prerequisites must be unique earlier stages.",
      );
    stages.push({
      id: name,
      title: text(s.title, "stage title", 256),
      explanation: text(s.explanation, "stage explanation", 4000),
      prerequisites: requires as string[],
    });
    seenStages.add(name);
  }
  const positions = new Set<string>();
  const blocks: Mechanism["blocks"] = array(v.blocks, "blocks", 4096).map(
    (item) => {
      const b = object(item),
        pos = position(b.pos),
        stage = identifier(b.stage, "stage"),
        state = text(b.state, "block state", 512),
        key = JSON.stringify(pos);
      if (
        positions.has(key) ||
        Math.max(Math.abs(pos.x), Math.abs(pos.y), Math.abs(pos.z)) > 64 ||
        !seenStages.has(stage) ||
        !/^minecraft:(?:stone|lever|repeater|redstone_lamp|redstone_wall_torch)(?:\[[a-z0-9_=,]+\])?$/u.test(
          state,
        )
      )
        throw new RequestError("Invalid catalog block geometry/state.");
      positions.add(key);
      return {
        pos,
        state,
        stage,
        component: identifier(b.component, "component"),
      };
    },
  );
  const names = new Set<string>();
  const ports: Mechanism["ports"] = array(v.ports, "ports", 32).map((item) => {
    const p = object(item),
      name = identifier(p.name, "port"),
      pos = position(p.pos),
      block = blocks.find((b) => JSON.stringify(b.pos) === JSON.stringify(pos));
    if (
      names.has(name) ||
      !block ||
      !["input", "output"].includes(String(p.kind)) ||
      (p.kind === "input"
        ? !block.state.startsWith("minecraft:lever[")
        : !block.state.startsWith("minecraft:redstone_lamp["))
    )
      throw new RequestError("Invalid catalog port.");
    names.add(name);
    return { name, pos, kind: p.kind as "input" | "output" };
  });
  const inputs = ports.filter((p) => p.kind === "input").map((p) => p.name),
    outputs = ports.filter((p) => p.kind === "output").map((p) => p.name);
  if (!inputs.length || !outputs.length)
    throw new RequestError("Catalog needs input and output ports.");
  const cases = array(v.cases, "cases", 128).map((item) => {
    const c = object(item);
    return {
      inputs: booleanMap(c.inputs, inputs),
      outputs: booleanMap(c.outputs, outputs),
    };
  });
  return {
    id: id as Mechanism["id"],
    name: text(v.name, "name", 256),
    explanation: text(v.explanation, "explanation", 4000),
    maxDelay: integer(v.maxDelay, "maxDelay", 1, 4),
    settleTicks: integer(v.settleTicks, "settleTicks", 8, 200),
    stages,
    blocks,
    ports,
    cases,
  };
}
if (raw.schemaVersion !== 1 || raw.minecraft !== "26.3")
  throw new RequestError("Unsupported mechanism catalog.");
export const CATALOG_REVISION = integer(
  raw.revision,
  "catalogRevision",
  1,
  Number.MAX_SAFE_INTEGER,
);
function immutable<T>(value: T): T {
  if (value !== null && typeof value === "object") {
    for (const child of Object.values(value)) immutable(child);
    Object.freeze(value);
  }
  return value;
}
export const MECHANISMS: ReadonlyArray<Mechanism> = immutable(
  array(raw.mechanisms, "mechanisms", 64).map(readMechanism),
);
if (new Set(MECHANISMS.map((m) => m.id)).size !== MECHANISMS.length)
  throw new RequestError("Duplicate catalog identity.");
export function mechanism(id: string): Mechanism {
  const found = MECHANISMS.find((m) => m.id === id);
  if (!found) throw new RequestError("Unsupported mechanism.", 422);
  return found;
}
export function adaptation(
  template: string,
  value: unknown,
): { delay: number } {
  const m = mechanism(template),
    v = object(value);
  if (Object.keys(v).some((key) => key !== "delay"))
    throw new RequestError("Unknown mechanism adaptation.");
  return { delay: integer(v.delay, "delay", 1, m.maxDelay) };
}
function bounds(blocks: { pos: Position }[]) {
  return {
    min: {
      x: Math.min(...blocks.map((b) => b.pos.x)),
      y: Math.min(...blocks.map((b) => b.pos.y)),
      z: Math.min(...blocks.map((b) => b.pos.z)),
    },
    max: {
      x: Math.max(...blocks.map((b) => b.pos.x)),
      y: Math.max(...blocks.map((b) => b.pos.y)),
      z: Math.max(...blocks.map((b) => b.pos.z)),
    },
  };
}
export function makeMechanismBlueprint(
  input: DesignRequest,
  plan: Plan,
): Blueprint {
  if (plan.template === "unsupported")
    throw new RequestError(plan.explanation, 422);
  const m = mechanism(plan.template),
    delay = adaptation(m.id, plan.parameters ?? { delay: 1 }).delay,
    rotation = plan.rotation ?? input.rotation;
  if (
    input.revision &&
    (m.id !== input.revision.template || rotation !== input.revision.rotation)
  )
    throw new RequestError(
      "A revision must keep the current mechanism and rotation.",
      422,
    );
  if (![0, 90, 180, 270].includes(rotation))
    throw new RequestError("Unsupported mechanism rotation.");
  const blocks = m.blocks.map((b) => ({
    ...b,
    pos: { ...b.pos },
    state: b.state.replace(/delay=\d/u, `delay=${delay}`),
    prerequisites: [...m.stages.find((s) => s.id === b.stage)!.prerequisites],
  }));
  const rotated = bounds(
      blocks.map((b) => ({ pos: rotatePosition(b.pos, rotation) })),
    ),
    size = {
      x: rotated.max.x - rotated.min.x + 1,
      y: rotated.max.y - rotated.min.y + 1,
      z: rotated.max.z - rotated.min.z + 1,
    };
  if (
    input.maxSize &&
    (size.x > input.maxSize.x ||
      size.y > input.maxSize.y ||
      size.z > input.maxSize.z)
  )
    throw new RequestError(
      `The ${m.name} does not fit the selected dimensions.`,
      422,
    );
  const anchor = position({
    x: input.anchor.x - rotated.min.x,
    y: input.anchor.y - rotated.min.y,
    z: input.anchor.z - rotated.min.z,
  });
  for (const block of blocks) {
    const p = rotatePosition(block.pos, rotation);
    const absolute = position({
      x: anchor.x + p.x,
      y: anchor.y + p.y,
      z: anchor.z + p.z,
    });
    if (absolute.y > 2047)
      throw new RequestError("Mechanism exceeds supported world bounds.");
  }
  const resources: Record<string, number> = {};
  for (const b of blocks) {
    const block = b.state.split("[", 1)[0]!,
      item =
        block === "minecraft:redstone_wall_torch"
          ? "minecraft:redstone_torch"
          : block;
    resources[item] = (resources[item] ?? 0) + 1;
  }
  return {
    schemaVersion: 1,
    id: randomUUID(),
    revision: 1,
    projectId: input.projectId ?? randomUUID(),
    worldId: input.worldId,
    dimension: input.dimension,
    name: m.name,
    explanation: plan.explanation,
    anchor,
    rotation,
    blocks,
    stages: m.stages.map((s) => ({
      ...s,
      prerequisites: [...s.prerequisites],
    })),
    bounds: bounds(blocks),
    resources,
    ports: m.ports.map((p) => ({ ...p, pos: { ...p.pos } })),
    tests: [
      {
        id: m.id === "signal-lamp" ? "off-on-off" : "truth-table",
        description: `Test all ${m.cases.length} catalog input vectors against actual Minecraft outputs; restore every original input.`,
      },
    ],
    provenance: {
      kind: "curated",
      template: m.id,
      minecraft: "26.3",
      catalogRevision: CATALOG_REVISION,
    },
    parameters: { delay },
    verification: "untested",
  };
}
export function fittingMechanisms(
  input: DesignRequest,
): { template: Mechanism["id"]; rotation: Rotation; maxDelay: number }[] {
  const fits: ReturnType<typeof fittingMechanisms> = [];
  const candidates = input.revision
    ? [
        {
          template: input.revision.template,
          rotation: input.revision.rotation,
        },
      ]
    : MECHANISMS.flatMap((m) =>
        ([0, 90, 180, 270] as const).map((rotation) => ({
          template: m.id,
          rotation,
        })),
      );
  for (const candidate of candidates) {
    const m = mechanism(candidate.template),
      rotation = candidate.rotation;
    try {
      makeMechanismBlueprint(input, {
        template: m.id,
        explanation: m.explanation,
        rotation,
      });
      fits.push({ template: m.id, rotation, maxDelay: m.maxDelay });
    } catch (error) {
      if (!(error instanceof RequestError) || error.status !== 422) throw error;
    }
  }
  if (!fits.length)
    throw new RequestError(
      "No supported mechanism fits the selected dimensions.",
      422,
    );
  return fits;
}
