import { randomUUID } from "node:crypto";
import {
  type Blueprint,
  type DesignRequest,
  type Position,
  type Rotation,
  RequestError,
} from "./types.js";

export function rotatePosition(pos: Position, rotation: Rotation): Position {
  let { x, z } = pos;
  for (let i = 0; i < rotation / 90; i++) [x, z] = [-z, x];
  return { x: x === 0 ? 0 : x, y: pos.y, z: z === 0 ? 0 : z };
}
const DIRECTIONS = ["north", "east", "south", "west"];
export function rotateBlockState(state: string, rotation: Rotation): string {
  return state.replace(
    /\[([^\]]+)\]/u,
    (_whole: string, properties: string) =>
      `[${properties
        .split(",")
        .map((pair) => {
          let [key, value] = pair.split("=") as [string, string];
          const keyIndex = DIRECTIONS.indexOf(key),
            valueIndex = DIRECTIONS.indexOf(value);
          if (keyIndex >= 0) key = DIRECTIONS[(keyIndex + rotation / 90) % 4]!;
          if (valueIndex >= 0)
            value = DIRECTIONS[(valueIndex + rotation / 90) % 4]!;
          if (
            key === "axis" &&
            rotation % 180 === 90 &&
            (value === "x" || value === "z")
          )
            value = value === "x" ? "z" : "x";
          return `${key}=${value}`;
        })
        .sort()
        .join(",")}]`,
  );
}
export function makeReferenceBlueprint(
  input: DesignRequest,
  explanation = "A lever powers a repeater whose output points east, which powers the lamp. The repeater isolates the output and introduces one redstone tick of delay.",
): Blueprint {
  const size =
    input.rotation % 180 === 0 ? { x: 3, y: 2, z: 1 } : { x: 1, y: 2, z: 3 };
  if (
    input.maxSize &&
    (size.x > input.maxSize.x ||
      size.y > input.maxSize.y ||
      size.z > input.maxSize.z)
  )
    throw new RequestError(
      "The supported signal lamp does not fit the selected dimensions.",
      422,
    );
  const projectId = input.projectId ?? randomUUID();
  const blocks: Blueprint["blocks"] = [0, 1, 2].map((x) => ({
    pos: { x, y: 0, z: 0 },
    state: "minecraft:stone",
    stage: "foundation",
    component: "support",
    prerequisites: [],
  }));
  blocks.push(
    {
      pos: { x: 0, y: 1, z: 0 },
      state: "minecraft:lever[face=floor,facing=east,powered=false]",
      stage: "input",
      component: "input",
      prerequisites: ["foundation"],
    },
    {
      pos: { x: 1, y: 1, z: 0 },
      state:
        "minecraft:repeater[delay=1,facing=west,locked=false,powered=false]",
      stage: "output",
      component: "output",
      prerequisites: ["foundation", "input"],
    },
    {
      pos: { x: 2, y: 1, z: 0 },
      state: "minecraft:redstone_lamp[lit=false]",
      stage: "output",
      component: "output",
      prerequisites: ["foundation"],
    },
  );
  return {
    schemaVersion: 1,
    id: randomUUID(),
    revision: 1,
    projectId,
    worldId: input.worldId,
    dimension: input.dimension,
    name: "Signal lamp",
    explanation,
    anchor: input.anchor,
    rotation: input.rotation,
    blocks,
    stages: [
      {
        id: "foundation",
        title: "Build the foundation",
        explanation:
          "Place three stone supports in a straight line. They support the lever and repeater.",
      },
      {
        id: "input",
        title: "Add the input",
        explanation:
          "Place the lever on top of the first support. Leave it off while building.",
      },
      {
        id: "output",
        title: "Add the delayed output",
        explanation:
          "Place the repeater facing the lamp, then place the lamp at its output. The repeater arrow must point away from the lever.",
      },
    ],
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 2, y: 1, z: 0 } },
    resources: {
      "minecraft:stone": 3,
      "minecraft:lever": 1,
      "minecraft:repeater": 1,
      "minecraft:redstone_lamp": 1,
    },
    ports: [
      { name: "enable", pos: { x: 0, y: 1, z: 0 }, kind: "input" },
      { name: "light", pos: { x: 2, y: 1, z: 0 }, kind: "output" },
    ],
    tests: [
      {
        id: "off-on-off",
        description:
          "Within an isolated authorized region, turn the lever off, on, then off. Check the lamp at actual server ticks and restore the original lever state.",
      },
    ],
    provenance: { kind: "curated", template: "signal-lamp", minecraft: "26.3" },
    verification: "untested",
  };
}

export { validateDesignRequest } from "./types.js";
