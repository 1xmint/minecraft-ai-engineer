import { actorId } from "./gateway-types.js";
import {
  identifier,
  integer,
  object,
  position,
  RequestError,
  type Position,
} from "./types.js";

export interface InventorySnapshot {
  schemaVersion: 1;
  worldId: string;
  dimension: string;
  actorId: string;
  connectionId: string;
  source: "player" | "container";
  slots: number;
  tick: number;
  containers: Position[];
  items: { id: string; count: number }[];
}

function keys(value: Record<string, unknown>, allowed: string[]): void {
  if (Object.keys(value).some((key) => !allowed.includes(key)))
    throw new RequestError("Unknown private inventory field.");
}

/** Accept only bounded registry IDs and counts, never names, NBT or text. */
export function validateInventory(value: unknown): InventorySnapshot {
  const v = object(value);
  keys(v, ["schemaVersion", "worldId", "dimension", "actorId", "connectionId",
    "source", "slots", "tick", "containers", "items"]);
  if (v.schemaVersion !== 1 || (v.source !== "player" && v.source !== "container"))
    throw new RequestError("Invalid private inventory schema or source.");
  const slots = integer(v.slots, "slots", 1, 128);
  if (!Array.isArray(v.containers) ||
      (v.source === "player" ? v.containers.length !== 0 :
        v.containers.length < 1 || v.containers.length > 2))
    throw new RequestError("Invalid inventory backing positions.");
  const containers = v.containers.map((entry) => {
    const p = object(entry);
    keys(p, ["x", "y", "z"]);
    return position(p);
  });
  if (new Set(containers.map((p) => `${p.x},${p.y},${p.z}`)).size !== containers.length)
    throw new RequestError("Duplicate inventory backing position.");
  if (!Array.isArray(v.items) || v.items.length > slots)
    throw new RequestError("Invalid inventory item totals.");
  const seen = new Set<string>();
  let total = 0;
  const items = v.items.map((entry) => {
    const item = object(entry);
    keys(item, ["id", "count"]);
    if (typeof item.id !== "string" || item.id.length > 128 ||
        !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/u.test(item.id) || seen.has(item.id))
      throw new RequestError("Invalid or duplicate inventory item ID.");
    seen.add(item.id);
    const count = integer(item.count, "item count", 1, slots * 2_147_483_647);
    total += count;
    if (total > slots * 2_147_483_647)
      throw new RequestError("Inventory item total exceeds slot capacity.");
    return { id: item.id, count };
  });
  return {
    schemaVersion: 1,
    worldId: identifier(v.worldId, "worldId"),
    dimension: identifier(v.dimension, "dimension"),
    actorId: actorId(v.actorId),
    connectionId: actorId(v.connectionId),
    source: v.source,
    slots,
    tick: integer(v.tick, "tick", 0, Number.MAX_SAFE_INTEGER),
    containers,
    items,
  };
}
