# Schematic interchange

`companion/src/schematics/` reads and writes Sponge schematic v3 `.schem`
files (the format WorldEdit, FAWE and many other tools export) and converts
them to and from a small neutral geometry JSON format. It is standalone: it
does not touch the blueprint schema, the gateway, storage, the planner or the
mod, and it never changes a world.

Imported geometry is untrusted shape data. It carries no owner, permission,
execution authority or test result, and nothing in it says the build works.
A schematic of a "working" circuit is only a claim by whoever made the file.

## Command line

Build once with `npm run build`, then:

```
node dist/companion/src/schematics/cli.js import <in.schem> <out.json> [--force] [--discard-block-entities] [--discard-biomes]
node dist/companion/src/schematics/cli.js export <in.json> <out.schem> [--force]
node dist/companion/src/schematics/cli.js inspect <in.schem> [--discard-block-entities] [--discard-biomes]
```

- `import` writes neutral geometry JSON. `export` writes a Sponge v3 file.
  `inspect` prints a JSON summary (size, offset, DataVersion, palette count, a
  SHA-256 digest of the geometry and anything discarded) without writing.
- An existing output file is never replaced unless `--force` is given, and the
  output path may not be the input path.
- Exit codes: `0` success, `1` the input was rejected (the message starts with
  an error code such as `E_ENTITIES`), `2` usage error, including a refused
  overwrite.

## What is kept

Width, height and length; the offset; `DataVersion`; every block id and every
block-state property. A missing namespace is read as `minecraft`, and
properties are stored in sorted key order, so `repeater[facing=north,delay=2]`
becomes `minecraft:repeater` with `{ "delay": "2", "facing": "north" }`. The
palette is renumbered in its original index order, so sparse palettes import
cleanly.

From the optional `Metadata` compound, `Name`, `Author`, `Date` and
`RequiredMods` are copied into `untrustedMetadata`. Any other metadata key is
dropped and its name is listed under `discarded.metadataKeys` (and on stderr
in the CLI).

## What is rejected or discarded

| Data                                     | Default                      | With the opt-in flag                                                                                                       |
| ---------------------------------------- | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Entities (mobs, item frames, armour)     | rejected, `E_ENTITIES`       | always rejected; there is no discard option                                                                                |
| Block entities (signs, chests, commands) | rejected, `E_BLOCK_ENTITIES` | `--discard-block-entities` drops them; each id and position is reported, the data itself is never copied (the block stays) |
| Biomes                                   | rejected, `E_BIOMES`         | `--discard-biomes` drops them and reports it                                                                               |

An empty `Entities` list is accepted. Nothing is dropped silently: every
discard appears in `ImportResult.discarded` and on the CLI's stderr.

## Neutral geometry JSON

```json
{
  "format": "minecraft-ai-engineer.schematic-geometry",
  "formatVersion": 1,
  "dataVersion": 3578,
  "size": { "x": 2, "y": 1, "z": 1 },
  "offset": { "x": 0, "y": 64, "z": 0 },
  "palette": [
    { "name": "minecraft:air", "properties": {} },
    {
      "name": "minecraft:repeater",
      "properties": { "delay": "2", "facing": "north" }
    }
  ],
  "blocks": [1, 0],
  "untrustedMetadata": { "name": "demo", "dateMillis": "1700000000123" }
}
```

- `blocks` holds one palette index per block, ordered
  `x + z * size.x + y * size.x * size.z`, the same order as Sponge.
- `size` values are 0 to 65535; `offset` values are 32-bit integers.
- `untrustedMetadata` is optional. `dateMillis` is a decimal string so 64-bit
  times keep full precision.
- Each palette `name` must be a namespaced id such as `minecraft:stone`, and
  the same block state may not appear twice.
- Unknown keys are rejected at every level. A file that adds `owner`,
  `permissions`, `verified` or similar fields fails with `E_GEOMETRY` rather
  than having them ignored.

From TypeScript, import from `companion/src/schematics/index.js`:
`importSpongeSchematic`, `exportSpongeSchematic`, `parseGeometryJson`,
`stringifyGeometry`, `validateGeometry` and `geometryDigest`.

## Limits

| Limit                                   | Default   | Error                      |
| --------------------------------------- | --------- | -------------------------- |
| Compressed file                         | 16 MiB    | `E_INPUT_TOO_LARGE`        |
| Decompressed NBT (gzip bombs stop here) | 64 MiB    | `E_DECOMPRESSED_TOO_LARGE` |
| NBT nesting depth                       | 32        | `E_NBT_TOO_DEEP`           |
| NBT tag count                           | 1,000,000 | `E_NBT_TOO_MANY_TAGS`      |
| Volume (width × height × length)        | 8,388,608 | `E_TOO_LARGE`              |
| Palette entries                         | 65,536    | `E_TOO_LARGE`              |
| Geometry JSON                           | 96 MiB    | `E_INPUT_TOO_LARGE`        |

Decompression stops at the limit instead of inflating first. Every list and
array length is checked against the bytes actually left before anything is
allocated. Library callers can lower any limit through `options.limits`.

Other errors: `E_NOT_GZIP`, `E_DECOMPRESS`, `E_NBT_MALFORMED`,
`E_NBT_TRAILING_DATA`, `E_ROOT`, `E_UNSUPPORTED_VERSION` (anything but
version 3), `E_FIELD` (missing or wrongly typed field),
`E_UNSUPPORTED_FIELD` (unknown top-level or `Blocks` field), `E_PALETTE`,
`E_BLOCK_STATE`, `E_BLOCK_DATA`, `E_METADATA`, `E_GEOMETRY` and `E_JSON`.

## Limitations

- Sponge schematic v3 only. Versions 1 and 2, MCEdit `.schematic`, Litematica
  `.litematic` and vanilla structure `.nbt` files are rejected or unsupported.
- No entity, block-entity or biome data is carried, so sign text, container
  contents, command-block commands and spawner settings are lost when they are
  discarded.
- Block ids are checked for syntax only. They are not checked against a
  Minecraft version, and no DataVersion upgrade is applied.
- Export writes only `Version`, `DataVersion`, optional `Metadata`, the size,
  `Offset` and `Blocks` (`Palette` and `Data`).
- Nothing here places blocks, verifies a build or links geometry to a project.

## How it is tested

Tests run on GitHub CI. `tests/schematics-interop.test.ts` reads three
third-party files (see `tests/fixtures/schematics/README.md`) with this
importer and, independently, with `prismarine-nbt`, then compares every
block. Files built by `prismarine-nbt` are imported, and this exporter's
output is read back by `prismarine-nbt`. `tests/schematics-reject.test.ts`
covers malformed, oversized and unsupported input, and
`tests/schematics-cli.test.ts` covers the command line.
