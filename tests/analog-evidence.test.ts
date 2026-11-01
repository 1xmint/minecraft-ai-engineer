import assert from "node:assert/strict";
import { test } from "node:test";
import raw from "./fixtures/analog-native-probe.json" with { type: "json" };
import { validateAnalogEvidence } from "../companion/src/analog-evidence.js";

type Mode = "compare" | "subtract";
function nativePrefix(mode: Mode) {
  return [0, 1].map((index) => {
    const samples = raw.modes[mode].slice(index * 3, index * 3 + 3);
    const first = samples[0]!;
    const last = samples[2]!;
    const inputs: Record<string, boolean> = {};
    for (let bank = 0; bank < 3; bank++)
      for (let selector = 0; selector < 15; selector++)
        inputs[`bank-${bank}-${selector}`] =
          index === 1 && bank === 0 && selector === 0;
    return {
      case: index,
      tick: last.tick,
      elapsedTicks: last.elapsed,
      inputs,
      outputs: { output: last.output > 0 },
      analog: {
        schemaVersion: 1,
        function: "comparator-transfer",
        mode,
        requested: first.requested,
        startedTick: first.tick - first.elapsed,
        sampleStartGameTicks: 8,
        stableSamples: 3,
        expectedOutput: last.output,
        samples: samples.map((sample) => ({
          tick: sample.tick,
          elapsedTicks: sample.elapsed,
          mode: sample.mode,
          inputs: sample.inputs,
          output: sample.output,
        })),
        verdict: "passed",
        failure: "",
      },
    };
  });
}

test("actual archived native prefix replays without becoming a complete suite", () => {
  for (const mode of ["compare", "subtract"] as const) {
    const prefix = nativePrefix(mode);
    assert.equal(validateAnalogEvidence(prefix, mode).length, 2);
    assert.throws(() => validateAnalogEvidence(prefix, mode, true));
    assert.throws(() =>
      validateAnalogEvidence(
        prefix,
        mode === "compare" ? "subtract" : "compare",
      ),
    );
    assert.throws(() => validateAnalogEvidence([], mode, true));
  }
});

test("analog suite cannot skip, rebind or mint summaries and authority", () => {
  const original = nativePrefix("compare");
  const changed = structuredClone(original);
  changed[0]!.inputs["bank-0-0"] = true;
  assert.throws(() => validateAnalogEvidence(changed, "compare"));
  const output = structuredClone(original);
  output[0]!.outputs.output = true;
  assert.throws(() => validateAnalogEvidence(output, "compare"));
  const clock = structuredClone(original);
  clock[0]!.tick++;
  assert.throws(() => validateAnalogEvidence(clock, "compare"));
  assert.throws(() =>
    validateAnalogEvidence([...original].reverse(), "compare"),
  );
  assert.throws(() => validateAnalogEvidence([original[1]], "compare"));
  const sparse = structuredClone(original);
  delete sparse[0];
  assert.throws(() => validateAnalogEvidence(sparse, "compare"));
  assert.throws(() =>
    validateAnalogEvidence([{ ...original[0], ownerId: "owner" }], "compare"),
  );
  const detached = validateAnalogEvidence(original, "compare");
  original[0]!.inputs["bank-0-0"] = true;
  assert.equal(detached[0]!.inputs["bank-0-0"], false);
});
