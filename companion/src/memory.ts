import { DatabaseSync } from "node:sqlite";
import { mkdirSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import {
  type Blueprint,
  type Checkpoint,
  type DesignRequest,
  type Observation,
  RequestError,
} from "./types.js";

export class MemoryStore {
  private readonly db: DatabaseSync;
  constructor(
    dbPath: string,
    private readonly blueprintsDir?: string,
  ) {
    if (dbPath !== ":memory:")
      mkdirSync(dirname(dbPath), { recursive: true, mode: 0o700 });
    if (blueprintsDir)
      mkdirSync(blueprintsDir, { recursive: true, mode: 0o700 });
    this.db = new DatabaseSync(dbPath);
    this.db
      .exec(`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA synchronous=FULL;
      CREATE TABLE IF NOT EXISTS metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
      INSERT OR IGNORE INTO metadata VALUES('schema_version','1');
      CREATE TABLE IF NOT EXISTS projects(id TEXT PRIMARY KEY,world_id TEXT NOT NULL,dimension TEXT NOT NULL,name TEXT NOT NULL,objective TEXT NOT NULL,updated_at TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS blueprints(project_id TEXT NOT NULL REFERENCES projects(id),revision INTEGER NOT NULL,blueprint_id TEXT NOT NULL,document TEXT NOT NULL,PRIMARY KEY(project_id,revision));
      CREATE TABLE IF NOT EXISTS observations(id INTEGER PRIMARY KEY,world_id TEXT NOT NULL,dimension TEXT NOT NULL,tick INTEGER NOT NULL,document TEXT NOT NULL,created_at TEXT NOT NULL);
      CREATE INDEX IF NOT EXISTS observations_world ON observations(world_id,dimension,id);
      CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY,project_id TEXT NOT NULL REFERENCES projects(id),status TEXT NOT NULL,detail TEXT NOT NULL,observed TEXT,verified TEXT,updated_at TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS components(project_id TEXT NOT NULL REFERENCES projects(id),id TEXT NOT NULL,owner TEXT NOT NULL DEFAULT 'player',intended TEXT NOT NULL,PRIMARY KEY(project_id,id));
      CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,project_id TEXT,kind TEXT NOT NULL,document TEXT NOT NULL,created_at TEXT NOT NULL);`);
    const version = this.db
      .prepare("SELECT value FROM metadata WHERE key='schema_version'")
      .get() as { value: string };
    if (version.value !== "1")
      throw new Error(
        "Unsupported project database version. Preserve the file and upgrade the service.",
      );
  }
  private projectScope(
    projectId: string,
    worldId: string,
    dimension: string,
  ): boolean {
    const row = this.db
      .prepare("SELECT world_id,dimension FROM projects WHERE id=?")
      .get(projectId) as { world_id: string; dimension: string } | undefined;
    if (row && (row.world_id !== worldId || row.dimension !== dimension))
      throw new RequestError(
        "Project belongs to a different world or dimension.",
        409,
      );
    return !!row;
  }
  assertScope(projectId: string, worldId: string, dimension: string): void {
    this.projectScope(projectId, worldId, dimension);
  }
  saveDesign(input: DesignRequest, blueprint: Blueprint): Blueprint {
    this.projectScope(blueprint.projectId, input.worldId, input.dimension);
    const latest = this.db
      .prepare(
        "SELECT MAX(revision) AS revision FROM blueprints WHERE project_id=?",
      )
      .get(blueprint.projectId) as { revision: number | null };
    const saved: Blueprint = {
      ...blueprint,
      revision: (latest.revision ?? 0) + 1,
    };
    const document = JSON.stringify(saved),
      now = new Date().toISOString();
    this.db.exec("BEGIN IMMEDIATE");
    try {
      this.db
        .prepare(
          "INSERT INTO projects VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,objective=excluded.objective,updated_at=excluded.updated_at",
        )
        .run(
          saved.projectId,
          input.worldId,
          input.dimension,
          saved.name,
          input.request,
          now,
        );
      this.db
        .prepare("INSERT INTO blueprints VALUES(?,?,?,?)")
        .run(saved.projectId, saved.revision, saved.id, document);
      this.db
        .prepare(
          "UPDATE tasks SET status='PAUSED',detail='Blueprint revised; reconcile this task against the current revision before resuming.',observed=NULL,verified=NULL,updated_at=? WHERE project_id=?",
        )
        .run(now, saved.projectId);
      const componentIds = new Set(
        saved.blocks.map((block) => block.component),
      );
      for (const component of componentIds)
        this.db
          .prepare(
            "INSERT INTO components(project_id,id,intended) VALUES(?,?,?) ON CONFLICT(project_id,id) DO UPDATE SET intended=excluded.intended",
          )
          .run(
            saved.projectId,
            component,
            JSON.stringify(
              saved.blocks.filter((block) => block.component === component),
            ),
          );
      this.db
        .prepare(
          "INSERT INTO audit(project_id,kind,document,created_at) VALUES(?,?,?,?)",
        )
        .run(
          saved.projectId,
          "design",
          JSON.stringify({
            blueprintId: saved.id,
            revision: saved.revision,
            request: input.request,
          }),
          now,
        );
      this.db.exec("COMMIT");
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
    // SQLite is authoritative. The immutable export can be regenerated from it.
    if (this.blueprintsDir) {
      const file = join(
          this.blueprintsDir,
          `${saved.projectId}-r${saved.revision}.json`,
        ),
        temporary = `${file}.tmp`;
      writeFileSync(temporary, document, { mode: 0o600 });
      renameSync(temporary, file);
    }
    return saved;
  }
  projects(worldId: string): Record<string, unknown>[] {
    return this.db
      .prepare(
        `SELECT p.id,p.world_id AS worldId,p.dimension,p.name,p.objective,p.updated_at AS updatedAt,
      (SELECT MAX(revision) FROM blueprints b WHERE b.project_id=p.id) AS revision,
      (SELECT status FROM tasks t WHERE t.project_id=p.id ORDER BY updated_at DESC LIMIT 1) AS status
      FROM projects p WHERE world_id=? ORDER BY updated_at DESC LIMIT 100`,
      )
      .all(worldId) as Record<string, unknown>[];
  }
  recordObservation(observation: Observation): {
    changed: number;
    previousTick: number | null;
  } {
    const previous = this.db
      .prepare(
        "SELECT document,tick FROM observations WHERE world_id=? AND dimension=? ORDER BY id DESC LIMIT 1",
      )
      .get(observation.worldId, observation.dimension) as
      { document: string; tick: number } | undefined;
    let changed = 0;
    if (previous) {
      const old = JSON.parse(previous.document) as Observation;
      const states = new Map(
        old.blocks.map((b) => [`${b.pos.x},${b.pos.y},${b.pos.z}`, b.state]),
      );
      for (const block of observation.blocks)
        if (
          states.has(`${block.pos.x},${block.pos.y},${block.pos.z}`) &&
          states.get(`${block.pos.x},${block.pos.y},${block.pos.z}`) !==
            block.state
        )
          changed++;
    }
    this.db
      .prepare(
        "INSERT INTO observations(world_id,dimension,tick,document,created_at) VALUES(?,?,?,?,?)",
      )
      .run(
        observation.worldId,
        observation.dimension,
        observation.tick,
        JSON.stringify(observation),
        new Date().toISOString(),
      );
    // Bounded recent perception; project designs, checkpoints and audit stay durable.
    if (changed > 0) {
      this.db
        .prepare(
          "UPDATE tasks SET status='PAUSED',detail='World changed since observation; prior functional evidence is stale. Reconcile and test again.',verified=NULL,updated_at=? WHERE project_id IN (SELECT id FROM projects WHERE world_id=? AND dimension=?)",
        )
        .run(
          new Date().toISOString(),
          observation.worldId,
          observation.dimension,
        );
    }
    this.db
      .prepare(
        "DELETE FROM observations WHERE world_id=? AND dimension=? AND id NOT IN (SELECT id FROM observations WHERE world_id=? AND dimension=? ORDER BY id DESC LIMIT 20)",
      )
      .run(
        observation.worldId,
        observation.dimension,
        observation.worldId,
        observation.dimension,
      );
    return { changed, previousTick: previous?.tick ?? null };
  }
  checkpoint(checkpoint: Checkpoint): void {
    if (
      !this.projectScope(
        checkpoint.projectId,
        checkpoint.worldId,
        checkpoint.dimension,
      )
    )
      throw new RequestError(
        "Unknown project. Plan a design before reporting checkpoints.",
        404,
      );
    if (
      checkpoint.verified !== undefined &&
      checkpoint.blueprintRevision === undefined
    )
      throw new RequestError(
        "Functional evidence requires its blueprintRevision.",
      );
    if (checkpoint.blueprintRevision !== undefined) {
      if (
        !Number.isSafeInteger(checkpoint.blueprintRevision) ||
        checkpoint.blueprintRevision < 1
      )
        throw new RequestError("Invalid blueprintRevision.");
      if (
        this.blueprint(checkpoint.projectId)?.revision !==
        checkpoint.blueprintRevision
      )
        throw new RequestError(
          "Checkpoint belongs to a stale blueprint revision. Reconcile before reporting progress.",
          409,
        );
    }
    const existing = this.db
      .prepare("SELECT project_id FROM tasks WHERE id=?")
      .get(checkpoint.taskId) as { project_id: string } | undefined;
    if (existing && existing.project_id !== checkpoint.projectId)
      throw new RequestError("Task belongs to a different project.", 409);
    const now = new Date().toISOString();
    this.db.exec("BEGIN IMMEDIATE");
    try {
      this.db
        .prepare(
          `INSERT INTO tasks VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=excluded.status,detail=excluded.detail,
        observed=COALESCE(excluded.observed,tasks.observed),verified=excluded.verified,updated_at=excluded.updated_at`,
        )
        .run(
          checkpoint.taskId,
          checkpoint.projectId,
          checkpoint.status,
          checkpoint.detail,
          checkpoint.observed === undefined
            ? null
            : JSON.stringify(checkpoint.observed),
          checkpoint.verified === undefined
            ? null
            : JSON.stringify(checkpoint.verified),
          now,
        );
      this.db
        .prepare("UPDATE projects SET updated_at=? WHERE id=?")
        .run(now, checkpoint.projectId);
      this.db
        .prepare(
          "INSERT INTO audit(project_id,kind,document,created_at) VALUES(?,?,?,?)",
        )
        .run(
          checkpoint.projectId,
          "checkpoint",
          JSON.stringify(checkpoint),
          now,
        );
      this.db.exec("COMMIT");
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
  }
  context(
    projectId: string | undefined,
    worldId: string,
    dimension: string,
  ): string {
    if (!projectId) return "No prior project.";
    if (!this.projectScope(projectId, worldId, dimension))
      return "No prior project.";
    const project = this.db
      .prepare("SELECT name,objective FROM projects WHERE id=?")
      .get(projectId);
    const tasks = this.db
      .prepare(
        "SELECT id,status,detail FROM tasks WHERE project_id=? ORDER BY updated_at DESC LIMIT 4",
      )
      .all(projectId);
    return JSON.stringify({ project, tasks }).slice(0, 6000);
  }
  audit(projectId: string): Record<string, unknown>[] {
    return this.db
      .prepare(
        "SELECT kind,document,created_at AS createdAt FROM audit WHERE project_id=? ORDER BY id",
      )
      .all(projectId) as Record<string, unknown>[];
  }
  blueprint(projectId: string, revision?: number): Blueprint | undefined {
    const row =
      revision === undefined
        ? this.db
            .prepare(
              "SELECT document FROM blueprints WHERE project_id=? ORDER BY revision DESC LIMIT 1",
            )
            .get(projectId)
        : this.db
            .prepare(
              "SELECT document FROM blueprints WHERE project_id=? AND revision=?",
            )
            .get(projectId, revision);
    return row
      ? (JSON.parse((row as { document: string }).document) as Blueprint)
      : undefined;
  }
  close(): void {
    this.db.close();
  }
}
