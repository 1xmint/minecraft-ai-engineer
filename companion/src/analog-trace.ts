import { isDeepStrictEqual } from "node:util";
import { integer, object, RequestError } from "./types.js";

export type AnalogMode = "compare" | "subtract";

export interface AnalogIntent {
  mode: AnalogMode;
  requested: readonly number[];
}

interface AnalogSample {
  tick: number;
  elapsedTicks: number;
  mode: AnalogMode;
  inputs: number[];
  output: number;
}

const fields = (value: Record<string, unknown>, keys: string[]) => {
  if (!isDeepStrictEqual(Object.keys(value).sort(), [...keys].sort()))
    throw new RequestError("Invalid comparator evidence fields.");
};

function mode(value: unknown, name: string): AnalogMode {
  if (value !== "compare" && value !== "subtract")
    throw new RequestError(`Invalid ${name}.`);
  return value;
}

function levels(value: unknown, name: string): number[] {
  if (!Array.isArray(value) || value.length !== 3)
    throw new RequestError(`Invalid ${name}.`);
  return Array.from(value, (level, index) =>
    integer(level, `${name}[${index}]`, 0, 15),
  );
}

function sample(value: unknown): AnalogSample {
  const v = object(value);
  fields(v, ["tick", "elapsedTicks", "mode", "inputs", "output"]);
  return {
    tick: integer(v.tick, "tick", 0, Number.MAX_SAFE_INTEGER),
    elapsedTicks: integer(v.elapsedTicks, "elapsedTicks", 8, 10),
    mode: mode(v.mode, "observed comparator mode"),
    inputs: levels(v.inputs, "inputs"),
    output: integer(v.output, "output", 0, 15),
  };
}

function expectedLevel(mode: AnalogMode, requested: number[]): number {
  const rear = requested[0]!;
  const side = Math.max(requested[1]!, requested[2]!);
  return mode === "compare"
    ? rear >= side
      ? rear
      : 0
    : Math.max(0, rear - side);
}

/**
 * Replays one settled comparator transfer against separately supplied intent.
 * This single-case verdict grants no scope, permission, execution authority, or
 * claim about any circuit beyond the supplied observations.
 */
export function validateAnalogTrace(
  value: unknown,
  intent: AnalogIntent,
  requirePass = false,
) {
  const requestedMode = mode(intent?.mode, "intended comparator mode"),
    requested = levels(intent?.requested, "intended inputs"),
    v = object(value);
  fields(v, [
    "schemaVersion",
    "function",
    "mode",
    "requested",
    "startedTick",
    "sampleStartGameTicks",
    "stableSamples",
    "expectedOutput",
    "samples",
    "verdict",
    "failure",
  ]);
  if (!Array.isArray(v.samples) || v.samples.length > 3)
    throw new RequestError("Invalid bounded comparator samples.");

  const startedTick = integer(
      v.startedTick,
      "startedTick",
      0,
      Number.MAX_SAFE_INTEGER - 10,
    ),
    expectedOutput = expectedLevel(requestedMode, requested);
  let last = 7,
    missing: string | undefined,
    mismatch: string | undefined;
  const samples = Array.from(v.samples, (raw: unknown) => {
    const row = sample(raw),
      elapsed = row.elapsedTicks;
    if (elapsed <= last)
      throw new RequestError(
        "Comparator samples must advance within the bounded window.",
      );
    if (row.tick !== startedTick + elapsed)
      throw new RequestError(
        "Comparator sample clock disagrees with its start.",
      );
    if (elapsed !== last + 1 && missing === undefined)
      missing = `missing game-tick sample after ${last}, next observed ${elapsed}`;
    if (mismatch === undefined) {
      if (row.mode !== requestedMode)
        mismatch = `comparator mode differs at ${elapsed}`;
      else if (!isDeepStrictEqual(row.inputs, requested))
        mismatch = `input signals differ at ${elapsed}`;
      else if (row.output !== expectedOutput)
        mismatch = `output signal differs at ${elapsed}`;
    }
    last = elapsed;
    return row;
  });

  const expected = {
    schemaVersion: 1,
    function: "comparator-transfer",
    mode: requestedMode,
    requested,
    startedTick,
    sampleStartGameTicks: 8,
    stableSamples: 3,
    expectedOutput,
    samples,
    verdict:
      missing !== undefined
        ? "inconclusive"
        : mismatch !== undefined
          ? "failed"
          : last === 10
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
      "Stored comparator claims disagree with observed samples.",
    );
  return expected;
}
