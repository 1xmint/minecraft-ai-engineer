import { randomUUID } from "node:crypto";
import { actorId } from "./gateway-types.js";
import {
  validateProjectNote,
  readProjectNote,
  type ProjectNote,
  type ProjectKnowledge,
} from "./project-knowledge.js";
import type { DatabaseSync } from "node:sqlite";
import { validateInventory, type InventorySnapshot } from "./inventory.js";
import {
  componentMemoryScopes,
  expectedWorldStates,
  geometryState,
  positionKey,
  readsPosition,
} from "./component-memory.js";
interface TaskRow {
  task_id: string;
  project_revision: number;
  status: string;
  observed: string | null;
}

import type { Blueprint, Checkpoint, Observation } from "./types.js";
import { integer, RequestError } from "./types.js";
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
  private readonly inventories = new Map<
    string,
    {
      context: GatewayContext;
      snapshot: InventorySnapshot;
      recordedAt: number;
    }
  >();
  constructor(private readonly db: DatabaseSync) {
    db.exec(`
      INSERT OR IGNORE INTO metadata VALUES('gateway_schema_version','1');
      INSERT OR IGNORE INTO metadata VALUES('project_knowledge_schema_version','1');
      CREATE TABLE IF NOT EXISTS gateway_knowledge_revisions(server_id TEXT NOT NULL,project_id TEXT NOT NULL,revision INTEGER NOT NULL CHECK(revision>=0),PRIMARY KEY(server_id,project_id));
      CREATE TABLE IF NOT EXISTS gateway_project_notes(server_id TEXT NOT NULL,project_id TEXT NOT NULL,note_revision INTEGER NOT NULL CHECK(note_revision>0),document TEXT NOT NULL,PRIMARY KEY(server_id,project_id,note_revision));
      CREATE TABLE IF NOT EXISTS gateway_servers(server_id TEXT PRIMARY KEY,current_epoch TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS gateway_epochs(server_id TEXT NOT NULL,epoch TEXT NOT NULL,retired INTEGER NOT NULL,PRIMARY KEY(server_id,epoch));
      CREATE TABLE IF NOT EXISTS gateway_boots(server_id TEXT PRIMARY KEY,generation INTEGER NOT NULL,epoch TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS gateway_projects(server_id TEXT NOT NULL,project_id TEXT NOT NULL,world_id TEXT NOT NULL,dimension TEXT NOT NULL,name TEXT NOT NULL,project_revision INTEGER NOT NULL,authorization_revision INTEGER NOT NULL,members TEXT NOT NULL,synced_epoch TEXT NOT NULL,PRIMARY KEY(server_id,project_id));
      CREATE TABLE IF NOT EXISTS gateway_blueprints(server_id TEXT NOT NULL,project_id TEXT NOT NULL,revision INTEGER NOT NULL,document TEXT NOT NULL,PRIMARY KEY(server_id,project_id,revision));
      CREATE TABLE IF NOT EXISTS gateway_tasks(server_id TEXT NOT NULL,project_id TEXT NOT NULL,task_id TEXT NOT NULL,project_revision INTEGER NOT NULL,authorization_revision INTEGER NOT NULL,epoch TEXT NOT NULL,status TEXT NOT NULL,detail TEXT NOT NULL,observed TEXT,verified TEXT,PRIMARY KEY(server_id,project_id,task_id));
      CREATE TABLE IF NOT EXISTS gateway_observations(id INTEGER PRIMARY KEY,server_id TEXT NOT NULL,project_id TEXT NOT NULL,document TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS gateway_requests(server_id TEXT NOT NULL,request_id TEXT NOT NULL,kind TEXT NOT NULL,PRIMARY KEY(server_id,request_id));
      CREATE TABLE IF NOT EXISTS gateway_reports(server_id TEXT NOT NULL,project_id TEXT NOT NULL,epoch TEXT NOT NULL,last_sequence INTEGER NOT NULL,PRIMARY KEY(server_id,project_id,epoch));
      CREATE TABLE IF NOT EXISTS gateway_audit(id INTEGER PRIMARY KEY,server_id TEXT NOT NULL,project_id TEXT,kind TEXT NOT NULL,document TEXT NOT NULL,created_at TEXT NOT NULL);
    `);
    const version = db
      .prepare("SELECT value FROM metadata WHERE key='gateway_schema_version'")
      .get() as { value: string };
    if (version.value !== "1")
      throw new Error(
        "Unsupported gateway database version; preserve the server data before upgrading.",
      );
    const knowledgeVersion = db
      .prepare(
        "SELECT value FROM metadata WHERE key='project_knowledge_schema_version'",
      )
      .get() as { value: string };
    if (knowledgeVersion.value !== "1")
      throw new Error(
        "Unsupported project knowledge database version; preserve the server data before upgrading.",
      );
  }
  private noteRevision(context: GatewayContext): number {
    const row = this.db
      .prepare(
        "SELECT revision FROM gateway_knowledge_revisions WHERE server_id=? AND project_id=?",
      )
      .get(context.serverId, context.projectId) as
      { revision: number } | undefined;
    return integer(
      row?.revision ?? 0,
      "project knowledge revision",
      0,
      Number.MAX_SAFE_INTEGER,
    );
  }
  /** Append-only literal project knowledge; this never changes design/evidence or grants a lease. */
  rememberNote(
    context: GatewayContext,
    value: unknown,
    expectedRevision: number,
  ): ProjectNote {
    const input = validateProjectNote(value);
    integer(
      expectedRevision,
      "expected knowledge revision",
      0,
      Number.MAX_SAFE_INTEGER - 1,
    );
    if (!context.connectionId)
      throw new RequestError(
        "Project notes require a current connected owner.",
        403,
      );
    actorId(context.connectionId);
    return this.atomic(() => {
      const project = this.assertCurrent(context, "edit");
      if (
        project.members.find((member) => member.actorId === context.actorId)
          ?.role !== "owner"
      )
        throw new RequestError(
          "Only the project owner may author shared project notes.",
          403,
        );
      if (this.knowledge(context).revision !== expectedRevision)
        throw new RequestError(
          "Project knowledge changed; read it before authoring another note.",
          409,
        );
      this.claim(context, "project-note");
      const note = readProjectNote({
        ...input,
        id: randomUUID(),
        revision: expectedRevision + 1,
        authorId: context.actorId,
        createdAt: Date.now(),
        source: "owner-authored",
      });
      this.db
        .prepare("INSERT INTO gateway_project_notes VALUES(?,?,?,?)")
        .run(
          context.serverId,
          context.projectId,
          note.revision,
          JSON.stringify(note),
        );
      this.db
        .prepare(
          "INSERT INTO gateway_knowledge_revisions VALUES(?,?,?) ON CONFLICT(server_id,project_id) DO UPDATE SET revision=excluded.revision",
        )
        .run(context.serverId, context.projectId, note.revision);
      this.audit(context.serverId, context.projectId, "project-note", {
        context,
        note,
      });
      return note;
    });
  }
  knowledge(context: GatewayContext, limit = 32): ProjectKnowledge {
    this.assertCurrent(context, "read");
    integer(limit, "project-note page size", 1, 32);
    const revision = this.noteRevision(context);
    const rows = this.db
      .prepare(
        "SELECT note_revision,document FROM gateway_project_notes WHERE server_id=? AND project_id=? ORDER BY note_revision DESC LIMIT ?",
      )
      .all(context.serverId, context.projectId, limit) as {
      note_revision: number;
      document: string;
    }[];
    const notes = rows
      .map((row, index) => {
        const note = readProjectNote(JSON.parse(row.document));
        if (
          note.revision !== row.note_revision ||
          note.revision !== revision - index
        )
          throw new RequestError(
            "Stored project-note revision is inconsistent.",
            409,
          );
        return note;
      })
      .reverse();
    if (notes.length !== Math.min(revision, limit))
      throw new RequestError("Stored project-note history is incomplete.", 409);
    return Object.freeze({
      schemaVersion: 1,
      revision,
      notes: Object.freeze(notes),
      hasEarlier: revision > notes.length,
    });
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
  open(
    serverId: string,
    epoch: string,
    generation: number,
    refresh = true,
  ): void {
    integer(generation, "serverGeneration", 1, Number.MAX_SAFE_INTEGER);
    this.atomic(() => {
      const boot = this.db
        .prepare("SELECT generation,epoch FROM gateway_boots WHERE server_id=?")
        .get(serverId) as { generation: number; epoch: string } | undefined;
      if (
        boot &&
        (generation < boot.generation ||
          (generation === boot.generation && epoch !== boot.epoch) ||
          (generation > boot.generation && epoch === boot.epoch))
      )
        throw new RequestError(
          "Stale or mismatched server boot generation.",
          409,
        );
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
          "INSERT INTO gateway_boots VALUES(?,?,?) ON CONFLICT(server_id) DO UPDATE SET generation=excluded.generation,epoch=excluded.epoch",
        )
        .run(serverId, generation, epoch);
      if (!refresh) return;
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
    if (refresh) this.clearInventories(serverId);
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
  /** Called inside the report's transaction, so rejection cannot partially update memory. */
  private claimReport(
    context: GatewayContext,
    reportSequence: number,
    kind: string,
  ): void {
    integer(reportSequence, "reportSequence", 1, Number.MAX_SAFE_INTEGER);
    const previous = this.db
      .prepare(
        "SELECT last_sequence FROM gateway_reports WHERE server_id=? AND project_id=? AND epoch=?",
      )
      .get(context.serverId, context.projectId, context.serverEpoch) as
      { last_sequence: number } | undefined;
    if (previous && reportSequence <= previous.last_sequence)
      throw new RequestError(
        "Stale or duplicate project report sequence.",
        409,
      );
    this.claim(context, kind);
    this.db
      .prepare(
        "INSERT INTO gateway_reports VALUES(?,?,?,?) ON CONFLICT(server_id,project_id,epoch) DO UPDATE SET last_sequence=excluded.last_sequence",
      )
      .run(
        context.serverId,
        context.projectId,
        context.serverEpoch,
        reportSequence,
      );
  }
  private intended(
    serverId: string,
    projectId: string,
    revision: number,
  ): Blueprint | undefined {
    const row = this.db
      .prepare(
        "SELECT document FROM gateway_blueprints WHERE server_id=? AND project_id=? AND revision=?",
      )
      .get(serverId, projectId, revision) as { document: string } | undefined;
    return row ? (JSON.parse(row.document) as Blueprint) : undefined;
  }
  private tasks(serverId: string, projectId: string): TaskRow[] {
    return this.db
      .prepare(
        "SELECT task_id,project_revision,status,observed FROM gateway_tasks WHERE server_id=? AND project_id=?",
      )
      .all(serverId, projectId) as unknown as TaskRow[];
  }
  private taskComponent(task: TaskRow, projectId: string): string | undefined {
    if (!task.observed) return undefined;
    const observed: unknown = JSON.parse(task.observed);
    if (
      !observed ||
      typeof observed !== "object" ||
      !("component" in observed) ||
      typeof observed.component !== "string"
    )
      return undefined;
    return task.task_id === `${projectId}:${observed.component}`
      ? observed.component
      : undefined;
  }
  private invalidate(
    serverId: string,
    projectId: string,
    taskId: string,
    detail: string,
  ): void {
    this.db
      .prepare(
        "UPDATE gateway_tasks SET status='PAUSED',detail=?,observed=NULL,verified=NULL WHERE server_id=? AND project_id=? AND task_id=?",
      )
      .run(detail, serverId, projectId, taskId);
  }
  sync(input: ProjectSync): GatewayProject {
    const c = input.context;
    this.assertEpoch(c.serverId, c.serverEpoch);
    const before = this.row(c.serverId, c.projectId);
    const result = this.atomic(() => {
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
        previous.authorization_revision !== c.authorizationRevision
      ) {
        this.db
          .prepare(
            "UPDATE gateway_tasks SET status='PAUSED',detail='Project authorization changed; reconcile before resuming.',observed=NULL,verified=NULL WHERE server_id=? AND project_id=?",
          )
          .run(c.serverId, c.projectId);
      } else if (previous && previous.project_revision !== c.projectRevision) {
        const current = componentMemoryScopes(input.blueprint);
        const old = new Map<number, ReturnType<typeof componentMemoryScopes>>();
        for (const task of this.tasks(c.serverId, c.projectId)) {
          const component = this.taskComponent(task, c.projectId);
          if (!old.has(task.project_revision))
            old.set(
              task.project_revision,
              componentMemoryScopes(
                this.intended(c.serverId, c.projectId, task.project_revision),
              ),
            );
          const before =
            component && old.get(task.project_revision)?.get(component);
          const after = component && current.get(component);
          if (
            !before ||
            !after ||
            before.intention !== after.intention ||
            ["EXECUTING", "VERIFYING", "PLANNING"].includes(task.status)
          )
            this.invalidate(
              c.serverId,
              c.projectId,
              task.task_id,
              "Component or prerequisite intent changed; reconcile before resuming.",
            );
          // An unchanged row retains its original revision, scope and evidence verbatim.
        }
      }
      this.audit(c.serverId, c.projectId, "project-sync", input);
      return this.publicProject(this.row(c.serverId, c.projectId)!);
    });
    if (
      before &&
      (before.project_revision !== c.projectRevision ||
        before.authorization_revision !== c.authorizationRevision ||
        before.synced_epoch !== c.serverEpoch)
    )
      this.clearInventories(c.serverId, c.projectId);
    return result;
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
  checkpoint(
    context: GatewayContext,
    checkpoint: Checkpoint,
    reportSequence: number,
  ): void {
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
      this.claimReport(context, reportSequence, "checkpoint");
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
        reportSequence,
        checkpoint,
      });
    });
  }
  observe(
    context: GatewayContext,
    observation: Observation,
    reportSequence: number,
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
    if (
      observation.perception &&
      observation.perception.actorId !== context.actorId
    )
      throw new RequestError(
        "Targeted perception must describe its authenticated requesting actor.",
        403,
      );
    return this.atomic(() => {
      this.claimReport(context, reportSequence, "observe");
      const history = this.db
        .prepare(
          "SELECT document FROM gateway_observations WHERE server_id=? AND project_id=? ORDER BY id DESC LIMIT 20",
        )
        .all(context.serverId, context.projectId) as { document: string }[];
      const oldStates = new Map<string, string>();
      let previousTick: number | null = null;
      for (const row of history) {
        const previous = JSON.parse(row.document) as Observation;
        previousTick ??= previous.tick;
        for (const block of previous.blocks) {
          const key = positionKey(block.pos);
          if (!oldStates.has(key)) oldStates.set(key, block.state);
        }
      }
      const changedBlocks = observation.blocks.filter((block) => {
        const old = oldStates.get(positionKey(block.pos));
        return old !== undefined && old !== block.state;
      });
      const changed = changedBlocks.length;
      const intended = this.intended(
        context.serverId,
        context.projectId,
        context.projectRevision,
      );
      const scopes = componentMemoryScopes(intended),
        expected = expectedWorldStates(intended);
      // Previously uncovered/expired coordinates also stale relevant results when they contradict geometry.
      const conflicting = observation.blocks.filter(
        (block) =>
          !oldStates.has(positionKey(block.pos)) &&
          geometryState(block.state) !==
            (expected.get(positionKey(block.pos)) ??
              geometryState("minecraft:air")),
      );
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
      if (changed || conflicting.length)
        for (const task of this.tasks(context.serverId, context.projectId)) {
          const component = this.taskComponent(task, context.projectId);
          const scope = component && scopes.get(component);
          if (
            !scope ||
            [...changedBlocks, ...conflicting].some((block) =>
              readsPosition(scope, block.pos),
            )
          )
            this.invalidate(
              context.serverId,
              context.projectId,
              task.task_id,
              "Observed component or prerequisite changes invalidate functional evidence.",
            );
        }
      return { changed, previousTick };
    });
  }
  private inventoryKey(context: GatewayContext): string {
    return JSON.stringify([
      context.serverId,
      context.projectId,
      context.actorId,
    ]);
  }
  private clearInventories(serverId: string, projectId?: string): void {
    for (const [key, entry] of this.inventories)
      if (
        entry.context.serverId === serverId &&
        (projectId === undefined || entry.context.projectId === projectId)
      )
        this.inventories.delete(key);
  }
  private expireInventories(now: number): void {
    for (const [key, entry] of this.inventories) {
      const age = now - entry.recordedAt;
      if (age < 0 || age >= 60_000) this.inventories.delete(key);
    }
  }
  inventory(
    context: GatewayContext,
    input: InventorySnapshot,
    reportSequence: number,
  ): void {
    // Validate again for direct callers; no rejection may claim report ordering.
    const snapshot = validateInventory(input);
    if (
      !context.connectionId ||
      snapshot.actorId !== context.actorId ||
      snapshot.connectionId !== context.connectionId
    )
      throw new RequestError(
        "Inventory must match its authenticated actor and connection.",
        403,
      );
    const project = this.assertCurrent(context);
    if (
      snapshot.worldId !== project.worldId ||
      snapshot.dimension !== project.dimension
    )
      throw new RequestError(
        "Inventory belongs to a different world/dimension.",
        409,
      );
    this.atomic(() => this.claimReport(context, reportSequence, "inventory"));
    const now = Date.now();
    this.expireInventories(now);
    const key = this.inventoryKey(context);
    this.inventories.delete(key);
    if (this.inventories.size >= 256)
      this.inventories.delete(this.inventories.keys().next().value!);
    this.inventories.set(key, {
      context: { ...context },
      snapshot: structuredClone(snapshot),
      recordedAt: now,
    });
  }
  readInventory(context: GatewayContext): {
    snapshot: InventorySnapshot;
    ageMs: number;
  } | null {
    this.assertCurrent(context);
    const now = Date.now();
    this.expireInventories(now);
    if (!context.connectionId) return null;
    const entry = this.inventories.get(this.inventoryKey(context));
    if (
      !entry ||
      entry.context.connectionId !== context.connectionId ||
      entry.context.serverEpoch !== context.serverEpoch ||
      entry.context.projectRevision !== context.projectRevision ||
      entry.context.authorizationRevision !== context.authorizationRevision ||
      entry.snapshot.actorId !== context.actorId
    )
      return null;
    return {
      snapshot: structuredClone(entry.snapshot),
      ageMs: now - entry.recordedAt,
    };
  }
  /** Private advice only. Never pass this to shared blueprint planning. */
  privateContext(context: GatewayContext): string {
    return JSON.stringify({ inventory: this.readInventory(context) });
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
