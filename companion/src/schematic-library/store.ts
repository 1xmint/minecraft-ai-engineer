import type { DatabaseSync } from "node:sqlite";
import { RequestError } from "../types.js";
import type { GatewayContext } from "../gateway-types.js";
import {
  geometryDigest,
  parseGeometryJson,
  stringifyGeometry,
  type SchematicGeometry,
} from "../schematics/index.js";
import type { DiscardedBlockEntity } from "../schematics/sponge.js";
import type { GeometryTransform } from "../schematic-transforms/index.js";
import { IN_GAME_LIMITS } from "./limits.js";

export const MAX_DESIGNS_PER_PROJECT = 32;

export interface DesignDiscards {
  readonly blockEntities: readonly DiscardedBlockEntity[];
  readonly biomes: boolean;
  readonly metadataKeys: readonly string[];
}

/**
 * What a player sees about a stored design. `trust` and `verification` are
 * constants, not columns: nothing stored can make imported geometry trusted
 * or tested, and a transformed child never inherits evidence.
 */
export interface DesignSummary {
  readonly designId: number;
  readonly parentId: number | null;
  readonly source: { readonly name: string; readonly sha256: string };
  readonly transforms: readonly GeometryTransform[];
  readonly digest: string;
  readonly size: SchematicGeometry["size"];
  readonly createdBy: string;
  readonly createdAt: string;
  readonly discarded: DesignDiscards;
  readonly trust: "untrusted-geometry";
  readonly verification: "untested";
}

export interface StoredDesign extends DesignSummary {
  readonly geometry: SchematicGeometry;
}

interface Row {
  design_id: number;
  parent_id: number | null;
  source_name: string;
  source_sha256: string;
  transforms: string;
  discarded: string;
  geometry: string;
  digest: string;
  created_by: string;
  created_at: string;
}

/** Imported schematic designs per server project, in the companion's existing SQLite file. */
export class SchematicDesignStore {
  constructor(private readonly db: DatabaseSync) {
    db.exec(`INSERT OR IGNORE INTO metadata VALUES('schematic_design_schema_version','1');
      CREATE TABLE IF NOT EXISTS schematic_designs(server_id TEXT NOT NULL,project_id TEXT NOT NULL,design_id INTEGER NOT NULL,parent_id INTEGER,source_name TEXT NOT NULL,source_sha256 TEXT NOT NULL,transforms TEXT NOT NULL,discarded TEXT NOT NULL,geometry TEXT NOT NULL,digest TEXT NOT NULL,created_by TEXT NOT NULL,created_at TEXT NOT NULL,PRIMARY KEY(server_id,project_id,design_id));`);
    const version = db
      .prepare(
        "SELECT value FROM metadata WHERE key='schematic_design_schema_version'",
      )
      .get() as { value: string };
    if (version.value !== "1")
      throw new Error(
        "Unsupported schematic design database version. Preserve the file and upgrade the service.",
      );
  }

  add(
    context: GatewayContext,
    input: {
      parentId: number | null;
      source: { name: string; sha256: string };
      transforms: readonly GeometryTransform[];
      discarded: DesignDiscards;
      geometry: SchematicGeometry;
    },
  ): DesignSummary {
    const geometry = stringifyGeometry(input.geometry),
      digest = geometryDigest(input.geometry),
      now = new Date().toISOString();
    this.db.exec("BEGIN IMMEDIATE");
    try {
      const row = this.db
        .prepare(
          "SELECT COUNT(*) AS count, MAX(design_id) AS latest FROM schematic_designs WHERE server_id=? AND project_id=?",
        )
        .get(context.serverId, context.projectId) as {
        count: number;
        latest: number | null;
      };
      if (row.count >= MAX_DESIGNS_PER_PROJECT)
        throw new RequestError(
          `This project already keeps ${MAX_DESIGNS_PER_PROJECT} imported designs.`,
          409,
        );
      const designId = (row.latest ?? 0) + 1;
      this.db
        .prepare(
          "INSERT INTO schematic_designs VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        )
        .run(
          context.serverId,
          context.projectId,
          designId,
          input.parentId,
          input.source.name,
          input.source.sha256,
          JSON.stringify(input.transforms),
          JSON.stringify(input.discarded),
          geometry,
          digest,
          context.actorId,
          now,
        );
      this.db.exec("COMMIT");
      return summary({
        design_id: designId,
        parent_id: input.parentId,
        source_name: input.source.name,
        source_sha256: input.source.sha256,
        transforms: JSON.stringify(input.transforms),
        discarded: JSON.stringify(input.discarded),
        geometry,
        digest,
        created_by: context.actorId,
        created_at: now,
      });
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
  }

  list(context: GatewayContext): DesignSummary[] {
    const rows = this.db
      .prepare(
        "SELECT design_id,parent_id,source_name,source_sha256,transforms,discarded,'' AS geometry,digest,created_by,created_at,json_extract(geometry,'$.size.x') AS x,json_extract(geometry,'$.size.y') AS y,json_extract(geometry,'$.size.z') AS z FROM schematic_designs WHERE server_id=? AND project_id=? ORDER BY design_id",
      )
      .all(context.serverId, context.projectId) as unknown as (Row &
      SchematicGeometry["size"])[];
    return rows.map((row) => summary(row, { x: row.x, y: row.y, z: row.z }));
  }

  get(context: GatewayContext, designId: number): StoredDesign {
    const row = this.db
      .prepare(
        "SELECT design_id,parent_id,source_name,source_sha256,transforms,discarded,geometry,digest,created_by,created_at FROM schematic_designs WHERE server_id=? AND project_id=? AND design_id=?",
      )
      .get(context.serverId, context.projectId, designId) as Row | undefined;
    if (!row)
      throw new RequestError(
        `This project has no imported design #${designId}.`,
        404,
      );
    // Stored geometry is re-validated on every read; the database is not a trust boundary.
    const geometry = parseGeometryJson(row.geometry, IN_GAME_LIMITS);
    if (geometryDigest(geometry) !== row.digest)
      throw new RequestError(
        `Stored design #${designId} no longer matches its digest and was not used.`,
        409,
      );
    return { ...summary(row), geometry };
  }
}

function summary(row: Row, size?: SchematicGeometry["size"]): DesignSummary {
  return {
    designId: row.design_id,
    parentId: row.parent_id,
    source: { name: row.source_name, sha256: row.source_sha256 },
    transforms: JSON.parse(row.transforms) as GeometryTransform[],
    digest: row.digest,
    size:
      size ??
      (JSON.parse(row.geometry) as { size: SchematicGeometry["size"] }).size,
    createdBy: row.created_by,
    createdAt: row.created_at,
    discarded: JSON.parse(row.discarded) as DesignDiscards,
    trust: "untrusted-geometry",
    verification: "untested",
  };
}
