import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";
import { gunzipSync, gzipSync } from "node:zlib";
import {
  DEFAULT_SCHEMATIC_LIMITS,
  GEOMETRY_FORMAT,
  exportSpongeSchematic,
  importSpongeSchematic,
  parseGeometryJson,
  stringifyGeometry,
  validateGeometry,
} from "../companion/src/schematics/index.js";

// Hostile inputs are written with prismarine-nbt or by hand, never with our own writer.
const nbt = createRequire(import.meta.url)(
  "prismarine-nbt",
) as typeof import("prismarine-nbt");

type Fields = Record<string, unknown>;

const defined = (fields: Fields): Fields =>
  Object.fromEntries(Object.entries(fields).filter(([, v]) => v !== undefined));

function schematic(
  overrides: Fields = {},
  blocks: Fields = {},
  rootExtra: Fields = {},
): Buffer {
  const fields = defined({
    Version: nbt.int(3),
    DataVersion: nbt.int(3953),
    Width: nbt.short(2),
    Height: nbt.short(1),
    Length: nbt.short(1),
    Blocks: nbt.comp(
      defined({
        Palette: nbt.comp({
          "minecraft:air": nbt.int(0),
          "minecraft:stone": nbt.int(1),
        }),
        Data: nbt.byteArray([0, 1]),
        ...blocks,
      }),
    ),
    ...overrides,
  });
  const root = {
    type: "compound",
    name: "",
    value: { Schematic: nbt.comp(fields as object), ...rootExtra },
  };
  return gzipSync(
    nbt.writeUncompressed(root as Parameters<typeof nbt.writeUncompressed>[0]),
  );
}

const rejects = (bytes: Uint8Array, code: string, options = {}): void => {
  assert.throws(() => importSpongeSchematic(bytes, options), { code });
};

test("the baseline used below imports cleanly", () => {
  const { geometry } = importSpongeSchematic(schematic());
  assert.deepEqual(geometry.blocks, [0, 1]);
});

test("container and compression problems are reported, and decompression is bounded", () => {
  rejects(Buffer.from("this is plainly not a gzip file"), "E_NOT_GZIP");
  rejects(schematic(), "E_INPUT_TOO_LARGE", {
    limits: { maxCompressedBytes: 10 },
  });
  const good = schematic();
  rejects(good.subarray(0, good.length - 12), "E_DECOMPRESS");
  rejects(gzipSync(Buffer.alloc(1_000_000)), "E_DECOMPRESSED_TOO_LARGE", {
    limits: { maxDecompressedBytes: 4096 },
  });
  // A small file that inflates past the default limit (a "gzip bomb").
  const bomb = gzipSync(
    Buffer.alloc(DEFAULT_SCHEMATIC_LIMITS.maxDecompressedBytes + 1),
  );
  assert.ok(bomb.length < 1_000_000);
  rejects(bomb, "E_DECOMPRESSED_TOO_LARGE");
});

test("malformed NBT is rejected before any large allocation", () => {
  const raw = gunzipSync(schematic());
  rejects(gzipSync(raw.subarray(0, raw.length - 5)), "E_NBT_MALFORMED");
  rejects(
    gzipSync(Buffer.concat([raw, Buffer.from([0])])),
    "E_NBT_TRAILING_DATA",
  );
  rejects(gzipSync(Buffer.from([8, 0, 0, 0, 0])), "E_ROOT");
  rejects(
    gzipSync(Buffer.from([10, 0, 0, 99, 0, 1, 65, 0])),
    "E_NBT_MALFORMED",
  );
  // A list claiming 2^31-1 ints, and a byte array claiming 2^31-1 bytes, in a 12-byte file.
  rejects(
    gzipSync(Buffer.from([10, 0, 0, 9, 0, 1, 76, 3, 0x7f, 0xff, 0xff, 0xff])),
    "E_NBT_MALFORMED",
  );
  rejects(
    gzipSync(Buffer.from([10, 0, 0, 7, 0, 1, 66, 0x7f, 0xff, 0xff, 0xff])),
    "E_NBT_MALFORMED",
  );
  rejects(
    gzipSync(Buffer.from([10, 0, 0, 7, 0, 1, 66, 0xff, 0xff, 0xff, 0xff])),
    "E_NBT_MALFORMED",
  );
  // Compounds nested 40 deep.
  const deep = [10, 0, 0];
  for (let i = 0; i < 40; i++) deep.push(10, 0, 1, 97);
  for (let i = 0; i < 41; i++) deep.push(0);
  rejects(gzipSync(Buffer.from(deep)), "E_NBT_TOO_DEEP");
  rejects(schematic(), "E_NBT_TOO_MANY_TAGS", { limits: { maxNbtTags: 5 } });
  // Duplicate keys in one compound.
  rejects(
    gzipSync(Buffer.from([10, 0, 0, 1, 0, 1, 65, 1, 1, 0, 1, 65, 2, 0])),
    "E_NBT_MALFORMED",
  );
});

