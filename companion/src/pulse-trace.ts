import { isDeepStrictEqual } from "node:util";
import { object, integer, RequestError } from "./types.js";

export const OBSERVER_TIMING = Object.freeze({
  schemaVersion: 1,
  delayGameTicks: 2,
  widthGameTicks: 2,
  lampReleaseGameTicks: 4,
  horizonGameTicks: 16,
});
const fields = (value: Record<string, unknown>, keys: string[]) => {
  if (!isDeepStrictEqual(Object.keys(value).sort(), [...keys].sort()))
    throw new RequestError("Invalid observer evidence fields.");
};
function bool(value: unknown): boolean {
  if (typeof value !== "boolean")
    throw new RequestError("Expected observer evidence boolean.");
  return value;
}
function sample(value: unknown) {
  const v = object(value);
  fields(v, ["elapsedTicks", "input", "observer", "receiver", "lamp"]);
  return {
    elapsedTicks: integer(v.elapsedTicks, "elapsedTicks", 0, 16),
    input: bool(v.input),
    observer: bool(v.observer),
    receiver: bool(v.receiver),
    lamp: bool(v.lamp),
  };
}
/** Replays observations, never a permission, execution instruction or fresh verification. */
export function validatePulseTrace(value: unknown, requirePass = false) {
  const v = object(value),
    from = bool(v.from);
  if (!Array.isArray(v.samples) || v.samples.length > 17)
    throw new RequestError("Invalid bounded pulse samples.");
  let last = -1,
    missing: string | undefined,
    mismatch: string | undefined;
  const samples = v.samples.map((raw) => {
    const row = sample(raw),
      elapsed = row.elapsedTicks;
    if (elapsed <= last)
      throw new RequestError(
        "Pulse samples must advance within the bounded window.",
      );
    if (elapsed !== last + 1 && missing === undefined)
      missing = `missing game-tick sample after ${last}, next observed ${elapsed}`;
    const pulse = elapsed >= 2 && elapsed < 4,
      lit = elapsed >= 2 && elapsed < 8;
    if (mismatch === undefined) {
      if (row.input === from)
        mismatch = `input changed or did not reach the requested edge at ${elapsed}`;
      else if (row.observer !== pulse)
        mismatch = `observer pulse differs at ${elapsed}`;
      else if (row.receiver !== pulse)
        mismatch = `receiver signal differs at ${elapsed}`;
      else if (row.lamp !== lit)
        mismatch = `lamp response differs at ${elapsed}`;
    }
    last = elapsed;
    return row;
  });
  const expected = {
    schemaVersion: 1,
    function: "observer-pulse",
    from,
    to: !from,
    delayGameTicks: 2,
    widthGameTicks: 2,
    lampReleaseGameTicks: 4,
    horizonGameTicks: 16,
    samples,
    verdict:
      missing !== undefined
        ? "inconclusive"
        : mismatch !== undefined
          ? "failed"
          : last === 16
            ? "passed"
            : "running",
    failure:
      missing !== undefined ? `inconclusive: ${missing}` : (mismatch ?? ""),
  };
  if (
    !isDeepStrictEqual(v, expected) ||
    (requirePass && expected.verdict !== "passed")
  )
    throw new RequestError(
      "Stored pulse claims disagree with observed samples.",
    );
  return expected;
}

/** Scope is checked separately against the saved project/component identity. */
export function validatePulseEvidence(value: unknown, requirePass = false) {
  if (
    !Array.isArray(value) ||
    value.length > 3 ||
    (requirePass && value.length !== 3)
  )
    throw new RequestError("Observer evidence needs baseline and both edges.");
  let original: boolean | undefined,
    previous = -1;
  return value.map((raw, index) => {
    const row = object(raw);
    fields(row, [
      "case",
      "tick",
      "elapsedTicks",
      "originalInput",
      "inputs",
      "outputs",
      "pulse",
    ]);
    if (integer(row.case, "case", 0, 2) !== index)
      throw new RequestError("Invalid observer evidence case.");
    const initial = bool(row.originalInput),
      elapsed = integer(row.elapsedTicks, "elapsedTicks", 0, 16),
      tick = integer(row.tick, "tick", 0, Number.MAX_SAFE_INTEGER);
    if (
      (original !== undefined && original !== initial) ||
      tick < elapsed ||
      (previous >= 0 && tick - elapsed !== previous)
    )
      throw new RequestError(
        "Observer evidence changed its input or game-tick continuity.",
      );
    original = initial;
    const trace = object(row.pulse);
    let samples: ReturnType<typeof sample>[],
      complete: boolean,
      passed: boolean;
    if (index === 0) {
      fields(trace, ["schemaVersion", "function", "samples"]);
      if (
        trace.schemaVersion !== 1 ||
        trace.function !== "observer-baseline" ||
        !Array.isArray(trace.samples) ||
        trace.samples.length < 1 ||
        trace.samples.length > 9
      )
        throw new RequestError("Invalid observer baseline.");
      samples = trace.samples.map(sample);
      if (samples.some((s, i) => s.elapsedTicks !== i))
        throw new RequestError(
          "Observer baseline is missing consecutive ticks.",
        );
      complete = samples.length === 9;
      passed = samples.every(
        (s) => s.input === initial && !s.observer && !s.receiver && !s.lamp,
      );
    } else {
      const pulse = validatePulseTrace(trace);
      if (
        pulse.from !== (index === 1 ? initial : !initial) ||
        pulse.samples.length === 0
      )
        throw new RequestError(
          "Observer edge direction differs from original input.",
        );
      samples = pulse.samples;
      complete = samples.at(-1)!.elapsedTicks === 16;
      passed = pulse.verdict === "passed";
    }
    const last = samples.at(-1)!,
      inputs = object(row.inputs),
      outputs = object(row.outputs);
    fields(inputs, ["input"]);
    fields(outputs, ["output"]);
    if (
      last.elapsedTicks !== elapsed ||
      bool(inputs.input) !== last.input ||
      bool(outputs.output) !== last.lamp
    )
      throw new RequestError("Observer endpoint differs from actual trace.");
    if ((requirePass || index < value.length - 1) && (!complete || !passed))
      throw new RequestError(
        "Observer evidence has not proved its complete temporal contract.",
      );
    previous = tick;
    return structuredClone(row);
  });
}
