# Schematic transforms

`companion/src/schematic-transforms/` turns or mirrors neutral schematic
geometry (the `SchematicGeometry` produced by `companion/src/schematics/`).
It is a standalone library plus a file-only command line tool: nothing here
places blocks, talks to the mod, or grants anything.

## API

```ts
import {
  rotateGeometry,
  mirrorGeometry,
  transformGeometry,
  transformBlockState,
} from "./schematic-transforms/index.js";

rotateGeometry(geometry, 90); // 90 | 180 | 270, clockwise seen from above
mirrorGeometry(geometry, "east-west"); // or "north-south"
transformGeometry(geometry, "rotate-180"); // any of GEOMETRY_TRANSFORMS
```

Every call:

1. validates the input with the existing `validateGeometry` (so malformed
   geometry or extra fields such as `verified` or `owner` fail with
   `E_GEOMETRY`, and size limits with `E_TOO_LARGE`);
2. transforms every palette entry, or rejects the whole call;
3. moves every block;
4. validates the result again and returns a new frozen object.

The input is never changed. `dataVersion`, `format`, `formatVersion` and
`untrustedMetadata` are copied as they are. The output has exactly the same
fields as any other geometry: there is no ownership, permission, trust or
verification field, and the validator would reject one.

## Command line

Build first (`npm run build`), then run the compiled entry point:

```sh
node dist/companion/src/schematic-transforms/cli.js <operation>   --from <schem|json> --to <schem|json> <input> <output>   [--force] [--discard-block-entities] [--discard-biomes]
```

`<operation>` is one of `rotate-90`, `rotate-180`, `rotate-270`,
`mirror-east-west` or `mirror-north-south`. For example, to turn a WorldEdit
export a quarter turn clockwise into a new `.schem`:

```sh
node dist/companion/src/schematic-transforms/cli.js rotate-90   --from schem --to schem house.schem house-east.schem --discard-block-entities
```

- **Formats are explicit.** `--from` and `--to` are both required; the tool
  never guesses from a file name. `schem` means a gzip-compressed Sponge v3
  file, `json` the neutral geometry JSON from `companion/src/schematics/`.
  Any pairing is allowed.
- **Same codec, same limits.** Input is read through the existing importer or
  JSON parser with their size limits (16 MiB `.schem`, 96 MiB JSON, 8 Mi
  blocks, 65 536 palette entries). Reads stop at the byte limit.
- **What is kept.** `DataVersion` and the supported metadata (name, author,
  date, required mods) are carried over. The output never gains ownership,
  permission, trust or verification fields.
- **What is dropped, only when asked.** A `.schem` with block entities (sign
  text, chest contents, commands) fails with `E_BLOCK_ENTITIES`, and one with
  biomes fails with `E_BIOMES`, unless `--discard-block-entities` or
  `--discard-biomes` is given. Every discarded block entity (id and position),
  discarded biome data and unsupported metadata field is listed on stderr.
  The discard options are refused with `--from json`. Entities (mobs, item
  frames) always fail with `E_ENTITIES`.
- **Nothing is written until everything has worked.** Reading, validating,
  transforming and encoding all finish before the output is opened, so a
  rejected input leaves any existing output untouched.
- **Existing outputs are protected.** An existing output is refused unless
  `--force` is given. The output may never be the input: the same path, a
  hard link to it, or a symbolic link is refused even with `--force`. Output
  paths that are symbolic links are always refused.

Exit codes: `0` written, `1` the input or output was rejected (the message
starts with the error code, such as `E_UNSUPPORTED_STATE`), `2` a usage
mistake (the usage text is printed).

## How size and offset change

The offset is the Sponge `Offset`: where the box's minimum corner sits
relative to the paste origin. Transforms turn or mirror the box **about the
paste origin**, the way a structure turns about the point it is pasted from.
`y` never changes.

| Transform            | World (x, z) becomes | Size x / z | Directions                  |
| -------------------- | -------------------- | ---------- | --------------------------- |
| `rotate-90`          | (−z, x)              | swapped    | north → east → south → west |
| `rotate-180`         | (−x, −z)             | same       | north ↔ south, east ↔ west  |
| `rotate-270`         | (z, −x)              | swapped    | north → west → south → east |
| `mirror-east-west`   | (−x, z)              | same       | east ↔ west                 |
| `mirror-north-south` | (x, −z)              | same       | north ↔ south               |

