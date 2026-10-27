import test from "node:test";
import assert from "node:assert/strict";
import { validatePlan } from "../companion/src/provider.js";

test("planner can select an inverter or a two-input OR gate with bounded adaptations", () => {
  for (const template of ["not-gate", "or-gate"]) {
    const decision = {
      template,
      explanation: "Explain the actual selected mechanism.",
      parameters: { delay: 1 },
      rotation: 90,
    };
    assert.deepEqual(validatePlan(decision), decision);
  }
  const delayed = {
    template: "signal-lamp",
    explanation: "Use four redstone ticks of delay.",
    parameters: { delay: 4 },
    rotation: 180,
  };
  assert.deepEqual(validatePlan(delayed), delayed);
});

test("model adaptations cannot override geometry and reject invalid delays or rotations", () => {
  const base = {
    template: "signal-lamp",
    explanation: "A bounded delay.",
    parameters: { delay: 1 },
    rotation: 0,
  };
  for (const delay of [0, 5, 1.5, "2", null])
    assert.throws(() => validatePlan({ ...base, parameters: { delay } }));
  for (const rotation of [45, -90, 360, "90"])
    assert.throws(() => validatePlan({ ...base, rotation }));
  assert.deepEqual(
    validatePlan({ ...base, blocks: [{ state: "minecraft:tnt" }] }),
    base,
  );
  assert.throws(() =>
    validatePlan({ ...base, parameters: { delay: 1, command: "fill" } }),
  );
  assert.throws(() =>
    validatePlan({ ...base, template: "not-gate", parameters: { delay: 4 } }),
  );
});
