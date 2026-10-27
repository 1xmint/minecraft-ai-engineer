import { createHash } from "node:crypto";
import { validateComponentBlueprint } from "./components.js";
import { rotateBlockState, rotatePosition } from "./geometry.js";
import type { Blueprint, Position } from "./types.js";

export interface ComponentMemoryScope {
  /** Service-local comparison of canonical intent, never an execution credential. */
  intention: string;
  reads: Position[];
}
export const positionKey = (pos: Position): string =>
  `${pos.x},${pos.y},${pos.z}`;

/** Derive scopes from the immutable catalog mirror, never from reported task metadata. */
export function componentMemoryScopes(
  blueprint: Blueprint | undefined,
): Map<string, ComponentMemoryScope> {
  const scopes = new Map<string, ComponentMemoryScope>();
  if (!blueprint?.components) return scopes;
  const canonical = validateComponentBlueprint(blueprint);
  const leaves = canonical.components!.nodes.filter(
    (node) => node.kind === "mechanism",
  );
  const byId = new Map(leaves.map((node) => [node.id, node]));
  const own = new Map(
    leaves.map((node) => [
      node.id,
      canonical.blocks
        .filter((block) => block.component === node.id)
        .map((block) => {
          const offset = rotatePosition(block.pos, canonical.rotation);
          return {
            pos: {
              x: canonical.anchor.x + offset.x,
              y: canonical.anchor.y + offset.y,
              z: canonical.anchor.z + offset.z,
            },
            state: rotateBlockState(block.state, canonical.rotation),
          };
        }),
    ]),
  );
  const visit = (id: string): ComponentMemoryScope => {
    const cached = scopes.get(id);
    if (cached) return cached;
    const node = byId.get(id)!;
    const dependencies = [...node.dependsOn]
      .sort()
      .map((dependency) => [dependency, visit(dependency)] as const);
    const blocks = own.get(id)!;
    const intention = createHash("sha256")
      .update(
        JSON.stringify({
          version: 1,
          world: canonical.worldId,
          dimension: canonical.dimension,
          id,
          template: node.template,
          parameters: node.parameters,
          blocks,
          dependencies: dependencies.map(([dependency, scope]) => [
            dependency,
            scope.intention,
          ]),
        }),
      )
      .digest("hex");
    const reads = new Map(
      blocks.map((block) => [positionKey(block.pos), block.pos]),
    );
    for (const [, scope] of dependencies)
      for (const pos of scope.reads) reads.set(positionKey(pos), pos);
    const scope = { intention, reads: [...reads.values()] };
    scopes.set(id, scope);
    return scope;
  };
  for (const node of leaves) visit(node.id);
  return scopes;
}

export function readsPosition(
  scope: ComponentMemoryScope,
  pos: Position,
): boolean {
  return scope.reads.some(
    (block) =>
      Math.abs(block.x - pos.x) <= 2 &&
      Math.abs(block.y - pos.y) <= 2 &&
      Math.abs(block.z - pos.z) <= 2,
  );
}

/** Same geometry comparison as Fabric Blueprint.matches: ordinary power changes are dynamic. */
export function geometryState(state: string): string {
  const [kind, properties] = state.replace(/\]$/u, "").split("[");
  const stable = (properties?.split(",") ?? [])
    .filter(
      (property) =>
        !["powered", "lit", "power", "locked"].includes(
          property.split("=")[0]!,
        ),
    )
    .sort();
  return `${kind}[${stable.join(",")}]`;
}

export function expectedWorldStates(
  blueprint: Blueprint | undefined,
): Map<string, string> {
  return new Map(
    blueprint?.blocks.map((block) => {
      const offset = rotatePosition(block.pos, blueprint.rotation);
      const pos = {
        x: blueprint.anchor.x + offset.x,
        y: blueprint.anchor.y + offset.y,
        z: blueprint.anchor.z + offset.z,
      };
      return [
        positionKey(pos),
        geometryState(rotateBlockState(block.state, blueprint.rotation)),
      ];
    }),
  );
}
