import {
  validateBehavior,
  validateBehaviorProposal,
  makeGeneratedBlueprint,
  type GeneratedRequest,
} from "./generated.js";
import { validateGeneratedAcceptance } from "./generated-context.js";
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
        "/v2/knowledge/read",
        "/v2/knowledge/note",
        "/v2/design",
        "/v2/behavior/propose",
        "/v2/design/generated",
      ].includes(pathname)
    )
      throw new RequestError("Unknown gateway route or method.", 404);
    const v = object(value),
      context = gatewayContext(v.context);
    this.requireEpoch(context);
    if (
      pathname === "/v2/knowledge/read" ||
      pathname === "/v2/knowledge/note"
    ) {
      const allowed =
        pathname === "/v2/knowledge/read"
          ? ["context"]
          : ["context", "note", "expectedRevision"];
      if (
        Object.keys(v).length !== allowed.length ||
        Object.keys(v).some((key) => !allowed.includes(key))
      )
        throw new RequestError("Invalid project knowledge request fields.");
      if (!context.connectionId)
        throw new RequestError(
          "Project knowledge requests require a current connection.",
          403,
        );
      if (pathname === "/v2/knowledge/read")
        return {
          protocol: 2,
          status: "shared",
          context,
          knowledge: memory.knowledge(context),
        };
      const note = memory.rememberNote(
        context,
        v.note,
        integer(
          v.expectedRevision,
          "expected knowledge revision",
          0,
          Number.MAX_SAFE_INTEGER - 1,
        ),
      );
      // New explicit requirements invalidate reasoning even when geometry/auth revisions did not change.
      for (const active of this.planning)
        if (
          active.context.serverId === context.serverId &&
          active.context.projectId === context.projectId
        )
          active.controller.abort();
      return { protocol: 2, status: "recorded", context, note };
    }
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
    if (
      pathname === "/v2/behavior/propose" ||
      pathname === "/v2/design/generated"
    )
      return this.generatedPlanning(pathname, v, context, signal);
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
      if (current.provenance.kind !== "curated")
        throw new RequestError(
          "Generated layouts require their separate confirmed design flow.",
          422,
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
            Object.freeze({ ...context }),
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
  private async generatedPlanning(
    route: string,
    v: Record<string, unknown>,
    context: GatewayContext,
    signal: AbortSignal,
  ): Promise<unknown> {
    const memory = this.options.memory,
      project = memory.assertCurrent(context, "plan");
    if (
      !optionalBoolean(v.modelAuthorized, "modelAuthorized") ||
      !optionalBoolean(v.isOperator, "isOperator")
    )
      throw new RequestError(
        "Generated planning requires explicit account-owning operator approval.",
        403,
      );
    if (!context.connectionId)
      throw new RequestError(
        "Generated planning requires a live native connection.",
        403,
      );
    const geometry = route === "/v2/design/generated";
    const fields = [
      "context",
      "request",
      "worldId",
      "dimension",
      "anchor",
      "rotation",
      "maxSize",
      "projectId",
      "modelAuthorized",
      "isOperator",
      ...(geometry ? ["behavior", "acceptance"] : []),
    ];
    if (Object.keys(v).some((key) => !fields.includes(key)))
      throw new RequestError("Unknown generated planning field.");
    const input = validateDesignRequest({ ...v, projectId: context.projectId });
    if (
      input.worldId !== project.worldId ||
      input.dimension !== project.dimension ||
      (v.projectId !== undefined && v.projectId !== context.projectId)
    )
      throw new RequestError(
        "Generated planning belongs to a different world/project.",
        409,
      );
    if (!input.maxSize)
      throw new RequestError(
        "Generated planning requires explicit delegated dimensions.",
      );
    if (context.projectRevision >= Number.MAX_SAFE_INTEGER)
      throw new RequestError("Project revision exhausted.", 409);
    const behavior = geometry ? validateBehavior(v.behavior) : undefined;
    const acceptance = behavior
      ? validateGeneratedAcceptance(v.acceptance, context, input, behavior)
      : undefined;
    const provider = this.options.provider;
    const propose = provider.proposeBehavior?.bind(provider),
      generate = provider.generateGeometry?.bind(provider);
    if ((geometry && !generate) || (!geometry && !propose))
      throw new RequestError(
        "This planning provider does not support generated designs.",
        503,
      );
    memory.claim(
      context,
      geometry ? "generated-geometry" : "generated-behavior",
    );
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
          if (acceptance && behavior) {
            validateGeneratedAcceptance(acceptance, context, input, behavior);
            // Reuse the existing durable replay ledger, not a new authorization system.
            memory.claim(
              {
                ...context,
                requestId: `generated-confirm:${acceptance.proposalId}`,
              },
              "generated-confirmation",
            );
          }
          memory.consumePlanningBudget(
            context,
            this.options.actorPlansPerMinute ?? 4,
            this.options.serverPlansPerHour ?? 24,
          );
          const authority = Object.freeze({ ...context });
          if (!geometry) {
            const proposal = validateBehaviorProposal(
              await propose!(
                input,
                controller.signal,
                memory.context(context),
                authority,
              ),
            );
            if (controller.signal.aborted)
              throw new RequestError(
                "Generated planning cancelled; no proposal accepted.",
                503,
              );
            this.requireEpoch(context);
            memory.assertCurrent(context, "plan");
            memory.audit(
              context.serverId,
              context.projectId,
              "behavior-proposal",
              { context, proposal },
            );
            return { protocol: 2, context, proposal };
          }
          const request: GeneratedRequest = Object.freeze({
            ...input,
            anchor: Object.freeze({ ...input.anchor }),
            maxSize: Object.freeze({ ...input.maxSize! }),
            behavior: Object.freeze({ ...behavior! }),
          });
          const raw = await generate!(
            request,
            controller.signal,
            memory.context(context),
            authority,
          );
          if (controller.signal.aborted)
            throw new RequestError(
              "Generated planning cancelled; no design accepted.",
              503,
            );
          this.requireEpoch(context);
          memory.assertCurrent(context, "plan");
          validateGeneratedAcceptance(acceptance, context, input, behavior!);
          const blueprint = {
            ...makeGeneratedBlueprint(input, raw, behavior),
            revision: context.projectRevision + 1,
          };
          memory.audit(
            context.serverId,
            context.projectId,
            "generated-design-proposal",
            { context, blueprint },
          );
          return {
            protocol: 2,
            context,
            blueprint,
            projectId: context.projectId,
            message: blueprint.explanation,
          };
        },
      );
    } finally {
      signal.removeEventListener("abort", abort);
      this.planning.delete(active);
    }
  }
}
