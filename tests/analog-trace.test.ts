import test from "node:test";
import assert from "node:assert/strict";
import native from "./fixtures/analog-native-probe.json" with { type: "json" };
import {
  validateAnalogTrace,
  type AnalogMode,
} from "../companion/src/analog-trace.js";

function captured(mode: AnalogMode, rows = native.modes[mode].slice(9, 12)) {
  const first = rows[0]!;
  return {
    schemaVersion: 1,
    function: "comparator-transfer",
    mode,
    requested: [...first.requested],
    startedTick: first.tick - first.elapsed,
    sampleStartGameTicks: 8,
    stableSamples: 3,
    expectedOutput: first.output,
    samples: rows.map((row) => ({
      tick: row.tick,
      elapsedTicks: row.elapsed,
      mode: row.mode,
      inputs: [...row.inputs],
      output: row.output,
    })),
    verdict: "passed",
    failure: "",
  };
}
const intent = (mode: AnalogMode = "compare") => ({
  mode,
  requested: [8, 3, 0],
});

test("replay every archived native mode window without treating it as fresh authority", () => {
  assert.equal(native.ciRun, 41320293103);
  assert.equal(native.kind, "archival-native-game-evidence");
  for (const mode of ["compare", "subtract"] as const) {
    const rows = native.modes[mode];
    assert.equal(rows.length, 54);
    for (let index = 0; index < rows.length; index += 3) {
      const value = captured(mode, rows.slice(index, index + 3));
      const decoded = validateAnalogTrace(
        value,
        { mode, requested: value.requested },
        true,
      );
      assert.equal(decoded.verdict, "passed");
      assert.equal(decoded.expectedOutput, rows[index]!.output);
    }
  }
});

test("observed transfer claims cannot be rebound to another intended mode or stimulus", () => {
  assert.throws(() =>
    validateAnalogTrace(captured("compare"), intent("subtract")),
  );
  assert.throws(() =>
    validateAnalogTrace(captured("compare"), {
      mode: "compare",
      requested: [8, 7, 0],
    }),
  );
  const value = captured("compare");
  value.samples[0]!.mode = "subtract";
  assert.throws(() => validateAnalogTrace(value, intent()));
  value.verdict = "failed";
  value.failure = "comparator mode differs at 8";
  assert.equal(validateAnalogTrace(value, intent()).verdict, "failed");
  assert.throws(() => validateAnalogTrace(value, intent(), true));
});

test("missing observations remain inconclusive and a partial window cannot pass", () => {
  const gap = captured("compare");
  gap.samples.splice(1, 1);
  assert.throws(() => validateAnalogTrace(gap, intent()));
  gap.verdict = "inconclusive";
  gap.failure =
    "inconclusive: missing game-tick sample after 8, next observed 10";
  assert.equal(validateAnalogTrace(gap, intent()).verdict, "inconclusive");
  assert.throws(() => validateAnalogTrace(gap, intent(), true));
  const partial = captured("compare");
  partial.samples = partial.samples.slice(0, 1);
  partial.verdict = "running";
  assert.equal(validateAnalogTrace(partial, intent()).verdict, "running");
  assert.throws(() => validateAnalogTrace(partial, intent(), true));
});

test("a later correct sample cannot hide a wrong output or changed input", () => {
  const output = captured("compare");
  output.samples[1]!.output = 0;
  assert.throws(() => validateAnalogTrace(output, intent()));
  output.verdict = "failed";
  output.failure = "output signal differs at 9";
  assert.equal(validateAnalogTrace(output, intent()).verdict, "failed");
  const input = captured("compare");
  input.samples[0]!.inputs[0] = 7;
  input.verdict = "failed";
  input.failure = "input signals differ at 8";
  assert.equal(validateAnalogTrace(input, intent()).verdict, "failed");
});

test("reject non-integer levels, clock corruption, reordered samples and authority fields", () => {
  for (const level of [-1, 16, 0.5, Number.NaN, Infinity, true, "8"]) {
    const value = captured("compare");
    value.samples[0]!.inputs[0] = level as number;
    assert.throws(() => validateAnalogTrace(value, intent()));
  }
  const clock = captured("compare");
  clock.samples[1]!.tick++;
  assert.throws(() => validateAnalogTrace(clock, intent()));
  const reverse = captured("compare");
  reverse.samples.reverse();
  assert.throws(() => validateAnalogTrace(reverse, intent()));
  const duplicate = captured("compare");
  duplicate.samples[1] = structuredClone(duplicate.samples[0]!);
  assert.throws(() => validateAnalogTrace(duplicate, intent()));
  assert.throws(() =>
    validateAnalogTrace({ ...captured("compare"), authority: true }, intent()),
  );
  assert.throws(() =>
    validateAnalogTrace({ ...captured("compare"), stableSamples: 1 }, intent()),
  );
  assert.throws(() =>
    validateAnalogTrace(
      { ...captured("compare"), expectedOutput: 0 },
      intent(),
    ),
  );
  const sparse = captured("compare");
  sparse.samples = new Array(1) as typeof sparse.samples;
  sparse.verdict = "running";
  assert.throws(() => validateAnalogTrace(sparse, intent()));
  assert.throws(() =>
    validateAnalogTrace(captured("compare"), {
      mode: "compare",
      requested: new Array<number>(3),
    }),
  );
});

test("decoded readings do not retain mutable caller arrays", () => {
  const value = captured("compare"),
    expected = intent();
  const decoded = validateAnalogTrace(value, expected, true);
  value.samples[0]!.inputs[0] = 0;
  value.samples[0]!.output = 0;
  expected.requested[0] = 0;
  assert.deepEqual(decoded.requested, [8, 3, 0]);
  assert.deepEqual(decoded.samples[0]!.inputs, [8, 3, 0]);
  assert.equal(decoded.samples[0]!.output, 8);
});
