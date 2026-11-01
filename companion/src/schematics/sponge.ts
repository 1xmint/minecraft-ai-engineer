import { gunzipSync, gzipSync } from "node:zlib";
import { SchematicError } from "./errors.js";
import {
  GEOMETRY_FORMAT,
  formatBlockState,
  parseBlockState,
  validateGeometry,
  validateUntrustedMetadata,
  type BlockState,
  type GeometryLimits,
  type SchematicGeometry,
  type UntrustedMetadata,
} from "./geometry.js";
import { readNbt, writeNbt, type NbtTag } from "./nbt.js";

export const SPONGE_VERSION = 3;

export interface SchematicLimits extends GeometryLimits {
  readonly maxCompressedBytes: number;
  readonly maxDecompressedBytes: number;
  readonly maxNbtDepth: number;
  readonly maxNbtTags: number;
}

export const DEFAULT_SCHEMATIC_LIMITS: SchematicLimits = Object.freeze({
  maxCompressedBytes: 16 * 1024 * 1024,
  maxDecompressedBytes: 64 * 1024 * 1024,
  maxNbtDepth: 32,
  maxNbtTags: 1_000_000,
  maxVolume: 8 * 1024 * 1024,
  maxPaletteEntries: 65_536,
});

export interface ImportOptions {
  /** Explicitly drop block entities (sign text, inventories, commands). Each one is listed in the result. */
  readonly discardBlockEntities?: boolean;
  /** Explicitly drop biome data. */
  readonly discardBiomes?: boolean;
  readonly limits?: Partial<SchematicLimits>;
}

export interface DiscardedBlockEntity {
  readonly id: string;
  readonly pos: readonly [number, number, number];
}

export interface ImportResult {
  readonly geometry: SchematicGeometry;
  /** Everything in the file that is not in `geometry`. Nothing is dropped without appearing here. */
  readonly discarded: {
    readonly blockEntities: readonly DiscardedBlockEntity[];
    readonly biomes: boolean;
    readonly metadataKeys: readonly string[];
  };
  /** "flat" means the root compound itself is the schematic, as some older writers produce. */
  readonly rootLayout: "nested" | "flat";
}

type Compound = ReadonlyMap<string, NbtTag>;

const SCHEMATIC_KEYS = [
  "Version",
  "DataVersion",
  "Metadata",
  "Width",
  "Height",
  "Length",
  "Offset",
  "Blocks",
  "Biomes",
  "Entities",
];
const BLOCK_KEYS = ["Palette", "Data", "BlockEntities"];

function limitsOf(
  partial: Partial<SchematicLimits> | undefined,
): SchematicLimits {
  const limits = { ...DEFAULT_SCHEMATIC_LIMITS, ...partial };
  for (const [k, v] of Object.entries(limits))
    if (!Number.isSafeInteger(v) || v < 1)
      throw new RangeError(`limit ${k} must be a positive integer`);
  return limits;
}

function fieldError(path: string, why: string): never {
  throw new SchematicError("E_FIELD", `${path}: ${why}`);
}

function get<T extends NbtTag["type"]>(
  compound: Compound,
  key: string,
  type: T,
  path: string,
): Extract<NbtTag, { type: T }> | undefined {
  const tag = compound.get(key);
  if (tag === undefined) return undefined;
  if (tag.type !== type)
    fieldError(`${path}.${key}`, `expected NBT ${type}, found ${tag.type}`);
  return tag as Extract<NbtTag, { type: T }>;
}

function required<T extends NbtTag["type"]>(
  compound: Compound,
  key: string,
  type: T,
  path: string,
): Extract<NbtTag, { type: T }> {
  return (
    get(compound, key, type, path) ??
    fieldError(`${path}.${key}`, "required field is missing")
  );
}

function rejectUnknown(
  compound: Compound,
  allowed: readonly string[],
  path: string,
): void {
  for (const key of compound.keys())
    if (!allowed.includes(key))
      throw new SchematicError(
        "E_UNSUPPORTED_FIELD",
        `${path}.${key} is not part of Sponge schematic v3 and is not supported`,
      );
}

export function decompressSchematic(
  bytes: Uint8Array,
  limits: SchematicLimits,
): Uint8Array {
  if (bytes.byteLength > limits.maxCompressedBytes)
    throw new SchematicError(
      "E_INPUT_TOO_LARGE",
      `file is ${bytes.byteLength} bytes; the limit is ${limits.maxCompressedBytes}`,
    );
  if (bytes.byteLength < 18 || bytes[0] !== 0x1f || bytes[1] !== 0x8b)
    throw new SchematicError(
      "E_NOT_GZIP",
      "Sponge schematics must be gzip-compressed NBT",
    );
  try {
    return gunzipSync(bytes, { maxOutputLength: limits.maxDecompressedBytes });
  } catch (error) {
    if ((error as { code?: string }).code === "ERR_BUFFER_TOO_LARGE")
      throw new SchematicError(
        "E_DECOMPRESSED_TOO_LARGE",
        `decompressed data exceeds ${limits.maxDecompressedBytes} bytes`,
      );
    throw new SchematicError(
      "E_DECOMPRESS",
      `gzip data is corrupt: ${(error as Error).message}`,
    );
  }
}