The new offset is the minimum corner of the moved box. Worked example: a
3 × 2 × 2 box at offset (10, 64, −5) covers x 10..12, z −5..−4. Turned 90° it
covers x 4..5, z 10..12, so its size is 2 × 2 × 3 and its offset (4, 64, 10).

An axis of size 0 is treated as one cell wide when working out the corner, so
empty geometry still moves by the same reversible rule.

Because of this rule, four quarter turns, or the same mirror twice, give back
exactly the original geometry, offset included. East-west then north-south
equals a 180° turn.

If the new offset would fall outside the 32-bit range the format allows (for
example mirroring an offset of −2147483648), the call fails with
`E_COORDINATE_RANGE`.

## Which block states are supported

Support is a fixed, listed subset. It is **not** universal Minecraft block
support. A property is either known to mean the same in every orientation,
has a transform rule below, or the call is rejected.

Transformed properties:

| Property                         | Rule                                                                                                                                            |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `facing`                         | `north/east/south/west` turn or mirror; `up/down` stay                                                                                          |
| `axis`                           | `x` ↔ `z` on 90° and 270°; `y` stays                                                                                                            |
| `rotation` (0–15)                | 16 steps clockwise from south (0 south, 4 west, 8 north, 12 east)                                                                               |
| `north`, `east`, `south`, `west` | connection keys (fences, walls, redstone wire, vines…) move with the world; values are kept                                                     |
| `shape`                          | stair shapes keep their kind and swap `left`/`right` under a mirror; rail shapes turn, in vanilla's spelling (`south_east`, never `east_south`) |
| `hinge`                          | `left` ↔ `right` under a mirror                                                                                                                 |
| `type`                           | `left` ↔ `right` (chests) under a mirror; `single`, `top`, `bottom`, `double`, `normal`, `sticky` stay                                          |
| `orientation`                    | jigsaw and crafter `<front>_<top>` values, both parts turned                                                                                    |

Properties passed through unchanged are listed in
`ORIENTATION_FREE_PROPERTIES` in `states.ts` (for example `waterlogged`,
`powered`, `half`, `face`, `delay`, `mode`, `up`, `down`, `age`).

Rejected with `E_UNSUPPORTED_STATE`, naming the block and property:

- any property not in either list (for example the chiseled bookshelf's
  `slot_0_occupied`, the brewing stand's `has_bottle_0`, or a mod's
  `direction`);
- an unexpected value for a transformed property (`facing=sideways`,
  `rotation=16`, `shape=west_south`).

Every palette entry is checked, including ones no block uses, so a call
never returns half-transformed geometry.

## Errors

| Code                   | When                                                     |
| ---------------------- | -------------------------------------------------------- |
| `E_TRANSFORM_ARGUMENT` | rotation not 90/180/270, mirror not one of the two names |
| `E_UNSUPPORTED_STATE`  | a palette entry is outside the supported subset          |
| `E_COORDINATE_RANGE`   | the new offset would leave the 32-bit range              |
| `E_GEOMETRY`           | the input is malformed (from the existing validator)     |
| `E_TOO_LARGE`          | the input exceeds the volume or palette limits           |

## Limitations

- Not universal block support: modded and unlisted properties reject rather
  than guess.
- Block entities (chest contents, sign text) and entities are not part of the
  geometry, so they are not transformed here.
- Stair shapes, fence connections and similar are transformed as stored. Once
  placed in a world, neighbour updates may reshape them.
- The command line tool only converts files. It does not import into or
  export from a running game.
- Nothing here places blocks, verifies a build, or grants ownership or
  permissions.

## Testing

`tests/schematic-transforms.test.ts` covers hand-calculated fixtures for all
five transforms, the restore laws (4 × 90°, mirror twice, compositions) for
both geometry and every supported state value, unchanged inputs, rejection of
unsupported states, bad arguments, malformed geometry and offset overflow,
and 300 seeded random cases checked against a naive position-map oracle.

`tests/schematic-transforms-cli.test.ts` drives the command line tool on temp
files: every operation from and to both formats against hand-calculated
sizes, offsets, cells and directional states (stairs, logs, chests), with
exported `.schem` files read back by the independent `prismarine-nbt` reader.
It also covers zero-volume geometry at the maximal 65 535 dimensions,
block-entity and biome discards, bad arguments, malformed, oversized and
unsupported inputs, output collisions, hard-link and symbolic-link aliases,
that failures leave existing files unchanged, and the compiled program run on
its own. None of it uses the network, models, paid services or keys.
