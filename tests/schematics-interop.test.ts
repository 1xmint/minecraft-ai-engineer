import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { test } from "node:test";
import { gzipSync } from "node:zlib";
import {
  exportSpongeSchematic,
  formatBlockState,
  importSpongeSchematic,
  parseGeometryJson,
  stringifyGeometry,
  DEFAULT_SCHEMATIC_LIMITS,
  type SchematicGeometry,
} from "../companion/src/schematics/index.js";

// prismarine-nbt is the independent reader and writer. Nothing below the oracle
// line uses our own NBT or Sponge code to decide what the right answer is.
const nbt = createRequire(import.meta.url)(
  "prismarine-nbt",
) as typeof import("prismarine-nbt");

const fixture = (name: string): Buffer =>
  readFileSync(
    new URL(`../../tests/fixtures/schematics/${name}`, import.meta.url),
  );

const FIXTURES = {
  "worldedit-fabric-7.3.schem":
    "460016176cf662e5078e1a45b73902455063c857a088bbcfe38c12b034cad136",
  "hollowcube-2x2-chunk-vert.schem":
    "66849d2d8a83bc0d3ac7bc45b49f80e2f6d8a17f68a1d3bbc5a1dd06d6cb9922",
  "schematic4j-sponge-v3.schem":
    "8098f37db5dd3cba385d16db36e9da2240cfbf2545a18bd6c02a34d14cf408d6",
} as const;

// ---- oracle: prismarine-nbt plus a test-local reading of the Sponge v3 spec ----

interface OracleSchematic {
  dataVersion: number;
  size: { x: number; y: number; z: number };
  offset: { x: number; y: number; z: number };
  voxels: string[];
  blockEntities: { id: string; pos: number[] }[];
  dateMillis?: string;
}

function normalizeState(key: string): string {
  const open = key.indexOf("[");
  const id = open === -1 ? key : key.slice(0, open);
  const name = id.includes(":") ? id : `minecraft:${id}`;
  if (open === -1) return name;
  const props = key
    .slice(open + 1, -1)
    .split(",")
    .sort();
  return `${name}[${props.join(",")}]`;
}

async function oracleRead(bytes: Buffer): Promise<OracleSchematic> {
  const { parsed } = await nbt.parse(bytes);
  const root = parsed.value as any;
  const s = root.Schematic ? root.Schematic.value : root;
  assert.equal(s.Version.value, 3);
  const size = {
    x: s.Width.value & 0xffff,
    y: s.Height.value & 0xffff,
    z: s.Length.value & 0xffff,
  };
  const off: number[] = s.Offset ? s.Offset.value : [0, 0, 0];
  const byIndex = new Map<number, string>();
  for (const [key, tag] of Object.entries(s.Blocks.value.Palette.value))
    byIndex.set((tag as { value: number }).value, normalizeState(key));
  const data: number[] = s.Blocks.value.Data.value;
  const voxels: string[] = [];
  let value = 0;
  let shift = 0;
  for (const signed of data) {
    const byte = signed & 0xff;
    value |= (byte & 0x7f) << shift;
    shift += 7;
    if ((byte & 0x80) === 0) {
      const state = byIndex.get(value);
      assert.ok(state !== undefined, `oracle: palette index ${value} missing`);
      voxels.push(state);
      value = 0;
      shift = 0;
    }
  }
  assert.equal(voxels.length, size.x * size.y * size.z);
  const entities = s.Blocks.value.BlockEntities?.value.value ?? [];
  const date = s.Metadata?.value.Date?.value as [number, number] | undefined;
  return {
    dataVersion: s.DataVersion.value,
    size,
    offset: { x: off[0]!, y: off[1]!, z: off[2]! },
    voxels,
    blockEntities: entities.map((e: any) => ({
      id: e.Id.value,
      pos: e.Pos.value,
    })),
    ...(date
      ? {
          dateMillis: BigInt.asIntN(
            64,
            (BigInt(date[0]) << 32n) | BigInt(date[1] >>> 0),
          ).toString(),
        }
      : {}),
  };
}

// ---- comparisons ----

function voxelsOf(g: SchematicGeometry): string[] {
  const names = g.palette.map(formatBlockState);
  return g.blocks.map((i) => names[i]!);
}

function assertSameVoxels(
  actual: string[],
  expected: string[],
  label: string,
): void {
  assert.equal(actual.length, expected.length, `${label}: voxel count`);
  for (let i = 0; i < expected.length; i++)
    if (actual[i] !== expected[i])
      assert.fail(
        `${label}: voxel ${i} is ${actual[i]}, expected ${expected[i]}`,
      );
}