function locateSchematic(
  name: string,
  root: Compound,
): { schematic: Compound; rootLayout: "nested" | "flat" } {
  const nested = root.get("Schematic");
  if (nested !== undefined) {
    if (nested.type !== "compound")
      throw new SchematicError("E_ROOT", "Schematic is not a compound");
    if (root.size !== 1)
      throw new SchematicError(
        "E_ROOT",
        "root compound has fields besides Schematic",
      );
    return { schematic: nested.value, rootLayout: "nested" };
  }
  if (name === "Schematic" && root.has("Version"))
    return { schematic: root, rootLayout: "flat" };
  throw new SchematicError("E_ROOT", "no Schematic compound in the root tag");
}

function decodeVarints(
  data: Uint8Array,
  count: number,
  paletteSize: number,
): number[] {
  const out = new Array<number>(count);
  let pos = 0;
  for (let i = 0; i < count; i++) {
    let value = 0;
    let shift = 0;
    for (;;) {
      if (pos >= data.length)
        throw new SchematicError(
          "E_BLOCK_DATA",
          `block data ends after ${i} of ${count} entries`,
        );
      const byte = data[pos++]!;
      value += (byte & 0x7f) * 2 ** shift;
      if ((byte & 0x80) === 0) break;
      shift += 7;
      if (shift > 28)
        throw new SchematicError(
          "E_BLOCK_DATA",
          `varint longer than 5 bytes at entry ${i}`,
        );
    }
    if (value > 0x7fffffff)
      throw new SchematicError(
        "E_BLOCK_DATA",
        `varint overflows int32 at entry ${i}`,
      );
    if (value >= paletteSize)
      throw new SchematicError(
        "E_BLOCK_DATA",
        `entry ${i} refers to palette index ${value}, which does not exist`,
      );
    out[i] = value;
  }
  if (pos !== data.length)
    throw new SchematicError(
      "E_BLOCK_DATA",
      `${data.length - pos} extra bytes after ${count} block entries`,
    );
  return out;
}

function readMetadata(tag: Compound | undefined): {
  metadata?: UntrustedMetadata;
  discarded: string[];
} {
  if (tag === undefined) return { discarded: [] };
  const path = "Schematic.Metadata";
  const out: {
    name?: string;
    author?: string;
    dateMillis?: string;
    requiredMods?: string[];
  } = {};
  const discarded: string[] = [];
  for (const [key, value] of tag) {
    if (key === "Name" || key === "Author") {
      if (value.type !== "string")
        throw new SchematicError(
          "E_METADATA",
          `${path}.${key}: expected NBT string`,
        );
      out[key === "Name" ? "name" : "author"] = value.value;
    } else if (key === "Date") {
      if (value.type !== "long")
        throw new SchematicError(
          "E_METADATA",
          `${path}.Date: expected NBT long`,
        );
      out.dateMillis = value.value.toString();
    } else if (key === "RequiredMods") {
      if (
        value.type !== "list" ||
        (value.value.length > 0 && value.elementType !== "string")
      )
        throw new SchematicError(
          "E_METADATA",
          `${path}.RequiredMods: expected a list of strings`,
        );
      out.requiredMods = value.value.map((v) => (v as { value: string }).value);
    } else discarded.push(key);
  }
  if (Object.keys(out).length === 0) return { discarded };
  try {
    // Same bounds as the neutral format, so anything imported can be exported again.
    return { metadata: validateUntrustedMetadata(out, path), discarded };
  } catch (error) {
    throw new SchematicError("E_METADATA", (error as Error).message);
  }
}

