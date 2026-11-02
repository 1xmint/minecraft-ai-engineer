# Schematic transforms

`companion/src/schematic-transforms/` turns or mirrors neutral schematic
geometry (the `SchematicGeometry` produced by `companion/src/schematics/`).
It is a standalone, read-only library: nothing here reads files, places
blocks, talks to the mod, or grants anything.

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
- Library only: there is no CLI command and nothing writes files.
- Nothing here places blocks, verifies a build, or grants ownership or
  permissions.

## Testing

`tests/schematic-transforms.test.ts` covers hand-calculated fixtures for all
five transforms, the restore laws (4 × 90°, mirror twice, compositions) for
both geometry and every supported state value, unchanged inputs, rejection of
unsupported states, bad arguments, malformed geometry and offset overflow,
and 300 seeded random cases checked against a naive position-map oracle. It
uses no network, models, paid services or keys.
