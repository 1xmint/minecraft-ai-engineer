import { isDeepStrictEqual } from "node:util";
import { integer, object, RequestError } from "./types.js";

export interface ConnectedTraceIntent {
  graphDigest: string;
  a: boolean;
  b: boolean;
}

export type ConnectedVerdict = "inconclusive" | "failed" | "passed";

export interface ConnectedMismatch {
  elapsedTicks: number;
  gameTick: number;
  pin: string;
  expected: boolean;
  observed: boolean | number;
}

interface ConnectedSample {
  gameTick: number;
  elapsedTicks: number;
  ports: Record<string, boolean | number>;
}

interface ConnectedTraceJson {
  schemaVersion: 1;
  function: "half-adder";
  graphDigest: string;
  a: boolean;
  b: boolean;
  startGameTick: number;
  settleGameTicks: 48;
  stableSamples: 8;
  samples: ConnectedSample[];
  verdict: ConnectedVerdict;
  firstMismatch: ConnectedMismatch | null;
}

const PORTS = [
  "a:control",
  "a:out",
  "b:control",
  "b:out",
  "n1:in0",
  "n1:in1",
  "n1:out",
  "n2:in0",
  "n2:in1",
  "n2:out",
  "n3:in0",
  "n3:in1",
  "n3:out",
  "sum:in0",
  "sum:in1",
  "sum:out",
  "carry:in",
  "carry:out",
  "sum-light:in",
  "carry-light:in",
] as const;
const WIRE_PORTS = new Set(
  PORTS.filter(
    (pin) => !pin.endsWith(":control") && !pin.endsWith("-light:in"),
  ),
);
const FIXED = { settleGameTicks: 48 as const, stableSamples: 8 as const };

function exactFields(
  value: Record<string, unknown>,
  keys: readonly string[],
  name: string,
): void {
  if (
    !isPlainJsonObject(value) ||
    !isDeepStrictEqual(Object.keys(value).sort(), [...keys].sort())
  )
    throw new RequestError(`Invalid ${name} fields.`);
}

function isPlainJsonObject(value: object): boolean {
  const proto = Object.getPrototypeOf(value);
  if (proto !== Object.prototype && proto !== null) return false;
  if (Object.getOwnPropertySymbols(value).length !== 0) return false;
  return Object.values(Object.getOwnPropertyDescriptors(value)).every(
    (descriptor) => descriptor.enumerable && "value" in descriptor,
  );
}

function intentValue(value: ConnectedTraceIntent): ConnectedTraceIntent {
  const v = object(value);
  exactFields(v, ["graphDigest", "a", "b"], "connected trace expectation");
  if (
    typeof v.graphDigest !== "string" ||
    !/^[a-f0-9]{64}$/u.test(v.graphDigest)
  )
    throw new RequestError("Invalid connected graph digest.");
  if (typeof v.a !== "boolean" || typeof v.b !== "boolean")
    throw new RequestError("Connected half-adder inputs must be booleans.");
  return Object.freeze({ graphDigest: v.graphDigest, a: v.a, b: v.b });
}

function expectedPorts(
  a: boolean,
  b: boolean,
): Record<(typeof PORTS)[number], boolean> {
  const n1 = !(a && b),
    n2 = !(a && n1),
    n3 = !(n1 && b),
    sum = a !== b,
    carry = a && b;
  return {
    "a:control": a,
    "a:out": a,
    "b:control": b,
    "b:out": b,
    "n1:in0": a,
    "n1:in1": b,
    "n1:out": n1,
    "n2:in0": a,
    "n2:in1": n1,
    "n2:out": n2,
    "n3:in0": n1,
    "n3:in1": b,
    "n3:out": n3,
    "sum:in0": n2,
    "sum:in1": n3,
    "sum:out": sum,
    "carry:in": n1,
    "carry:out": carry,
    "sum-light:in": sum,
    "carry-light:in": carry,
  };
}

