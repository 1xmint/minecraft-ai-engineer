import {
  type Blueprint,
  type Position,
  type Rotation,
  RequestError,
  identifier,
  integer,
  object,
  position,
  text,
} from "./types.js";

export interface GatewayContext {
  serverId: string;
  serverEpoch: string;
  actorId: string;
  projectId: string;
  projectRevision: number;
  authorizationRevision: number;
  requestId: string;
}
export interface ProjectMember {
  actorId: string;
  role: "owner" | "editor" | "viewer";
  canPlan: boolean;
}
export interface ProjectSync {
  context: GatewayContext;
  worldId: string;
  dimension: string;
  name: string;
  members: ProjectMember[];
  blueprint?: Blueprint;
  isOperator: boolean;
}
export function actorId(value: unknown): string {
  const result = identifier(value, "actorId");
  if (
    !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/iu.test(
      result,
    )
  )
    throw new RequestError("actorId must be a Minecraft player UUID.");
  return result.toLowerCase();
}
export function gatewayContext(value: unknown): GatewayContext {
  const v = object(value);
  return {
    serverId: identifier(v.serverId, "serverId"),
    serverEpoch: identifier(v.serverEpoch, "serverEpoch"),
    actorId: actorId(v.actorId),
    projectId: identifier(v.projectId, "projectId"),
    projectRevision: integer(
      v.projectRevision,
      "projectRevision",
      1,
      Number.MAX_SAFE_INTEGER,
    ),
    authorizationRevision: integer(
      v.authorizationRevision,
      "authorizationRevision",
      1,
      Number.MAX_SAFE_INTEGER,
    ),
    requestId: identifier(v.requestId, "requestId"),
  };
}
export function projectMembers(value: unknown): ProjectMember[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > 128)
    throw new RequestError("Project membership must contain 1–128 players.");
  const seen = new Set<string>();
  const members = value.map((entry: unknown) => {
    const v = object(entry),
      id = actorId(v.actorId);
    if (!["owner", "editor", "viewer"].includes(String(v.role)) || seen.has(id))
      throw new RequestError("Invalid or duplicate project membership.");
    if (v.canPlan !== undefined && typeof v.canPlan !== "boolean")
      throw new RequestError("canPlan must be an explicit boolean.");
    seen.add(id);
    return {
      actorId: id,
      role: v.role as ProjectMember["role"],
      canPlan: v.canPlan === true,
    };
  });
  if (members.filter((m) => m.role === "owner").length !== 1)
    throw new RequestError("A project must have exactly one owner.");
  return members.sort((a, b) => a.actorId.localeCompare(b.actorId));
}
export function optionalBoolean(value: unknown, name: string): boolean {
  if (value !== undefined && typeof value !== "boolean")
    throw new RequestError(`${name} must be a boolean.`);
  return value === true;
}

