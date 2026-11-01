import { createHash } from "node:crypto";
import { SchematicError } from "./errors.js";

export const GEOMETRY_FORMAT = "minecraft-ai-engineer.schematic-geometry";

export interface Vec3 {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export interface BlockState {
  /** Namespaced block id, always with an explicit namespace, e.g. "minecraft:stone". */
  readonly name: string;
  /** Block-state properties with keys in sorted order. */
  readonly properties: Readonly<Record<string, string>>;
}

/** Descriptive strings copied from the file. They never grant ownership or permissions. */
export interface UntrustedMetadata {
  readonly name?: string;
  readonly author?: string;
  /** Milliseconds since the Unix epoch, as a decimal string to keep 64-bit precision. */
  readonly dateMillis?: string;
  readonly requiredMods?: readonly string[];
}

/**
 * Neutral block geometry: shape and block states only. It carries no project,
 * owner, permission or test result, and says nothing about whether it works.
 */
export interface SchematicGeometry {
  readonly format: typeof GEOMETRY_FORMAT;
  readonly formatVersion: 1;
  readonly dataVersion: number;
  readonly size: Vec3;
  readonly offset: Vec3;
  readonly palette: readonly BlockState[];
  /** Palette indices, ordered x + z * size.x + y * size.x * size.z. */
  readonly blocks: readonly number[];
  readonly untrustedMetadata?: UntrustedMetadata;
}

export interface GeometryLimits {
  readonly maxVolume: number;
  readonly maxPaletteEntries: number;
}

const NAMESPACE = /^[a-z0-9_.-]+$/;
const PATH = /^[a-z0-9_.\-/]+$/;
const PROPERTY = /^[a-z0-9_]+$/;
const NAMESPACED_ID = /^[a-z0-9_.-]+:[a-z0-9_.\-/]+$/;
const MAX_STATE_LENGTH = 4096;
const MAX_METADATA_STRING = 1024;
const MAX_REQUIRED_MODS = 256;
const INT32_MIN = -2147483648;
const INT32_MAX = 2147483647;

function stateError(input: string, why: string): never {
  throw new SchematicError(
    "E_BLOCK_STATE",
    `invalid block state ${JSON.stringify(input.slice(0, 200))}: ${why}`,
  );
}

/** Parses "ns:id[k=v,...]". A missing namespace means "minecraft", per the Sponge spec. */
export function parseBlockState(input: string): BlockState {
  if (input.length === 0 || input.length > MAX_STATE_LENGTH)
    stateError(input, "empty or too long");
  const open = input.indexOf("[");
  const id = open === -1 ? input : input.slice(0, open);
  const colon = id.indexOf(":");
  const namespace = colon === -1 ? "minecraft" : id.slice(0, colon);
  const path = colon === -1 ? id : id.slice(colon + 1);
  if (!NAMESPACE.test(namespace) || !PATH.test(path))
    stateError(input, "block id is not a valid resource location");
  const entries: [string, string][] = [];
  if (open !== -1) {
    if (!input.endsWith("]") || open + 2 > input.length - 1)
      stateError(input, "malformed property list");
    for (const pair of input.slice(open + 1, -1).split(",")) {
      const eq = pair.indexOf("=");
      const key = pair.slice(0, eq);
      const value = pair.slice(eq + 1);
      if (eq === -1 || !PROPERTY.test(key) || !PROPERTY.test(value))
        stateError(input, `invalid property ${JSON.stringify(pair)}`);
      if (entries.some(([k]) => k === key))
        stateError(input, `duplicate property "${key}"`);
      entries.push([key, value]);
    }
  }
  entries.sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return Object.freeze({
    name: `${namespace}:${path}`,
    properties: Object.freeze(Object.fromEntries(entries)),
  });
}

/** Canonical Sponge palette key: namespaced id plus properties in sorted order. */
export function formatBlockState(state: BlockState): string {
  const entries = Object.entries(state.properties);
  return entries.length === 0
    ? state.name
    : `${state.name}[${entries.map(([k, v]) => `${k}=${v}`).join(",")}]`;
}

function geometryError(path: string, why: string): never {
  throw new SchematicError("E_GEOMETRY", `${path}: ${why}`);
}

function record(
  value: unknown,
  path: string,
  keys: readonly string[],
  required: readonly string[],
): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value))
    geometryError(path, "expected an object");
  const v = value as Record<string, unknown>;
  for (const k of Object.keys(v))
    if (!keys.includes(k)) geometryError(path, `unsupported field "${k}"`);
  for (const k of required)
    if (!Object.hasOwn(v, k)) geometryError(path, `missing field "${k}"`);
  return v;
}

