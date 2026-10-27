import { randomUUID } from "node:crypto";
import {
  CATALOG_REVISION,
  adaptation,
  mechanism,
} from "./catalog.js";
import { rotateBlockState, rotatePosition } from "./geometry.js";
import {
  type Blueprint,
  type ComponentMechanismNode,
  type ComponentNode,
  type ComponentTree,
  type DesignRequest,
  type Position,
  type Rotation,
  RequestError,
  identifier,
  object,
  position,
  text,
} from "./types.js";

const ID_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/u;
const TEMPLATES = ["signal-lamp", "not-gate", "or-gate"] as const;

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stable(record[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value) ?? "undefined";
}

function keys(value: Record<string, unknown>, allowed: string[]): void {
  if (Object.keys(value).some((key) => !allowed.includes(key)))
    throw new RequestError("Component node contains an unknown field.");
}

function componentId(value: unknown): string {
  const id = text(value, "component id", 32);
  if (!ID_PATTERN.test(id)) throw new RequestError("Invalid component id.");
  return id;
}

function rotation(value: unknown): Rotation {
  if (
    typeof value !== "number" ||
    ![0, 90, 180, 270].includes(value)
  )
    throw new RequestError("Component rotation must be 0, 90, 180 or 270.");
  return value as Rotation;
}

/** Validates the bounded organizational tree and its leaf dependency DAG. */
export function validateComponentTree(value: unknown): ComponentTree {
  const tree = object(value);
  keys(tree, ["schemaVersion", "nodes"]);
  if (tree.schemaVersion !== 1 || !Array.isArray(tree.nodes))
    throw new RequestError("Invalid component tree schema.");
  if (tree.nodes.length < 1 || tree.nodes.length > 64)
    throw new RequestError("A component tree must contain 1–64 nodes.");

  const seen = new Map<string, ComponentNode>();
  const nodes: ComponentNode[] = tree.nodes.map((entry: unknown) => {
    const raw = object(entry),
      id = componentId(raw.id);
    if (seen.has(id)) throw new RequestError("Duplicate component id.");
    if (raw.kind === "group") {
      keys(raw, ["id", "parent", "kind"]);
      if (id === "root") {
        if (raw.parent !== undefined)
          throw new RequestError("The root group cannot have a parent.");
        const node: ComponentNode = { id, kind: "group" };
        seen.set(id, node);
        return node;
      }
      const node: ComponentNode = {
        id,
        kind: "group",
        parent: componentId(raw.parent),
      };
      seen.set(id, node);
      return node;
    }
    if (raw.kind !== "mechanism")
      throw new RequestError("Invalid component node kind.");
    keys(raw, [
      "id",
      "parent",
      "kind",
      "template",
      "anchor",
      "rotation",
      "parameters",
      "dependsOn",
    ]);
    const template = text(raw.template, "mechanism template", 64);
    if (!(TEMPLATES as readonly string[]).includes(template))
      throw new RequestError("Unsupported component mechanism.", 422);
    const params = object(raw.parameters);
    if (Object.keys(params).some((key) => key !== "delay"))
      throw new RequestError("Unknown component mechanism parameter.");
    if (!Array.isArray(raw.dependsOn) || raw.dependsOn.length > 16)
      throw new RequestError("Invalid component dependencies.");
    const dependsOn = raw.dependsOn.map(componentId);
    if (new Set(dependsOn).size !== dependsOn.length || dependsOn.includes(id))
      throw new RequestError("Component dependencies must be unique and non-self.");
    const node: ComponentMechanismNode = {
      id,
      parent: componentId(raw.parent),
      kind: "mechanism",
      template: template as ComponentMechanismNode["template"],
      anchor: position(raw.anchor),
      rotation: rotation(raw.rotation),
      parameters: adaptation(template, params),
      dependsOn,
    };
    seen.set(id, node);
    return node;
  });

  const root = seen.get("root");
  if (!root || root.kind !== "group")
    throw new RequestError("A component tree requires a root group named root.");
  const groups = new Set(
    nodes.filter((node) => node.kind === "group").map((node) => node.id),
  );
  const leaves = new Map(
    nodes
      .filter((node): node is ComponentMechanismNode => node.kind === "mechanism")
      .map((node) => [node.id, node]),
  );
  if (leaves.size < 1 || leaves.size > 16)
    throw new RequestError("A component tree must contain 1–16 mechanisms.");
  for (const node of nodes) {
    if (node.id !== "root" && !groups.has(node.parent!))
      throw new RequestError("Every component must be parented by a group.");
    let current: ComponentNode | undefined = node,
      depth = 0;
    const path = new Set<string>();
    while (current && current.id !== "root") {
      if (path.has(current.id)) throw new RequestError("Component parent cycle.");
      path.add(current.id);
      const parent = current.parent;
      current = parent ? seen.get(parent) : undefined;
      depth++;
      if (!current) throw new RequestError("Component parent is missing.");
      if (depth > 8) throw new RequestError("Component tree depth exceeds 8.");
    }
  }
  for (const leaf of leaves.values())
    for (const dependency of leaf.dependsOn)
      if (!leaves.has(dependency))
        throw new RequestError("A dependency must reference another mechanism leaf.");

  const visiting = new Set<string>(), visited = new Set<string>();
  const visit = (id: string) => {
    if (visiting.has(id)) throw new RequestError("Component dependency cycle.");
    if (visited.has(id)) return;
    visiting.add(id);
    for (const dep of leaves.get(id)!.dependsOn) visit(dep);
    visiting.delete(id);
    visited.add(id);
  };
  for (const id of leaves.keys()) visit(id);
  return { schemaVersion: 1, nodes };
}

