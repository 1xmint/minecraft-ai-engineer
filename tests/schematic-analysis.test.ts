import assert from "node:assert/strict";
import { test } from "node:test";
import {
  AnalysisError,
  analyzeGeometry,
  compareGeometries,
  type GeometryComparison,
} from "../companion/src/schematic-analysis/index.js";
import {
  GEOMETRY_FORMAT,
  SchematicError,
  parseBlockState,
} from "../companion/src/schematics/index.js";

type V = { x: number; y: number; z: number };

function geo(
  size: V,
  offset: V,
  palette: readonly string[],
  blocks: readonly number[],
  dataVersion = 3578,
) {
  return {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion,
    size,
    offset,
    palette: palette.map((s) => {
      const { name, properties } = parseBlockState(s);
      return { name, properties: { ...properties } };
    }),
    blocks: [...blocks],
  };
}

const AIR = "minecraft:air";
const STONE = "minecraft:stone";
const DIRT = "minecraft:dirt";
const GLASS = "minecraft:glass";
const REP2 = "minecraft:repeater[delay=2,facing=north]";
const REP1 = "minecraft:repeater[delay=1,facing=north]";

function rejects(fn: () => unknown, code: string) {
  assert.throws(fn, (e: unknown) => {
    assert.ok(e instanceof AnalysisError || e instanceof SchematicError);
    assert.equal(e.code, code);
    return true;
  });
}

test("analysis counts a hand-built 3x2x4 volume by id and full state", () => {
  // y=0: stone floor with two repeaters; y=1: air with one repeater and one cave_air.
  // Palette entry 4 (gold_block) is never used and must not be reported.
  const g = geo(
    { x: 3, y: 2, z: 4 },
    { x: -7, y: 60, z: 12 },
    [AIR, STONE, REP2, REP1, "minecraft:gold_block", "minecraft:cave_air"],
    [1, 1, 1, 1, 2, 1, 1, 3, 1, 1, 1, 1, 0, 0, 0, 0, 2, 0, 5, 0, 0, 0, 0, 0],
  );
  const r = analyzeGeometry(g);
  assert.equal(r.report, "minecraft-ai-engineer.schematic-analysis");
  assert.equal(r.dataVersion, 3578);
  assert.deepEqual(r.size, { x: 3, y: 2, z: 4 });
  assert.deepEqual(r.offset, { x: -7, y: 60, z: 12 });
  assert.equal(r.totalCells, 24);
  assert.equal(r.airCells, 11);
  assert.equal(r.nonAirCells, 13);
  assert.deepEqual(r.blockIds, [
    { key: "minecraft:air", count: 10 },
    { key: "minecraft:stone", count: 10 },
    { key: "minecraft:repeater", count: 3 },
    { key: "minecraft:cave_air", count: 1 },
  ]);
  assert.deepEqual(r.blockStates, [
    { key: AIR, count: 10 },
    { key: STONE, count: 10 },
    { key: REP2, count: 2 },
    { key: "minecraft:cave_air", count: 1 },
    { key: REP1, count: 1 },
  ]);
});

