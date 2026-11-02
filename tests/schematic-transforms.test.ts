import assert from "node:assert/strict";
import { test } from "node:test";
import {
  GEOMETRY_FORMAT,
  SchematicError,
  formatBlockState,
  parseBlockState,
  validateGeometry,
  DEFAULT_SCHEMATIC_LIMITS,
  type SchematicGeometry,
  type Vec3,
} from "../companion/src/schematics/index.js";
import {
  GEOMETRY_TRANSFORMS,
  TransformError,
  mirrorGeometry,
  rotateGeometry,
  transformBlockState,
  transformGeometry,
  type GeometryTransform,
} from "../companion/src/schematic-transforms/index.js";

const INT32_MIN = -2147483648;
const INT32_MAX = 2147483647;

test("maximal empty boxes skip traversal for every transform", () => {
  for (const size of [
    { x: 0, y: 32767, z: 32767 },
    { x: 32767, y: 0, z: 32767 },
    { x: 32767, y: 32767, z: 0 },
  ]) {
    const input = geo(size, { x: 0, y: 0, z: 0 }, ["minecraft:air"], []);
    for (const transform of GEOMETRY_TRANSFORMS) {
      const result = transformGeometry(input, transform);
      assert.deepEqual(result.blocks, []);
      assert.equal(result.size.y, size.y);
    }
  }
});

function geo(
  size: Vec3,
  offset: Vec3,
  palette: string[],
  blocks: number[],
  dataVersion = 3578,
) {
  return {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion,
    size,
    offset,
    palette: palette.map((p) => {
      const s = parseBlockState(p);
      return { name: s.name, properties: { ...s.properties } };
    }),
    blocks,
  };
}

function state(text: string, transform: GeometryTransform): string {
  return formatBlockState(
    transformBlockState(parseBlockState(text), transform),
  );
}

function code(c: string) {
  return (e: unknown) =>
    (e instanceof TransformError || e instanceof SchematicError) &&
    e.code === c;
}

/** 3 wide, 2 tall, 2 long; block i names its own input cell, so every move is visible. */
const BOX = geo(
  { x: 3, y: 2, z: 2 },
  { x: 10, y: 64, z: -5 },
  Array.from({ length: 12 }, (_, i) => `test:c${i}`),
  Array.from({ length: 12 }, (_, i) => i),
);

test("quarter turns move cells, size and offset to hand-calculated places", () => {
  // Clockwise 90: world (x, z) -> (-z, x). Box x 10..12, z -5..-4 becomes
  // x 4..5, z 10..12, so c0 at world (10, 64, -5) lands at (5, 64, 10).
  const r90 = rotateGeometry(BOX, 90);
  assert.deepEqual(r90.size, { x: 2, y: 2, z: 3 });
  assert.deepEqual(r90.offset, { x: 4, y: 64, z: 10 });
  assert.deepEqual(r90.blocks, [3, 0, 4, 1, 5, 2, 9, 6, 10, 7, 11, 8]);

  // 180: (x, z) -> (-x, -z). Box becomes x -12..-10, z 4..5.
  const r180 = rotateGeometry(BOX, 180);
  assert.deepEqual(r180.size, { x: 3, y: 2, z: 2 });
  assert.deepEqual(r180.offset, { x: -12, y: 64, z: 4 });
  assert.deepEqual(r180.blocks, [5, 4, 3, 2, 1, 0, 11, 10, 9, 8, 7, 6]);

  // Clockwise 270: (x, z) -> (z, -x). Box becomes x -5..-4, z -12..-10.
  const r270 = rotateGeometry(BOX, 270);
  assert.deepEqual(r270.size, { x: 2, y: 2, z: 3 });
  assert.deepEqual(r270.offset, { x: -5, y: 64, z: -12 });
  assert.deepEqual(r270.blocks, [2, 5, 1, 4, 0, 3, 8, 11, 7, 10, 6, 9]);

  // The palette and the DataVersion are untouched; only cells move.
  for (const g of [r90, r180, r270]) {
    assert.deepEqual(
      g.palette,
      validateGeometry(BOX, DEFAULT_SCHEMATIC_LIMITS).palette,
    );
    assert.equal(g.dataVersion, 3578);
  }
});

