import { SchematicError } from "./errors.js";
import {
  validateGeometry,
  type GeometryLimits,
  type SchematicGeometry,
} from "./geometry.js";

export { SchematicError, type SchematicErrorCode } from "./errors.js";
export {
  GEOMETRY_FORMAT,
  blockAt,
  formatBlockState,
  geometryDigest,
  parseBlockState,
  validateGeometry,
  type BlockState,
  type GeometryLimits,
  type SchematicGeometry,
  type UntrustedMetadata,
  type Vec3,
} from "./geometry.js";
export {
  DEFAULT_SCHEMATIC_LIMITS,
  SPONGE_VERSION,
  exportSpongeSchematic,
  importSpongeSchematic,
  type DiscardedBlockEntity,
  type ImportOptions,
  type ImportResult,
  type SchematicLimits,
} from "./sponge.js";

export const MAX_GEOMETRY_JSON_BYTES = 96 * 1024 * 1024;

/** Parses neutral geometry JSON. The size is checked before parsing so huge input is never materialised. */
export function parseGeometryJson(
  text: string,
  limits: GeometryLimits,
  maxBytes = MAX_GEOMETRY_JSON_BYTES,
): SchematicGeometry {
  if (Buffer.byteLength(text) > maxBytes)
    throw new SchematicError(
      "E_INPUT_TOO_LARGE",
      `geometry JSON is larger than ${maxBytes} bytes`,
    );
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch (error) {
    throw new SchematicError(
      "E_JSON",
      `geometry is not valid JSON: ${(error as Error).message}`,
    );
  }
  return validateGeometry(value, limits);
}

/** One field per line with the block array kept on a single line, so files stay readable and diffable. */
export function stringifyGeometry(geometry: SchematicGeometry): string {
  const fields = Object.entries(geometry).map(
    ([key, value]) => `  ${JSON.stringify(key)}: ${JSON.stringify(value)}`,
  );
  return `{\n${fields.join(",\n")}\n}\n`;
}
