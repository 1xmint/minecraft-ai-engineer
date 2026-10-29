import test from "node:test";
import assert from "node:assert/strict";
import { validateSettledTrace } from "../companion/src/settled-trace.js";
import {
  validateTruthTableBehavior,
  truthTableDigest,
} from "../companion/src/truth-table.js";
const behavior = validateTruthTableBehavior({
  schemaVersion: 2,
  function: "truth-table",
  inputs: ["a", "b"],
  outputs: [false, false, false, true],
  settleGameTicks: 2,
});
const trace = () => ({
  schemaVersion: 1,
  behaviorDigest: truthTableDigest(behavior),
  row: 3,
  expected: true,
  settleGameTicks: 2,
  stableGameTicks: 8,
  samples: Array.from({ length: 11 }, (_, i) => ({
    elapsedTicks: i,
    observed: i >= 2,
  })),
  verdict: "passed",
  failure: "",
});
test("settled evidence replays only against the accepted table and exact row", () => {
  assert.equal(
    validateSettledTrace(trace(), behavior, 3, true).verdict,
    "passed",
  );
  assert.throws(() => validateSettledTrace(trace(), behavior, 2, true));
  const changed = validateTruthTableBehavior({
    ...behavior,
    outputs: [false, false, false, false],
  });
  assert.throws(() => validateSettledTrace(trace(), changed, 3, true));
  for (const patch of [
    { stableGameTicks: 1 },
    { settleGameTicks: 1 },
    { authority: true },
    { behaviorDigest: "forged" },
    { expected: false },
  ])
    assert.throws(() =>
      validateSettledTrace({ ...trace(), ...patch }, behavior, 3, true),
    );
});
test("a correct endpoint cannot conceal a transient error or a sampling gap", () => {
  const bad = trace();
  bad.samples[5]!.observed = false;
  assert.throws(() => validateSettledTrace(bad, behavior, 3, true));
  bad.verdict = "failed";
  bad.failure = "output expected true, observed false at elapsed game tick 5";
  assert.equal(validateSettledTrace(bad, behavior, 3).verdict, "failed");
  assert.throws(() => validateSettledTrace(bad, behavior, 3, true));
  const gap = trace();
  gap.samples.splice(5, 1);
  gap.verdict = "inconclusive";
  gap.failure =
    "inconclusive: missing game-tick sample after 4, next observed 6";
  assert.equal(validateSettledTrace(gap, behavior, 3).verdict, "inconclusive");
  assert.throws(() => validateSettledTrace(gap, behavior, 3, true));
  const duplicate = trace();
  duplicate.samples[5]!.elapsedTicks = 4;
  assert.throws(() => validateSettledTrace(duplicate, behavior, 3));
});
