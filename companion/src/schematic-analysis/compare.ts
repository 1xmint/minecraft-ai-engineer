import {
  DEFAULT_SCHEMATIC_LIMITS,
  formatBlockState,
  validateGeometry,
  type GeometryLimits,
  type SchematicGeometry,
  type Vec3,
} from "../schematics/index.js";
import { AIR_BLOCK_IDS, ANALYSIS_NOTICE } from "./analysis.js";
import { AnalysisError } from "./errors.js";

export const DEFAULT_MAX_DETAILS = 100;
/** Hard ceiling per detail list; block states can be up to 4 KB each. */
export const MAX_DETAILS_LIMIT = 10_000;

export interface CompareOptions {
  /** Detail entries listed per category, 0 to MAX_DETAILS_LIMIT. Counts stay exact. */
  readonly maxDetails?: number;
  readonly limits?: GeometryLimits;
}

export interface ChangedCell {
  readonly pos: Vec3;
  readonly a: string;
  readonly b: string;
}

/** A cell inside one volume and outside the other's bounding box entirely. */
export interface OneSidedCell {
  readonly pos: Vec3;
  readonly state: string;
}

export interface DetailList<T> {
  readonly listed: number;
  readonly truncated: boolean;
  readonly items: readonly T[];
}

export interface OneSidedCount {
  readonly cells: number;
  readonly air: number;
  readonly nonAir: number;
}

export interface VolumeSummary {
  readonly size: Vec3;
  readonly offset: Vec3;
  readonly cells: number;
}

export interface GeometryComparison {
  readonly report: "minecraft-ai-engineer.schematic-comparison";
  readonly reportVersion: 1;
  readonly notice: typeof ANALYSIS_NOTICE;
  readonly dataVersion: number;
  readonly a: VolumeSummary;
  readonly b: VolumeSummary;
  /** World-space intersection of the two volumes, or null when they do not overlap. */
  readonly overlap: { readonly min: Vec3; readonly size: Vec3 } | null;
  readonly counts: {
    readonly identical: number;
    readonly changed: number;
    readonly onlyInA: OneSidedCount;
    readonly onlyInB: OneSidedCount;
  };
  readonly maxDetails: number;
  /** Listed in each input's storage order (x fastest, then z, then y). */
  readonly details: {
    readonly changed: DetailList<ChangedCell>;
    readonly onlyInA: DetailList<OneSidedCell>;
    readonly onlyInB: DetailList<OneSidedCell>;
  };
}

class Collector<T> {
  readonly items: T[] = [];
  total = 0;
  constructor(private readonly max: number) {}
  add(make: () => T): void {
    if (this.total++ < this.max) this.items.push(make());
  }
  done(): DetailList<T> {
    return {
      listed: this.items.length,
      truncated: this.total > this.items.length,
      items: this.items,
    };
  }
}

/** Maps each palette index to an id shared by both inputs, so palette order never matters. */
function intern(
  g: SchematicGeometry,
  ids: Map<string, number>,
  names: string[],
  air: boolean[],
): Uint32Array {
  return Uint32Array.from(g.palette, (state) => {
    const key = formatBlockState(state);
    let id = ids.get(key);
    if (id === undefined) {
      id = names.length;
      ids.set(key, id);
      names.push(key);
      air.push(AIR_BLOCK_IDS.has(state.name));
    }
    return id;
  });
}

function axisOverlap(
  aOff: number,
  aSize: number,
  bOff: number,
  bSize: number,
): [number, number] | null {
  const lo = Math.max(aOff, bOff);
  const hi = Math.min(aOff + aSize, bOff + bSize);
  return hi > lo ? [lo, hi - lo] : null;
}

function checkMaxDetails(n: number): number {
  if (!Number.isInteger(n) || n < 0 || n > MAX_DETAILS_LIMIT)
    throw new AnalysisError(
      "E_DETAIL_LIMIT",
      `maxDetails must be an integer from 0 to ${MAX_DETAILS_LIMIT}`,
    );
  return n;
}

/**
 * Compares two geometries cell by cell in world space (offsets applied).
 * Work is proportional to the two volumes; the gap between distant volumes is
 * never visited. World coordinates are plain numbers, so offset plus position
 * may exceed the int32 range without wrapping.
 */