function int(value: unknown, path: string, min: number, max: number): number {
  if (
    !Number.isInteger(value) ||
    (value as number) < min ||
    (value as number) > max
  )
    geometryError(path, `expected an integer from ${min} to ${max}`);
  return value as number;
}

function vec(value: unknown, path: string, min: number, max: number): Vec3 {
  const v = record(value, path, ["x", "y", "z"], ["x", "y", "z"]);
  return Object.freeze({
    x: int(v.x, `${path}.x`, min, max),
    y: int(v.y, `${path}.y`, min, max),
    z: int(v.z, `${path}.z`, min, max),
  });
}

function metadataString(value: unknown, path: string): string {
  if (typeof value !== "string" || value.length > MAX_METADATA_STRING)
    geometryError(
      path,
      `expected a string of at most ${MAX_METADATA_STRING} characters`,
    );
  return value;
}

export function validateUntrustedMetadata(
  value: unknown,
  path: string,
): UntrustedMetadata {
  const v = record(
    value,
    path,
    ["name", "author", "dateMillis", "requiredMods"],
    [],
  );
  const out: {
    name?: string;
    author?: string;
    dateMillis?: string;
    requiredMods?: readonly string[];
  } = {};
  if (v.name !== undefined) out.name = metadataString(v.name, `${path}.name`);
  if (v.author !== undefined)
    out.author = metadataString(v.author, `${path}.author`);
  if (v.dateMillis !== undefined) {
    if (
      typeof v.dateMillis !== "string" ||
      !/^-?(0|[1-9][0-9]{0,18})$/.test(v.dateMillis) ||
      BigInt(v.dateMillis) !== BigInt.asIntN(64, BigInt(v.dateMillis))
    )
      geometryError(
        `${path}.dateMillis`,
        "expected a signed 64-bit decimal string",
      );
    out.dateMillis = v.dateMillis;
  }
  if (v.requiredMods !== undefined) {
    if (
      !Array.isArray(v.requiredMods) ||
      v.requiredMods.length > MAX_REQUIRED_MODS
    )
      geometryError(
        `${path}.requiredMods`,
        `expected at most ${MAX_REQUIRED_MODS} strings`,
      );
    out.requiredMods = Object.freeze(
      Array.from(v.requiredMods, (m, i) =>
        metadataString(m, `${path}.requiredMods[${i}]`),
      ),
    );
  }
  return Object.freeze(out);
}

