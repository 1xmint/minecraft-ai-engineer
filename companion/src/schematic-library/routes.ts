import type { GatewayMemory } from "../gateway-memory.js";
import type { GatewayContext } from "../gateway-types.js";
import { integer, RequestError } from "../types.js";
import {
  SchematicError,
  exportSpongeSchematic,
  formatBlockState,
  geometryDigest,
  importSpongeSchematic,
  validateGeometry,
  type ImportResult,
  type SchematicGeometry,
} from "../schematics/index.js";
import {
  AIR_BLOCK_IDS,
  AnalysisError,
  analyzeGeometry,
  compareGeometries,
  type Count,
} from "../schematic-analysis/index.js";
import {
  GEOMETRY_TRANSFORMS,
  TransformError,
  transformGeometry,
  type GeometryTransform,
} from "../schematic-transforms/index.js";
import { SchematicFiles, schematicName } from "./files.js";
import { IN_GAME_LIMITS, MAX_PLACEMENT_BLOCKS } from "./limits.js";
import type { DesignSummary, SchematicDesignStore } from "./store.js";

const FIELDS: Record<string, readonly string[]> = {
  "/v2/schematic/library": ["context"],
  "/v2/schematic/inspect": ["context", "name"],
  "/v2/schematic/import": ["context", "name", "discard", "transform"],
  "/v2/schematic/designs": ["context"],
  "/v2/schematic/design": ["context", "designId"],
  "/v2/schematic/transform": ["context", "designId", "transform"],
  "/v2/schematic/compare": ["context", "designId", "otherId"],
  "/v2/schematic/export": ["context", "name", "geometry"],
};
const OPTIONAL: Record<string, readonly string[]> = {
  "/v2/schematic/import": ["transform"],
};
const MAX_MATERIALS = 48;
const UNTESTED =
  "Imported geometry is untrusted and untested: it says nothing about whether it works in game.";

export interface SchematicRoutesOptions {
  memory: GatewayMemory;
  designs: SchematicDesignStore;
  directory: string;
}

/**
 * Player-facing schematic routes. Every route needs a connected project
 * member; importing, transforming and exporting need edit rights. Nothing
 * here places blocks: placement goes through the mod's execution core.
 */
export class SchematicRoutes {
  private readonly files: SchematicFiles;
  constructor(private readonly options: SchematicRoutesOptions) {
    this.files = new SchematicFiles(options.directory);
  }