test("mirrors are named by the directions they swap", () => {
  // east-west negates x: box x 10..12 becomes -12..-10, z unchanged.
  const ew = mirrorGeometry(BOX, "east-west");
  assert.deepEqual(ew.size, { x: 3, y: 2, z: 2 });
  assert.deepEqual(ew.offset, { x: -12, y: 64, z: -5 });
  assert.deepEqual(ew.blocks, [2, 1, 0, 5, 4, 3, 8, 7, 6, 11, 10, 9]);

  // north-south negates z: box z -5..-4 becomes 4..5, x unchanged.
  const ns = mirrorGeometry(BOX, "north-south");
  assert.deepEqual(ns.offset, { x: 10, y: 64, z: 4 });
  assert.deepEqual(ns.blocks, [3, 4, 5, 0, 1, 2, 9, 10, 11, 6, 7, 8]);
});

test("four quarter turns, two equal mirrors and known compositions restore", () => {
  const original = validateGeometry(BOX, DEFAULT_SCHEMATIC_LIMITS);
  let g: SchematicGeometry = original;
  for (let i = 0; i < 4; i++) {
    g = rotateGeometry(g, 90);
    if (i < 3) assert.notDeepEqual(g, original);
  }
  assert.deepEqual(g, original);
  for (const swap of ["east-west", "north-south"] as const) {
    const once = mirrorGeometry(original, swap);
    assert.notDeepEqual(once, original);
    assert.deepEqual(mirrorGeometry(once, swap), original);
  }
  assert.deepEqual(
    mirrorGeometry(mirrorGeometry(original, "east-west"), "north-south"),
    rotateGeometry(original, 180),
  );
  assert.deepEqual(rotateGeometry(rotateGeometry(original, 90), 270), original);
  assert.deepEqual(
    rotateGeometry(rotateGeometry(original, 90), 90),
    rotateGeometry(original, 180),
  );
});

