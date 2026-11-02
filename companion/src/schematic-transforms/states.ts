import { formatBlockState, type BlockState } from "../schematics/index.js";
import { TransformError } from "./errors.js";

/**
 * Rotations are clockwise seen from above (north up, east right), matching
 * Minecraft's CLOCKWISE_90. Mirrors are named by the two directions they swap.
 */
export type GeometryTransform =
  | "rotate-90"
  | "rotate-180"
  | "rotate-270"
  | "mirror-east-west"
  | "mirror-north-south";

export const GEOMETRY_TRANSFORMS: readonly GeometryTransform[] = Object.freeze([
  "rotate-90",
  "rotate-180",
  "rotate-270",
  "mirror-east-west",
  "mirror-north-south",
]);

interface Operation {
  /** x' = m[0]x + m[1]z, z' = m[2]x + m[3]z, in world-relative coordinates. */
  readonly m: readonly [number, number, number, number];
  readonly mirrored: boolean;
  /** Maps a 16-step `rotation` value (0 = south, 4 = west, 8 = north, 12 = east). */
  readonly rotation16: (r: number) => number;
}

const OPERATIONS: Readonly<Record<GeometryTransform, Operation>> = {
  "rotate-90": {
    m: [0, -1, 1, 0],
    mirrored: false,
    rotation16: (r) => (r + 4) % 16,
  },
  "rotate-180": {
    m: [-1, 0, 0, -1],
    mirrored: false,
    rotation16: (r) => (r + 8) % 16,
  },
  "rotate-270": {
    m: [0, 1, -1, 0],
    mirrored: false,
    rotation16: (r) => (r + 12) % 16,
  },
  "mirror-east-west": {
    m: [-1, 0, 0, 1],
    mirrored: true,
    rotation16: (r) => (16 - r) % 16,
  },
  "mirror-north-south": {
    m: [1, 0, 0, -1],
    mirrored: true,
    rotation16: (r) => (24 - r) % 16,
  },
};

export function operation(transform: unknown): Operation {
  if (typeof transform !== "string" || !Object.hasOwn(OPERATIONS, transform))
    throw new TransformError(
      "E_TRANSFORM_ARGUMENT",
      `transform must be one of ${GEOMETRY_TRANSFORMS.join(", ")}`,
    );
  return OPERATIONS[transform as GeometryTransform];
}

const HORIZONTAL = ["north", "east", "south", "west"] as const;
type Horizontal = (typeof HORIZONTAL)[number];
const VECTORS: Readonly<Record<Horizontal, readonly [number, number]>> = {
  north: [0, -1],
  east: [1, 0],
  south: [0, 1],
  west: [-1, 0],
};

function isHorizontal(value: string): value is Horizontal {
  return (HORIZONTAL as readonly string[]).includes(value);
}

function turn(op: Operation, direction: Horizontal): Horizontal {
  const [x, z] = VECTORS[direction];
  const nx = op.m[0] * x + op.m[1] * z;
  const nz = op.m[2] * x + op.m[3] * z;
  return HORIZONTAL.find((h) => VECTORS[h][0] === nx && VECTORS[h][1] === nz)!;
}

function turnDirection(op: Operation, value: string): string | undefined {
  if (value === "up" || value === "down") return value;
  return isHorizontal(value) ? turn(op, value) : undefined;
}

/** Straight and corner rails, by the two directions they connect. Spellings are vanilla's. */
const RAIL_LINKS: Readonly<Record<string, readonly [Horizontal, Horizontal]>> =
  {
    north_south: ["north", "south"],
    east_west: ["east", "west"],
    north_east: ["north", "east"],
    north_west: ["north", "west"],
    south_east: ["south", "east"],
    south_west: ["south", "west"],
  };

function turnRail(op: Operation, shape: string): string | undefined {
  if (shape.startsWith("ascending_")) {
    const up = shape.slice("ascending_".length);
    return isHorizontal(up) ? `ascending_${turn(op, up)}` : undefined;
  }
  const links = RAIL_LINKS[shape];
  if (!links) return undefined;
  const a = turn(op, links[0]);
  const b = turn(op, links[1]);
  return Object.keys(RAIL_LINKS).find((name) => {
    const [p, q] = RAIL_LINKS[name]!;
    return (p === a && q === b) || (p === b && q === a);
  });
}

const STAIR_SHAPES = new Set([
  "straight",
  "inner_left",
  "inner_right",
  "outer_left",
  "outer_right",
]);

function swapHandedness(op: Operation, value: string): string {
  if (!op.mirrored) return value;
  if (value.endsWith("left")) return `${value.slice(0, -4)}right`;
  if (value.endsWith("right")) return `${value.slice(0, -5)}left`;
  return value;
}

