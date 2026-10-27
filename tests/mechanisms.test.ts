import test from "node:test";
import assert from "node:assert/strict";
import { validatePlan } from "../companion/src/provider.js";
import {
  MECHANISMS,
  makeMechanismBlueprint,
  fittingMechanisms,
} from "../companion/src/catalog.js";
import { rotatePosition } from "../companion/src/geometry.js";
import { serverBlueprint } from "../companion/src/gateway-types.js";
import { readFileSync } from "node:fs";

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

const input = {
  request: "Choose a useful mechanism",
  worldId: "world",
  dimension: "minecraft:overworld",
  anchor: { x: 10, y: 100, z: 10 },
  rotation: 0 as const,
  maxSize: { x: 3, y: 2, z: 3 },
};
test("catalog recipes carry ports, prerequisites and resources and fit all four rotations", () => {
  for (const rotation of [0, 90, 180, 270] as const) {
    const blueprint = makeMechanismBlueprint(input, {
      template: "or-gate",
      explanation: "Either input powers the lamp.",
      parameters: { delay: 4 },
      rotation,
    });
    assert.equal(blueprint.blocks.length, 10);
    assert.equal(blueprint.ports.filter((p) => p.kind === "input").length, 2);
    assert.equal(
      Object.values(blueprint.resources).reduce((a, b) => a + b, 0),
      10,
    );
    for (const block of blueprint.blocks) {
      const relative = rotatePosition(block.pos, rotation);
      for (const axis of ["x", "y", "z"] as const) {
        const absolute = blueprint.anchor[axis] + relative[axis];
        assert.ok(
          absolute >= input.anchor[axis] &&
            absolute < input.anchor[axis] + input.maxSize[axis],
        );
      }
    }
    assert.ok(
      blueprint.blocks
        .filter((b) => b.state.startsWith("minecraft:repeater"))
        .every((b) => b.state.includes("delay=4")),
    );
    assert.deepEqual(blueprint.stages[2]!.prerequisites, [
      "foundation",
      "input",
    ]);
    assert.equal(blueprint.verification, "untested");
  }
  assert.ok(
    fittingMechanisms({ ...input, maxSize: { x: 1, y: 2, z: 3 } }).some(
      (f) => f.template === "signal-lamp" && f.rotation === 90,
    ),
  );
  assert.throws(() =>
    fittingMechanisms({ ...input, maxSize: { x: 1, y: 1, z: 1 } }),
  );
});

test("inverter construction orders its output before its torch and uses the real torch item", () => {
  const blueprint = makeMechanismBlueprint(input, {
    template: "not-gate",
    explanation: "Invert the input.",
  });
  assert.ok(
    blueprint.blocks.findIndex((b) =>
      b.state.startsWith("minecraft:redstone_lamp"),
    ) <
      blueprint.blocks.findIndex((b) =>
        b.state.startsWith("minecraft:redstone_wall_torch"),
      ),
  );
  assert.equal(blueprint.resources["minecraft:redstone_torch"], 1);
  assert.equal(blueprint.resources["minecraft:redstone_wall_torch"], undefined);
  assert.equal(blueprint.provenance.catalogRevision, 1);
});

test("gateway mirrors new contracts without rewriting old immutable revision metadata", () => {
  for (const m of MECHANISMS) {
    const blueprint = makeMechanismBlueprint(input, {
      template: m.id,
      explanation: m.explanation,
    });
    const context = {
      serverId: "server",
      serverEpoch: "epoch",
      actorId: "00000000-0000-0000-0000-000000000001",
      projectId: blueprint.projectId,
      projectRevision: blueprint.revision,
      authorizationRevision: 1,
      requestId: "request",
    };
    assert.deepEqual(
      serverBlueprint(blueprint, context, input.worldId, input.dimension),
      blueprint,
    );
    const legacy = structuredClone(blueprint);
    delete legacy.parameters;
    delete legacy.provenance.catalogRevision;
    for (const stage of legacy.stages) delete stage.prerequisites;
    const mirrored = serverBlueprint(
      legacy,
      context,
      input.worldId,
      input.dimension,
    );
    assert.equal("parameters" in mirrored, false);
    assert.equal("catalogRevision" in mirrored.provenance, false);
    assert.ok(mirrored.stages.every((stage) => !("prerequisites" in stage)));
  }
});

test("compiled companion carries its canonical mechanism data", () => {
  const packaged = JSON.parse(
    readFileSync(
      new URL("../catalog/mechanisms.json", import.meta.url),
      "utf8",
    ),
  );
  assert.deepEqual(
    packaged.mechanisms.map((m: { id: string }) => m.id),
    MECHANISMS.map((m) => m.id),
  );
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
