import assert from "node:assert/strict";
import { test } from "node:test";
import {
  fittingMechanisms,
  makeMechanismBlueprint,
} from "../companion/src/catalog.js";
import { validatePlan } from "../companion/src/provider.js";
import { validateAnalogIntention } from "../companion/src/analog-intention.js";
import { rotatePosition } from "../companion/src/geometry.js";

const input = {
  request: "Compare redstone strengths",
  worldId: "world",
  dimension: "minecraft:overworld",
  projectId: "9a71b5bd-2073-4c67-9cd8-60d66f43cb6a",
  anchor: { x: 10, y: 100, z: 20 },
  rotation: 0 as const,
};

test("planner routes both analog modes to canonical workbenches fitting all rotations", () => {
  for (const template of ["comparator-compare", "comparator-subtract"] as const)
    for (const rotation of [0, 90, 180, 270] as const) {
      const plan = {
        template,
        rotation,
        parameters: { delay: 1 },
        explanation: "Use the actual native comparator mode.",
      };
      assert.deepEqual(validatePlan(plan), plan);
      const maxSize =
        rotation === 0 || rotation === 180
          ? { x: 17, y: 3, z: 31 }
          : { x: 31, y: 3, z: 17 };
      const source = makeMechanismBlueprint({ ...input, maxSize }, plan);
      const canonical = validateAnalogIntention(source, {
        worldId: input.worldId,
        dimension: input.dimension,
        projectId: input.projectId,
      });
      assert.equal(
        canonical.analog.mode,
        template === "comparator-compare" ? "compare" : "subtract",
      );
      assert.equal(canonical.verification, "untested");
      assert.equal(canonical.resources["minecraft:redstone"], 46);
      for (const block of canonical.blocks) {
        const pos = rotatePosition(block.pos, rotation);
        for (const axis of ["x", "y", "z"] as const) {
          const actual = canonical.anchor[axis] + pos[axis];
          assert.ok(
            actual >= input.anchor[axis] &&
              actual < input.anchor[axis] + maxSize[axis],
          );
        }
      }
      assert.ok(
        fittingMechanisms({ ...input, maxSize }).some(
          (option) =>
            option.template === template &&
            option.rotation === rotation &&
            option.maxDelay === 1,
        ),
      );
      assert.throws(() =>
        makeMechanismBlueprint(
          { ...input, maxSize: { ...maxSize, y: 2 } },
          plan,
        ),
      );
      assert.throws(() => validatePlan({ ...plan, parameters: { delay: 2 } }));
      assert.throws(() =>
        makeMechanismBlueprint(
          {
            ...input,
            revision: {
              template,
              rotation: ((rotation + 90) % 360) as 0 | 90 | 180 | 270,
            },
          },
          plan,
        ),
      );
    }
});

test("analog planning does not crowd out legacy electrical options or accept foreign identity", () => {
  const choices = fittingMechanisms({
    ...input,
    projectId: "legacy-project",
    maxSize: { x: 3, y: 2, z: 3 },
  });
  assert.ok(choices.some((option) => option.template === "signal-lamp"));
  assert.ok(
    choices.every((option) => !option.template.startsWith("comparator-")),
  );
  assert.throws(() =>
    makeMechanismBlueprint(
      { ...input, projectId: "legacy-project" },
      {
        template: "comparator-compare",
        explanation: "Cannot weaken identity for analog.",
      },
    ),
  );
  assert.throws(() =>
    validatePlan({
      template: "comparator-invented",
      explanation: "Unsupported comparator mode.",
    }),
  );
});
