import { validateAnalogTrace, type AnalogMode } from "./analog-trace.js";
import { integer, RequestError } from "./types.js";

export interface AnalogEvidenceRow {
  case: number;
  tick: number;
  elapsedTicks: number;
  inputs: Record<string, boolean>;
  outputs: { output: boolean };
  analog: ReturnType<typeof validateAnalogTrace>;
}

function fail(message: string): never {
  throw new RequestError(message);
}

function record(value: unknown, name: string): Record<string, unknown> {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    (Object.getPrototypeOf(value) !== Object.prototype &&
      Object.getPrototypeOf(value) !== null)
  )
    return fail(`Invalid ${name}.`);
  return value as Record<string, unknown>;
}

function exactKeys(
  value: Record<string, unknown>,
  expected: readonly string[],
) {
  const keys = Reflect.ownKeys(value);
  if (
    keys.length !== expected.length ||
    keys.some((key) => typeof key !== "string" || !expected.includes(key))
  )
    fail("Invalid analog evidence fields.");
}

function denseArray(
  value: unknown,
  maxLength: number,
  name: string,
): unknown[] {
  if (!Array.isArray(value) || value.length > maxLength)
    return fail(`Invalid ${name}.`);
  const keys = Reflect.ownKeys(value);
  if (keys.length !== value.length + 1 || !keys.includes("length"))
    return fail(`Invalid sparse or extended ${name}.`);
  for (let index = 0; index < value.length; index++)
    if (!Object.hasOwn(value, index)) return fail(`Invalid sparse ${name}.`);
  return value;
}

function freeze<T>(value: T): T {
  if (value !== null && typeof value === "object" && !Object.isFrozen(value)) {
    for (const child of Object.values(value)) freeze(child);
    Object.freeze(value);
  }
  return value;
}

function suiteCases(): number[][] {
  const result: number[][] = [];
  for (let bank = 0; bank < 3; bank++)
    for (let level = 0; level <= 15; level++) {
      const values = [8, 0, 0];
      values[bank] = level;
      result.push(values);
    }
  result.push(
    [0, 0, 0],
    [1, 0, 0],
    [15, 0, 0],
    [8, 3, 0],
    [8, 0, 3],
    [8, 8, 0],
    [8, 0, 8],
    [8, 9, 0],
    [8, 0, 9],
    [8, 3, 7],
    [8, 7, 3],
    [3, 15, 0],
    [15, 14, 1],
    [15, 1, 14],
    [1, 1, 1],
    [0, 15, 15],
    [15, 15, 15],
    [0, 0, 0],
  );
  return result;
}

function controls(values: readonly number[]): Record<string, boolean> {
  const result: Record<string, boolean> = {};
  for (let bank = 0; bank < 3; bank++)
    for (let index = 0; index < 15; index++) {
      const level = values[bank]!;
      const selected = level === 0 ? -1 : bank === 2 ? 15 - level : level - 1;
      result[`bank-${bank}-${index}`] = index === selected;
    }
  return result;
}

function sameControls(
  value: unknown,
  expected: Record<string, boolean>,
): boolean {
  const actual = record(value, "analog selector controls");
  exactKeys(actual, Object.keys(expected));
  const keys = Object.keys(actual);
  const expectedKeys = Object.keys(expected);
  return (
    keys.length === expectedKeys.length &&
    expectedKeys.every((key) => actual[key] === expected[key])
  );
}

/** Replay the ordered 66-case analog suite against a separately supplied mode. */
export function validateAnalogEvidence(
  value: unknown,
  mode: AnalogMode,
  requirePass = false,
): readonly AnalogEvidenceRow[] {
  if (mode !== "compare" && mode !== "subtract")
    return fail("Unknown comparator mode.");
  if (typeof requirePass !== "boolean")
    return fail("Invalid analog pass requirement.");
  const cases = suiteCases();
  const rows = denseArray(value, cases.length, "analog evidence suite");
  if (requirePass && rows.length !== cases.length)
    return fail("Incomplete analog evidence suite.");

  let previousEnd = -1;
  const validated: AnalogEvidenceRow[] = [];
  for (let index = 0; index < rows.length; index++) {
    const row = record(rows[index], "analog evidence row");
    exactKeys(row, [
      "case",
      "tick",
      "elapsedTicks",
      "inputs",
      "outputs",
      "analog",
    ]);
    if (integer(row.case, "analog case", 0, cases.length - 1) !== index)
      return fail("Invalid analog case identity or order.");

    const requested = cases[index]!;
    const trace = validateAnalogTrace(row.analog, { mode, requested });
    const started = trace.startedTick;
    if (started < previousEnd)
      return fail("Analog evidence reordered native game time.");
    if (index > 0 && validated[index - 1]!.analog.verdict !== "passed")
      return fail(
        "Analog suite cannot continue after an incomplete or failed case.",
      );

    const samples = trace.samples;
    const last = samples[samples.length - 1];
    const tick = last?.tick ?? started;
    const elapsedTicks = last?.elapsedTicks ?? 0;
    const output = last?.output ?? 0;
    if (
      integer(row.tick, "analog summary tick", 0, Number.MAX_SAFE_INTEGER) !==
        tick ||
      integer(row.elapsedTicks, "analog summary elapsed ticks", 0, 10) !==
        elapsedTicks
    )
      return fail("Analog summary cannot change its observed game clock.");

    const expectedInputs = controls(requested);
    if (!sameControls(row.inputs, expectedInputs))
      return fail(
        "Analog controls disagree with the requested selector levels.",
      );
    const outputs = record(row.outputs, "analog output summary");
    exactKeys(outputs, ["output"]);
    if (typeof outputs.output !== "boolean" || outputs.output !== output > 0)
      return fail(
        "Analog output summary disagrees with the numeric observation.",
      );
    if (requirePass && trace.verdict !== "passed")
      return fail("Analog numeric case has no complete functional pass.");

    validated.push({
      case: index,
      tick,
      elapsedTicks,
      inputs: expectedInputs,
      outputs: { output: output > 0 },
      analog: trace,
    });
    previousEnd = tick;
  }
  return freeze(validated);
}