test("schematic structure problems name the field", () => {
  rejects(schematic({ Version: nbt.int(2) }), "E_UNSUPPORTED_VERSION");
  rejects(schematic({ DataVersion: undefined }), "E_FIELD");
  rejects(schematic({ Width: nbt.int(2) }), "E_FIELD");
  rejects(schematic({ Offset: nbt.intArray([1, 2]) }), "E_FIELD");
  rejects(schematic({ Blocks: undefined }), "E_FIELD");
  rejects(
    schematic({ Signature: nbt.string("trust me") }),
    "E_UNSUPPORTED_FIELD",
  );
  rejects(
    schematic({}, { Permissions: nbt.string("op") }),
    "E_UNSUPPORTED_FIELD",
  );
  rejects(schematic({}, {}, { Owner: nbt.string("someone") }), "E_ROOT");
  rejects(
    schematic({
      Width: nbt.short(-1),
      Height: nbt.short(-1),
      Length: nbt.short(1),
    }),
    "E_TOO_LARGE",
  );
  assert.throws(
    () => importSpongeSchematic(schematic({ Version: nbt.int(2) })),
    /version 2 is not supported/,
  );
});

test("palette and block data must agree exactly", () => {
  rejects(
    schematic(
      {},
      {
        Palette: nbt.comp({
          "minecraft:air": nbt.int(0),
          "minecraft:stone": nbt.int(0),
        }),
      },
    ),
    "E_PALETTE",
  );
  rejects(
    schematic(
      {},
      {
        Palette: nbt.comp({
          "minecraft:air": nbt.int(0),
          "minecraft:stone": nbt.int(-1),
        }),
      },
    ),
    "E_PALETTE",
  );
  rejects(
    schematic(
      {},
      {
        Palette: nbt.comp({ stone: nbt.int(0), "minecraft:stone": nbt.int(1) }),
      },
    ),
    "E_PALETTE",
  );
  rejects(
    schematic(
      {},
      {
        Palette: nbt.comp({
          "lever[face=wall,powered=true]": nbt.int(0),
          "lever[powered=true,face=wall]": nbt.int(1),
        }),
      },
    ),
    "E_PALETTE",
  );
  rejects(
    schematic(
      {},
      { Palette: nbt.comp({ "Minecraft:Stone": nbt.int(0), air: nbt.int(1) }) },
    ),
    "E_BLOCK_STATE",
  );
  rejects(
    schematic(
      {},
      { Palette: nbt.comp({ "stone[": nbt.int(0), air: nbt.int(1) }) },
    ),
    "E_BLOCK_STATE",
  );
  rejects(
    schematic(
      {},
      { Palette: nbt.comp({ "stone[a=1,a=2]": nbt.int(0), air: nbt.int(1) }) },
    ),
    "E_BLOCK_STATE",
  );
  rejects(schematic({}, { Data: nbt.byteArray([0, 2]) }), "E_BLOCK_DATA");
  rejects(schematic({}, { Data: nbt.byteArray([0]) }), "E_BLOCK_DATA");
  rejects(schematic({}, { Data: nbt.byteArray([0, 1, 1]) }), "E_BLOCK_DATA");
  rejects(
    schematic(
      {},
      { Data: nbt.byteArray([0, -128, -128, -128, -128, -128, 0]) },
    ),
    "E_BLOCK_DATA",
  );
  rejects(schematic({}, { Data: nbt.intArray([0, 1]) }), "E_FIELD");
  rejects(schematic({}, { Palette: undefined }), "E_FIELD");
});

