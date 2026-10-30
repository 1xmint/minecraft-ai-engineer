import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  makeShutterDesign,
  readShutterDesign,
  shutterFrame,
} from "../companion/src/shutter-design.js";
import {
  MECHANISMS,
  PLANNING_MECHANISMS,
  fittingMechanisms,
  makeMechanismBlueprint,
} from "../companion/src/catalog.js";
import { validatePlan, PLAN_SCHEMA } from "../companion/src/provider.js";
import { validateComponentTree } from "../companion/src/components.js";
import {
  serverBlueprint,
  type GatewayContext,
} from "../companion/src/gateway-types.js";
const input = {
  request: "Close the aperture when the lever is on",
  projectId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  worldId: "contract-proof",
  dimension: "minecraft:overworld",
  anchor: { x: 100, y: 100, z: 100 },
  rotation: 0 as const,
};
const identity = { id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", revision: 2 };
test("server sync preserves canonical mechanical intention and fences its project identity", () => {
  const design = makeShutterDesign(input, identity);
  const context: GatewayContext = {
    serverId: "server",
    serverEpoch: "epoch",
    actorId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
    projectId: input.projectId,
    projectRevision: 2,
    authorizationRevision: 1,
    requestId: "request",
  };
  assert.deepEqual(
    serverBlueprint(design, context, input.worldId, input.dimension),
    design,
  );
  for (const change of [
    { projectId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd" },
    { projectRevision: 3 },
  ])
    assert.throws(() =>
      serverBlueprint(
        design,
        { ...context, ...change },
        input.worldId,
        input.dimension,
      ),
    );
  const forged = structuredClone(design);
  forged.mechanical.on = "open" as any;
  assert.throws(() =>
    serverBlueprint(forged, context, input.worldId, input.dimension),
  );
  const accepted = serverBlueprint(
    design,
    context,
    input.worldId,
    input.dimension,
  );
  design.blocks[0]!.pos.x = 99;
  assert.equal(accepted.blocks[0]!.pos.x, 0);
});
test("canonical shutter preserves an empty aperture and derives the accepted frame in each rotation", () => {
  const expected = [
    [[101, 100, 100], [103, 100, 100], "east"],
    [[100, 100, 101], [100, 100, 103], "south"],
    [[102, 100, 100], [100, 100, 100], "west"],
    [[100, 100, 102], [100, 100, 100], "north"],
  ];
  for (const [index, rotation] of ([0, 90, 180, 270] as const).entries()) {
    const design = makeShutterDesign({ ...input, rotation }, identity),
      frame = shutterFrame(design);
    assert.deepEqual(
      [
        Object.values(frame.piston),
        Object.values(frame.aperture),
        frame.facing,
      ],
      expected[index],
    );
    assert.equal(design.blocks.length, 4);
    assert.equal(design.verification, "untested");
    assert.equal(
      design.blocks.some((block) => block.pos.x === 3),
      false,
      "The aperture is reserved air, never a hidden placed output block",
    );
    assert.equal(design.bounds.max.x, 3);
    assert.deepEqual(readShutterDesign(design), design);
  }
  assert.equal(
    MECHANISMS.some((mechanism) => String(mechanism.id) === "sticky-shutter"),
    false,
    "Mechanical choices do not expand the electrical component recipe library",
  );
});
test("shutter planning uses the exact canonical recipe and reserves aperture dimensions", () => {
  const plan = validatePlan({
    template: "sticky-shutter",
    explanation: "Use one bounded shutter.",
    parameters: { delay: 1 },
    rotation: 0,
  });
  assert.ok(PLAN_SCHEMA.properties.template.enum.includes("sticky-shutter"));
  assert.ok(PLANNING_MECHANISMS.some((m) => m.template === "sticky-shutter"));
  const workspace = { ...input, maxSize: { x: 4, y: 2, z: 1 } };
  const blueprint = makeMechanismBlueprint(workspace, plan);
  assert.deepEqual(readShutterDesign(blueprint), blueprint);
  assert.equal(blueprint.verification, "untested");
  assert.equal(blueprint.projectId, input.projectId);
  assert.deepEqual(
    fittingMechanisms({
      ...workspace,
      revision: { template: "sticky-shutter", rotation: 0 },
    }),
    [{ template: "sticky-shutter", rotation: 0, maxDelay: 1 }],
  );
  assert.throws(
    () =>
      makeMechanismBlueprint(
        { ...workspace, maxSize: { x: 3, y: 2, z: 1 } },
        plan,
      ),
    /movement space/,
  );
  assert.throws(() => validatePlan({ ...plan, parameters: { delay: 2 } }));
  assert.throws(() =>
    validatePlan({ ...plan, parameters: { delay: 1, commands: "build" } }),
  );
  assert.throws(
    () =>
      makeMechanismBlueprint(
        { ...workspace, revision: { template: "not-gate", rotation: 0 } },
        plan,
      ),
    /revision must keep/,
  );
  assert.throws(() =>
    validateComponentTree({
      schemaVersion: 1,
      nodes: [
        { id: "root", kind: "group" },
        {
          id: "shutter",
          parent: "root",
          kind: "mechanism",
          template: "sticky-shutter",
          anchor: { x: 0, y: 0, z: 0 },
          rotation: 0,
          parameters: { delay: 1 },
          dependsOn: [],
        },
      ],
    }),
  );
});
test("shutter contract rejects rewritten behavior, geometry, resources and authority", () => {
  const original = makeShutterDesign(input, identity);
  const changes = [
    (d: any) => (d.mechanical.settleGameTicks = 100),
    (d: any) => (d.mechanical.off = "closed"),
    (d: any) => (d.mechanical.piston.x = 2),
    (d: any) =>
      (d.blocks[2].state = "minecraft:piston[extended=false,facing=east]"),
    (d: any) => (d.resources["minecraft:stone"] = 99),
    (d: any) => (d.bounds.max.x = 2),
    (d: any) => (d.verification = "passed"),
    (d: any) => (d.authority = true),
  ];
  for (const change of changes) {
    const value = structuredClone(original);
    change(value);
    assert.throws(() => readShutterDesign(value));
  }
  const accepted = readShutterDesign(original);
  original.blocks[0]!.pos.x = 9;
  assert.equal(accepted.blocks[0]!.pos.x, 0);
  assert.throws(() =>
    makeShutterDesign({ ...input, maxSize: { x: 3, y: 2, z: 1 } }, identity),
  );
  assert.throws(() =>
    makeShutterDesign(
      { ...input, anchor: { x: 29_999_984, y: 100, z: 0 } },
      identity,
    ),
  );
});
test("shared mechanical source fixture retains exact canonical metadata", () => {
  const fixture = JSON.parse(
    readFileSync(
      new URL("../../tests/fixtures/shutter-design.json", import.meta.url),
      "utf8",
    ),
  );
  assert.deepEqual(
    readShutterDesign(fixture),
    makeShutterDesign({ ...input, rotation: 90 }, identity),
  );
  assert.equal(shutterFrame(fixture).facing, "south");
});