function portsValue(value: unknown): Record<string, boolean | number> {
  const v = object(value);
  exactFields(v, PORTS, "connected trace ports");
  const result: Record<string, boolean | number> = {};
  for (const pin of PORTS) {
    const raw = v[pin];
    if (WIRE_PORTS.has(pin)) result[pin] = integer(raw, `ports.${pin}`, 0, 15);
    else {
      if (typeof raw !== "boolean")
        throw new RequestError(`Invalid ports.${pin}.`);
      result[pin] = raw;
    }
  }
  return result;
}

function mismatchFor(
  sample: ConnectedSample,
  wanted: Record<string, boolean>,
): ConnectedMismatch | null {
  for (const pin of PORTS) {
    if (
      sample.elapsedTicks < FIXED.settleGameTicks &&
      pin !== "a:control" &&
      pin !== "b:control"
    )
      continue;
    const observed = sample.ports[pin]!;
    const logical = typeof observed === "number" ? observed > 0 : observed;
    if (logical !== wanted[pin])
      return {
        elapsedTicks: sample.elapsedTicks,
        gameTick: sample.gameTick,
        pin,
        expected: wanted[pin]!,
        observed,
      };
  }
  return null;
}

function frozenSample(
  gameTick: number,
  elapsedTicks: number,
  ports: Record<string, boolean | number>,
): ConnectedSample {
  return Object.freeze({
    gameTick,
    elapsedTicks,
    ports: Object.freeze({ ...ports }),
  });
}

function copyJson(value: ConnectedTraceJson): ConnectedTraceJson {
  return {
    ...value,
    samples: value.samples.map((row) => ({ ...row, ports: { ...row.ports } })),
    firstMismatch:
      value.firstMismatch === null ? null : { ...value.firstMismatch },
  };
}

function freezeJson(value: ConnectedTraceJson): Readonly<ConnectedTraceJson> {
  for (const row of value.samples)
    (Object.freeze(row.ports), Object.freeze(row));
  if (value.firstMismatch !== null) Object.freeze(value.firstMismatch);
  Object.freeze(value.samples);
  return Object.freeze(value);
}

/**
 * Captures and replays one fixed, curated half-adder case. The verdict is
 * evidence for these supplied pins and clocks only; it carries no project or
 * execution authority and makes no claim about other graph designs.
 */
export class ConnectedTrace {
  private readonly expected: ConnectedTraceIntent;
  private readonly start: number;
  private readonly rows: ConnectedSample[] = [];
  private gap = false;
  private mismatch: ConnectedMismatch | null = null;

  constructor(expected: ConnectedTraceIntent, startGameTick: number) {
    this.expected = intentValue(expected);
    this.start = integer(
      startGameTick,
      "startGameTick",
      0,
      Number.MAX_SAFE_INTEGER - 55,
    );
  }

  sample(gameTick: number, ports: unknown): Readonly<ConnectedSample> {
    const tick = integer(gameTick, "gameTick", 0, Number.MAX_SAFE_INTEGER);
    const elapsed = tick - this.start;
    if (
      elapsed < 0 ||
      elapsed > 55 ||
      (this.rows.length > 0 && elapsed <= this.rows.at(-1)!.elapsedTicks)
    )
      throw new RequestError(
        "Connected trace ticks must advance within elapsed ticks 0 through 55.",
      );
    const observedPorts = portsValue(ports);
    const row = frozenSample(tick, elapsed, observedPorts);
    const hasGap =
      elapsed !==
      (this.rows.length === 0 ? 0 : this.rows.at(-1)!.elapsedTicks + 1);
    const mismatch =
      this.mismatch ??
      mismatchFor(row, expectedPorts(this.expected.a, this.expected.b));
    if (hasGap) this.gap = true;
    this.rows.push(row);
    this.mismatch = mismatch;
    return row;
  }