/** Strictly validates neutral geometry: unknown fields anywhere are rejected, so no authority can ride along. */
export function validateGeometry(
  value: unknown,
  limits: GeometryLimits,
): SchematicGeometry {
  const v = record(
    value,
    "geometry",
    [
      "format",
      "formatVersion",
      "dataVersion",
      "size",
      "offset",
      "palette",
      "blocks",
      "untrustedMetadata",
    ],
    [
      "format",
      "formatVersion",
      "dataVersion",
      "size",
      "offset",
      "palette",
      "blocks",
    ],
  );
  if (v.format !== GEOMETRY_FORMAT)
    geometryError("geometry.format", `expected "${GEOMETRY_FORMAT}"`);
  if (v.formatVersion !== 1)
    geometryError("geometry.formatVersion", "expected 1");
  const dataVersion = int(v.dataVersion, "geometry.dataVersion", 1, INT32_MAX);
  const size = vec(v.size, "geometry.size", 0, 0xffff);
  const offset = vec(v.offset, "geometry.offset", INT32_MIN, INT32_MAX);
  const volume = size.x * size.y * size.z;
  if (volume > limits.maxVolume)
    throw new SchematicError(
      "E_TOO_LARGE",
      `geometry volume ${volume} exceeds the limit of ${limits.maxVolume} blocks`,
    );
  if (!Array.isArray(v.palette))
    geometryError("geometry.palette", "expected an array");
  if (v.palette.length > limits.maxPaletteEntries)
    throw new SchematicError(
      "E_TOO_LARGE",
      `palette has ${v.palette.length} entries; the limit is ${limits.maxPaletteEntries}`,
    );
  const seen = new Set<string>();
  const palette = Array.from(v.palette, (entry, i) => {
    const path = `geometry.palette[${i}]`;
    const e = record(
      entry,
      path,
      ["name", "properties"],
      ["name", "properties"],
    );
    if (typeof e.name !== "string" || !NAMESPACED_ID.test(e.name))
      geometryError(`${path}.name`, "expected a namespaced block id");
    const props = record(
      e.properties,
      `${path}.properties`,
      Object.keys(e.properties ?? {}),
      [],
    );
    const pairs = Object.entries(props).map(([k, val]) => {
      if (typeof val !== "string")
        geometryError(`${path}.properties.${k}`, "expected a string");
      return `${k}=${val}`;
    });
    const key = pairs.length === 0 ? e.name : `${e.name}[${pairs.join(",")}]`;
    const state = parseBlockState(key);
    const canonical = formatBlockState(state);
    if (seen.has(canonical))
      geometryError(path, `duplicate block state ${canonical}`);
    seen.add(canonical);
    return state;
  });
  if (!Array.isArray(v.blocks) || v.blocks.length !== volume)
    geometryError(
      "geometry.blocks",
      `expected exactly ${volume} palette indices`,
    );
  const blocks = v.blocks as unknown[];
  for (let i = 0; i < blocks.length; i++) {
    const b = blocks[i];
    if (
      !Number.isInteger(b) ||
      (b as number) < 0 ||
      (b as number) >= palette.length
    )
      geometryError(
        `geometry.blocks[${i}]`,
        `expected a palette index below ${palette.length}`,
      );
  }
  const out: {
    -readonly [K in keyof SchematicGeometry]: SchematicGeometry[K];
  } = {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion,
    size,
    offset,
    palette: Object.freeze(palette),
    blocks: Object.freeze(blocks.slice() as number[]),
  };
  if (v.untrustedMetadata !== undefined)
    out.untrustedMetadata = validateUntrustedMetadata(
      v.untrustedMetadata,
      "geometry.untrustedMetadata",
    );
  return Object.freeze(out);
}

/** Stable digest of the block content, independent of JSON whitespace. */
export function geometryDigest(geometry: SchematicGeometry): string {
  const hash = createHash("sha256");
  const { size, offset } = geometry;
  hash.update(
    `geometry-v1|${geometry.dataVersion}|${size.x},${size.y},${size.z}|${offset.x},${offset.y},${offset.z}|`,
  );
  hash.update(geometry.palette.map(formatBlockState).join("\n"));
  hash.update("|");
  hash.update(Uint32Array.from(geometry.blocks));
  return hash.digest("hex");
}

export function blockAt(
  geometry: SchematicGeometry,
  x: number,
  y: number,
  z: number,
): BlockState {
  const { size } = geometry;
  if (x < 0 || y < 0 || z < 0 || x >= size.x || y >= size.y || z >= size.z)
    throw new SchematicError(
      "E_GEOMETRY",
      `position ${x},${y},${z} is outside the geometry`,
    );
  const index = geometry.blocks[x + z * size.x + y * size.x * size.z]!;
  return geometry.palette[index]!;
}