test("entities, biomes and block entities are never silently dropped", () => {
  const creeper = nbt.list(
    nbt.comp([
      {
        Id: nbt.string("minecraft:creeper"),
        Pos: nbt.list(nbt.double([0.5, 0, 0.5])),
      },
    ]),
  );
  rejects(schematic({ Entities: creeper }), "E_ENTITIES");
  rejects(schematic({ Entities: creeper }), "E_ENTITIES", {
    discardBlockEntities: true,
    discardBiomes: true,
  });
  assert.equal(
    importSpongeSchematic(
      schematic({ Entities: nbt.list({ type: "end", value: [] } as never) }),
    ).geometry.blocks.length,
    2,
  );

  const biomes = nbt.comp({
    Palette: nbt.comp({ "minecraft:plains": nbt.int(0) }),
    Data: nbt.byteArray([0, 0]),
  });
  rejects(schematic({ Biomes: biomes }), "E_BIOMES");
  const kept = importSpongeSchematic(schematic({ Biomes: biomes }), {
    discardBiomes: true,
  });
  assert.equal(kept.discarded.biomes, true);

  const secret = "op attacker; give @a minecraft:diamond 64";
  const commandBlock = (pos: number[]): unknown =>
    nbt.list(
      nbt.comp([
        {
          Pos: nbt.intArray(pos),
          Id: nbt.string("minecraft:command_block"),
          Data: nbt.comp({ Command: nbt.string(secret) }),
        },
      ]),
    );
  const palette = nbt.comp({
    "minecraft:air": nbt.int(0),
    "minecraft:command_block[conditional=false,facing=up]": nbt.int(1),
  });
  const withCommand = schematic(
    {},
    { Palette: palette, BlockEntities: commandBlock([1, 0, 0]) },
  );
  assert.throws(
    () => importSpongeSchematic(withCommand),
    (error: Error & { code?: string }) =>
      error.code === "E_BLOCK_ENTITIES" && !error.message.includes(secret),
  );
  const result = importSpongeSchematic(withCommand, {
    discardBlockEntities: true,
  });
  assert.deepEqual(result.discarded.blockEntities, [
    { id: "minecraft:command_block", pos: [1, 0, 0] },
  ]);
  assert.ok(!JSON.stringify(result).includes("attacker"));
  assert.ok(!stringifyGeometry(result.geometry).includes("attacker"));
  assert.ok(
    !gunzipSync(exportSpongeSchematic(result.geometry))
      .toString("latin1")
      .includes("attacker"),
  );

  rejects(
    schematic({}, { Palette: palette, BlockEntities: commandBlock([2, 0, 0]) }),
    "E_FIELD",
    { discardBlockEntities: true },
  );
  rejects(
    schematic({}, { Palette: palette, BlockEntities: commandBlock([0, 0]) }),
    "E_FIELD",
    { discardBlockEntities: true },
  );
});

test("metadata is bounded and typed, and stays labelled untrusted", () => {
  rejects(
    schematic({ Metadata: nbt.comp({ Name: nbt.int(1) }) }),
    "E_METADATA",
  );
  rejects(
    schematic({ Metadata: nbt.comp({ Name: nbt.string("x".repeat(2000)) }) }),
    "E_METADATA",
  );
  rejects(
    schematic({ Metadata: nbt.comp({ Date: nbt.string("yesterday") }) }),
    "E_METADATA",
  );
  const { geometry, discarded } = importSpongeSchematic(
    schematic({
      Metadata: nbt.comp({
        Author: nbt.string("Notch"),
        Owner: nbt.string("Notch"),
        Permissions: nbt.string("all"),
      }),
    }),
  );
  assert.deepEqual(geometry.untrustedMetadata, { author: "Notch" });
  assert.deepEqual(discarded.metadataKeys, ["Owner", "Permissions"]);
});