  verdict(): ConnectedVerdict {
    if (this.mismatch !== null) return "failed";
    if (this.gap) return "inconclusive";
    if (
      this.rows.length === 56 &&
      this.rows[0]!.elapsedTicks === 0 &&
      this.rows[55]!.elapsedTicks === 55
    )
      return "passed";
    return "inconclusive";
  }

  passed(): boolean {
    return this.verdict() === "passed";
  }

  json(): Readonly<ConnectedTraceJson> {
    return freezeJson({
      schemaVersion: 1,
      function: "half-adder",
      graphDigest: this.expected.graphDigest,
      a: this.expected.a,
      b: this.expected.b,
      startGameTick: this.start,
      ...FIXED,
      samples: this.rows.map((row) => ({ ...row, ports: { ...row.ports } })),
      verdict: this.verdict(),
      firstMismatch: this.mismatch === null ? null : { ...this.mismatch },
    });
  }
}

/** Replays a stored snapshot against separately trusted graph identity and inputs. */
export function readConnectedTrace(
  value: unknown,
  expected: ConnectedTraceIntent,
): Readonly<ConnectedTraceJson> {
  const trusted = intentValue(expected),
    v = object(value);
  exactFields(
    v,
    [
      "schemaVersion",
      "function",
      "graphDigest",
      "a",
      "b",
      "startGameTick",
      "settleGameTicks",
      "stableSamples",
      "samples",
      "verdict",
      "firstMismatch",
    ],
    "connected trace",
  );
  if (
    v.schemaVersion !== 1 ||
    v.function !== "half-adder" ||
    v.graphDigest !== trusted.graphDigest ||
    v.a !== trusted.a ||
    v.b !== trusted.b ||
    v.settleGameTicks !== 48 ||
    v.stableSamples !== 8
  )
    throw new RequestError(
      "Stored connected trace metadata disagrees with its trusted expectation.",
    );
  const start = integer(
    v.startGameTick,
    "startGameTick",
    0,
    Number.MAX_SAFE_INTEGER - 55,
  );
  if (
    !Array.isArray(v.samples) ||
    v.samples.length > 56 ||
    !isPlainJsonArray(v.samples)
  )
    throw new RequestError("Invalid bounded connected samples.");
  const replay = new ConnectedTrace(trusted, start);
  for (const raw of v.samples) {
    const row = object(raw);
    exactFields(row, ["gameTick", "elapsedTicks", "ports"], "connected sample");
    const gameTick = integer(
        row.gameTick,
        "gameTick",
        0,
        Number.MAX_SAFE_INTEGER,
      ),
      elapsed = integer(row.elapsedTicks, "elapsedTicks", 0, 55);
    if (elapsed !== gameTick - start)
      throw new RequestError(
        "Connected sample clock disagrees with its start.",
      );
    replay.sample(gameTick, row.ports);
  }
  if (v.firstMismatch !== null) {
    const mismatch = object(v.firstMismatch);
    exactFields(
      mismatch,
      ["elapsedTicks", "gameTick", "pin", "expected", "observed"],
      "connected mismatch",
    );
  }
  const actual = replay.json();
  if (!isDeepStrictEqual(v, actual))
    throw new RequestError(
      "Stored connected verdict or mismatch disagrees with observed samples.",
    );
  return actual;
}

function isPlainJsonArray(value: unknown[]): boolean {
  if (
    Object.getPrototypeOf(value) !== Array.prototype ||
    Object.getOwnPropertySymbols(value).length !== 0
  )
    return false;
  const descriptors = Object.getOwnPropertyDescriptors(value);
  for (let index = 0; index < value.length; index++) {
    const descriptor = descriptors[String(index)];
    if (!descriptor || !descriptor.enumerable || !("value" in descriptor))
      return false;
  }
  return Object.keys(descriptors).every((key) => {
    if (key === "length") return true;
    if (!/^(0|[1-9][0-9]*)$/u.test(key)) return false;
    const index = Number(key);
    return Number.isSafeInteger(index) && index < value.length;
  });
}
