import test from "node:test";
import assert from "node:assert/strict";
import {
  validatePulseEvidence,
  validatePulseTrace,
} from "../companion/src/pulse-trace.js";
import { makeMechanismBlueprint, mechanism } from "../companion/src/catalog.js";
import { validatePlan, PLAN_SCHEMA } from "../companion/src/provider.js";

const trace = (from = false) => ({
  schemaVersion: 1,
  function: "observer-pulse",
  from,
  to: !from,
  delayGameTicks: 2,
  widthGameTicks: 2,
  lampReleaseGameTicks: 4,
  horizonGameTicks: 16,
  samples: Array.from({ length: 17 }, (_, elapsedTicks) => ({
    elapsedTicks,
    input: !from,
    observer: elapsedTicks >= 2 && elapsedTicks < 4,
    receiver: elapsedTicks >= 2 && elapsedTicks < 4,
    lamp: elapsedTicks >= 2 && elapsedTicks < 8,
  })),
  verdict: "passed",
  failure: "",
});
function evidence(originalInput = false) {
  return [0, 1, 2].map((index) => ({
    case: index,
    tick: [108, 124, 140][index]!,
    elapsedTicks: index === 0 ? 8 : 16,
    originalInput,
    inputs: { input: index === 1 ? !originalInput : originalInput },
    outputs: { output: false },
    pulse:
      index === 0
        ? {
            schemaVersion: 1,
            function: "observer-baseline",
            samples: Array.from({ length: 9 }, (_, elapsedTicks) => ({
              elapsedTicks,
              input: originalInput,
              observer: false,
              receiver: false,
              lamp: false,
            })),
          }
        : trace(index === 1 ? originalInput : !originalInput),
  }));
}
test("both observer edges require actual receiver and lamp timing", () => {
  for (const from of [false, true])
    assert.equal(validatePulseTrace(trace(from), true).verdict, "passed");
  for (const mutate of [
    (v: ReturnType<typeof trace>) => {
      v.samples[2]!.observer = false;
    },
    (v: ReturnType<typeof trace>) => {
      v.samples[2]!.receiver = false;
    },
    (v: ReturnType<typeof trace>) => {
      v.samples[6]!.lamp = false;
    },
    (v: ReturnType<typeof trace>) => {
      v.samples[12]!.observer = true;
    },
    (v: ReturnType<typeof trace>) => {
      v.samples[12]!.input = false;
    },
    (v: ReturnType<typeof trace>) => {
      v.samples.splice(3, 1);
    },
    (v: ReturnType<typeof trace>) => {
      v.samples[3]!.elapsedTicks = 2;
    },
    (v: ReturnType<typeof trace>) => {
      v.delayGameTicks = 3;
    },
  ]) {
    const v = trace();
    mutate(v);
    assert.throws(() => validatePulseTrace(v, true));
  }
  assert.throws(() => validatePulseTrace({ ...trace(), authority: true }));
});
test("observer evidence needs quiet baseline, both edges, exact endpoints and continuous phase ticks", () => {
  for (const initial of [false, true])
    assert.equal(validatePulseEvidence(evidence(initial), true).length, 3);
  for (const mutate of [
    (v: ReturnType<typeof evidence>) => {
      v.pop();
    },
    (v: ReturnType<typeof evidence>) => {
      v[1]!.tick++;
    },
    (v: ReturnType<typeof evidence>) => {
      v[2]!.originalInput = true;
    },
    (v: ReturnType<typeof evidence>) => {
      v[2]!.inputs.input = true;
    },
    (v: ReturnType<typeof evidence>) => {
      v[0]!.pulse.samples[1]!.lamp = true;
    },
    (v: ReturnType<typeof evidence>) => {
      v[0]!.pulse.samples.splice(2, 1);
    },
  ]) {
    const v = evidence();
    mutate(v);
    assert.throws(() => validatePulseEvidence(v, true));
  }
  assert.equal(validatePulseEvidence([], false).length, 0);
  assert.throws(() => validatePulseEvidence([], true));
});
test("observer catalog exposes a temporal contract and fixed geometry, never settled vectors", () => {
  const m = mechanism("observer-pulse");
  assert.equal(m.cases.length, 0);
  assert.equal(m.pulse?.widthGameTicks, 2);
  assert.ok(PLAN_SCHEMA.properties.template.enum.includes("observer-pulse"));
  const plan = validatePlan({
    template: "observer-pulse",
    explanation: "Detect both edges",
    rotation: 90,
    parameters: { delay: 1 },
  });
  const b = makeMechanismBlueprint(
    {
      request: "Detect lever changes",
      worldId: "world",
      dimension: "minecraft:overworld",
      anchor: { x: 0, y: 100, z: 0 },
      rotation: 0,
      maxSize: { x: 3, y: 2, z: 3 },
    },
    plan,
  );
  assert.equal(b.resources["minecraft:observer"], 1);
  assert.equal(b.blocks.length, 6);
  assert.equal(b.verification, "untested");
  assert.ok(b.tests[0]!.description.includes("both input-edge pulses"));
  assert.throws(() =>
    validatePlan({
      template: "observer-pulse",
      explanation: "Wrong configurable delay",
      parameters: { delay: 2 },
      rotation: 0,
    }),
  );
});