test("directional block states follow hand-worked rules", () => {
  const cases: [string, GeometryTransform, string][] = [
    // facing: clockwise north -> east -> south -> west; up/down stay.
    [
      "minecraft:repeater[delay=2,facing=north]",
      "rotate-90",
      "minecraft:repeater[delay=2,facing=east]",
    ],
    [
      "minecraft:repeater[delay=2,facing=north]",
      "rotate-180",
      "minecraft:repeater[delay=2,facing=south]",
    ],
    [
      "minecraft:repeater[delay=2,facing=north]",
      "rotate-270",
      "minecraft:repeater[delay=2,facing=west]",
    ],
    [
      "minecraft:repeater[delay=2,facing=north]",
      "mirror-east-west",
      "minecraft:repeater[delay=2,facing=north]",
    ],
    [
      "minecraft:repeater[delay=2,facing=north]",
      "mirror-north-south",
      "minecraft:repeater[delay=2,facing=south]",
    ],
    [
      "minecraft:observer[facing=east,powered=false]",
      "mirror-east-west",
      "minecraft:observer[facing=west,powered=false]",
    ],
    [
      "minecraft:piston[extended=false,facing=up]",
      "rotate-90",
      "minecraft:piston[extended=false,facing=up]",
    ],
    [
      "minecraft:hopper[enabled=true,facing=down]",
      "mirror-north-south",
      "minecraft:hopper[enabled=true,facing=down]",
    ],
    // axis: x and z swap on quarter turns only.
    ["minecraft:oak_log[axis=x]", "rotate-90", "minecraft:oak_log[axis=z]"],
    ["minecraft:oak_log[axis=x]", "rotate-180", "minecraft:oak_log[axis=x]"],
    ["minecraft:oak_log[axis=z]", "rotate-270", "minecraft:oak_log[axis=x]"],
    [
      "minecraft:oak_log[axis=x]",
      "mirror-east-west",
      "minecraft:oak_log[axis=x]",
    ],
    ["minecraft:oak_log[axis=y]", "rotate-90", "minecraft:oak_log[axis=y]"],
    // rotation: 16 steps clockwise from south (0); west 4, north 8, east 12.
    [
      "minecraft:oak_sign[rotation=1,waterlogged=false]",
      "rotate-90",
      "minecraft:oak_sign[rotation=5,waterlogged=false]",
    ],
    [
      "minecraft:oak_sign[rotation=1,waterlogged=false]",
      "rotate-180",
      "minecraft:oak_sign[rotation=9,waterlogged=false]",
    ],
    [
      "minecraft:oak_sign[rotation=1,waterlogged=false]",
      "rotate-270",
      "minecraft:oak_sign[rotation=13,waterlogged=false]",
    ],
    [
      "minecraft:oak_sign[rotation=1,waterlogged=false]",
      "mirror-east-west",
      "minecraft:oak_sign[rotation=15,waterlogged=false]",
    ],
    [
      "minecraft:oak_sign[rotation=1,waterlogged=false]",
      "mirror-north-south",
      "minecraft:oak_sign[rotation=7,waterlogged=false]",
    ],
    [
      "minecraft:skeleton_skull[powered=false,rotation=0]",
      "mirror-east-west",
      "minecraft:skeleton_skull[powered=false,rotation=0]",
    ],
    [
      "minecraft:skeleton_skull[powered=false,rotation=0]",
      "mirror-north-south",
      "minecraft:skeleton_skull[powered=false,rotation=8]",
    ],
    // Connection keys move with the world: new east = old north on a clockwise turn.
    [
      "minecraft:redstone_wire[east=side,north=up,power=3,south=none,west=none]",
      "rotate-90",
      "minecraft:redstone_wire[east=up,north=none,power=3,south=side,west=none]",
    ],
    [
      "minecraft:redstone_wire[east=side,north=up,power=3,south=none,west=none]",
      "mirror-east-west",
      "minecraft:redstone_wire[east=none,north=up,power=3,south=none,west=side]",
    ],
    [
      "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
      "rotate-270",
      "minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]",
    ],
    [
      "minecraft:cobblestone_wall[east=low,north=none,south=tall,up=true,waterlogged=false,west=none]",
      "mirror-north-south",
      "minecraft:cobblestone_wall[east=low,north=tall,south=none,up=true,waterlogged=false,west=none]",
    ],
    // Handedness: kept by rotations, swapped by every mirror.
    [
      "minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]",
      "rotate-90",
      "minecraft:oak_stairs[facing=south,half=bottom,shape=inner_left,waterlogged=false]",
    ],
    [
      "minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]",
      "mirror-east-west",
      "minecraft:oak_stairs[facing=west,half=bottom,shape=inner_right,waterlogged=false]",
    ],
    [
      "minecraft:oak_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]",
      "mirror-north-south",
      "minecraft:oak_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]",
    ],
    [
      "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
      "mirror-north-south",
      "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
    ],
    [
      "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
      "rotate-90",
      "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]",
    ],
    [
      "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
      "mirror-east-west",
      "minecraft:oak_door[facing=north,half=lower,hinge=right,open=false,powered=false]",
    ],
    [
      "minecraft:chest[facing=north,type=left,waterlogged=false]",
      "mirror-north-south",
      "minecraft:chest[facing=south,type=right,waterlogged=false]",
    ],
    [
      "minecraft:chest[facing=north,type=left,waterlogged=false]",
      "rotate-180",
      "minecraft:chest[facing=south,type=left,waterlogged=false]",
    ],
    [
      "minecraft:stone_slab[type=top,waterlogged=false]",
      "mirror-east-west",
      "minecraft:stone_slab[type=top,waterlogged=false]",
    ],
    // Rails use vanilla's spelling (north/south before east/west).
    [
      "minecraft:rail[shape=north_east,waterlogged=false]",
      "rotate-90",
      "minecraft:rail[shape=south_east,waterlogged=false]",
    ],
    [
      "minecraft:rail[shape=south_east,waterlogged=false]",
      "rotate-90",
      "minecraft:rail[shape=south_west,waterlogged=false]",
    ],
    [
      "minecraft:rail[shape=south_west,waterlogged=false]",
      "rotate-90",
      "minecraft:rail[shape=north_west,waterlogged=false]",
    ],
    [
      "minecraft:rail[shape=north_west,waterlogged=false]",
      "rotate-90",
      "minecraft:rail[shape=north_east,waterlogged=false]",
    ],
    [
      "minecraft:rail[shape=north_east,waterlogged=false]",
      "mirror-east-west",
      "minecraft:rail[shape=north_west,waterlogged=false]",
    ],
    [
      "minecraft:rail[shape=north_south,waterlogged=false]",
      "rotate-270",
      "minecraft:rail[shape=east_west,waterlogged=false]",
    ],
    [
      "minecraft:powered_rail[powered=true,shape=ascending_north,waterlogged=false]",
      "rotate-90",
      "minecraft:powered_rail[powered=true,shape=ascending_east,waterlogged=false]",
    ],
    [
      "minecraft:powered_rail[powered=true,shape=ascending_east,waterlogged=false]",
      "mirror-east-west",
      "minecraft:powered_rail[powered=true,shape=ascending_west,waterlogged=false]",
    ],
    // Jigsaw / crafter orientation: "<front>_<top>", both turned.
    [
      "minecraft:jigsaw[orientation=north_up]",
      "rotate-90",
      "minecraft:jigsaw[orientation=east_up]",
    ],
    [
      "minecraft:jigsaw[orientation=down_east]",
      "rotate-90",
      "minecraft:jigsaw[orientation=down_south]",
    ],
    [
      "minecraft:crafter[crafting=false,orientation=up_north,triggered=false]",
      "mirror-north-south",
      "minecraft:crafter[crafting=false,orientation=up_south,triggered=false]",
    ],
    // Comparator mode and lever face mean the same thing in any orientation.
    [
      "minecraft:comparator[facing=west,mode=subtract,powered=false]",
      "rotate-90",
      "minecraft:comparator[facing=north,mode=subtract,powered=false]",
    ],
    [
      "minecraft:lever[face=floor,facing=south,powered=true]",
      "rotate-270",
      "minecraft:lever[face=floor,facing=east,powered=true]",
    ],
    ["minecraft:stone", "rotate-90", "minecraft:stone"],
  ];
  for (const [input, transform, expected] of cases)
    assert.equal(state(input, transform), expected, `${input} ${transform}`);
});