export const parseComponentTree = validateComponentTree;

function leafOrder(tree: ComponentTree): ComponentMechanismNode[] {
  const leaves = new Map(
    tree.nodes
      .filter((node): node is ComponentMechanismNode => node.kind === "mechanism")
      .map((node) => [node.id, node]),
  );
  const ordered: ComponentMechanismNode[] = [], visited = new Set<string>();
  const visit = (node: ComponentMechanismNode) => {
    if (visited.has(node.id)) return;
    node.dependsOn.forEach((id) => visit(leaves.get(id)!));
    visited.add(node.id);
    ordered.push(node);
  };
  for (const node of leaves.values()) visit(node);
  return ordered;
}

function content(tree: ComponentTree, wholeAnchor: Position, wholeRotation: Rotation) {
  const blocks: Blueprint["blocks"] = [],
    stages: Blueprint["stages"] = [],
    ports: Blueprint["ports"] = [],
    tests: Blueprint["tests"] = [],
    resources: Record<string, number> = {};
  const occupied = new Set<string>();
  const finalStages = new Map<string, string>();
  for (const node of leafOrder(tree)) {
    const catalog = mechanism(node.template),
      delay = adaptation(catalog.id, node.parameters).delay,
      leafStages = catalog.stages.map((stage) => ({
        id: `${node.id}/${stage.id}`,
        title: stage.title,
        explanation: stage.explanation,
        prerequisites: [
          ...stage.prerequisites.map((id) => `${node.id}/${id}`),
          ...(stage.id === catalog.stages[0]!.id
            ? node.dependsOn.map((id) => finalStages.get(id)!)
            : []),
        ],
      }));
    stages.push(...leafStages);
    finalStages.set(node.id, leafStages.at(-1)!.id);
    for (const block of catalog.blocks) {
      const rotated = rotatePosition(block.pos, node.rotation),
        pos = position({
          x: node.anchor.x + rotated.x,
          y: node.anchor.y + rotated.y,
          z: node.anchor.z + rotated.z,
        }),
        stage = `${node.id}/${block.stage}`,
        prerequisites = [
          ...catalog.stages.find((s) => s.id === block.stage)!.prerequisites.map(
            (id) => `${node.id}/${id}`,
          ),
          ...(block.stage === catalog.stages[0]!.id
            ? node.dependsOn.map((id) => finalStages.get(id)!)
            : []),
        ],
        state = rotateBlockState(
          block.state.replace(/delay=\d/u, `delay=${delay}`),
          node.rotation,
        );
      if (Math.max(Math.abs(pos.x), Math.abs(pos.y), Math.abs(pos.z)) > 64)
        throw new RequestError("Composed component geometry exceeds 64 blocks from the blueprint anchor.");
      const key = `${pos.x},${pos.y},${pos.z}`;
      if (occupied.has(key))
        throw new RequestError("Composed component mechanisms collide.", 422);
      occupied.add(key);
      const world = rotatePosition(pos, wholeRotation);
      if (wholeAnchor.y + world.y > 2047 || wholeAnchor.y + world.y < -2048)
        throw new RequestError("Composed mechanism exceeds supported world bounds.");
      blocks.push({ pos, state, stage, component: node.id, prerequisites });
      const kind = state.split("[", 1)[0]!,
        item = kind === "minecraft:redstone_wall_torch" ? "minecraft:redstone_torch" : kind;
      resources[item] = (resources[item] ?? 0) + 1;
    }
    for (const port of catalog.ports) {
      const rotated = rotatePosition(port.pos, node.rotation);
      ports.push({
        name: `${node.id}/${port.name}`,
        pos: position({
          x: node.anchor.x + rotated.x,
          y: node.anchor.y + rotated.y,
          z: node.anchor.z + rotated.z,
        }),
        kind: port.kind,
      });
    }
    tests.push({
      id: `${node.id}/functional-vectors`,
      description:
        "Exercise each catalog truth-table vector at actual server ticks, then restore every input.",
    });
  }
  if (blocks.length > 4096)
    throw new RequestError("Composed blueprint exceeds 4096 blocks.");
  if (stages.length > 64)
    throw new RequestError("Composed blueprint exceeds 64 stages.");
  const min = {
      x: Math.min(...blocks.map((block) => block.pos.x)),
      y: Math.min(...blocks.map((block) => block.pos.y)),
      z: Math.min(...blocks.map((block) => block.pos.z)),
    },
    max = {
      x: Math.max(...blocks.map((block) => block.pos.x)),
      y: Math.max(...blocks.map((block) => block.pos.y)),
      z: Math.max(...blocks.map((block) => block.pos.z)),
    };
  if (
    max.x - min.x + 1 > 64 ||
    max.y - min.y + 1 > 64 ||
    max.z - min.z + 1 > 64 ||
    (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1) > 4096
  )
    throw new RequestError("Composed blueprint extent exceeds supported limits.");
  if (
    blocks.some((block) => {
      const rotated = rotatePosition(block.pos, wholeRotation);
      return (
        wholeAnchor.x + rotated.x < -29_999_984 ||
        wholeAnchor.x + rotated.x > 29_999_984 ||
        wholeAnchor.z + rotated.z < -29_999_984 ||
        wholeAnchor.z + rotated.z > 29_999_984
      );
    })
  )
    throw new RequestError("Composed mechanism exceeds supported world bounds.");
  return { blocks, stages, bounds: { min, max }, resources, ports, tests };
}

