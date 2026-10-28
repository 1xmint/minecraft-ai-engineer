import { fittingMechanisms, makeMechanismBlueprint } from "./catalog.js";
import { GatewayMemory } from "./gateway-memory.js";
import {
  actorId,
  gatewayContext,
  optionalBoolean,
  projectSync,
  type GatewayContext,
} from "./gateway-types.js";
import { PlanningQueue } from "./planning-queue.js";
import { validateInventory } from "./inventory.js";
import {
  identifier,
  integer,
  object,
  RequestError,
  validateCheckpoint,
  validateDesignRequest,
  validateObservation,
  type PlanningProvider,
} from "./types.js";

export interface GatewayOptions {
  memory: GatewayMemory;
  provider: PlanningProvider;
  queue: PlanningQueue;
  actorPlansPerMinute?: number;
  serverPlansPerHour?: number;
}

/** Called only after native loopback/host/token checks in the HTTP server. */
export class ServerGateway {
  private readonly liveEpochs = new Map<string, string>();
  private readonly planning = new Set<{
    context: GatewayContext;
    controller: AbortController;
  }>();
  constructor(private readonly options: GatewayOptions) {}
  private requireEpoch(
    context: Pick<GatewayContext, "serverId" | "serverEpoch">,
  ): void {
    if (this.liveEpochs.get(context.serverId) !== context.serverEpoch)
      throw new RequestError(
        "Server must register its current epoch with this companion process.",
        409,
      );
    this.options.memory.assertEpoch(context.serverId, context.serverEpoch);
  }
  private cancelStale(context: GatewayContext): void {
    for (const active of this.planning)
      if (
        active.context.serverId === context.serverId &&
        active.context.projectId === context.projectId &&
        (active.context.serverEpoch !== context.serverEpoch ||
          active.context.projectRevision !== context.projectRevision ||
          active.context.authorizationRevision !==
            context.authorizationRevision)
      )
        active.controller.abort();
  }
  close(): void {
    for (const active of this.planning) active.controller.abort();
    this.liveEpochs.clear();
  }