/** Jigsaw and crafter orientation: "<front>_<top>". */
const ORIENTATIONS = new Set([
  "down_east",
  "down_north",
  "down_south",
  "down_west",
  "up_east",
  "up_north",
  "up_south",
  "up_west",
  "west_up",
  "east_up",
  "north_up",
  "south_up",
]);

/** `type` values that keep their meaning; `left`/`right` (chests) swap under a mirror. */
const TYPE_VALUES = new Set([
  "top",
  "bottom",
  "double",
  "normal",
  "sticky",
  "single",
  "left",
  "right",
]);

/**
 * Properties that mean the same thing whatever way the block is turned or
 * mirrored. Anything not listed here or handled below is rejected, so an
 * unfamiliar property can never be copied through unchanged by mistake.
 */
export const ORIENTATION_FREE_PROPERTIES: ReadonlySet<string> = new Set([
  "active",
  "age",
  "attached",
  "attachment",
  "berries",
  "bites",
  "bloom",
  "bottom",
  "can_summon",
  "candles",
  "charges",
  "conditional",
  "cracked",
  "crafting",
  "delay",
  "disarmed",
  "distance",
  "down",
  "drag",
  "dusted",
  "eggs",
  "enabled",
  "extended",
  "eye",
  "face",
  "flower_amount",
  "half",
  "hanging",
  "has_book",
  "has_record",
  "hatch",
  "honey_level",
  "in_wall",
  "instrument",
  "inverted",
  "layers",
  "leaves",
  "level",
  "lit",
  "locked",
  "mode",
  "moisture",
  "note",
  "occupied",
  "ominous",
  "open",
  "part",
  "persistent",
  "pickles",
  "power",
  "powered",
  "sculk_sensor_phase",
  "short",
  "shrieking",
  "signal_fire",
  "snowy",
  "stage",
  "thickness",
  "tilt",
  "tip",
  "trial_spawner_state",
  "triggered",
  "unstable",
  "up",
  "vault_state",
  "vertical_direction",
  "waterlogged",
]);

/** Properties that are rewritten, with the rule used for each. */
export const TRANSFORMED_PROPERTIES: ReadonlySet<string> = new Set([
  "facing",
  "axis",
  "rotation",
  "north",
  "east",
  "south",
  "west",
  "shape",
  "hinge",
  "type",
  "orientation",
]);

const ROTATION16 = /^(0|[1-9]|1[0-5])$/;

function unsupported(state: BlockState, why: string): never {
  throw new TransformError(
    "E_UNSUPPORTED_STATE",
    `${formatBlockState(state)}: ${why}; it is outside the supported transform subset`,
  );
}

function transformProperty(
  state: BlockState,
  op: Operation,
  key: string,
  value: string,
): [string, string] {
  if (ORIENTATION_FREE_PROPERTIES.has(key)) return [key, value];
  const bad = (): never =>
    unsupported(state, `property ${key}=${value} has an unexpected value`);
  if (isHorizontal(key)) return [turn(op, key), value];
  switch (key) {
    case "facing":
      return [key, turnDirection(op, value) ?? bad()];
    case "axis":
      if (value === "y") return [key, value];
      if (value !== "x" && value !== "z") return bad();
      return [key, op.m[0] === 0 ? (value === "x" ? "z" : "x") : value];
    case "rotation":
      if (!ROTATION16.test(value)) return bad();
      return [key, String(op.rotation16(Number(value)))];
    case "shape":
      if (STAIR_SHAPES.has(value)) return [key, swapHandedness(op, value)];
      return [key, turnRail(op, value) ?? bad()];
    case "hinge":
      if (value !== "left" && value !== "right") return bad();
      return [key, swapHandedness(op, value)];
    case "type":
      if (!TYPE_VALUES.has(value)) return bad();
      return [key, swapHandedness(op, value)];
    case "orientation": {
      if (!ORIENTATIONS.has(value)) return bad();
      const [front, top] = value.split("_") as [string, string];
      return [key, `${turnDirection(op, front)}_${turnDirection(op, top)}`];
    }
    default:
      return unsupported(
        state,
        `property "${key}" may depend on direction and has no transform rule`,
      );
  }
}

/** Returns the block state as it reads after the transform. The input is not changed. */
export function transformBlockState(
  state: BlockState,
  transform: GeometryTransform,
): BlockState {
  const op = operation(transform);
  const entries = Object.entries(state.properties).map(([k, v]) =>
    transformProperty(state, op, k, v),
  );
  entries.sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return Object.freeze({
    name: state.name,
    properties: Object.freeze(Object.fromEntries(entries)),
  });
}