function assertMatchesOracle(
  g: SchematicGeometry,
  o: OracleSchematic,
  label: string,
): void {
  assert.equal(g.dataVersion, o.dataVersion, `${label}: DataVersion`);
  assert.deepEqual(g.size, o.size, `${label}: size`);
  assert.deepEqual(g.offset, o.offset, `${label}: offset`);
  assertSameVoxels(voxelsOf(g), o.voxels, label);
}

function blockFitsEntity(block: string, id: string): boolean {
  if (id === "minecraft:sign") return /^minecraft:[a-z_]*sign\b/.test(block);
  if (id === "minecraft:brushable_block")
    return /^minecraft:suspicious_(sand|gravel)\b/.test(block);
  return block === id || block.startsWith(`${id}[`);
}

const discardAll = { discardBlockEntities: true, discardBiomes: true } as const;

test("fixtures are the pinned third-party files", () => {
  for (const [name, sha] of Object.entries(FIXTURES))
    assert.equal(
      createHash("sha256").update(fixture(name)).digest("hex"),
      sha,
      name,
    );
});

for (const name of [
  "worldedit-fabric-7.3.schem",
  "hollowcube-2x2-chunk-vert.schem",
] as const) {
  test(`${name}: every voxel matches the independent reader, before and after re-export`, async () => {
    const bytes = fixture(name);
    const oracle = await oracleRead(bytes);
    const result = importSpongeSchematic(bytes, discardAll);
    assert.equal(result.rootLayout, "nested");
    assertMatchesOracle(result.geometry, oracle, "import");

    // Block entities are reported by id and position, and each sits on the block it belongs to.
    assert.equal(
      result.discarded.blockEntities.length,
      oracle.blockEntities.length,
    );
    assert.ok(oracle.blockEntities.length > 0);
    for (const [i, e] of result.discarded.blockEntities.entries()) {
      assert.deepEqual(
        [e.id, [...e.pos]],
        [oracle.blockEntities[i]!.id, oracle.blockEntities[i]!.pos],
      );
      const [x, y, z] = e.pos;
      const block = voxelsOf(result.geometry)[
        x + z * oracle.size.x + y * oracle.size.x * oracle.size.z
      ]!;
      assert.ok(
        blockFitsEntity(block, e.id),
        `${e.id} at ${e.pos} sits on ${block}`,
      );
    }
    if (oracle.dateMillis !== undefined)
      assert.equal(
        result.geometry.untrustedMetadata?.dateMillis,
        oracle.dateMillis,
      );
    assert.ok(result.discarded.metadataKeys.includes("WorldEdit"));

    const exported = exportSpongeSchematic(result.geometry);
    const reread = await oracleRead(Buffer.from(exported));
    assertMatchesOracle(
      result.geometry,
      reread,
      "re-export read by prismarine-nbt",
    );
    assert.equal(reread.blockEntities.length, 0);

    const again = importSpongeSchematic(exported);
    assert.deepEqual(again.geometry, result.geometry);
    assert.deepEqual(
      parseGeometryJson(
        stringifyGeometry(result.geometry),
        DEFAULT_SCHEMATIC_LIMITS,
      ),
      result.geometry,
    );
  });
}

test("fixtures with block entities are refused unless the caller opts in to discarding them", () => {
  for (const name of [
    "worldedit-fabric-7.3.schem",
    "hollowcube-2x2-chunk-vert.schem",
  ])
    assert.throws(() => importSpongeSchematic(fixture(name)), {
      code: "E_BLOCK_ENTITIES",
    });
});

test("schematic4j flat-root fixture is refused for its entity, even with discard options", () => {
  assert.throws(
    () =>
      importSpongeSchematic(fixture("schematic4j-sponge-v3.schem"), discardAll),
    {
      code: "E_ENTITIES",
    },
  );
});

// ---- files written by prismarine-nbt exercise the edge cases real files rarely hit ----

function prismarineSchematic(
  fields: Record<string, unknown>,
  flat = false,
): Buffer {
  const schematic = nbt.comp(fields as object);
  const root = flat
    ? { ...schematic, name: "Schematic" }
    : { type: "compound" as const, name: "", value: { Schematic: schematic } };
  return gzipSync(
    nbt.writeUncompressed(root as Parameters<typeof nbt.writeUncompressed>[0]),
  );
}

function varintBytes(values: number[]): number[] {
  const out: number[] = [];
  for (let v of values) {
    while (v >= 0x80) {
      out.push((v & 0x7f) | 0x80);
      v >>>= 7;
    }
    out.push(v);
  }
  return out.map((b) => (b > 127 ? b - 256 : b));
}

