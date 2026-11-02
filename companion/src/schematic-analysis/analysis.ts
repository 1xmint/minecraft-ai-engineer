import {
  DEFAULT_SCHEMATIC_LIMITS,
  formatBlockState,
  validateGeometry,
  type GeometryLimits,
  type SchematicGeometry,
  type Vec3,
} from "../schematics/index.js";

export const ANALYSIS_NOTICE =
  "Block counts and differences only. Not crafting costs, available inventory, circuit reliability or functional verification. Grants no ownership or permissions.";

/** Vanilla air blocks. Any other id, including modded "air", counts as a block. */
export const AIR_BLOCK_IDS: ReadonlySet<string> = new Set([
  "minecraft:air",
  "minecraft:cave_air",
  "minecraft:void_air",
]);

export interface Count {
  readonly key: string;
  readonly count: number;
}

export interface GeometryAnalysis {
  readonly report: "minecraft-ai-engineer.schematic-analysis";
  readonly reportVersion: 1;
  readonly notice: typeof ANALYSIS_NOTICE;
  readonly dataVersion: number;
  readonly size: Vec3;
  readonly offset: Vec3;
  readonly totalCells: number;
  readonly airCells: number;
  readonly nonAirCells: number;
  /** Cells per block id, counting every state of that id. */
  readonly blockIds: readonly Count[];
  /** Cells per complete canonical block state. */
  readonly blockStates: readonly Count[];
}

/** Most frequent first; ties by key in code-unit order, so output never depends on locale. */
export function sortCounts(counts: ReadonlyMap<string, number>): Count[] {
  return [...counts]
    .map(([key, count]) => ({ key, count }))
    .sort((a, b) =>
      b.count !== a.count
        ? b.count - a.count
        : a.key < b.key
          ? -1
          : a.key > b.key
            ? 1
            : 0,
    );
}

/**
 * Validates first so states are canonical (sorted properties, explicit
 * namespace) whatever the caller passed in.
 */
export function analyzeGeometry(
  value: unknown,
  limits: GeometryLimits = DEFAULT_SCHEMATIC_LIMITS,
): GeometryAnalysis {
  const g: SchematicGeometry = validateGeometry(value, limits);
  // Count from blocks, not the palette, so unused palette entries never appear.
  const perIndex = new Uint32Array(g.palette.length);
  for (const b of g.blocks) perIndex[b]!++;
  const ids = new Map<string, number>();
  const states = new Map<string, number>();
  let air = 0;
  g.palette.forEach((state, i) => {
    const n = perIndex[i]!;
    if (n === 0) return;
    states.set(formatBlockState(state), n);
    ids.set(state.name, (ids.get(state.name) ?? 0) + n);
    if (AIR_BLOCK_IDS.has(state.name)) air += n;
  });
  return {
    report: "minecraft-ai-engineer.schematic-analysis",
    reportVersion: 1,
    notice: ANALYSIS_NOTICE,
    dataVersion: g.dataVersion,
    size: g.size,
    offset: g.offset,
    totalCells: g.blocks.length,
    airCells: air,
    nonAirCells: g.blocks.length - air,
    blockIds: sortCounts(ids),
    blockStates: sortCounts(states),
  };
}
