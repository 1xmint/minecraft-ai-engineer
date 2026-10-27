import assert from "node:assert/strict";
import { test } from "node:test";
import {
  makeComponentBlueprint,
  validateComponentTree,
} from "../companion/src/components.js";
import {
  serverBlueprint,
  type GatewayContext,
} from "../companion/src/gateway-types.js";
import type { ComponentNode, DesignRequest } from "../companion/src/types.js";

const input: DesignRequest = {
  projectId: "components",
  worldId: "world",
  dimension: "minecraft:overworld",
  request: "Build independent mechanisms",
  anchor: { x: 60, y: 100, z: 0 },
  rotation: 0,
};
const nodes: ComponentNode[] = [
  { id: "root", kind: "group" },
  {
    id: "a",
    parent: "root",
    kind: "mechanism",
    template: "signal-lamp",
    anchor: { x: 0, y: 0, z: 0 },
    rotation: 0,
    parameters: { delay: 1 },
    dependsOn: [],
  },
  {
    id: "b",
    parent: "root",
    kind: "mechanism",
    template: "not-gate",
    anchor: { x: 8, y: 0, z: 0 },
    rotation: 90,
    parameters: { delay: 1 },
    dependsOn: ["a"],
  },
];
const context: GatewayContext = {
  serverId: "server",
  serverEpoch: "epoch",
  actorId: "11111111-1111-4111-8111-111111111111",
  projectId: "components",
  projectRevision: 1,
  authorizationRevision: 1,
  requestId: "request",
};

test("composed mechanisms preserve pivot geometry, dependency order and canonical durable mirrors", () => {
  // Deliberately declare the dependant before its prerequisite.
  const project = makeComponentBlueprint(input, [
    nodes[0]!,
    nodes[2]!,
    nodes[1]!,
  ]);
  assert.equal(project.blocks.length, 10);
  assert.equal(project.blocks[0]?.component, "a");
  assert.deepEqual(
    project.stages.find((stage) => stage.id === "b/foundation")?.prerequisites,
    ["a/output"],
  );
  assert.deepEqual(project.resources, {
    "minecraft:stone": 4,
    "minecraft:lever": 2,
    "minecraft:repeater": 1,
    "minecraft:redstone_lamp": 2,
    "minecraft:redstone_torch": 1,
  });
  assert.ok(
    project.ports.some((port) => port.name === "b/enable" && port.pos.x === 8),
  );
  assert.deepEqual(
    serverBlueprint(project, context, input.worldId, input.dimension),
    project,
  );
});

test("catalog-backed composition rejects forged geometry, resources, ports and dependency cycles", () => {
  const project = makeComponentBlueprint(input, nodes);
  for (const alter of [
    (copy: typeof project) => {
      copy.blocks[0]!.state = "minecraft:tnt";
    },
    (copy: typeof project) => {
      copy.blocks[0]!.component = "b";
    },
    (copy: typeof project) => {
      copy.resources["minecraft:stone"] = 999;
    },
    (copy: typeof project) => {
      copy.ports[0]!.pos.x = 30;
    },
  ]) {
    const forged = structuredClone(project);
    alter(forged);
    assert.throws(() =>
      serverBlueprint(forged, context, input.worldId, input.dimension),
    );
  }
  const cycle = structuredClone(nodes);
  if (cycle[1]?.kind === "mechanism") cycle[1].dependsOn = ["b"];
  assert.throws(() =>
    validateComponentTree({ schemaVersion: 1, nodes: cycle }),
  );
  const collision = structuredClone(nodes);
  if (collision[2]?.kind === "mechanism")
    collision[2].anchor = { x: 0, y: 0, z: 0 };
  assert.throws(() => makeComponentBlueprint(input, collision));
});