export function compareGeometries(
  aValue: unknown,
  bValue: unknown,
  options: CompareOptions = {},
): GeometryComparison {
  const maxDetails = checkMaxDetails(options.maxDetails ?? DEFAULT_MAX_DETAILS);
  const limits = options.limits ?? DEFAULT_SCHEMATIC_LIMITS;
  const a = validateGeometry(aValue, limits);
  const b = validateGeometry(bValue, limits);
  if (a.dataVersion !== b.dataVersion)
    throw new AnalysisError(
      "E_DATA_VERSION_MISMATCH",
      `DataVersion ${a.dataVersion} and ${b.dataVersion} differ; no version conversion is applied`,
    );

  const ids = new Map<string, number>();
  const names: string[] = [];
  const isAir: boolean[] = [];
  const idA = intern(a, ids, names, isAir);
  const idB = intern(b, ids, names, isAir);

  const changed = new Collector<ChangedCell>(maxDetails);
  const onlyA = new Collector<OneSidedCell>(maxDetails);
  const onlyB = new Collector<OneSidedCell>(maxDetails);
  let identical = 0;
  let onlyAAir = 0;
  let onlyBAir = 0;

  const { size: sa, offset: oa } = a;
  const { size: sb, offset: ob } = b;

  // Walk A: each cell is identical, changed, or outside B's volume.
  let i = 0;
  for (let y = 0; a.blocks.length > 0 && y < sa.y; y++) {
    const wy = oa.y + y;
    const by = wy - ob.y;
    const yIn = by >= 0 && by < sb.y;
    for (let z = 0; z < sa.z; z++) {
      const wz = oa.z + z;
      const bz = wz - ob.z;
      const yzIn = yIn && bz >= 0 && bz < sb.z;
      for (let x = 0; x < sa.x; x++, i++) {
        const wx = oa.x + x;
        const bx = wx - ob.x;
        const ia = idA[a.blocks[i]!]!;
        if (yzIn && bx >= 0 && bx < sb.x) {
          const ib = idB[b.blocks[bx + bz * sb.x + by * sb.x * sb.z]!]!;
          if (ia === ib) identical++;
          else
            changed.add(() => ({
              pos: { x: wx, y: wy, z: wz },
              a: names[ia]!,
              b: names[ib]!,
            }));
        } else {
          if (isAir[ia]) onlyAAir++;
          onlyA.add(() => ({
            pos: { x: wx, y: wy, z: wz },
            state: names[ia]!,
          }));
        }
      }
    }
  }

  // Walk B: only cells outside A's volume; the rest were counted above.
  let j = 0;
  for (let y = 0; b.blocks.length > 0 && y < sb.y; y++) {
    const wy = ob.y + y;
    const ay = wy - oa.y;
    const yIn = ay >= 0 && ay < sa.y;
    for (let z = 0; z < sb.z; z++) {
      const wz = ob.z + z;
      const az = wz - oa.z;
      const yzIn = yIn && az >= 0 && az < sa.z;
      for (let x = 0; x < sb.x; x++, j++) {
        const wx = ob.x + x;
        const ax = wx - oa.x;
        if (yzIn && ax >= 0 && ax < sa.x) continue;
        const ib = idB[b.blocks[j]!]!;
        if (isAir[ib]) onlyBAir++;
        onlyB.add(() => ({ pos: { x: wx, y: wy, z: wz }, state: names[ib]! }));
      }
    }
  }

  const ox = axisOverlap(oa.x, sa.x, ob.x, sb.x);
  const oy = axisOverlap(oa.y, sa.y, ob.y, sb.y);
  const oz = axisOverlap(oa.z, sa.z, ob.z, sb.z);
  const overlap =
    ox && oy && oz
      ? {
          min: { x: ox[0], y: oy[0], z: oz[0] },
          size: { x: ox[1], y: oy[1], z: oz[1] },
        }
      : null;

  return {
    report: "minecraft-ai-engineer.schematic-comparison",
    reportVersion: 1,
    notice: ANALYSIS_NOTICE,
    dataVersion: a.dataVersion,
    a: { size: sa, offset: oa, cells: a.blocks.length },
    b: { size: sb, offset: ob, cells: b.blocks.length },
    overlap,
    counts: {
      identical,
      changed: changed.total,
      onlyInA: {
        cells: onlyA.total,
        air: onlyAAir,
        nonAir: onlyA.total - onlyAAir,
      },
      onlyInB: {
        cells: onlyB.total,
        air: onlyBAir,
        nonAir: onlyB.total - onlyBAir,
      },
    },
    maxDetails,
    details: {
      changed: changed.done(),
      onlyInA: onlyA.done(),
      onlyInB: onlyB.done(),
    },
  };
}
