import type { DatabaseSync } from "node:sqlite";
import type { Blueprint, Checkpoint, Observation } from "./types.js";
import { RequestError } from "./types.js";
import type {
  GatewayContext,
  ProjectMember,
  ProjectSync,
} from "./gateway-types.js";

interface ProjectRow {
  server_id: string;
  project_id: string;
  world_id: string;
  dimension: string;
  name: string;
  project_revision: number;
  authorization_revision: number;
  members: string;
  synced_epoch: string;
}
export interface GatewayProject {
  serverId: string;
  projectId: string;
  worldId: string;
  dimension: string;
  name: string;
  projectRevision: number;
  authorizationRevision: number;
  members: ProjectMember[];
}

/** Server assertions are mirrored for stale/privacy checks, never issued here. */
export class GatewayMemory {
  constructor(private readonly db: DatabaseSync) {
    db.exec(`
      INSERT OR IGNORE INTO metadata VALUES('gateway_schema_version','1');
      CREATE TABLE IF NOT EXISTS gateway_servers(server_id TEXT PRIMARY KEY,current_epoch TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS gateway_epochs(server_id TEXT NOT NULL,epoch TEXT NOT NULL,retired INTEGER NOT NULL,PRIMARY KEY(server_id,epoch));
      CREATE TABLE IF NOT EXISTS gateway_projects(server_id TEXT NOT NULL,project_id TEXT NOT NULL,world_id TEXT NOT NULL,dimension TEXT NOT NULL,name TEXT NOT NULL,project_revision INTEGER NOT NULL,authorization_revision INTEGER NOT NULL,members TEXT NOT NULL,synced_epoch TEXT NOT NULL,PRIMARY KEY(server_id,project_id));
      CREATE TABLE IF NOT EXISTS gateway_blueprints(server_id TEXT NOT NULL,project_id TEXT NOT NULL,revision INTEGER NOT NULL,document TEXT NOT NULL,PRIMARY KEY(server_id,project_id,revision));
      CREATE TABLE IF NOT EXISTS gateway_tasks(server_id TEXT NOT NULL,project_id TEXT NOT NULL,task_id TEXT NOT NULL,project_revision INTEGER NOT NULL,authorization_revision INTEGER NOT NULL,epoch TEXT NOT NULL,status TEXT NOT NULL,detail TEXT NOT NULL,observed TEXT,verified TEXT,PRIMARY KEY(server_id,project_id,task_id));
      CREATE TABLE IF NOT EXISTS gateway_observations(id INTEGER PRIMARY KEY,server_id TEXT NOT NULL,project_id TEXT NOT NULL,document TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS gateway_requests(server_id TEXT NOT NULL,request_id TEXT NOT NULL,kind TEXT NOT NULL,PRIMARY KEY(server_id,request_id));
      CREATE TABLE IF NOT EXISTS gateway_audit(id INTEGER PRIMARY KEY,server_id TEXT NOT NULL,project_id TEXT,kind TEXT NOT NULL,document TEXT NOT NULL,created_at TEXT NOT NULL);
    `);
    const version = db
      .prepare("SELECT value FROM metadata WHERE key='gateway_schema_version'")
      .get() as { value: string };
    if (version.value !== "1")
      throw new Error(
        "Unsupported gateway database version; preserve the server data before upgrading.",
      );
  }
  private atomic<T>(work: () => T): T {
    this.db.exec("BEGIN IMMEDIATE");
    try {
      const result = work();
      this.db.exec("COMMIT");
      return result;
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
  }
  audit(
    serverId: string,
    projectId: string | undefined,
    kind: string,
    document: unknown,
  ): void {
    this.db
      .prepare(
        "INSERT INTO gateway_audit(server_id,project_id,kind,document,created_at) VALUES(?,?,?,?,?)",
      )
      .run(
        serverId,
        projectId ?? null,
        kind,
        JSON.stringify(document),
        new Date().toISOString(),
      );
  }
  open(serverId: string, epoch: string): void {
    this.atomic(() => {
      const known = this.db
        .prepare(
          "SELECT retired FROM gateway_epochs WHERE server_id=? AND epoch=?",
        )
        .get(serverId, epoch) as { retired: number } | undefined;
      if (known?.retired)
        throw new RequestError(
          "Server epoch is retired. Reopen the current server instance.",
          409,
        );
      this.db
        .prepare(
          "UPDATE gateway_epochs SET retired=1 WHERE server_id=? AND epoch<>?",
        )
        .run(serverId, epoch);
      this.db
        .prepare(
          "INSERT INTO gateway_epochs VALUES(?,?,0) ON CONFLICT(server_id,epoch) DO NOTHING",
        )
        .run(serverId, epoch);
      this.db
        .prepare(
          "INSERT INTO gateway_servers VALUES(?,?) ON CONFLICT(server_id) DO UPDATE SET current_epoch=excluded.current_epoch",
        )
        .run(serverId, epoch);
      // Every service process must receive a fresh server-owned project mirror.
      this.db
        .prepare(
          "UPDATE gateway_projects SET synced_epoch='' WHERE server_id=?",
        )
        .run(serverId);
      this.db
        .prepare(
          "UPDATE gateway_tasks SET status='PAUSED',detail='Server/service registration changed; reconcile before resuming.',observed=NULL,verified=NULL WHERE server_id=?",
        )
        .run(serverId);
      this.audit(serverId, undefined, "server-open", { serverEpoch: epoch });
    });
  }
  assertEpoch(serverId: string, epoch: string): void {
    const row = this.db
      .prepare("SELECT current_epoch FROM gateway_servers WHERE server_id=?")
      .get(serverId) as { current_epoch: string } | undefined;
    if (row?.current_epoch !== epoch)
      throw new RequestError("Stale or unopened server epoch.", 409);
  }
  private row(serverId: string, projectId: string): ProjectRow | undefined {
    return this.db
      .prepare(
        "SELECT * FROM gateway_projects WHERE server_id=? AND project_id=?",
      )
      .get(serverId, projectId) as ProjectRow | undefined;
  }
  private publicProject(row: ProjectRow): GatewayProject {
    return {
      serverId: row.server_id,
      projectId: row.project_id,
      worldId: row.world_id,
      dimension: row.dimension,
      name: row.name,
      projectRevision: row.project_revision,
      authorizationRevision: row.authorization_revision,
      members: JSON.parse(row.members) as ProjectMember[],
    };
  }
  assertCurrent(
    context: GatewayContext,
    right: "read" | "edit" | "plan" = "read",
  ): GatewayProject {
    this.assertEpoch(context.serverId, context.serverEpoch);
    const row = this.row(context.serverId, context.projectId);
    if (!row)
      throw new RequestError(
        "Unknown server project. Synchronize it first.",
        404,
      );
    if (row.synced_epoch !== context.serverEpoch)
      throw new RequestError(
        "Project needs a fresh server-owned synchronization.",
        409,
      );
    const project = this.publicProject(row),
      member = project.members.find((m) => m.actorId === context.actorId);
    if (
      !member ||
      (right === "edit" && member.role === "viewer") ||
      (right === "plan" && (member.role !== "owner" || !member.canPlan))
    )
      throw new RequestError(
        "Player is not authorized for this project operation.",
        403,
      );
    if (
      project.projectRevision !== context.projectRevision ||
      project.authorizationRevision !== context.authorizationRevision
    )
      throw new RequestError("Stale project or authorization revision.", 409);
    return project;
  }
  claim(context: GatewayContext, kind: string): void {
    const previous = this.db
      .prepare(
        "SELECT kind FROM gateway_requests WHERE server_id=? AND request_id=?",
      )
      .get(context.serverId, context.requestId);
    if (previous)
      throw new RequestError(
        "Request already processed; use a new explicit request after reconciliation.",
        409,
      );
    this.db
      .prepare("INSERT INTO gateway_requests VALUES(?,?,?)")
      .run(context.serverId, context.requestId, kind);
  }
  sync(input: ProjectSync): GatewayProject {
    const c = input.context;
    this.assertEpoch(c.serverId, c.serverEpoch);
    return this.atomic(() => {
      const previous = this.row(c.serverId, c.projectId),
        members = JSON.stringify(input.members);
      if (previous) {
        const oldMembers = JSON.parse(previous.members) as ProjectMember[],
          actor = oldMembers.find((m) => m.actorId === c.actorId);
        if (!input.isOperator && (!actor || actor.role === "viewer"))
          throw new RequestError(
            "Project synchronization requires an authorized editor.",
            403,
          );
        if (
          previous.world_id !== input.worldId ||
          previous.dimension !== input.dimension
        )
          throw new RequestError(
            "Project belongs to a different world/dimension.",
            409,
          );
        if (
          c.projectRevision < previous.project_revision ||
          c.authorizationRevision < previous.authorization_revision
        )
          throw new RequestError("Stale project synchronization.", 409);
        if (
          previous.members !== members &&
          c.authorizationRevision === previous.authorization_revision
        )
          throw new RequestError(
            "Changed membership requires a new authorization revision.",
            409,
          );
        if (
          c.authorizationRevision > previous.authorization_revision &&
          !input.isOperator &&
          actor?.role !== "owner"
        )
          throw new RequestError(
            "Only the owner may mirror a changed authorization grant.",
            403,
          );
      } else if (
        !input.isOperator &&
        !input.members.some(
          (m) => m.actorId === c.actorId && m.role === "owner",
        )
      ) {
        throw new RequestError(
          "Initial project synchronization requires its owner.",
          403,
        );
      }
      if (input.blueprint) {
        const stored = this.db
          .prepare(
            "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
          )
          .get(c.serverId, c.projectId, c.projectRevision) as
          { document: string } | undefined;
        const document = JSON.stringify(input.blueprint);
        if (stored && stored.document !== document)
          throw new RequestError(
            "An immutable blueprint revision cannot be replaced.",
            409,
          );
        if (!stored)
          this.db
            .prepare("INSERT INTO gateway_blueprints VALUES(?,?,?,?)")
            .run(c.serverId, c.projectId, c.projectRevision, document);
      }
      this.claim(c, "project-sync");
      this.db
        .prepare(
          "INSERT INTO gateway_projects VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(server_id,project_id) DO UPDATE SET name=excluded.name,project_revision=excluded.project_revision,authorization_revision=excluded.authorization_revision,members=excluded.members,synced_epoch=excluded.synced_epoch",
        )
        .run(
          c.serverId,
          c.projectId,
          input.worldId,
          input.dimension,
          input.name,
          c.projectRevision,
          c.authorizationRevision,
          members,
          c.serverEpoch,
        );
      if (
        previous &&
        (previous.project_revision !== c.projectRevision ||
          previous.authorization_revision !== c.authorizationRevision)
      )
        this.db
          .prepare(
            "UPDATE gateway_tasks SET status='PAUSED',detail='Project or authorization revision changed; reconcile before resuming.',observed=NULL,verified=NULL WHERE server_id=? AND project_id=?",
          )
          .run(c.serverId, c.projectId);
      this.audit(c.serverId, c.projectId, "project-sync", input);
      return this.publicProject(this.row(c.serverId, c.projectId)!);
    });
  }
  projects(
    serverId: string,
    epoch: string,
    actorId: string,
    worldId: string,
  ): GatewayProject[] {
    this.assertEpoch(serverId, epoch);
    return (
      this.db
        .prepare(
          "SELECT * FROM gateway_projects WHERE server_id=? AND world_id=? AND synced_epoch=? AND EXISTS(SELECT 1 FROM json_each(gateway_projects.members) member WHERE json_extract(member.value,'$.actorId')=?) ORDER BY project_id LIMIT 100",
        )
        .all(serverId, worldId, epoch, actorId) as unknown as ProjectRow[]
    )
      .map((row) => this.publicProject(row))
      .filter((p) => p.members.some((m) => m.actorId === actorId));
  }
  blueprint(context: GatewayContext): Blueprint | undefined {
    this.assertCurrent(context);
    const row = this.db
      .prepare(
        "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
      )
      .get(context.serverId, context.projectId, context.projectRevision) as
      { document: string } | undefined;
    return row ? (JSON.parse(row.document) as Blueprint) : undefined;
  }
  checkpoint(context: GatewayContext, checkpoint: Checkpoint): void {
    const project = this.assertCurrent(context, "edit");
    if (
      checkpoint.projectId !== context.projectId ||
      checkpoint.worldId !== project.worldId ||
      checkpoint.dimension !== project.dimension ||
      (checkpoint.blueprintRevision !== undefined &&
        checkpoint.blueprintRevision !== context.projectRevision)
    )
      throw new RequestError(
        "Checkpoint identity/revision does not match its server context.",
        409,
      );
    if (checkpoint.verified !== undefined && !this.blueprint(context))
      throw new RequestError(
        "Functional evidence requires a synchronized intended blueprint.",
        409,
      );
    this.atomic(() => {
      this.claim(context, "checkpoint");
      this.db
        .prepare(
          "INSERT INTO gateway_tasks VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(server_id,project_id,task_id) DO UPDATE SET project_revision=excluded.project_revision,authorization_revision=excluded.authorization_revision,epoch=excluded.epoch,status=excluded.status,detail=excluded.detail,observed=COALESCE(excluded.observed,gateway_tasks.observed),verified=excluded.verified",
        )
        .run(
          context.serverId,
          context.projectId,
          checkpoint.taskId,
          context.projectRevision,
          context.authorizationRevision,
          context.serverEpoch,
          checkpoint.status,
          checkpoint.detail,
          checkpoint.observed === undefined
            ? null
            : JSON.stringify(checkpoint.observed),
          checkpoint.verified === undefined
            ? null
            : JSON.stringify(checkpoint.verified),
        );
      this.audit(context.serverId, context.projectId, "checkpoint", {
        context,
        checkpoint,
      });
    });
  }
  observe(
    context: GatewayContext,
    observation: Observation,
  ): { changed: number; previousTick: number | null } {
    const project = this.assertCurrent(context);
    if (
      project.worldId !== observation.worldId ||
      project.dimension !== observation.dimension
    )
      throw new RequestError(
        "Observation belongs to a different world/dimension.",
        409,
      );
    return this.atomic(() => {
      this.claim(context, "observe");
      const previous = this.db
        .prepare(
          "SELECT document FROM gateway_observations WHERE server_id=? AND project_id=? ORDER BY id DESC LIMIT 1",
        )
        .get(context.serverId, context.projectId) as
        { document: string } | undefined;
      const old = previous
        ? (JSON.parse(previous.document) as Observation)
        : undefined;
      const oldStates = new Map(
        old?.blocks.map((b) => [`${b.pos.x},${b.pos.y},${b.pos.z}`, b.state]),
      );
      const changed = observation.blocks.filter((b) => {
        const key = `${b.pos.x},${b.pos.y},${b.pos.z}`;
        return oldStates.has(key) && oldStates.get(key) !== b.state;
      }).length;
      this.db
        .prepare(
          "INSERT INTO gateway_observations(server_id,project_id,document) VALUES(?,?,?)",
        )
        .run(context.serverId, context.projectId, JSON.stringify(observation));
      this.db
        .prepare(
          "DELETE FROM gateway_observations WHERE server_id=? AND project_id=? AND id NOT IN (SELECT id FROM gateway_observations WHERE server_id=? AND project_id=? ORDER BY id DESC LIMIT 20)",
        )
        .run(
          context.serverId,
          context.projectId,
          context.serverId,
          context.projectId,
        );
      if (changed)
        this.db
          .prepare(
            "UPDATE gateway_tasks SET status='PAUSED',detail='Observed world changes invalidate functional evidence.',observed=NULL,verified=NULL WHERE server_id=? AND project_id=?",
          )
          .run(context.serverId, context.projectId);
      return { changed, previousTick: old?.tick ?? null };
    });
  }
  context(context: GatewayContext): string {
    const project = this.assertCurrent(context, "plan");
    const tasks = this.db
      .prepare(
        "SELECT task_id AS id,status,detail FROM gateway_tasks WHERE server_id=? AND project_id=? ORDER BY rowid DESC LIMIT 4",
      )
      .all(context.serverId, context.projectId);
    return JSON.stringify({
      project: { name: project.name, projectRevision: project.projectRevision },
      tasks,
    }).slice(0, 6000);
  }
  consumePlanningBudget(
    context: GatewayContext,
    actorPerMinute: number,
    serverPerHour: number,
  ): void {
    this.atomic(() => {
      const minute = new Date(Date.now() - 60_000).toISOString(),
        hour = new Date(Date.now() - 3_600_000).toISOString();
      const actor = this.db
        .prepare(
          "SELECT COUNT(*) AS count FROM gateway_audit WHERE server_id=? AND kind='planning-start' AND created_at>? AND json_extract(document,'$.actorId')=?",
        )
        .get(context.serverId, minute, context.actorId) as { count: number };
      const server = this.db
        .prepare(
          "SELECT COUNT(*) AS count FROM gateway_audit WHERE server_id=? AND kind='planning-start' AND created_at>?",
        )
        .get(context.serverId, hour) as { count: number };
      if (actor.count >= actorPerMinute || server.count >= serverPerHour)
        throw new RequestError(
          "The operator's configured planning budget is exhausted. Retry manually after the window resets.",
          429,
        );
      this.audit(
        context.serverId,
        context.projectId,
        "planning-start",
        context,
      );
    });
  }
}