/** Builds a composite exclusively from trusted catalog mechanisms. */
export function makeComponentBlueprint(
  input: DesignRequest,
  nodes: ComponentNode[],
): Blueprint {
  const components = validateComponentTree({ schemaVersion: 1, nodes }),
    generated = content(components, input.anchor, input.rotation),
    wholeBounds = generated.blocks.map((block) => ({
      pos: rotatePosition(block.pos, input.rotation),
    })),
    size = {
      x: Math.max(...wholeBounds.map((block) => block.pos.x)) - Math.min(...wholeBounds.map((block) => block.pos.x)) + 1,
      y: Math.max(...wholeBounds.map((block) => block.pos.y)) - Math.min(...wholeBounds.map((block) => block.pos.y)) + 1,
      z: Math.max(...wholeBounds.map((block) => block.pos.z)) - Math.min(...wholeBounds.map((block) => block.pos.z)) + 1,
    };
  if (
    input.maxSize &&
    (size.x > input.maxSize.x ||
      size.y > input.maxSize.y ||
      size.z > input.maxSize.z)
  )
    throw new RequestError("The composed project does not fit the selected dimensions.", 422);
  return {
    schemaVersion: 1,
    id: randomUUID(),
    revision: 1,
    projectId: input.projectId ?? randomUUID(),
    worldId: input.worldId,
    dimension: input.dimension,
    name: "Component project",
    explanation: "Mechanism leaves are generated from the trusted catalog. Port labels describe leaf interfaces; physical connections require separate in-world verification.",
    anchor: { ...input.anchor },
    rotation: input.rotation,
    ...generated,
    provenance: {
      kind: "curated",
      template: "composition",
      minecraft: "26.3",
      catalogRevision: CATALOG_REVISION,
    },
    components,
    verification: "untested",
  };
}

/** Rebuilds canonical composite content and rejects any drift from catalog truth. */
export function validateComponentBlueprint(value: unknown): Blueprint {
  const v = object(value),
    components = validateComponentTree(v.components),
    anchor = position(v.anchor),
    turn = rotation(v.rotation),
    generated = content(components, anchor, turn);
  for (const field of ["blocks", "stages", "bounds", "resources", "ports", "tests"] as const)
    if (stable(v[field]) !== stable(generated[field]))
      throw new RequestError(`Composite blueprint ${field} do not match its trusted component tree.`);
  if (v.parameters !== undefined)
    throw new RequestError("Composite blueprints cannot have top-level parameters.");
  return { ...v, ...generated, components } as Blueprint;
}