  handle(
    pathname: string,
    v: Record<string, unknown>,
    context: GatewayContext,
  ): unknown {
    const allowed = FIELDS[pathname];
    if (!allowed)
      throw new RequestError("Unknown schematic route or method.", 404);
    // The mod's JSON writer omits null values, so an import with no transform
    // arrives without the key; every other field stays required.
    const required = allowed.filter(
      (key) => !(OPTIONAL[pathname] ?? []).includes(key),
    );
    if (
      required.some((key) => !(key in v)) ||
      Object.keys(v).some((key) => !allowed.includes(key))
    )
      throw new RequestError("Invalid schematic request fields.");
    if (!context.connectionId)
      throw new RequestError(
        "Schematic requests require a current connection.",
        403,
      );
    const { memory, designs } = this.options;
    const editing = [
      "/v2/schematic/import",
      "/v2/schematic/transform",
      "/v2/schematic/export",
    ].includes(pathname);
    memory.assertCurrent(context, editing ? "edit" : "read");
    if (editing) memory.claim(context, pathname.slice("/v2/".length));
    try {
      switch (pathname) {
        case "/v2/schematic/library":
          return {
            protocol: 2,
            status: "listed",
            context,
            ...this.files.list(),
          };
        case "/v2/schematic/inspect": {
          const name = schematicName(v.name);
          const file = this.files.read(name, IN_GAME_LIMITS.maxCompressedBytes);
          const result = importFile(file.bytes, true);
          return {
            protocol: 2,
            status: "inspected",
            context,
            name,
            sha256: file.sha256,
            ...describe(result.geometry),
            discarded: result.discarded,
            requiresDiscard:
              result.discarded.blockEntities.length > 0 ||
              result.discarded.biomes,
            notice: UNTESTED,
          };
        }
        case "/v2/schematic/import": {
          const name = schematicName(v.name);
          if (typeof v.discard !== "boolean")
            throw new RequestError("discard must be true or false.");
          const transform =
            v.transform === undefined || v.transform === null
              ? null
              : transformName(v.transform);
          const file = this.files.read(name, IN_GAME_LIMITS.maxCompressedBytes);
          const preview = importFile(file.bytes, true);
          const needsDiscard =
            preview.discarded.blockEntities.length > 0 ||
            preview.discarded.biomes;
          if (needsDiscard && !v.discard)
            return {
              protocol: 2,
              status: "needs-discard",
              context,
              name,
              discarded: preview.discarded,
            };
          const geometry =
            transform === null
              ? preview.geometry
              : transformGeometry(preview.geometry, transform, IN_GAME_LIMITS);
          const design = designs.add(context, {
            parentId: null,
            source: { name: `${name}.schem`, sha256: file.sha256 },
            transforms: transform === null ? [] : [transform],
            discarded: preview.discarded,
            geometry,
          });
          return {
            protocol: 2,
            status: "imported",
            context,
            design,
            ...describe(geometry),
            notice: UNTESTED,
          };
        }
        case "/v2/schematic/designs":
          return {
            protocol: 2,
            status: "listed",
            context,
            designs: designs.list(context),
          };
        case "/v2/schematic/design": {
          const design = designs.get(context, designId(v.designId));
          const { geometry, ...summary } = design;
          return {
            protocol: 2,
            status: "shared",
            context,
            design: summary,
            ...describe(geometry),
            placement: placement(geometry),
            notice: UNTESTED,
          };
        }
        case "/v2/schematic/transform": {
          const parent = designs.get(context, designId(v.designId));
          const transform = transformName(v.transform);
          const geometry = transformGeometry(
            parent.geometry,
            transform,
            IN_GAME_LIMITS,
          );
          const design = designs.add(context, {
            parentId: parent.designId,
            source: parent.source,
            transforms: [...parent.transforms, transform],
            discarded: parent.discarded,
            geometry,
          });
          return {
            protocol: 2,
            status: "transformed",
            context,
            design,
            ...describe(geometry),
            notice: UNTESTED,
          };
        }
        case "/v2/schematic/compare": {
          const a = designs.get(context, designId(v.designId)),
            b = designs.get(context, designId(v.otherId, "otherId"));
          const comparison = compareGeometries(a.geometry, b.geometry, {
            maxDetails: 0,
            limits: IN_GAME_LIMITS,
          });
          return {
            protocol: 2,
            status: "compared",
            context,
            a: brief(a, a.geometry),
            b: brief(b, b.geometry),
            overlap: comparison.overlap,
            counts: comparison.counts,
            materials: materialDelta(a.geometry, b.geometry),
            notice:
              "Geometry comparison only. Neither design is tested, and a difference in shape is not a difference in behaviour.",
          };
        }
        case "/v2/schematic/export": {
          const name = schematicName(v.name);
          const geometry = validateGeometry(v.geometry, IN_GAME_LIMITS);
          const bytes = exportSpongeSchematic(geometry);
          const file = this.files.create(name, bytes);
          return {
            protocol: 2,
            status: "exported",
            context,
            file,
            bytes: bytes.byteLength,
            digest: geometryDigest(geometry),
            ...describe(geometry),
          };
        }
      }
    } catch (error) {
      throw playerError(error);
    }
    throw new RequestError("Unknown schematic route or method.", 404);
  }
}

function importFile(bytes: Uint8Array, discard: boolean): ImportResult {
  return importSpongeSchematic(bytes, {
    discardBlockEntities: discard,
    discardBiomes: discard,
    limits: IN_GAME_LIMITS,
  });
}

function designId(value: unknown, name = "designId"): number {
  return integer(value, name, 1, Number.MAX_SAFE_INTEGER);
}

function transformName(value: unknown): GeometryTransform {
  if (!GEOMETRY_TRANSFORMS.includes(value as GeometryTransform))
    throw new RequestError(
      `Transform must be one of ${GEOMETRY_TRANSFORMS.join(", ")}.`,
    );
  return value as GeometryTransform;
}

function describe(geometry: SchematicGeometry) {
  const analysis = analyzeGeometry(geometry, IN_GAME_LIMITS);
  return {
    dataVersion: analysis.dataVersion,
    size: analysis.size,
    nonAirBlocks: analysis.nonAirCells,
    materials: analysis.blockIds
      .filter((c) => !AIR_BLOCK_IDS.has(c.key))
      .slice(0, MAX_MATERIALS),
    materialKinds: analysis.blockIds.filter((c) => !AIR_BLOCK_IDS.has(c.key))
      .length,
    states: analysis.blockStates
      .filter((c) => !AIR_BLOCK_IDS.has(c.key.split("[", 1)[0]!))
      .map((c) => c.key),
    placeable: analysis.nonAirCells <= MAX_PLACEMENT_BLOCKS,
  };
}