  async handle(
    method: string,
    pathname: string,
    value: unknown,
    query: URLSearchParams,
    signal: AbortSignal,
  ): Promise<unknown> {
    const memory = this.options.memory;
    if (method === "POST" && pathname === "/v2/server/open") {
      const v = object(value),
        serverId = identifier(v.serverId, "serverId"),
        serverEpoch = identifier(v.serverEpoch, "serverEpoch"),
        serverGeneration = integer(
          v.serverGeneration,
          "serverGeneration",
          1,
          Number.MAX_SAFE_INTEGER,
        );
      memory.open(
        serverId,
        serverEpoch,
        serverGeneration,
        this.liveEpochs.get(serverId) !== serverEpoch,
      );
      if (this.liveEpochs.get(serverId) !== serverEpoch) {
        for (const active of this.planning)
          if (active.context.serverId === serverId) active.controller.abort();
        this.liveEpochs.set(serverId, serverEpoch);
      }
      return {
        protocol: 2,
        status: "ready",
        serverId,
        serverEpoch,
        serverGeneration,
      };
    }
    if (method === "GET" && pathname === "/v2/projects") {
      const serverId = identifier(query.get("serverId"), "serverId"),
        serverEpoch = identifier(query.get("serverEpoch"), "serverEpoch");
      this.requireEpoch({ serverId, serverEpoch });
      return {
        protocol: 2,
        projects: memory.projects(
          serverId,
          serverEpoch,
          actorId(query.get("actorId")),
          identifier(query.get("worldId"), "worldId"),
        ),
      };
    }
    if (
      method !== "POST" ||
      ![
        "/v2/project",
        "/v2/checkpoint",
        "/v2/observe",
        "/v2/inventory",
        "/v2/inventory/read",
        "/v2/project/read",
        "/v2/design",
      ].includes(pathname)
    )
      throw new RequestError("Unknown gateway route or method.", 404);
    const v = object(value),
      context = gatewayContext(v.context);
    this.requireEpoch(context);
    if (pathname === "/v2/inventory") {
      const { context: _context, reportSequence, ...body } = v;
      memory.inventory(
        context,
        validateInventory(body),
        integer(reportSequence, "reportSequence", 1, Number.MAX_SAFE_INTEGER),
      );
      return { protocol: 2, status: "recorded", context };
    }
    if (pathname === "/v2/inventory/read") {
      return {
        protocol: 2,
        status: "private",
        context,
        inventory: memory.readInventory(context),
      };
    }
    if (method === "POST" && pathname === "/v2/project") {
      const input = projectSync(v),
        project = memory.sync(input);
      this.cancelStale(context);
      return {
        protocol: 2,
        status: "recorded",
        context,
        project,
        ...(input.blueprint ? { blueprint: input.blueprint } : {}),
      };
    }
    if (method === "POST" && pathname === "/v2/checkpoint") {
      memory.checkpoint(
        context,
        validateCheckpoint(v),
        integer(v.reportSequence, "reportSequence", 1, Number.MAX_SAFE_INTEGER),
      );
      return { protocol: 2, status: "recorded", context };
    }
    if (method === "POST" && pathname === "/v2/observe") {
      return {
        protocol: 2,
        status: "recorded",
        context,
        ...memory.observe(
          context,
          validateObservation(v),
          integer(
            v.reportSequence,
            "reportSequence",
            1,
            Number.MAX_SAFE_INTEGER,
          ),
        ),
      };
    }
    if (method === "POST" && pathname === "/v2/project/read") {
      const project = memory.assertCurrent(context);
      return {
        protocol: 2,
        context,
        project,
        blueprint: memory.blueprint(context) ?? null,
      };
    }
    if (method !== "POST" || pathname !== "/v2/design")
      throw new RequestError("Unknown gateway route or method.", 404);

    const project = memory.assertCurrent(context, "plan");
    if (
      !optionalBoolean(v.modelAuthorized, "modelAuthorized") ||
      !optionalBoolean(v.isOperator, "isOperator")
    )
      throw new RequestError(
        "Only explicit operator-owned planning is currently enabled. Shared-subscription eligibility is not established.",
        403,
      );
    const revisionOnly = optionalBoolean(v.revisionOnly, "revisionOnly");
    const input = validateDesignRequest({ ...v, projectId: context.projectId });
    if (
      input.worldId !== project.worldId ||
      input.dimension !== project.dimension ||
      (v.projectId !== undefined && v.projectId !== context.projectId)
    )
      throw new RequestError(
        "Design belongs to a different project/world.",
        409,
      );
    if (revisionOnly) {
      const current = memory.blueprint(context);
      if (!current)
        throw new RequestError(
          "A mechanism revision requires the current canonical blueprint.",
          409,
        );
      input.revision = {
        template: current.provenance.template,
        rotation: current.rotation,
      };
      input.rotation = current.rotation;
    }
    fittingMechanisms(input); // Fit validation before spending quota.
    if (context.projectRevision >= Number.MAX_SAFE_INTEGER)
      throw new RequestError("Project revision exhausted.", 409);
    memory.claim(context, "design");
    const controller = new AbortController(),
      active = { context, controller };
    const abort = () => controller.abort();
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) controller.abort();
    this.planning.add(active);
    try {
      return await this.options.queue.run(
        `${context.serverId}:${context.actorId}`,
        controller.signal,
        async () => {
          this.requireEpoch(context);
          memory.assertCurrent(context, "plan");
          memory.consumePlanningBudget(
            context,
            this.options.actorPlansPerMinute ?? 4,
            this.options.serverPlansPerHour ?? 24,
          );
          const plan = await this.options.provider.plan(
            input,
            controller.signal,
            memory.context(context),
          );
          if (controller.signal.aborted)
            throw new RequestError(
              "Planning cancelled; no proposal was accepted.",
              503,
            );
          this.requireEpoch(context);
          memory.assertCurrent(context, "plan");
          if (plan.template === "unsupported")
            throw new RequestError(
              plan.explanation || "Unsupported mechanism.",
              422,
            );
          const blueprint = {
            ...makeMechanismBlueprint(input, plan),
            revision: context.projectRevision + 1,
          };
          // The proposal is an audit record, not an accepted revision. Fabric must
          // registry-validate, accept, and sync it before any execution/report.
          memory.audit(context.serverId, context.projectId, "design-proposal", {
            context,
            blueprint,
          });
          return {
            protocol: 2,
            context,
            blueprint,
            projectId: context.projectId,
            message: plan.explanation,
          };
        },
      );
    } finally {
      signal.removeEventListener("abort", abort);
      this.planning.delete(active);
    }
  }
}