test("prismarine-written schematic: multi-byte varints, sparse indices, default namespace, metadata", async () => {
  const size = { x: 4, y: 16, z: 4 };
  const keys = Array.from({ length: 200 }, (_, i) =>
    i === 0
      ? "air"
      : i === 1
        ? "minecraft:repeater[powered=false,facing=north,delay=2]"
        : `example:block_${i}`,
  );
  const fileIndex = (i: number): number => i * 3 + 1;
  const blocks = Array.from(
    { length: size.x * size.y * size.z },
    (_, i) => (i * 7) % keys.length,
  );
  const bytes = prismarineSchematic({
    Version: nbt.int(3),
    DataVersion: nbt.int(3953),
    Metadata: nbt.comp({
      Name: nbt.string("Signal lamp"),
      Author: nbt.string("someone else"),
      Date: nbt.long([395, -807049093]),
      RequiredMods: nbt.list(nbt.string(["examplemod"])),
      Extra: nbt.int(1),
    }),
    Width: nbt.short(size.x),
    Height: nbt.short(size.y),
    Length: nbt.short(size.z),
    Offset: nbt.intArray([-5, 64, 2147483647]),
    Blocks: nbt.comp({
      Palette: nbt.comp(
        Object.fromEntries(keys.map((k, i) => [k, nbt.int(fileIndex(i))])),
      ),
      Data: nbt.byteArray(varintBytes(blocks.map(fileIndex))),
    }),
  });
  const oracle = await oracleRead(bytes);
  const { geometry, discarded } = importSpongeSchematic(bytes);
  assertMatchesOracle(geometry, oracle, "prismarine-written");
  assertSameVoxels(
    voxelsOf(geometry),
    blocks.map((i) => normalizeState(keys[i]!)),
    "generator",
  );
  assert.equal(geometry.palette[0]!.name, "minecraft:air");
  assert.deepEqual(geometry.palette[1], {
    name: "minecraft:repeater",
    properties: { delay: "2", facing: "north", powered: "false" },
  });
  assert.deepEqual(geometry.untrustedMetadata, {
    name: "Signal lamp",
    author: "someone else",
    dateMillis: "1700000000123",
    requiredMods: ["examplemod"],
  });
  assert.deepEqual(discarded.metadataKeys, ["Extra"]);
  const reread = await oracleRead(Buffer.from(exportSpongeSchematic(geometry)));
  assertMatchesOracle(geometry, reread, "re-export");
  assert.equal(reread.dateMillis, "1700000000123");
});

test("prismarine-written flat root and a width above 32767 (unsigned short)", async () => {
  const bytes = prismarineSchematic(
    {
      Version: nbt.int(3),
      DataVersion: nbt.int(3700),
      Width: nbt.short(40000 - 65536),
      Height: nbt.short(1),
      Length: nbt.short(1),
      Blocks: nbt.comp({
        Palette: nbt.comp({ "minecraft:stone": nbt.int(0) }),
        Data: nbt.byteArray(new Array<number>(40000).fill(0)),
      }),
    },
    true,
  );
  const result = importSpongeSchematic(bytes);
  assert.equal(result.rootLayout, "flat");
  assert.deepEqual(result.geometry.size, { x: 40000, y: 1, z: 1 });
  assert.deepEqual(result.geometry.offset, { x: 0, y: 0, z: 0 });
  const reread = await oracleRead(
    Buffer.from(exportSpongeSchematic(result.geometry)),
  );
  assertMatchesOracle(result.geometry, reread, "re-export");
});

test("exported files carry no ownership, permission or verification fields", async () => {
  const { geometry } = importSpongeSchematic(
    fixture("worldedit-fabric-7.3.schem"),
    discardAll,
  );
  const { parsed } = await nbt.parse(
    Buffer.from(exportSpongeSchematic(geometry)),
  );
  const s = (parsed.value as any).Schematic.value;
  assert.deepEqual(Object.keys(s).sort(), [
    "Blocks",
    "DataVersion",
    "Height",
    "Length",
    "Metadata",
    "Offset",
    "Version",
    "Width",
  ]);
  assert.deepEqual(Object.keys(s.Blocks.value).sort(), ["Data", "Palette"]);
  assert.deepEqual(Object.keys(geometry).sort(), [
    "blocks",
    "dataVersion",
    "format",
    "formatVersion",
    "offset",
    "palette",
    "size",
    "untrustedMetadata",
  ]);
});
