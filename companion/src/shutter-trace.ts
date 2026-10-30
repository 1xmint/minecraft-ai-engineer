import { createHash } from "node:crypto";
import { isDeepStrictEqual } from "node:util";
import { rotateBlockState } from "./geometry.js";
import { readShutterDesign, shutterFrame } from "./shutter-design.js";
import { object, integer, text, RequestError } from "./types.js";

const cells = ["input", "base", "front", "aperture"] as const;
type Cell = (typeof cells)[number];
type Entity = "none" | "moving-piston" | "other";
type Values<T> = Record<Cell, T>;
const exact = (v: Record<string, unknown>, keys: readonly string[]) => {
  if (Object.keys(v).length !== keys.length || keys.some((key) => !(key in v)))
    throw new RequestError("Unknown or missing mechanical observation fields.");
};
const state = (value: unknown) => {
  const s = text(value, "observed block state", 512);
  if (
    !/^minecraft:[a-z0-9_]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?$/u.test(
      s,
    ) ||
    rotateBlockState(s, 0) !== s
  )
    throw new RequestError("Invalid canonical mechanical block state.");
  if (s.includes("[")) {
    const keys = s
      .slice(s.indexOf("[") + 1, -1)
      .split(",")
      .map((pair) => pair.split("=")[0]);
    if (new Set(keys).size !== keys.length)
      throw new RequestError("Duplicate observed state property.");
  }
  return s;
};
const air = (value: string) =>
  ["minecraft:air", "minecraft:cave_air", "minecraft:void_air"].includes(value);
const same = (a: string, b: string) => a === b || (air(a) && air(b));
export function shutterExpectedPose(
  value: unknown,
  closed: boolean,
): Values<string> {
  const design = readShutterDesign(value),
    facing = shutterFrame(design).facing;
  return {
    input: `minecraft:lever[face=floor,facing=${facing},powered=${closed}]`,
    base: `minecraft:sticky_piston[extended=${closed},facing=${facing}]`,
    front: closed
      ? `minecraft:piston_head[facing=${facing},short=false,type=sticky]`
      : "minecraft:stone",
    aperture: closed ? "minecraft:stone" : "minecraft:air",
  };
}
export function shutterTraceDigest(value: unknown, closed: boolean): string {
  const d = readShutterDesign(value),
    frame = shutterFrame(d),
    p = frame.piston;
  return createHash("sha256")
    .update(
      `shutter-pose-v1|26.3|1|${d.worldId}|${d.dimension}|${d.projectId}|${d.id}|${d.revision}|${p.x},${p.y},${p.z}|${frame.facing}|${closed ? "closed" : "open"}|8|8`,
    )
    .digest("hex");
}
/** Recomputes physical samples against accepted intention; no observations are supplied by the model. */
export function validateShutterTrace(
  value: unknown,
  intention: unknown,
  closed: boolean,
  requirePass = false,
) {
  if (typeof closed !== "boolean")
    throw new RequestError("Invalid accepted mechanical phase.");
  const v = object(value),
    design = readShutterDesign(intention),
    facing = shutterFrame(design).facing;
  if (
    JSON.stringify(v).length > 131072 ||
    !Array.isArray(v.samples) ||
    v.samples.length > 17
  )
    throw new RequestError("Mechanical trace exceeds its bounded horizon.");
  const open = shutterExpectedPose(design, false),
    shut = shutterExpectedPose(design, true),
    expected = closed ? shut : open;
  let last = -1,
    missing: string | undefined,
    mismatch: string | undefined;
  const samples = v.samples.map((entry) => {
    const row = object(entry);
    exact(row, ["elapsedTicks", "states", "entities", "moved"]);
    const elapsed = integer(row.elapsedTicks, "elapsedTicks", 0, 16);
    if (elapsed <= last)
      throw new RequestError("Mechanical sample ticks must advance.");
    if (elapsed !== last + 1 && missing === undefined)
      missing = `missing game-tick sample after ${last}, next observed ${elapsed}`;
    const rawStates = object(row.states),
      rawEntities = object(row.entities),
      rawMoved = object(row.moved);
    for (const source of [rawStates, rawEntities, rawMoved])
      exact(source, cells);
    const states = {} as Values<string>,
      entities = {} as Values<Entity>,
      moved = {} as Values<string>;
    for (const cell of cells) {
      states[cell] = state(rawStates[cell]);
      const entity = rawEntities[cell];
      if (entity !== "none" && entity !== "moving-piston" && entity !== "other")
        throw new RequestError("Unknown mechanical entity observation.");
      entities[cell] = entity;
      moved[cell] = rawMoved[cell] === "none" ? "none" : state(rawMoved[cell]);
      const ordinary =
        entity === "none" &&
        moved[cell] === "none" &&
        (same(states[cell], open[cell]) || same(states[cell], shut[cell]));
      const animation =
        cell !== "input" &&
        entity === "moving-piston" &&
        ["normal", "sticky"].some(
          (type) =>
            states[cell] ===
            `minecraft:moving_piston[facing=${facing},type=${type}]`,
        ) &&
        moved[cell] !== "none" &&
        !air(moved[cell]) &&
        (same(moved[cell], open[cell]) || same(moved[cell], shut[cell]));
      if (
        mismatch === undefined &&
        ((!ordinary && !animation) ||
          (cell === "input" && states[cell] !== expected[cell]))
      )
        mismatch = `unexpected ${cell} state or entity at elapsed game tick ${elapsed}`;
      if (
        mismatch === undefined &&
        elapsed >= 8 &&
        (entity !== "none" ||
          moved[cell] !== "none" ||
          !same(states[cell], expected[cell]))
      )
        mismatch = `unsettled or incorrect ${cell} at elapsed game tick ${elapsed}`;
    }
    last = elapsed;
    return { elapsedTicks: elapsed, states, entities, moved };
  });
  const complete = last === 16;
  const verdict =
    missing !== undefined
      ? "inconclusive"
      : mismatch !== undefined
        ? "failed"
        : complete
          ? "passed"
          : "running";
  const canonical = {
    schemaVersion: 2,
    intentionDigest: shutterTraceDigest(design, closed),
    expected: closed ? "closed" : "open",
    settleGameTicks: 8,
    stableGameTicks: 8,
    samples,
    verdict,
    failure:
      missing !== undefined ? `inconclusive: ${missing}` : (mismatch ?? ""),
  };
  if (!isDeepStrictEqual(v, canonical) || (requirePass && verdict !== "passed"))
    throw new RequestError(
      "Mechanical trace differs from accepted intention or recomputed observations.",
    );
  return canonical;
}