function readBlockEntities(
  tag: Extract<NbtTag, { type: "list" }> | undefined,
  size: { x: number; y: number; z: number },
  discard: boolean,
): DiscardedBlockEntity[] {
  if (tag === undefined || tag.value.length === 0) return [];
  const path = "Schematic.Blocks.BlockEntities";
  if (tag.elementType !== "compound")
    fieldError(path, "expected a list of compounds");
  const entities = tag.value.map((entry, i) => {
    const e = (entry as Extract<NbtTag, { type: "compound" }>).value;
    const id = required(e, "Id", "string", `${path}[${i}]`).value;
    const pos = required(e, "Pos", "intArray", `${path}[${i}]`).value;
    if (pos.length !== 3)
      fieldError(`${path}[${i}].Pos`, "expected exactly 3 integers");
    const [x, y, z] = [pos[0]!, pos[1]!, pos[2]!];
    if (x < 0 || y < 0 || z < 0 || x >= size.x || y >= size.y || z >= size.z)
      fieldError(
        `${path}[${i}].Pos`,
        `position ${x},${y},${z} is outside the schematic`,
      );
    return Object.freeze({ id, pos: Object.freeze([x, y, z] as const) });
  });
  if (!discard) {
    const kinds = [...new Set(entities.map((e) => e.id))]
      .slice(0, 5)
      .join(", ");
    throw new SchematicError(
      "E_BLOCK_ENTITIES",
      `${entities.length} block entities (${kinds}) carry data such as inventories, sign text or commands, ` +
        "which is not supported. Re-run with the explicit discard-block-entities option to import geometry only.",
    );
  }
  return entities;
}

/** Imports a gzip-compressed Sponge v3 `.schem` file into neutral, untrusted geometry. */
export function importSpongeSchematic(
  bytes: Uint8Array,
  options: ImportOptions = {},
): ImportResult {
  const limits = limitsOf(options.limits);
  const nbt = readNbt(decompressSchematic(bytes, limits), {
    maxDepth: limits.maxNbtDepth,
    maxTags: limits.maxNbtTags,
  });
  const { schematic: s, rootLayout } = locateSchematic(nbt.name, nbt.tag.value);
  const path = "Schematic";
  const version = required(s, "Version", "int", path).value;
  if (version !== SPONGE_VERSION)
    throw new SchematicError(
      "E_UNSUPPORTED_VERSION",
      `Sponge schematic version ${version} is not supported; only version ${SPONGE_VERSION} is`,
    );
  rejectUnknown(s, SCHEMATIC_KEYS, path);
  const dataVersion = required(s, "DataVersion", "int", path).value;
  if (dataVersion < 1)
    fieldError(`${path}.DataVersion`, "must be a positive integer");
  const size = {
    x: required(s, "Width", "short", path).value & 0xffff,
    y: required(s, "Height", "short", path).value & 0xffff,
    z: required(s, "Length", "short", path).value & 0xffff,
  };
  const volume = size.x * size.y * size.z;
  if (volume > limits.maxVolume)
    throw new SchematicError(
      "E_TOO_LARGE",
      `schematic is ${size.x}x${size.y}x${size.z} (${volume} blocks); the limit is ${limits.maxVolume}`,
    );
  const offsetTag = get(s, "Offset", "intArray", path);
  if (offsetTag !== undefined && offsetTag.value.length !== 3)
    fieldError(`${path}.Offset`, "expected exactly 3 integers");
  const offset = offsetTag
    ? { x: offsetTag.value[0]!, y: offsetTag.value[1]!, z: offsetTag.value[2]! }
    : { x: 0, y: 0, z: 0 };

  const entities = get(s, "Entities", "list", path);
  if (entities !== undefined && entities.value.length > 0)
    throw new SchematicError(
      "E_ENTITIES",
      `${entities.value.length} entities (mobs, item frames, armor stands and similar) are not supported; ` +
        "remove them from the schematic before importing",
    );
  const biomes = get(s, "Biomes", "compound", path);
  if (biomes !== undefined && !options.discardBiomes)
    throw new SchematicError(
      "E_BIOMES",
      "biome data is not supported. Re-run with the explicit discard-biomes option to import geometry only.",
    );

  const blocks = get(s, "Blocks", "compound", path);
  if (blocks === undefined)
    throw new SchematicError(
      "E_FIELD",
      `${path}.Blocks: this schematic contains no block data to import`,
    );
  const bpath = `${path}.Blocks`;
  rejectUnknown(blocks.value, BLOCK_KEYS, bpath);
  const blockEntities = readBlockEntities(
    get(blocks.value, "BlockEntities", "list", bpath),
    size,
    options.discardBlockEntities === true,
  );
  const paletteTag = required(blocks.value, "Palette", "compound", bpath).value;
  if (paletteTag.size > limits.maxPaletteEntries)
    throw new SchematicError(
      "E_TOO_LARGE",
      `palette has ${paletteTag.size} entries; the limit is ${limits.maxPaletteEntries}`,
    );
  const byIndex = new Map<number, { key: string; state: BlockState }>();
  const canonical = new Map<string, string>();
  for (const [key, tag] of paletteTag) {
    if (tag.type !== "int")
      fieldError(
        `${bpath}.Palette.${key}`,
        `expected NBT int, found ${tag.type}`,
      );
    if (tag.value < 0)
      throw new SchematicError(
        "E_PALETTE",
        `${bpath}.Palette: ${key} has negative index ${tag.value}`,
      );
    const clash = byIndex.get(tag.value);
    if (clash !== undefined)
      throw new SchematicError(
        "E_PALETTE",
        `${bpath}.Palette: ${clash.key} and ${key} both use index ${tag.value}`,
      );
    const state = parseBlockState(key);
    const text = formatBlockState(state);
    const same = canonical.get(text);
    if (same !== undefined)
      throw new SchematicError(
        "E_PALETTE",
        `${bpath}.Palette: ${same} and ${key} are the same block state`,
      );
    canonical.set(text, key);
    byIndex.set(tag.value, { key, state });
  }
  // Indices need not be contiguous in the file; renumber in index order without changing any block.
  const order = [...byIndex.keys()].sort((a, b) => a - b);
  const renumber = new Map(order.map((fileIndex, i) => [fileIndex, i]));
  const maxIndex = order.length === 0 ? -1 : order[order.length - 1]!;
  const raw = decodeVarints(
    required(blocks.value, "Data", "byteArray", bpath).value,
    volume,
    maxIndex + 1,
  );
  const indices = raw.map((fileIndex, i) => {
    const mapped = renumber.get(fileIndex);
    if (mapped === undefined)
      throw new SchematicError(
        "E_BLOCK_DATA",
        `entry ${i} refers to palette index ${fileIndex}, which does not exist`,
      );
    return mapped;
  });
  const metadata = readMetadata(get(s, "Metadata", "compound", path)?.value);
  const geometry = validateGeometry(
    {
      format: GEOMETRY_FORMAT,
      formatVersion: 1,
      dataVersion,
      size,
      offset,
      palette: order.map((i) => byIndex.get(i)!.state),
      blocks: indices,
      ...(metadata.metadata ? { untrustedMetadata: metadata.metadata } : {}),
    },
    limits,
  );
  return Object.freeze({
    geometry,
    discarded: Object.freeze({
      blockEntities: Object.freeze(blockEntities),
      biomes: biomes !== undefined,
      metadataKeys: Object.freeze(metadata.discarded),
    }),
    rootLayout,
  });
}