function brief(design: DesignSummary, geometry: SchematicGeometry) {
  const analysis = analyzeGeometry(geometry, IN_GAME_LIMITS);
  return {
    designId: design.designId,
    source: design.source.name,
    transforms: design.transforms,
    size: analysis.size,
    nonAirBlocks: analysis.nonAirCells,
    verification: design.verification,
  };
}

/**
 * Non-air blocks relative to the design's minimum corner, for the mod's
 * placement adapter. Air is omitted: it never means "clear this block".
 */
function placement(geometry: SchematicGeometry) {
  const { size, palette, blocks } = geometry;
  let nonAir = 0;
  for (const index of blocks)
    if (!AIR_BLOCK_IDS.has(palette[index]!.name)) nonAir++;
  if (nonAir > MAX_PLACEMENT_BLOCKS)
    return {
      available: false,
      reason: `This design has ${nonAir} blocks; in-game placement is limited to ${MAX_PLACEMENT_BLOCKS}.`,
    };
  const states = palette.map(formatBlockState);
  const list: { pos: [number, number, number]; state: string }[] = [];
  for (let y = 0; y < size.y; y++)
    for (let z = 0; z < size.z; z++)
      for (let x = 0; x < size.x; x++) {
        const index = blocks[x + z * size.x + y * size.x * size.z]!;
        if (!AIR_BLOCK_IDS.has(palette[index]!.name))
          list.push({ pos: [x, y, z], state: states[index]! });
      }
  return { available: true, blocks: list };
}

function materialDelta(a: SchematicGeometry, b: SchematicGeometry) {
  const counts = new Map<string, { a: number; b: number }>();
  const add = (list: readonly Count[], side: "a" | "b") => {
    for (const c of list) {
      if (AIR_BLOCK_IDS.has(c.key)) continue;
      const entry = counts.get(c.key) ?? { a: 0, b: 0 };
      entry[side] = c.count;
      counts.set(c.key, entry);
    }
  };
  add(analyzeGeometry(a, IN_GAME_LIMITS).blockIds, "a");
  add(analyzeGeometry(b, IN_GAME_LIMITS).blockIds, "b");
  return [...counts]
    .filter(([, c]) => c.a !== c.b)
    .map(([block, c]) => ({ block, a: c.a, b: c.b }))
    .sort((x, y) => (x.block < y.block ? -1 : x.block > y.block ? 1 : 0))
    .slice(0, MAX_MATERIALS);
}

const EXPLANATIONS: Partial<Record<string, string>> = {
  E_ENTITIES:
    "This file contains entities (mobs, item frames, armour stands or similar). Entities are never imported, so the file was rejected and nothing changed.",
  E_TOO_LARGE: `This design is too large for in-game use (at most ${IN_GAME_LIMITS.maxVolume} cells and ${IN_GAME_LIMITS.maxPaletteEntries} block states). Nothing was imported.`,
  E_INPUT_TOO_LARGE: `This file is larger than the in-game limit of ${IN_GAME_LIMITS.maxCompressedBytes} bytes. Nothing was imported.`,
  E_DECOMPRESSED_TOO_LARGE:
    "This file expands to more data than the in-game limit allows. Nothing was imported.",
  E_DATA_VERSION_MISMATCH:
    "These designs were saved by different Minecraft versions, so they cannot be compared cell by cell.",
};

/** Turns library errors into player-readable 422s; other errors pass through unchanged. */
function playerError(error: unknown): unknown {
  if (
    !(error instanceof SchematicError) &&
    !(error instanceof TransformError) &&
    !(error instanceof AnalysisError)
  )
    return error;
  const detail = error.message.replace(/^E_[A-Z_]+: /u, "");
  return new RequestError(
    EXPLANATIONS[error.code] ??
      (error instanceof TransformError
        ? `That transform is not supported for this design: ${detail}. Nothing was changed.`
        : `The file could not be used as a Sponge schematic (${error.code}: ${detail}). Nothing was imported.`),
    422,
  );
}
