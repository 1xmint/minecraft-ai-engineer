import {
  DEFAULT_SCHEMATIC_LIMITS,
  validateGeometry,
  type GeometryLimits,
  type SchematicGeometry,
} from "../schematics/index.js";
import { TransformError } from "./errors.js";
import {
  operation,
  transformBlockState,
  type GeometryTransform,
} from "./states.js";

const INT32_MIN = -2147483648;
const INT32_MAX = 2147483647;

/** Turns -0 into 0 so results compare and serialise cleanly. */
const zero = (v: number) => (v === 0 ? 0 : v);

/**
 * Returns new geometry turned or mirrored about the paste origin (the point
 * the offset is measured from). The block box moves with it, so the offset
 * changes; `y` never changes. The input is validated first and never changed.
 * Every palette entry must be in the supported subset, used or not.
 */
export function transformGeometry(
  value: unknown,
  transform: GeometryTransform,
  limits: GeometryLimits = DEFAULT_SCHEMATIC_LIMITS,
): SchematicGeometry {
  const op = operation(transform);
  const g = validateGeometry(value, limits);
  const palette = g.palette.map((s) => transformBlockState(s, transform));
  const { size, offset } = g;
  const [a, b, c, d] = op.m;
  const outSize = a === 0 ? { x: size.z, y: size.y, z: size.x } : { ...size };

  // World-relative corners of the box; an empty axis counts as one cell wide
  // so the offset still moves by a definite, reversible rule.
  const fx = offset.x + Math.max(size.x, 1) - 1;
  const fz = offset.z + Math.max(size.z, 1) - 1;
  const outOffset = {
    x: zero(Math.min(a * offset.x + b * offset.z, a * fx + b * fz)),
    y: offset.y,
    z: zero(Math.min(c * offset.x + d * offset.z, c * fx + d * fz)),
  };
  for (const axis of ["x", "z"] as const)
    if (outOffset[axis] < INT32_MIN || outOffset[axis] > INT32_MAX)
      throw new TransformError(
        "E_COORDINATE_RANGE",
        `${transform} would move offset.${axis} to ${outOffset[axis]}, outside the 32-bit range`,
      );

  // local' = M * local + (M * offset - offset')
  const tx = a * offset.x + b * offset.z - outOffset.x;
  const tz = c * offset.x + d * offset.z - outOffset.z;
  const blocks = new Array<number>(g.blocks.length);
  const layer = outSize.x * outSize.z;
  let i = 0;
  for (let y = 0; g.blocks.length > 0 && y < size.y; y++)
    for (let z = 0; z < size.z; z++)
      for (let x = 0; x < size.x; x++) {
        const lx = a * x + b * z + tx;
        const lz = c * x + d * z + tz;
        blocks[lx + lz * outSize.x + y * layer] = g.blocks[i++]!;
      }

  return validateGeometry(
    {
      format: g.format,
      formatVersion: g.formatVersion,
      dataVersion: g.dataVersion,
      size: outSize,
      offset: outOffset,
      palette,
      blocks,
      ...(g.untrustedMetadata && { untrustedMetadata: g.untrustedMetadata }),
    },
    limits,
  );
}

/** Clockwise seen from above. Only 90, 180 and 270 are accepted. */
export function rotateGeometry(
  value: unknown,
  degrees: 90 | 180 | 270,
  limits?: GeometryLimits,
): SchematicGeometry {
  if (degrees !== 90 && degrees !== 180 && degrees !== 270)
    throw new TransformError(
      "E_TRANSFORM_ARGUMENT",
      "rotation must be 90, 180 or 270 degrees",
    );
  return transformGeometry(value, `rotate-${degrees}`, limits);
}

/** "east-west" swaps east and west (negates x); "north-south" negates z. */
export function mirrorGeometry(
  value: unknown,
  swap: "east-west" | "north-south",
  limits?: GeometryLimits,
): SchematicGeometry {
  if (swap !== "east-west" && swap !== "north-south")
    throw new TransformError(
      "E_TRANSFORM_ARGUMENT",
      'mirror must be "east-west" or "north-south"',
    );
  return transformGeometry(value, `mirror-${swap}`, limits);
}