const validGeometry = () => ({
  format: GEOMETRY_FORMAT,
  formatVersion: 1,
  dataVersion: 3953,
  size: { x: 2, y: 1, z: 1 },
  offset: { x: 0, y: 0, z: 0 },
  palette: [
    { name: "minecraft:air", properties: {} },
    { name: "minecraft:lever", properties: { face: "wall", powered: "true" } },
  ],
  blocks: [0, 1],
  untrustedMetadata: { name: "lamp" },
});

test("neutral geometry JSON rejects authority fields at every level", () => {
  const limits = DEFAULT_SCHEMATIC_LIMITS;
  assert.equal(validateGeometry(validGeometry(), limits).blocks.length, 2);
  const injections: ((g: ReturnType<typeof validGeometry>) => void)[] = [
    (g) => Object.assign(g, { owner: "someone" }),
    (g) => Object.assign(g, { permissions: ["build"] }),
    (g) => Object.assign(g, { verified: true }),
    (g) => Object.assign(g.size, { trusted: true }),
    (g) => Object.assign(g.offset, { world: "overworld" }),
    (g) => Object.assign(g.palette[0]!, { verified: true }),
    (g) => Object.assign(g.untrustedMetadata, { projectId: "p1" }),
  ];
  for (const inject of injections) {
    const g = validGeometry();
    inject(g);
    assert.throws(() => validateGeometry(g, limits), { code: "E_GEOMETRY" });
    assert.throws(() => exportSpongeSchematic(g), { code: "E_GEOMETRY" });
  }
  assert.throws(
    () => parseGeometryJson('{"__proto__": {"verified": true}}', limits),
    { code: "E_GEOMETRY" },
  );
});

test("neutral geometry JSON rejects bad shapes, indices and sizes", () => {
  const limits = DEFAULT_SCHEMATIC_LIMITS;
  type Make = (g: ReturnType<typeof validGeometry>) => unknown;
  const shapes: Make[] = [
    (g) => ({ ...g, blocks: [0] }),
    (g) => ({ ...g, blocks: [0, 2] }),
    (g) => ({ ...g, blocks: [0, 1.5] }),
    (g) => ({ ...g, size: { x: 65536, y: 0, z: 1 }, blocks: [] }),
    (g) => ({ ...g, offset: { x: 2 ** 31, y: 0, z: 0 } }),
    (g) => ({ ...g, dataVersion: 0 }),
    (g) => ({ ...g, format: "something-else" }),
    (g) => ({ ...g, formatVersion: 2 }),
    (g) => ({ ...g, palette: [g.palette[0], g.palette[0]] }),
    (g) => ({
      ...g,
      palette: [
        { name: "minecraft:stone[powered=true]", properties: {} },
        g.palette[1],
      ],
    }),
    (g) => ({
      ...g,
      palette: [{ name: "stone", properties: {} }, g.palette[1]],
    }),
    (g) => ({
      ...g,
      palette: [
        { name: "minecraft:stone", properties: { lit: true } },
        g.palette[1],
      ],
    }),
  ];
  const bad: [Make, string][] = shapes.map((f) => [f, "E_GEOMETRY"]);
  bad.push([
    (g) => ({
      ...g,
      palette: [
        { name: "minecraft:stone", properties: { Lit: "x" } },
        g.palette[1],
      ],
    }),
    "E_BLOCK_STATE",
  ]);
  bad.push([
    (g) => ({ ...g, size: { x: 4096, y: 4096, z: 1 }, blocks: [] }),
    "E_TOO_LARGE",
  ]);
  for (const [make, code] of bad)
    assert.throws(() => validateGeometry(make(validGeometry()), limits), {
      code,
    });

  assert.throws(() => parseGeometryJson("{not json", limits), {
    code: "E_JSON",
  });
  assert.throws(
    () => parseGeometryJson(JSON.stringify(validGeometry()), limits, 100),
    { code: "E_INPUT_TOO_LARGE" },
  );
  assert.deepEqual(
    parseGeometryJson(JSON.stringify(validGeometry()), limits),
    validGeometry(),
  );
});