test("analysis canonicalises raw input and handles empty geometry", () => {
  const raw = geo({ x: 1, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [AIR], [0]);
  raw.palette[0] = {
    name: "minecraft:repeater",
    properties: { facing: "north", delay: "2" },
  };
  assert.deepEqual(analyzeGeometry(raw).blockStates, [{ key: REP2, count: 1 }]);

  const empty = analyzeGeometry(
    geo({ x: 3, y: 0, z: 2 }, { x: 1, y: 2, z: 3 }, [STONE], []),
  );
  assert.equal(empty.totalCells, 0);
  assert.equal(empty.airCells, 0);
  assert.equal(empty.nonAirCells, 0);
  assert.deepEqual(empty.blockIds, []);
  assert.deepEqual(empty.blockStates, []);
});

test("analysis rejects malformed geometry and honours configured limits", () => {
  const g = geo(
    { x: 2, y: 1, z: 1 },
    { x: 0, y: 0, z: 0 },
    [AIR, STONE],
    [0, 1],
  );
  rejects(() => analyzeGeometry({ ...g, owner: "me" }), "E_GEOMETRY");
  rejects(() => analyzeGeometry({ ...g, blocks: [0, 2] }), "E_GEOMETRY");
  rejects(() => analyzeGeometry({ ...g, blocks: [0] }), "E_GEOMETRY");
  rejects(
    () => analyzeGeometry(g, { maxVolume: 1, maxPaletteEntries: 10 }),
    "E_TOO_LARGE",
  );
  rejects(
    () => analyzeGeometry(g, { maxVolume: 10, maxPaletteEntries: 1 }),
    "E_TOO_LARGE",
  );
  rejects(
    () =>
      compareGeometries(g, g, {
        limits: { maxVolume: 1, maxPaletteEntries: 9 },
      }),
    "E_TOO_LARGE",
  );
});

test("comparison of offset volumes matches a hand-calculated result", () => {
  // A covers x -1..0, z -2..0 at y=5. B covers x 0..2, z -1..0 at y=5.
  // They overlap in the column x=0, z -1..0 (2 cells).
  const a = geo(
    { x: 2, y: 1, z: 3 },
    { x: -1, y: 5, z: -2 },
    [STONE, AIR, DIRT],
    [0, 0, 1, 2, 0, 1],
  );
  const b = geo(
    { x: 3, y: 1, z: 2 },
    { x: 0, y: 5, z: -1 },
    [DIRT, STONE, AIR, GLASS],
    [0, 1, 2, 3, 3, 1],
  );
  const r = compareGeometries(a, b);
  assert.equal(r.report, "minecraft-ai-engineer.schematic-comparison");
  assert.deepEqual(r.overlap, {
    min: { x: 0, y: 5, z: -1 },
    size: { x: 1, y: 1, z: 2 },
  });
  assert.deepEqual(r.counts, {
    identical: 1,
    changed: 1,
    onlyInA: { cells: 4, air: 1, nonAir: 3 },
    onlyInB: { cells: 4, air: 1, nonAir: 3 },
  });
  // A's explicit air at (0,5,0) is inside B, so it is a changed state.
  assert.deepEqual(r.details.changed.items, [
    { pos: { x: 0, y: 5, z: 0 }, a: AIR, b: GLASS },
  ]);
  // A's explicit air at (-1,5,-1) is outside B's volume, so it is only in A.
  assert.deepEqual(r.details.onlyInA.items, [
    { pos: { x: -1, y: 5, z: -2 }, state: STONE },
    { pos: { x: 0, y: 5, z: -2 }, state: STONE },
    { pos: { x: -1, y: 5, z: -1 }, state: AIR },
    { pos: { x: -1, y: 5, z: 0 }, state: STONE },
  ]);
  assert.deepEqual(r.details.onlyInB.items, [
    { pos: { x: 1, y: 5, z: -1 }, state: STONE },
    { pos: { x: 2, y: 5, z: -1 }, state: AIR },
    { pos: { x: 1, y: 5, z: 0 }, state: GLASS },
    { pos: { x: 2, y: 5, z: 0 }, state: STONE },
  ]);
  for (const list of Object.values(r.details))
    assert.equal(list.truncated, false);
});

test("comparison keeps y and z distinct", () => {
  // 1x2x2: index = z + 2*y. A has dirt at y=1,z=0; B has dirt at y=0,z=1.
  const a = geo(
    { x: 1, y: 2, z: 2 },
    { x: 0, y: 0, z: 0 },
    [STONE, DIRT],
    [0, 0, 1, 0],
  );
  const b = geo(
    { x: 1, y: 2, z: 2 },
    { x: 0, y: 0, z: 0 },
    [STONE, DIRT],
    [0, 1, 0, 0],
  );
  const r = compareGeometries(a, b);
  assert.equal(r.counts.identical, 2);
  assert.deepEqual(r.details.changed.items, [
    { pos: { x: 0, y: 0, z: 1 }, a: STONE, b: DIRT },
    { pos: { x: 0, y: 1, z: 0 }, a: DIRT, b: STONE },
  ]);
});

test("palette order, unused entries and property order do not affect comparison", () => {
  const a = geo(
    { x: 3, y: 1, z: 1 },
    { x: 4, y: 4, z: 4 },
    [AIR, REP2, STONE],
    [0, 1, 2],
  );
  const b = geo(
    { x: 3, y: 1, z: 1 },
    { x: 4, y: 4, z: 4 },
    [STONE, GLASS, REP2, AIR],
    [3, 2, 0],
  );
  b.palette[2] = {
    name: "minecraft:repeater",
    properties: { facing: "north", delay: "2" },
  };
  const r = compareGeometries(a, b);
  assert.equal(r.counts.identical, 3);
  assert.equal(r.counts.changed, 0);
  assert.equal(r.counts.onlyInA.cells, 0);
  assert.equal(r.counts.onlyInB.cells, 0);
});

test("explicit air differs from a cell outside the other volume", () => {
  const a = geo({ x: 2, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [AIR], [0, 0]);
  const bAir = geo({ x: 1, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [AIR], [0]);
  const r = compareGeometries(a, bAir);
  assert.equal(r.counts.identical, 1);
  assert.equal(r.counts.changed, 0);
  assert.deepEqual(r.counts.onlyInA, { cells: 1, air: 1, nonAir: 0 });
  assert.deepEqual(r.details.onlyInA.items, [
    { pos: { x: 1, y: 0, z: 0 }, state: AIR },
  ]);

  const bStone = geo({ x: 1, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [STONE], [0]);
  const s = compareGeometries(a, bStone);
  assert.equal(s.counts.identical, 0);
  assert.deepEqual(s.details.changed.items, [
    { pos: { x: 0, y: 0, z: 0 }, a: AIR, b: STONE },
  ]);

  const cave = geo(
    { x: 1, y: 1, z: 1 },
    { x: 0, y: 0, z: 0 },
    ["minecraft:cave_air"],
    [0],
  );
  assert.equal(compareGeometries(bAir, cave).counts.changed, 1);
});

test("widely separated volumes at the int32 extremes finish without visiting the gap", () => {
  const MIN = -2147483648;
  const MAX = 2147483647;
  const a = geo(
    { x: 2, y: 1, z: 1 },
    { x: MIN, y: MIN, z: MIN },
    [STONE],
    [0, 0],
  );
  // B's far corner is 2^31 on every axis; int32 wrapping would land it on A's first cell.
  const b = geo(
    { x: 2, y: 2, z: 2 },
    { x: MAX, y: MAX, z: MAX },
    [AIR, STONE],
    [1, 1, 1, 1, 1, 1, 1, 0],
  );
  const r = compareGeometries(a, b);
  assert.equal(r.overlap, null);
  assert.deepEqual(r.counts, {
    identical: 0,
    changed: 0,
    onlyInA: { cells: 2, air: 0, nonAir: 2 },
    onlyInB: { cells: 8, air: 1, nonAir: 7 },
  });
  assert.deepEqual(r.details.onlyInA.items[0], {
    pos: { x: MIN, y: MIN, z: MIN },
    state: STONE,
  });
  assert.deepEqual(r.details.onlyInB.items[7], {
    pos: { x: 2147483648, y: 2147483648, z: 2147483648 },
    state: AIR,
  });
});

test("empty geometries compare cleanly", () => {
  const empty = geo({ x: 0, y: 0, z: 0 }, { x: 0, y: 0, z: 0 }, [], []);
  const flat = geo({ x: 4, y: 0, z: 4 }, { x: 0, y: 0, z: 0 }, [STONE], []);
  const two = geo({ x: 2, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [STONE], [0, 0]);
  const ee = compareGeometries(empty, flat);
  assert.equal(ee.overlap, null);
  assert.deepEqual(ee.counts, {
    identical: 0,
    changed: 0,
    onlyInA: { cells: 0, air: 0, nonAir: 0 },
    onlyInB: { cells: 0, air: 0, nonAir: 0 },
  });
  const en = compareGeometries(empty, two);
  assert.equal(en.overlap, null);
  assert.deepEqual(en.counts.onlyInB, { cells: 2, air: 0, nonAir: 2 });
  assert.deepEqual(compareGeometries(two, empty).counts.onlyInA, {
    cells: 2,
    air: 0,
    nonAir: 2,
  });
});

test("mismatched DataVersions are rejected, not converted", () => {
  const a = geo({ x: 1, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [STONE], [0], 3578);
  const b = geo({ x: 1, y: 1, z: 1 }, { x: 0, y: 0, z: 0 }, [STONE], [0], 3700);
  rejects(() => compareGeometries(a, b), "E_DATA_VERSION_MISMATCH");
});

test("detail lists are bounded and say so while counts stay exact", () => {
  // A is 5x1x2 stone; B is 5x1x1 dirt over A's z=0 row.
  const a = geo(
    { x: 5, y: 1, z: 2 },
    { x: 0, y: 0, z: 0 },
    [STONE],
    Array(10).fill(0),
  );
  const b = geo(
    { x: 5, y: 1, z: 1 },
    { x: 0, y: 0, z: 0 },
    [DIRT],
    Array(5).fill(0),
  );
  const two = compareGeometries(a, b, { maxDetails: 2 });
  assert.equal(two.counts.changed, 5);
  assert.equal(two.counts.onlyInA.cells, 5);
  assert.deepEqual(two.details.changed, {
    listed: 2,
    truncated: true,
    items: [
      { pos: { x: 0, y: 0, z: 0 }, a: STONE, b: DIRT },
      { pos: { x: 1, y: 0, z: 0 }, a: STONE, b: DIRT },
    ],
  });
  assert.equal(two.details.onlyInA.listed, 2);
  assert.equal(two.details.onlyInA.truncated, true);
  assert.deepEqual(two.details.onlyInB, {
    listed: 0,
    truncated: false,
    items: [],
  });

  const zero = compareGeometries(a, b, { maxDetails: 0 });
  assert.equal(zero.counts.changed, 5);
  assert.deepEqual(zero.details.changed, {
    listed: 0,
    truncated: true,
    items: [],
  });

  const exact = compareGeometries(a, b, { maxDetails: 5 });
  assert.equal(exact.details.changed.listed, 5);
  assert.equal(exact.details.changed.truncated, false);

  for (const bad of [-1, 1.5, 10_001, Number.NaN])
    rejects(
      () => compareGeometries(a, b, { maxDetails: bad }),
      "E_DETAIL_LIMIT",
    );
});

test("reports carry no ownership, permission, cost or verification fields", () => {
  const g = geo(
    { x: 2, y: 1, z: 1 },
    { x: 0, y: 0, z: 0 },
    [AIR, STONE],
    [0, 1],
  );
  const banned = /owner|permission|verified|working|cost|inventory|reliab/i;
  const walk = (v: unknown): void => {
    if (Array.isArray(v)) v.forEach(walk);
    else if (v && typeof v === "object")
      for (const [k, child] of Object.entries(v)) {
        assert.doesNotMatch(k, banned);
        walk(child);
      }
  };
  walk(analyzeGeometry(g));
  walk(compareGeometries(g, g));
  assert.match(
    analyzeGeometry(g).notice,
    /grants no ownership or permissions/i,
  );
});

// Independent oracle: world-position maps compared naively, on seeded random inputs.
function mulberry32(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const POOL = [AIR, "minecraft:cave_air", STONE, DIRT, GLASS, REP1, REP2];
const AIR_STATES = new Set([AIR, "minecraft:cave_air", "minecraft:void_air"]);

function randomCase(rand: () => number) {
  const int = (lo: number, hi: number) =>
    lo + Math.floor(rand() * (hi - lo + 1));
  const size = { x: int(0, 4), y: int(0, 3), z: int(0, 4) };
  const offset = { x: int(-4, 3), y: int(-3, 3), z: int(-4, 3) };
  const palette = [...POOL]
    .sort(() => rand() - 0.5)
    .slice(0, int(1, POOL.length));
  const blocks = Array.from({ length: size.x * size.y * size.z }, () =>
    int(0, palette.length - 1),
  );
  const cells = new Map<string, string>();
  const order: [V, string][] = [];
  let i = 0;
  for (let y = 0; y < size.y; y++)
    for (let z = 0; z < size.z; z++)
      for (let x = 0; x < size.x; x++) {
        const pos = { x: offset.x + x, y: offset.y + y, z: offset.z + z };
        const state = palette[blocks[i++]!]!;
        cells.set(`${pos.x},${pos.y},${pos.z}`, state);
        order.push([pos, state]);
      }
  return { g: geo(size, offset, palette, blocks), cells, order };
}

function oracle(
  a: ReturnType<typeof randomCase>,
  b: ReturnType<typeof randomCase>,
): Pick<GeometryComparison, "counts"> & {
  changed: unknown[];
  onlyA: unknown[];
  onlyB: unknown[];
} {
  let identical = 0;
  const changed: unknown[] = [];
  const onlyA: unknown[] = [];
  const onlyB: unknown[] = [];
  for (const [pos, state] of a.order) {
    const other = b.cells.get(`${pos.x},${pos.y},${pos.z}`);
    if (other === undefined) onlyA.push({ pos, state });
    else if (other === state) identical++;
    else changed.push({ pos, a: state, b: other });
  }
  for (const [pos, state] of b.order)
    if (!a.cells.has(`${pos.x},${pos.y},${pos.z}`)) onlyB.push({ pos, state });
  const side = (list: unknown[]) => {
    const air = list.filter((c) =>
      AIR_STATES.has((c as { state: string }).state),
    ).length;
    return { cells: list.length, air, nonAir: list.length - air };
  };
  return {
    counts: {
      identical,
      changed: changed.length,
      onlyInA: side(onlyA),
      onlyInB: side(onlyB),
    },
    changed,
    onlyA,
    onlyB,
  };
}

test("comparison agrees with a naive oracle on 400 seeded random cases", () => {
  const rand = mulberry32(20261101);
  for (let n = 0; n < 400; n++) {
    const a = randomCase(rand);
    const b = randomCase(rand);
    const want = oracle(a, b);
    const got = compareGeometries(a.g, b.g, { maxDetails: 10_000 });
    assert.deepEqual(got.counts, want.counts, `case ${n}`);
    assert.deepEqual(got.details.changed.items, want.changed, `case ${n}`);
    assert.deepEqual(got.details.onlyInA.items, want.onlyA, `case ${n}`);
    assert.deepEqual(got.details.onlyInB.items, want.onlyB, `case ${n}`);
  }
});
