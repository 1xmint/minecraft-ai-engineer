import test from "node:test";
import assert from "node:assert/strict";
import {
  validateTruthTableBehavior,
  truthTableDigest,
  truthTableSummary,
} from "../companion/src/truth-table.js";
const table = () => ({
  schemaVersion: 2,
  function: "truth-table",
  inputs: ["a", "b"],
  outputs: [false, false, false, true],
  settleGameTicks: 20,
});
test("truth-table identity is independent, immutable and shared with Java", () => {
  const source = table();
  const accepted = validateTruthTableBehavior(source);
  source.inputs[0] = "changed";
  source.outputs[3] = false;
  assert.deepEqual(accepted.inputs, ["a", "b"]);
  assert.equal(accepted.outputs[3], true);
  assert.equal(
    truthTableDigest(accepted),
    "665ee0ba2c6277abe2c658261c5d83eb06a8d218d3113baa75014549387667d9",
  );
  assert.equal(Object.isFrozen(accepted.outputs), true);
  assert.match(truthTableSummary(accepted), /a=on, b=off -> output=off/);
});
test("incomplete, ambiguous, unbounded and authority-bearing tables reject", () => {
  for (const patch of [
    { inputs: ["a", "a"] },
    { inputs: ["output"], outputs: [false, true] },
    { outputs: [false, true] },
    { outputs: [0, false, false, true] },
    { settleGameTicks: 129 },
    { settleGameTicks: 0.5 },
    { authority: true },
    { inputs: ["A", "b"] },
    { schemaVersion: 1 },
    { function: "buffer" },
  ])
    assert.throws(() => validateTruthTableBehavior({ ...table(), ...patch }));
  assert.throws(() => validateTruthTableBehavior(null));
});
test("all bounded arities require exactly every Boolean combination", () => {
  for (let n = 1; n <= 4; n++) {
    const value = {
      ...table(),
      inputs: Array.from({ length: n }, (_, i) => `in${i}`),
      outputs: Array.from({ length: 2 ** n }, (_, i) => i === 2 ** n - 1),
      settleGameTicks: 0,
    };
    assert.equal(validateTruthTableBehavior(value).outputs.length, 2 ** n);
    assert.throws(() =>
      validateTruthTableBehavior({ ...value, outputs: value.outputs.slice(1) }),
    );
  }
});
