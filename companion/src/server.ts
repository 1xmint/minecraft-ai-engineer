import {
  createServer,
  type IncomingMessage,
  type ServerResponse,
} from "node:http";
import { timingSafeEqual } from "node:crypto";
import { makeReferenceBlueprint } from "./blueprint.js";
import { MemoryStore } from "./memory.js";
import { ServerGateway } from "./gateway.js";
import { PlanningQueue } from "./planning-queue.js";
import {
  type PlanningProvider,
  RequestError,
  identifier,
  validateCheckpoint,
  validateDesignRequest,
  validateObservation,
} from "./types.js";

export interface ServerOptions {
  token: string;
  store: MemoryStore;
  provider: PlanningProvider;
  maxBodyBytes?: number;
  planTimeoutMs?: number;
  actorPlansPerMinute?: number;
  serverPlansPerHour?: number;
}
function respond(res: ServerResponse, status: number, data: unknown): void {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(JSON.stringify(data));
}
async function body(req: IncomingMessage, maxBytes: number): Promise<unknown> {
  if (
    req.headers["content-type"]?.split(";", 1)[0]?.trim().toLowerCase() !==
    "application/json"
  )
    throw new RequestError("Expected application/json.", 415);
  const declaredLength = Number(req.headers["content-length"] ?? 0);
  if (
    !Number.isSafeInteger(declaredLength) ||
    declaredLength < 0 ||
    declaredLength > maxBytes
  )
    throw new RequestError("Request body too large.", 413);
  const chunks: Buffer[] = [];
  let bytes = 0;
  for await (const chunk of req) {
    const buffer = Buffer.from(chunk as Uint8Array);
    bytes += buffer.length;
    if (bytes > maxBytes)
      throw new RequestError("Request body too large.", 413);
    chunks.push(buffer);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  } catch {
    throw new RequestError("Invalid JSON.");
  }
}
export function createCompanionServer(options: ServerOptions) {
  if (options.token.length < 32)
    throw new Error("Service token must contain at least 32 characters.");
  for (const limit of [options.actorPlansPerMinute, options.serverPlansPerHour])
    if (
      limit !== undefined &&
      (!Number.isSafeInteger(limit) || limit < 1 || limit > 1000)
    )
      throw new Error("Planning limits must be integers between 1 and 1000.");
  const expected = Buffer.from(`Bearer ${options.token}`);
  const planning = new PlanningQueue();
  const gateway = new ServerGateway({
    memory: options.store.gateway,
    provider: options.provider,
    queue: planning,
    ...(options.actorPlansPerMinute === undefined
      ? {}
      : { actorPlansPerMinute: options.actorPlansPerMinute }),
    ...(options.serverPlansPerHour === undefined
      ? {}
      : { serverPlansPerHour: options.serverPlansPerHour }),
  });
  const controllers = new Set<AbortController>();
  const server = createServer((req, res) => {
    void (async () => {
      const host = req.headers.host ?? "";
      if (
        req.headers.origin !== undefined ||
        !/^127\.0\.0\.1(?::\d{1,5})?$/u.test(host)
      )
        throw new RequestError(
          "Only native loopback clients are allowed.",
          403,
        );
      const actual = Buffer.from(req.headers.authorization ?? "");
      if (
        actual.length !== expected.length ||
        !timingSafeEqual(actual, expected)
      )
        throw new RequestError("Unauthorized.", 401);
      const url = new URL(req.url ?? "/", "http://127.0.0.1");
      if (url.pathname.startsWith("/v2/")) {
        const controller = new AbortController();
        const timeout = setTimeout(
          () => controller.abort(),
          options.planTimeoutMs ?? 95_000,
        );
        const abort = () => {
          if (!res.writableEnded) controller.abort();
        };
        controllers.add(controller);
        res.once("close", abort);
        try {
          const value =
            req.method === "POST"
              ? await body(req, options.maxBodyBytes ?? 2_000_000)
              : undefined;
          const result = await gateway.handle(
            req.method ?? "",
            url.pathname,
            value,
            url.searchParams,
            controller.signal,
          );
          if (!res.destroyed) respond(res, 200, result);
        } finally {
          clearTimeout(timeout);
          res.removeListener("close", abort);
          controllers.delete(controller);
        }
        return;
      }
      if (req.method === "GET" && url.pathname === "/v1/health")
        return respond(res, 200, { protocol: 1, status: "ok" });
      if (req.method === "GET" && url.pathname === "/v1/projects")
        return respond(res, 200, {
          projects: options.store.projects(
            identifier(url.searchParams.get("worldId"), "worldId"),
          ),
        });
      if (req.method === "POST" && url.pathname === "/v1/design") {
        if (planning.busy)
          throw new RequestError(
            "Another planning request is active. Wait or cancel it before retrying.",
            409,
          );
        const input = validateDesignRequest(
          await body(req, options.maxBodyBytes ?? 2_000_000),
        );
        if (input.projectId)
          options.store.assertScope(
            input.projectId,
            input.worldId,
            input.dimension,
          );
        // Check fit before spending subscription quota.
        makeReferenceBlueprint(input);
        // Body parsing yields to other requests. Acquire the single planning
        // slot only after that await and immediately before invoking a provider.
        if (planning.busy)
          throw new RequestError(
            "Another planning request is active. Wait or cancel it before retrying.",
            409,
          );
        const controller = new AbortController();
        controllers.add(controller);
        const timeout = setTimeout(
          () => controller.abort(),
          options.planTimeoutMs ?? 95_000,
        );
        const abort = () => {
          if (!res.writableEnded) controller.abort();
        };
        res.once("close", abort);
        try {
          const plan = await planning.run(
            "legacy",
            controller.signal,
            () =>
              options.provider.plan(
                input,
                controller.signal,
                options.store.context(
                  input.projectId,
                  input.worldId,
                  input.dimension,
                ),
              ),
            false,
          );
          if (controller.signal.aborted)
            throw new RequestError("Planning cancelled or timed out.", 503);
          if (plan.template !== "signal-lamp")
            throw new RequestError(
              plan.explanation ||
                "This request needs a mechanism that is not supported yet.",
              422,
            );
          const blueprint = options.store.saveDesign(
            input,
            makeReferenceBlueprint(input, plan.explanation),
          );
          respond(res, 200, {
            blueprint,
            message: plan.explanation,
            projectId: blueprint.projectId,
          });
        } finally {
          clearTimeout(timeout);
          res.removeListener("close", abort);
          controllers.delete(controller);
        }
        return;
      }
      if (req.method === "POST" && url.pathname === "/v1/observe") {
        const observation = validateObservation(
          await body(req, options.maxBodyBytes ?? 2_000_000),
        );
        return respond(res, 200, {
          status: "recorded",
          ...options.store.recordObservation(observation),
        });
      }
      if (req.method === "POST" && url.pathname === "/v1/checkpoint") {
        options.store.checkpoint(
          validateCheckpoint(
            await body(req, options.maxBodyBytes ?? 2_000_000),
          ),
        );
        return respond(res, 200, { status: "recorded" });
      }
      throw new RequestError("Unknown route or method.", 404);
    })().catch((error: unknown) => {
      if (!res.writableEnded && !res.destroyed)
        respond(res, error instanceof RequestError ? error.status : 500, {
          error:
            error instanceof RequestError
              ? error.message
              : "The local companion could not complete this request.",
        });
    });
  });
  server.requestTimeout = 100_000;
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5_000;
  server.maxHeadersCount = 40;
  server.once("close", () => {
    gateway.close();
    for (const controller of controllers) controller.abort();
  });
  return server;
}