/** Every value the supported rules accept, in a block that carries it. */
function supportedStates(): string[] {
  const out: string[] = [];
  for (const f of ["north", "east", "south", "west", "up", "down"])
    out.push(`minecraft:observer[facing=${f},powered=false]`);
  for (const a of ["x", "y", "z"]) out.push(`minecraft:oak_log[axis=${a}]`);
  for (let r = 0; r < 16; r++) out.push(`minecraft:oak_sign[rotation=${r}]`);
  for (const s of [
    "straight",
    "inner_left",
    "inner_right",
    "outer_left",
    "outer_right",
  ])
    for (const f of ["north", "east", "south", "west"])
      out.push(`minecraft:oak_stairs[facing=${f},half=bottom,shape=${s}]`);
  for (const s of [
    "north_south",
    "east_west",
    "north_east",
    "north_west",
    "south_east",
    "south_west",
    "ascending_north",
    "ascending_east",
    "ascending_south",
    "ascending_west",
  ])
    out.push(`minecraft:rail[shape=${s}]`);
  for (const h of ["left", "right"])
    for (const f of ["north", "east", "south", "west"])
      out.push(`minecraft:oak_door[facing=${f},half=upper,hinge=${h}]`);
  for (const t of ["single", "left", "right"])
    out.push(`minecraft:chest[facing=west,type=${t}]`);
  for (const o of [
    "down_east",
    "down_north",
    "down_south",
    "down_west",
    "up_east",
    "up_north",
    "up_south",
    "up_west",
    "west_up",
    "east_up",
    "north_up",
    "south_up",
  ])
    out.push(`minecraft:jigsaw[orientation=${o}]`);
  out.push(
    "minecraft:redstone_wire[east=side,north=up,power=0,south=none,west=side]",
    "minecraft:glow_lichen[down=true,east=false,north=true,south=false,up=false,waterlogged=false,west=true]",
  );
  return out;
}

