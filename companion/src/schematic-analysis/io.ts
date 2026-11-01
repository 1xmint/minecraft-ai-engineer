import { closeSync, fstatSync, openSync, readSync } from "node:fs";
import {
  DEFAULT_SCHEMATIC_LIMITS,
  MAX_GEOMETRY_JSON_BYTES,
  SchematicError,
  parseGeometryJson,
  type GeometryLimits,
  type SchematicGeometry,
} from "../schematics/index.js";
import { AnalysisError } from "./errors.js";

export interface ReadOptions {
  readonly maxBytes?: number;
  readonly limits?: GeometryLimits;
}

function tooLarge(path: string, maxBytes: number): never {
  throw new SchematicError(
    "E_INPUT_TOO_LARGE",
    `${path} exceeds the limit of ${maxBytes} bytes`,
  );
}

/**
 * Reads a neutral geometry JSON file read-only. The size is checked before
 * reading and again while reading, so at most maxBytes + 1 bytes are held.
 */
export function readGeometryFile(
  path: string,
  options: ReadOptions = {},
): SchematicGeometry {
  const maxBytes = options.maxBytes ?? MAX_GEOMETRY_JSON_BYTES;
  let descriptor: number;
  try {
    descriptor = openSync(path, "r");
  } catch (error) {
    throw new AnalysisError(
      "E_INPUT_FILE",
      `cannot open ${path}: ${(error as Error).message}`,
    );
  }
  let bytes: Buffer;
  try {
    const stat = fstatSync(descriptor, { bigint: true });
    if (!stat.isFile())
      throw new AnalysisError("E_INPUT_FILE", `${path} is not a regular file`);
    if (stat.size > BigInt(maxBytes)) tooLarge(path, maxBytes);
    const chunks: Buffer[] = [];
    let total = 0;
    while (true) {
      const chunk = Buffer.allocUnsafe(Math.min(65536, maxBytes - total + 1));
      const count = readSync(descriptor, chunk, 0, chunk.length, null);
      if (count === 0) break;
      total += count;
      if (total > maxBytes) tooLarge(path, maxBytes);
      chunks.push(chunk.subarray(0, count));
    }
    bytes = Buffer.concat(chunks, total);
  } finally {
    closeSync(descriptor);
  }
  return parseGeometryJson(
    bytes.toString("utf8"),
    options.limits ?? DEFAULT_SCHEMATIC_LIMITS,
    maxBytes,
  );
}