function encodeVarints(values: readonly number[]): Uint8Array {
  const out: number[] = [];
  for (let value of values) {
    while (value >= 0x80) {
      out.push((value & 0x7f) | 0x80);
      value >>>= 7;
    }
    out.push(value);
  }
  return Uint8Array.from(out);
}

/** Exports neutral geometry as a gzip-compressed Sponge v3 `.schem` file with no entities or block entities. */
export function exportSpongeSchematic(
  value: unknown,
  limits: Partial<SchematicLimits> = {},
): Uint8Array {
  const g = validateGeometry(value, limitsOf(limits));
  const short = (n: number): NbtTag => ({
    type: "short",
    value: n > 0x7fff ? n - 0x10000 : n,
  });
  const schematic = new Map<string, NbtTag>([
    ["Version", { type: "int", value: SPONGE_VERSION }],
    ["DataVersion", { type: "int", value: g.dataVersion }],
  ]);
  const m = g.untrustedMetadata;
  if (m !== undefined) {
    const meta = new Map<string, NbtTag>();
    if (m.name !== undefined)
      meta.set("Name", { type: "string", value: m.name });
    if (m.author !== undefined)
      meta.set("Author", { type: "string", value: m.author });
    if (m.dateMillis !== undefined)
      meta.set("Date", { type: "long", value: BigInt(m.dateMillis) });
    if (m.requiredMods !== undefined)
      meta.set("RequiredMods", {
        type: "list",
        elementType: "string",
        value: m.requiredMods.map((v) => ({ type: "string", value: v })),
      });
    schematic.set("Metadata", { type: "compound", value: meta });
  }
  schematic.set("Width", short(g.size.x));
  schematic.set("Height", short(g.size.y));
  schematic.set("Length", short(g.size.z));
  schematic.set("Offset", {
    type: "intArray",
    value: Int32Array.of(g.offset.x, g.offset.y, g.offset.z),
  });
  schematic.set("Blocks", {
    type: "compound",
    value: new Map<string, NbtTag>([
      [
        "Palette",
        {
          type: "compound",
          value: new Map(
            g.palette.map((state, i): [string, NbtTag] => [
              formatBlockState(state),
              { type: "int", value: i },
            ]),
          ),
        },
      ],
      ["Data", { type: "byteArray", value: encodeVarints(g.blocks) }],
    ]),
  });
  return gzipSync(
    writeNbt({
      name: "",
      tag: {
        type: "compound",
        value: new Map([["Schematic", { type: "compound", value: schematic }]]),
      },
    }),
  );
}