test("every supported value survives closure and composition laws", () => {
  for (const text of supportedStates()) {
    const s = parseBlockState(text);
    let r = s;
    for (let i = 0; i < 4; i++) r = transformBlockState(r, "rotate-90");
    assert.deepEqual(r, s, `4 x 90 ${text}`);
    for (const m of ["mirror-east-west", "mirror-north-south"] as const)
      assert.deepEqual(
        transformBlockState(transformBlockState(s, m), m),
        s,
        `${m} twice ${text}`,
      );
    assert.deepEqual(
      transformBlockState(
        transformBlockState(s, "mirror-east-west"),
        "mirror-north-south",
      ),
      transformBlockState(s, "rotate-180"),
      `mirrors = 180 ${text}`,
    );
    assert.deepEqual(
      transformBlockState(transformBlockState(s, "rotate-90"), "rotate-270"),
      s,
      `90 then 270 ${text}`,
    );
  }
});

test("16-step rotation agrees with facing under every transform", () => {
  const byStep: Record<number, string> = {
    0: "south",
    4: "west",
    8: "north",
    12: "east",
  };
  for (const t of GEOMETRY_TRANSFORMS)
    for (const step of [0, 4, 8, 12]) {
      const turned = transformBlockState(
        parseBlockState(`minecraft:oak_sign[rotation=${step}]`),
        t,
      );
      const faced = transformBlockState(
        parseBlockState(`minecraft:observer[facing=${byStep[step]}]`),
        t,
      );
      assert.equal(
        byStep[Number(turned.properties.rotation)],
        faced.properties.facing,
        `${t} ${step}`,
      );
    }
});

test("unsupported or unexpected directional states reject clearly", () => {
  const rejected = [
    "minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]",
    "minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=false]",
    "examplemod:conveyor[direction=north]",
    "minecraft:observer[facing=sideways]",
    "minecraft:oak_sign[rotation=16]",
    "minecraft:oak_log[axis=w]",
    "minecraft:rail[shape=zigzag]",
    "minecraft:rail[shape=west_south]",
    "minecraft:rail[shape=ascending_up]",
    "minecraft:oak_door[hinge=middle]",
    "minecraft:chest[type=sideways]",
    "minecraft:jigsaw[orientation=north_north]",
  ];
  for (const text of rejected)
    for (const t of GEOMETRY_TRANSFORMS)
      assert.throws(
        () => transformBlockState(parseBlockState(text), t),
        (e: unknown) =>
          code("E_UNSUPPORTED_STATE")(e) &&
          (e as Error).message.includes("supported transform subset"),
        `${text} ${t}`,
      );

  // A palette entry no block uses still rejects: nothing half-transformed comes back.
  const unused = geo(
    { x: 1, y: 1, z: 1 },
    { x: 0, y: 0, z: 0 },
    ["minecraft:stone", "examplemod:conveyor[direction=north]"],
    [0],
  );
  assert.throws(() => rotateGeometry(unused, 90), code("E_UNSUPPORTED_STATE"));
});

test("inputs are never changed, and metadata and DataVersion are kept", () => {
  const raw = {
    ...geo(
      { x: 2, y: 1, z: 1 },
      { x: 1, y: 2, z: 3 },
      ["minecraft:air"],
      [0, 1],
      3700,
    ),
    untrustedMetadata: {
      name: "Gate",
      author: "Someone",
      dateMillis: "1700000000000",
      requiredMods: ["fabric"],
    },
  };
  // Properties written out of order: the validator canonicalises a copy, not the input.
  raw.palette.push({
    name: "minecraft:repeater",
    properties: { facing: "north", delay: "2" } as Record<string, string>,
  });
  const deepFreeze = (o: unknown): void => {
    if (o && typeof o === "object") {
      Object.freeze(o);
      for (const v of Object.values(o)) deepFreeze(v);
    }
  };
  const snapshot = structuredClone(raw);
  deepFreeze(raw);
  for (const t of GEOMETRY_TRANSFORMS) {
    const out = transformGeometry(raw, t);
    assert.equal(out.dataVersion, 3700);
    assert.deepEqual(out.untrustedMetadata, raw.untrustedMetadata);
    assert.ok(Object.isFrozen(out) && Object.isFrozen(out.blocks));
  }
  assert.deepEqual(raw, snapshot);
  assert.deepEqual(Object.keys(raw.palette[1]!.properties), [
    "facing",
    "delay",
  ]);
  assert.equal(
    formatBlockState(rotateGeometry(raw, 90).palette[1]!),
    "minecraft:repeater[delay=2,facing=east]",
  );
});

