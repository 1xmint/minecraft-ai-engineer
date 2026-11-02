import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import {
  existsSync,
  linkSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  truncateSync,
  writeFileSync,
} from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test, type TestContext } from "node:test";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";
import {
  DEFAULT_SCHEMATIC_LIMITS,
  GEOMETRY_FORMAT,
  MAX_GEOMETRY_JSON_BYTES,
  exportSpongeSchematic,
  formatBlockState,
  type BlockState,
} from "../companion/src/schematics/index.js";
import { runSchematicTransformsCli } from "../companion/src/schematic-transforms/cli.js";

// prismarine-nbt is the independent reader: exported .schem files are checked
// with it, never with our own Sponge code.
const nbt = createRequire(import.meta.url)(
  "prismarine-nbt",
) as typeof import("prismarine-nbt");

const fixture = fileURLToPath(
  new URL(
    "../../tests/fixtures/schematics/worldedit-fabric-7.3.schem",
    import.meta.url,
  ),
);

function cli(...argv: string[]): { code: number; out: string; err: string } {
  let out = "";
  let err = "";
  const code = runSchematicTransformsCli(argv, {
    stdout: (t) => (out += t),
    stderr: (t) => (err += t),
  });
  return { code, out, err };
}

function tempDir(t: TestContext): string {
  const dir = mkdtempSync(join(tmpdir(), "mce-transform-cli-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  return dir;
}

function geo(
  size: { x: number; y: number; z: number },
  offset: { x: number; y: number; z: number },
  palette: string[],
  blocks: number[],
  extra: object = {},
) {
  return {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion: 3953,
    size,
    offset,
    palette: palette.map((key) => {
      const open = key.indexOf("[");
      if (open === -1) return { name: key, properties: {} };
      const properties = Object.fromEntries(
        key
          .slice(open + 1, -1)
          .split(",")
          .map((p) => p.split("=") as [string, string]),
      );
      return { name: key.slice(0, open), properties };
    }),
    blocks,
    ...extra,
  };
}

const STAIRS = (facing: string) =>
  `minecraft:oak_stairs[facing=${facing},half=bottom,shape=straight,waterlogged=false]`;
const LOG = (axis: string) => `minecraft:oak_log[axis=${axis}]`;
const CHEST = (facing: string, type: string) =>
  `minecraft:chest[facing=${facing},type=${type},waterlogged=false]`;
const PLAIN = Array.from({ length: 9 }, (_, i) => `test:c${i + 3}`);
const METADATA = { name: "Turnstile", author: "tester" };

/** 3 wide, 2 tall, 2 long at (10, 64, -5); cell i holds palette entry i, and 0..2 have a direction. */
const BOX = geo(
  { x: 3, y: 2, z: 2 },
  { x: 10, y: 64, z: -5 },
  [STAIRS("north"), LOG("x"), CHEST("east", "left"), ...PLAIN],
  Array.from({ length: 12 }, (_, i) => i),
  { untrustedMetadata: METADATA },
);

// Hand-calculated: see docs/schematic-transforms.md for the (x, z) rules.
const EXPECTED = {
  "rotate-90": {
    size: { x: 2, y: 2, z: 3 },
    offset: { x: 4, y: 64, z: 10 },
    blocks: [3, 0, 4, 1, 5, 2, 9, 6, 10, 7, 11, 8],
    palette: [STAIRS("east"), LOG("z"), CHEST("south", "left")],
  },
  "rotate-180": {
    size: { x: 3, y: 2, z: 2 },
    offset: { x: -12, y: 64, z: 4 },
    blocks: [5, 4, 3, 2, 1, 0, 11, 10, 9, 8, 7, 6],
    palette: [STAIRS("south"), LOG("x"), CHEST("west", "left")],
  },
  "rotate-270": {
    size: { x: 2, y: 2, z: 3 },
    offset: { x: -5, y: 64, z: -12 },
    blocks: [2, 5, 1, 4, 0, 3, 8, 11, 7, 10, 6, 9],
    palette: [STAIRS("west"), LOG("z"), CHEST("north", "left")],
  },
  "mirror-east-west": {
    size: { x: 3, y: 2, z: 2 },
    offset: { x: -12, y: 64, z: -5 },
    blocks: [2, 1, 0, 5, 4, 3, 8, 7, 6, 11, 10, 9],
    palette: [STAIRS("north"), LOG("x"), CHEST("west", "right")],
  },
  "mirror-north-south": {
    size: { x: 3, y: 2, z: 2 },
    offset: { x: 10, y: 64, z: 4 },
    blocks: [3, 4, 5, 0, 1, 2, 9, 10, 11, 6, 7, 8],
    palette: [STAIRS("south"), LOG("x"), CHEST("east", "right")],
  },
} as const;

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

/** Reads a Sponge v3 file with prismarine-nbt and decodes cells by the spec. */
async function oracleRead(bytes: Buffer) {
  const { parsed } = await nbt.parse(bytes);
  const s = (parsed.value as any).Schematic.value;
  assert.equal(s.Version.value, 3);
  const size = {
    x: s.Width.value & 0xffff,
    y: s.Height.value & 0xffff,
    z: s.Length.value & 0xffff,
  };
  const [x, y, z] = s.Offset.value as number[];
  const byIndex = new Map<number, string>();
  for (const [key, tag] of Object.entries(s.Blocks.value.Palette.value))
    byIndex.set((tag as { value: number }).value, normalizeState(key));
  const voxels: string[] = [];
  let value = 0;
  let shift = 0;
  for (const signed of s.Blocks.value.Data.value as number[]) {
    const byte = signed & 0xff;
    value |= (byte & 0x7f) << shift;
    shift += 7;
    if ((byte & 0x80) === 0) {
      voxels.push(byIndex.get(value)!);
      value = 0;
      shift = 0;
    }
  }
  assert.equal(voxels.length, size.x * size.y * size.z);
  return {
    keys: Object.keys(s).sort(),
    dataVersion: s.DataVersion.value as number,
    size,
    offset: { x, y, z },
    voxels,
    name: s.Metadata?.value.Name?.value as string | undefined,
    author: s.Metadata?.value.Author?.value as string | undefined,
  };
}

test("every operation, from and to both formats, matches hand-calculated geometry and states", async (t) => {
  const dir = tempDir(t);
  const inputs = {
    json: join(dir, "box.json"),
    schem: join(dir, "box.schem"),
  };
  writeFileSync(inputs.json, JSON.stringify(BOX));
  writeFileSync(inputs.schem, exportSpongeSchematic(BOX));

  for (const [op, want] of Object.entries(EXPECTED)) {
    const palette = [...want.palette, ...PLAIN];
    for (const from of ["json", "schem"] as const) {
      const jsonOut = join(dir, `${op}-${from}.json`);
      const r1 = cli(op, "--from", from, "--to", "json", inputs[from], jsonOut);
      assert.equal(r1.code, 0, r1.err);
      assert.equal(r1.err, "");
      assert.match(r1.out, /untrusted geometry only/);
      const g = JSON.parse(readFileSync(jsonOut, "utf8"));
      assert.deepEqual(
        Object.keys(g).sort(),
        [
          "blocks",
          "dataVersion",
          "format",
          "formatVersion",
          "offset",
          "palette",
          "size",
          "untrustedMetadata",
        ],
        `${op} from ${from}: no ownership, permission or verification fields`,
      );
      assert.equal(g.dataVersion, 3953);
      assert.deepEqual(g.untrustedMetadata, METADATA);
      assert.deepEqual(g.size, want.size, `${op} from ${from}`);
      assert.deepEqual(g.offset, want.offset, `${op} from ${from}`);
      assert.deepEqual(g.blocks, want.blocks, `${op} from ${from}`);
      assert.deepEqual(
        g.palette.map((s: BlockState) => formatBlockState(s)),
        palette.map(normalizeState),
        `${op} from ${from}`,
      );

      const schemOut = join(dir, `${op}-${from}.schem`);
      const r2 = cli(
        op,
        "--from",
        from,
        "--to",
        "schem",
        inputs[from],
        schemOut,
      );
      assert.equal(r2.code, 0, r2.err);
      const o = await oracleRead(readFileSync(schemOut));
      assert.deepEqual(o.keys, [
        "Blocks",
        "DataVersion",
        "Height",
        "Length",
        "Metadata",
        "Offset",
        "Version",
        "Width",
      ]);
      assert.equal(o.dataVersion, 3953);
      assert.equal(o.name, METADATA.name);
      assert.equal(o.author, METADATA.author);
      assert.deepEqual(o.size, want.size, `${op} from ${from} to schem`);
      assert.deepEqual(o.offset, want.offset, `${op} from ${from} to schem`);
      assert.deepEqual(
        o.voxels,
        want.blocks.map((i) => normalizeState(palette[i]!)),
        `${op} from ${from} to schem`,
      );
    }
  }
});

test("zero-volume geometry with maximal dimensions turns in both formats", async (t) => {
  const dir = tempDir(t);
  const input = join(dir, "flat.json");
  writeFileSync(
    input,
    JSON.stringify(
      geo(
        { x: 65535, y: 65535, z: 0 },
        { x: 0, y: 0, z: 0 },
        ["minecraft:air"],
        [],
      ),
    ),
  );
  const schem = join(dir, "flat.schem");
  const r1 = cli("rotate-90", "--from", "json", "--to", "schem", input, schem);
  assert.equal(r1.code, 0, r1.err);
  const o = await oracleRead(readFileSync(schem));
  assert.deepEqual(o.size, { x: 0, y: 65535, z: 65535 });
  assert.deepEqual(o.offset, { x: 0, y: 0, z: 0 });
  assert.deepEqual(o.voxels, []);

  // Back from .schem: a half turn of a 0 x 65535 x 65535 box at the origin.
  const json = join(dir, "flat-back.json");
  const r2 = cli("rotate-180", "--from", "schem", "--to", "json", schem, json);
  assert.equal(r2.code, 0, r2.err);
  const g = JSON.parse(readFileSync(json, "utf8"));
  assert.deepEqual(g.size, { x: 0, y: 65535, z: 65535 });
  assert.deepEqual(g.offset, { x: 0, y: 0, z: -65534 });
  assert.deepEqual(g.blocks, []);
});

test("block entities and biomes are refused unless discarded, and every discard is reported", (t) => {
  const dir = tempDir(t);
  const out = join(dir, "out.json");
  const args = ["rotate-90", "--from", "schem", "--to", "json", fixture, out];

  const refused = cli(...args);
  assert.equal(refused.code, 1);
  assert.match(refused.err, /E_BLOCK_ENTITIES/);
  assert.equal(existsSync(out), false);

  const kept = cli(...args, "--discard-block-entities");
  assert.equal(kept.code, 0, kept.err);
  assert.match(
    kept.err,
    /^Discarded 6 block entities \(their data was not read\):\n/,
  );
  assert.equal(
    kept.err.match(/^ {2}minecraft:\w*sign at \d+,\d+,\d+$/gm)?.length,
    6,
  );
  // 15 x 16 x 20 at (-14, 0, -19), turned 90: x 0..14 -> z -14..0, z -19..0 -> x 0..19.
  const g = JSON.parse(readFileSync(out, "utf8"));
  assert.deepEqual(g.size, { x: 20, y: 16, z: 15 });
  assert.deepEqual(g.offset, { x: 0, y: 0, z: -14 });
  assert.equal(g.dataVersion, 3578);

  const biomed = join(dir, "biomes.schem");
  writeFileSync(
    biomed,
    gzipSync(
      nbt.writeUncompressed({
        type: "compound",
        name: "",
        value: {
          Schematic: nbt.comp({
            Version: nbt.int(3),
            DataVersion: nbt.int(3953),
            Width: nbt.short(2),
            Height: nbt.short(1),
            Length: nbt.short(1),
            Blocks: nbt.comp({
              Palette: nbt.comp({
                "minecraft:air": nbt.int(0),
                "minecraft:stone": nbt.int(1),
              }),
              Data: nbt.byteArray([0, 1]),
            }),
            Biomes: nbt.comp({
              Palette: nbt.comp({ "minecraft:plains": nbt.int(0) }),
              Data: nbt.byteArray([0, 0]),
            }),
          } as object),
        },
      } as Parameters<typeof nbt.writeUncompressed>[0]),
    ),
  );
  const biomeArgs = ["mirror-east-west", "--from", "schem", "--to", "schem"];
  const noBiomes = cli(...biomeArgs, biomed, join(dir, "b.schem"));
  assert.equal(noBiomes.code, 1);
  assert.match(noBiomes.err, /E_BIOMES/);
  assert.equal(existsSync(join(dir, "b.schem")), false);
  const dropped = cli(
    ...biomeArgs,
    biomed,
    join(dir, "b.schem"),
    "--discard-biomes",
  );
  assert.equal(dropped.code, 0, dropped.err);
  assert.equal(dropped.err, "Discarded biome data.\n");

  const jsonDiscard = cli(
    "rotate-90",
    "--from",
    "json",
    "--to",
    "json",
    out,
    join(dir, "x.json"),
    "--discard-biomes",
  );
  assert.equal(jsonDiscard.code, 2);
  assert.match(jsonDiscard.err, /apply only to --from schem/);
});

test("bad arguments are usage errors that touch nothing", (t) => {
  const dir = tempDir(t);
  const input = join(dir, "box.json");
  const out = join(dir, "out.json");
  writeFileSync(input, JSON.stringify(BOX));
  const io = ["--from", "json", "--to", "json"];
  for (const argv of [
    [],
    ["spin-45", ...io, input, out],
    ["rotate-90", input, out],
    ["rotate-90", "--from", "json", input, out],
    ["rotate-90", "--to", "json", input, out],
    ["rotate-90", "--from", "nbt", "--to", "json", input, out],
    ["rotate-90", "--from", "json", "--to"],
    ["rotate-90", ...io, "--from", "json", input, out],
    ["rotate-90", ...io, input],
    ["rotate-90", ...io, input, out, join(dir, "third.json")],
    ["rotate-90", ...io, input, out, "--overwrite"],
    ["rotate-90", ...io, input, out, "--force", "--force"],
    ["rotate-90", ...io, input, out, "--discard-block-entities"],
  ]) {
    const r = cli(...argv);
    assert.equal(r.code, 2, `${argv.join(" ")}: ${r.err}`);
    assert.match(r.err, /Usage:/);
    assert.equal(existsSync(out), false, argv.join(" "));
  }
});

test("malformed, oversized and unsupported inputs fail and keep existing outputs", (t) => {
  const dir = tempDir(t);
  const out = join(dir, "out.schem");
  writeFileSync(out, "keep me");
  const file = (name: string, data: string | Uint8Array) => {
    const path = join(dir, name);
    writeFileSync(path, data);
    return path;
  };
  const cases: [string[], RegExp][] = [
    [["json", file("bad.json", "{ not json")], /E_JSON/],
    [
      ["json", file("owner.json", JSON.stringify({ ...BOX, owner: "me" }))],
      /E_GEOMETRY/,
    ],
    [
      [
        "json",
        file(
          "huge.json",
          JSON.stringify(
            geo(
              { x: 65535, y: 65535, z: 2 },
              { x: 0, y: 0, z: 0 },
              ["minecraft:air"],
              [],
            ),
          ),
        ),
      ],
      /E_TOO_LARGE/,
    ],
    [
      [
        "json",
        file(
          "bookshelf.json",
          JSON.stringify(
            geo(
              { x: 1, y: 1, z: 1 },
              { x: 0, y: 0, z: 0 },
              [
                "minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=true]",
              ],
              [0],
            ),
          ),
        ),
      ],
      /E_UNSUPPORTED_STATE: .*chiseled_bookshelf.*slot_0_occupied/,
    ],
    [["schem", file("text.schem", "not gzip")], /E_NOT_GZIP/],
    [["schem", file("box-as-schem.json", JSON.stringify(BOX))], /E_NOT_GZIP/],
    [["json", file("box-as-json.schem", exportSpongeSchematic(BOX))], /E_JSON/],
  ];
  const bigSchem = join(dir, "big.schem");
  writeFileSync(bigSchem, "");
  truncateSync(bigSchem, DEFAULT_SCHEMATIC_LIMITS.maxCompressedBytes + 1);
  cases.push([["schem", bigSchem], /E_INPUT_TOO_LARGE/]);
  const bigJson = join(dir, "big.json");
  writeFileSync(bigJson, "");
  truncateSync(bigJson, MAX_GEOMETRY_JSON_BYTES + 1);
  cases.push([["json", bigJson], /E_INPUT_TOO_LARGE/]);

  for (const [[from, input], pattern] of cases) {
    const r = cli(
      "rotate-90",
      "--from",
      from!,
      "--to",
      "schem",
      input!,
      out,
      "--force",
    );
    assert.equal(r.code, 1, `${input}: ${r.err}`);
    assert.match(r.err, pattern);
    assert.equal(readFileSync(out, "utf8"), "keep me", input!);
  }
});

test("existing outputs need --force, and input aliases are refused even with it", (t) => {
  const dir = tempDir(t);
  const input = join(dir, "box.json");
  const original = JSON.stringify(BOX);
  writeFileSync(input, original);
  const out = join(dir, "out.json");
  writeFileSync(out, "keep me");
  const args = ["rotate-180", "--from", "json", "--to", "json"];

  const clash = cli(...args, input, out);
  assert.equal(clash.code, 2);
  assert.match(clash.err, /already exists; pass --force/);
  assert.equal(readFileSync(out, "utf8"), "keep me");
  const forced = cli(...args, input, out, "--force");
  assert.equal(forced.code, 0, forced.err);
  assert.deepEqual(
    JSON.parse(readFileSync(out, "utf8")).offset,
    EXPECTED["rotate-180"].offset,
  );

  const same = cli(...args, input, join(dir, ".", "box.json"), "--force");
  assert.equal(same.code, 2);
  assert.match(same.err, /must differ from the input/);

  const hard = join(dir, "hard.json");
  linkSync(input, hard);
  const hardRun = cli(...args, input, hard, "--force");
  assert.equal(hardRun.code, 2);
  assert.match(hardRun.err, /including file aliases/);
  assert.equal(readFileSync(input, "utf8"), original);

  let symlinks = true;
  try {
    symlinkSync(input, join(dir, "soft-in.json"));
    symlinkSync(out, join(dir, "soft-out.json"));
  } catch {
    symlinks = false; // Windows without symlink rights; CI runs these on Linux.
  }
  if (symlinks) {
    const before = readFileSync(out, "utf8");
    for (const [src, dst] of [
      [input, join(dir, "soft-in.json")], // output is a link to the input
      [input, join(dir, "soft-out.json")], // output is a link to another file
      [join(dir, "soft-in.json"), input], // input is a link to the output
    ] as const) {
      const r = cli(...args, src, dst, "--force");
      assert.equal(r.code, 2, `${src} -> ${dst}: ${r.err}`);
      assert.match(r.err, /symbolic link|including file aliases/);
    }
    assert.equal(readFileSync(input, "utf8"), original);
    assert.equal(readFileSync(out, "utf8"), before);
  }
});

test("compiled CLI runs as a standalone program", (t) => {
  const dir = tempDir(t);
  const input = join(dir, "box.json");
  writeFileSync(input, JSON.stringify(BOX));
  const program = fileURLToPath(
    new URL("../companion/src/schematic-transforms/cli.js", import.meta.url),
  );
  const out = join(dir, "out.schem");
  const stdout = execFileSync(
    process.execPath,
    [program, "rotate-270", "--from", "json", "--to", "schem", input, out],
    { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
  );
  assert.match(stdout, /rotate-270, 2x2x3, Sponge schematic v3/);
  assert.ok(readFileSync(out).byteLength > 0);
});