/** Shape validation is defense in depth; Fabric still validates the live registry. */
export function serverBlueprint(
  value: unknown,
  context: GatewayContext,
  worldId: string,
  dimension: string,
): Blueprint {
  const v = object(value);
  if (
    v.schemaVersion !== 1 ||
    v.projectId !== context.projectId ||
    v.worldId !== worldId ||
    v.dimension !== dimension ||
    v.revision !== context.projectRevision
  )
    throw new RequestError(
      "Blueprint identity or revision does not match its server context.",
      409,
    );
  if (
    ![0, 90, 180, 270].includes(Number(v.rotation)) ||
    typeof v.rotation !== "number"
  )
    throw new RequestError("Invalid blueprint rotation.");
  if (!Array.isArray(v.stages) || v.stages.length < 1 || v.stages.length > 64)
    throw new RequestError("Invalid blueprint stages.");
  const stageIds = new Set<string>();
  const stages = v.stages.map((entry: unknown) => {
    const s = object(entry),
      id = identifier(s.id, "stage id");
    if (stageIds.has(id)) throw new RequestError("Duplicate blueprint stage.");
    stageIds.add(id);
    return {
      id,
      title: text(s.title, "stage title", 256),
      explanation: text(s.explanation, "stage explanation", 4000),
    };
  });
  if (!Array.isArray(v.blocks) || v.blocks.length < 1 || v.blocks.length > 4096)
    throw new RequestError("Invalid blueprint block count.");
  const seen = new Set<string>(),
    resources: Record<string, number> = {};
  const blocks = v.blocks.map((entry: unknown) => {
    const b = object(entry),
      pos = position(b.pos),
      stage = identifier(b.stage, "stage"),
      state = text(b.state, "block state", 512);
    const key = `${pos.x},${pos.y},${pos.z}`;
    if (
      seen.has(key) ||
      !stageIds.has(stage) ||
      Math.max(Math.abs(pos.x), Math.abs(pos.y), Math.abs(pos.z)) > 64
    )
      throw new RequestError(
        "Duplicate, out-of-range or unassigned blueprint block.",
      );
    if (!/^minecraft:[a-z0-9_]+(?:\[[a-z0-9_=,]+\])?$/u.test(state))
      throw new RequestError("Invalid block state syntax.");
    const prerequisites = b.prerequisites === undefined ? [] : b.prerequisites;
    if (
      !Array.isArray(prerequisites) ||
      prerequisites.length > 64 ||
      prerequisites.some(
        (id: unknown) => typeof id !== "string" || !stageIds.has(id),
      )
    )
      throw new RequestError("Invalid placement prerequisites.");
    seen.add(key);
    const name = state.split("[", 1)[0]!;
    resources[name] = (resources[name] ?? 0) + 1;
    return {
      pos,
      state,
      stage,
      component: identifier(b.component, "component"),
      prerequisites: prerequisites as string[],
    };
  });
  const coordinate = (
    axis: keyof Position,
    fn: (...numbers: number[]) => number,
  ) => fn(...blocks.map((b) => b.pos[axis]));
  const min = {
    x: coordinate("x", Math.min),
    y: coordinate("y", Math.min),
    z: coordinate("z", Math.min),
  };
  const max = {
    x: coordinate("x", Math.max),
    y: coordinate("y", Math.max),
    z: coordinate("z", Math.max),
  };
  const provenance = object(v.provenance);
  if (provenance.kind !== "curated" || provenance.minecraft !== "26.3")
    throw new RequestError("Unsupported blueprint provenance.");
  if (
    v.ports !== undefined &&
    (!Array.isArray(v.ports) || v.ports.length > 128)
  )
    throw new RequestError("Invalid blueprint ports.");
  const ports = ((v.ports ?? []) as unknown[]).map((entry) => {
    const port = object(entry),
      pos = position(port.pos);
    if (port.kind !== "input" && port.kind !== "output")
      throw new RequestError("Invalid port kind.");
    if (
      pos.x < min.x ||
      pos.x > max.x ||
      pos.y < min.y ||
      pos.y > max.y ||
      pos.z < min.z ||
      pos.z > max.z
    )
      throw new RequestError("Port lies outside blueprint bounds.");
    return {
      name: text(port.name, "port name", 128),
      pos,
      kind: port.kind as "input" | "output",
    };
  });
  if (
    v.tests !== undefined &&
    (!Array.isArray(v.tests) || v.tests.length > 128)
  )
    throw new RequestError("Invalid blueprint tests.");
  const tests = ((v.tests ?? []) as unknown[]).map((entry) => {
    const expected = object(entry);
    return {
      id: identifier(expected.id, "test id"),
      description: text(expected.description, "test description", 4000),
    };
  });
  return {
    schemaVersion: 1,
    id: identifier(v.id, "blueprint id"),
    revision: context.projectRevision,
    projectId: context.projectId,
    worldId,
    dimension,
    name: text(v.name, "blueprint name", 256),
    explanation:
      v.explanation === undefined
        ? "Server-provided intended blueprint; functional status is recorded separately."
        : text(v.explanation, "explanation", 4000),
    anchor: position(v.anchor),
    rotation: v.rotation as Rotation,
    blocks,
    stages,
    bounds: { min, max },
    resources,
    ports,
    tests,
    provenance: {
      kind: "curated",
      template: identifier(provenance.template, "template"),
      minecraft: "26.3",
    },
    verification: "untested",
  };
}
export function projectSync(value: unknown): ProjectSync {
  const v = object(value),
    context = gatewayContext(v.context),
    worldId = identifier(v.worldId, "worldId"),
    dimension = identifier(v.dimension, "dimension");
  const blueprint =
    v.blueprint === undefined
      ? undefined
      : serverBlueprint(v.blueprint, context, worldId, dimension);
  return {
    context,
    worldId,
    dimension,
    name:
      v.name === undefined
        ? (blueprint?.name ?? "Engineering project")
        : text(v.name, "name", 256),
    members: projectMembers(v.members),
    isOperator: optionalBoolean(v.isOperator, "isOperator"),
    ...(blueprint ? { blueprint } : {}),
  };
}