test("output carries no ownership, permission or verification", () => {
  const banned =
    /owner|permission|verif|tested|pass|project|trust(?!edMetadata)/i;
  const scan = (v: unknown, path: string) => {
    if (v && typeof v === "object")
      for (const [k, child] of Object.entries(v)) {
        assert.doesNotMatch(k, banned, `${path}.${k}`);
        scan(child, `${path}.${k}`);
      }
  };
  const out = rotateGeometry({ ...BOX, untrustedMetadata: { name: "x" } }, 270);
  assert.deepEqual(Object.keys(out).sort(), [
    "blocks",
    "dataVersion",
    "format",
    "formatVersion",
    "offset",
    "palette",
    "size",
    "untrustedMetadata",
  ]);
  scan(out, "");
  for (const extra of [
    { verified: true },
    { owner: "steve" },
    { permissions: ["build"] },
  ])
    assert.throws(
      () => rotateGeometry({ ...BOX, ...extra }, 90),
      code("E_GEOMETRY"),
    );
});

test("malformed geometry and bad arguments reject", () => {
  assert.throws(
    () => rotateGeometry({ ...BOX, blocks: [0] }, 90),
    code("E_GEOMETRY"),
  );
  assert.throws(
    () => rotateGeometry({ ...BOX, format: "other" }, 90),
    code("E_GEOMETRY"),
  );
  assert.throws(() => rotateGeometry(null, 90), code("E_GEOMETRY"));
  assert.throws(
    () => rotateGeometry(BOX, 90, { maxVolume: 11, maxPaletteEntries: 100 }),
    code("E_TOO_LARGE"),
  );
  for (const degrees of [0, 360, -90, "90", Number.NaN, 45, 90.5])
    assert.throws(
      () => rotateGeometry(BOX, degrees as 90),
      code("E_TRANSFORM_ARGUMENT"),
      String(degrees),
    );
  for (const swap of ["y", "x", "z", "up-down", ""])
    assert.throws(
      () => mirrorGeometry(BOX, swap as "east-west"),
      code("E_TRANSFORM_ARGUMENT"),
      swap,
    );
  for (const t of ["rotate-45", "rotate-0", "toString", "", 90])
    assert.throws(
      () => transformGeometry(BOX, t as GeometryTransform),
      code("E_TRANSFORM_ARGUMENT"),
      String(t),
    );
  assert.throws(
    () =>
      transformBlockState(
        parseBlockState("minecraft:stone"),
        "hasOwnProperty" as GeometryTransform,
      ),
    code("E_TRANSFORM_ARGUMENT"),
  );
});

test("offsets that would leave the 32-bit range reject; the edges just inside pass", () => {
  const at = (offset: Vec3, size: Vec3) =>
    geo(
      size,
      offset,
      ["minecraft:stone"],
      new Array(size.x * size.y * size.z).fill(0),
    );
  const one = { x: 1, y: 1, z: 1 };
  // Negating INT32_MIN gives INT32_MAX + 1.
  assert.throws(
    () => mirrorGeometry(at({ x: INT32_MIN, y: 0, z: 0 }, one), "east-west"),
    code("E_COORDINATE_RANGE"),
  );
  assert.throws(
    () => rotateGeometry(at({ x: 0, y: 0, z: INT32_MIN }, one), 90),
    code("E_COORDINATE_RANGE"),
  );
  // The far corner INT32_MAX + 2 flips below INT32_MIN.
  const far = { x: 3, y: 1, z: 1 };
  assert.throws(
    () => mirrorGeometry(at({ x: INT32_MAX, y: 0, z: 0 }, far), "east-west"),
    code("E_COORDINATE_RANGE"),
  );
  assert.throws(
    () => rotateGeometry(at({ x: INT32_MAX, y: 0, z: 0 }, far), 270),
    code("E_COORDINATE_RANGE"),
  );
  assert.throws(
    () =>
      rotateGeometry(
        at({ x: 0, y: 0, z: INT32_MAX }, { x: 1, y: 1, z: 3 }),
        90,
      ),
    code("E_COORDINATE_RANGE"),
  );

  assert.deepEqual(
    mirrorGeometry(at({ x: INT32_MIN + 1, y: 0, z: 0 }, one), "east-west")
      .offset,
    { x: INT32_MAX, y: 0, z: 0 },
  );
  assert.deepEqual(
    mirrorGeometry(
      at({ x: INT32_MAX - 1, y: INT32_MIN, z: 0 }, { x: 2, y: 1, z: 1 }),
      "east-west",
    ).offset,
    { x: -INT32_MAX, y: INT32_MIN, z: 0 },
  );
  // Far corner exactly INT32_MAX + 1 flips to INT32_MIN, which still fits.
  assert.deepEqual(
    mirrorGeometry(
      at({ x: INT32_MAX, y: 0, z: 0 }, { x: 2, y: 1, z: 1 }),
      "east-west",
    ).offset,
    { x: INT32_MIN, y: 0, z: 0 },
  );
});