/** Phase order, game-clock continuity, raw summaries and original-state restoration are independently required. */
export function validateShutterEvidence(
  value: unknown,
  intention: unknown,
  originalInput: string,
  requirePass = false,
) {
  const design = readShutterDesign(intention);
  const off = shutterExpectedPose(design, false).input,
    on = shutterExpectedPose(design, true).input;
  if (originalInput !== off && originalInput !== on)
    throw new RequestError(
      "Original mechanical input differs from accepted geometry.",
    );
  const count = originalInput === on ? 4 : 3;
  if (
    !Array.isArray(value) ||
    value.length > count ||
    (requirePass && value.length !== count)
  )
    throw new RequestError(
      "Mechanical evidence needs every phase and original-state restoration.",
    );
  let previous = -1;
  return value.map((entry, index) => {
    const row = object(entry);
    exact(row, [
      "case",
      "purpose",
      "tick",
      "elapsedTicks",
      "inputs",
      "outputs",
      "trace",
    ]);
    if (
      row.case !== index ||
      row.purpose !== (index < 3 ? "test" : "restore-original")
    )
      throw new RequestError("Mechanical case differs from accepted sequence.");
    const expected = index === 1 || index === 3;
    const trace = validateShutterTrace(
      row.trace,
      design,
      expected,
      requirePass || index + 1 < value.length,
    );
    const last = trace.samples.at(-1);
    if (!last) throw new RequestError("Mechanical phase has no samples.");
    const tick = integer(
        row.tick,
        "mechanical tick",
        0,
        Number.MAX_SAFE_INTEGER,
      ),
      elapsed = integer(row.elapsedTicks, "elapsedTicks", 0, 16);
    if (
      tick < elapsed ||
      elapsed !== last.elapsedTicks ||
      (previous >= 0 && tick - elapsed !== previous)
    )
      throw new RequestError("Mechanical case timestamps disagree.");
    const inputs = object(row.inputs),
      outputs = object(row.outputs);
    exact(inputs, ["input"]);
    exact(outputs, ["aperture"]);
    if (
      inputs.input !== expected ||
      outputs.aperture !== (last.states.aperture === "minecraft:stone")
    )
      throw new RequestError("Mechanical summary differs from raw samples.");
    previous = tick;
    return {
      case: index,
      purpose: row.purpose,
      tick,
      elapsedTicks: elapsed,
      inputs: { input: expected },
      outputs: { aperture: outputs.aperture },
      trace,
    };
  });
}
