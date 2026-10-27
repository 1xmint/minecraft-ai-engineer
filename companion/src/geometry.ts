import type { Position, Rotation } from "./types.js";

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