test("empty geometry moves its offset by the same reversible rule", () => {
  const empty = geo(
    { x: 0, y: 1, z: 2 },
    { x: 10, y: 64, z: -5 },
    ["minecraft:stone"],
    [],
  );
  // An empty axis counts as one cell: box x 10..10, z -5..-4 turns to x 4..5, z 10.
  const r90 = rotateGeometry(empty, 90);
  assert.deepEqual(r90.size, { x: 2, y: 1, z: 0 });
  assert.deepEqual(r90.offset, { x: 4, y: 64, z: 10 });
  assert.deepEqual(r90.blocks, []);
  const original = validateGeometry(empty, DEFAULT_SCHEMATIC_LIMITS);
  let g: SchematicGeometry = original;
  for (let i = 0; i < 4; i++) g = rotateGeometry(g, 90);
  assert.deepEqual(g, original);
  for (const swap of ["east-west", "north-south"] as const)
    assert.deepEqual(
      mirrorGeometry(mirrorGeometry(original, swap), swap),
      original,
    );
});

function mulberry32(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Naive oracle: each transform written out by hand as a world-position map. */
const WORLD: Record<
  GeometryTransform,
  (x: number, z: number) => [number, number]
> = {
  "rotate-90": (x, z) => [-z, x],
  "rotate-180": (x, z) => [-x, -z],
  "rotate-270": (x, z) => [z, -x],
  "mirror-east-west": (x, z) => [-x, z],
  "mirror-north-south": (x, z) => [x, -z],
};

function cells(g: SchematicGeometry): Map<string, string> {
  const out = new Map<string, string>();
  let i = 0;
  for (let y = 0; y < g.size.y; y++)
    for (let z = 0; z < g.size.z; z++)
      for (let x = 0; x < g.size.x; x++)
        out.set(
          `${g.offset.x + x},${g.offset.y + y},${g.offset.z + z}`,
          formatBlockState(g.palette[g.blocks[i++]!]!),
        );
  return out;
}

test("300 seeded random transforms match a naive position-map oracle", () => {
  const rand = mulberry32(20261101);
  const int = (n: number) => Math.floor(rand() * n);
  const pool = supportedStates();
  for (let n = 0; n < 300; n++) {
    const size = { x: int(5), y: int(3), z: int(5) };
    const offset = {
      x: int(2001) - 1000,
      y: int(200) - 64,
      z: int(2001) - 1000,
    };
    const names = [
      ...new Set(
        Array.from({ length: 1 + int(6) }, () => pool[int(pool.length)]!),
      ),
    ];
    const blocks = Array.from({ length: size.x * size.y * size.z }, () =>
      int(names.length),
    );
    const input = validateGeometry(
      geo(size, offset, names, blocks),
      DEFAULT_SCHEMATIC_LIMITS,
    );
    const t = GEOMETRY_TRANSFORMS[int(GEOMETRY_TRANSFORMS.length)]!;
    const out = transformGeometry(input, t);

    const expected = new Map<string, string>();
    for (const [pos, s] of cells(input)) {
      const [x, y, z] = pos.split(",").map(Number) as [number, number, number];
      const [nx, nz] = WORLD[t](x, z);
      expected.set(`${nx + 0},${y},${nz + 0}`, state(s, t));
    }
    assert.deepEqual(cells(out), expected, `case ${n} ${t}`);
    const volume = size.x * size.y * size.z;
    assert.equal(out.size.x * out.size.y * out.size.z, volume);
    assert.equal(out.size.y, size.y);
    assert.equal(out.offset.y, offset.y);
  }
});
