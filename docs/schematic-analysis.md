# Schematic analysis and comparison

`companion/src/schematic-analysis/` reads neutral geometry JSON (the format
described in [schematics.md](schematics.md)) and reports block counts and
cell-by-cell differences. It is read-only and standalone. It never writes or
deletes files, never touches a world, and does not use the gateway, storage,
the planner or the mod.

Reports are block counts and differences only. They are not crafting costs,
available inventory, circuit reliability or functional verification, and
they grant no ownership or permissions. Every report repeats this in its
`notice` field.

## Command line

Build once with `npm run build`, then:

```
node dist/companion/src/schematic-analysis/cli.js analyze <in.json>
node dist/companion/src/schematic-analysis/cli.js compare <a.json> <b.json> [--max-details N]
```

- Both commands print one JSON report to stdout. There is no output-file
  option.
- `--max-details` is 0 to 10,000 per detail list (default 100). Counts are
  always exact, whatever the limit.
- Exit codes: `0` success, `1` an input was rejected (the message starts with
  an error code), `2` usage error.

## Analysis report

| Field                           | Meaning                                                                         |
| ------------------------------- | ------------------------------------------------------------------------------- |
| `dataVersion`, `size`, `offset` | Copied from the input                                                           |
| `totalCells`                    | `size.x × size.y × size.z`                                                      |
| `airCells`, `nonAirCells`       | Air is `minecraft:air`, `minecraft:cave_air` and `minecraft:void_air` only      |
| `blockIds`                      | Cells per block id, all states of the id combined                               |
| `blockStates`                   | Cells per complete block state, e.g. `minecraft:repeater[delay=2,facing=north]` |

Counts come from the blocks, not the palette, so a palette entry no block uses
is not listed. Lists are sorted by count, highest first, with ties in plain
character order. An empty geometry (any size of 0) reports zeros and empty
lists.

## Comparison report

Both inputs are placed in world space with their offsets applied, and every
cell of either volume falls into exactly one bucket:

| Bucket      | Meaning                                                |
| ----------- | ------------------------------------------------------ |
| `identical` | Inside both volumes with the same complete block state |
| `changed`   | Inside both volumes with different states              |
| `onlyInA`   | Inside A's volume and outside B's                      |
| `onlyInB`   | Inside B's volume and outside A's                      |

- **Explicit air is a block, not a gap.** A cell that one input stores as
  `minecraft:air` and the other stores as stone is `changed`. A cell outside
  the other input's volume is `onlyInA` or `onlyInB`, and its detail entry has
  no state for the missing side. `air` versus `cave_air` counts as changed.
  `onlyInA` and `onlyInB` are also split into `air` and `nonAir`, so air
  padding stays visible.
- **Palette order does not matter.** States are compared by their canonical
  text, so the same blocks under a reordered palette, or with properties
  written in another order, compare as identical.
- **DataVersions must match.** Different DataVersions are rejected with
  `E_DATA_VERSION_MISMATCH`. No version conversion is attempted.
- `overlap` is the world-space box both volumes share, or `null` when they do
  not overlap or one is empty.
- Detail lists (`changed`, `onlyInA`, `onlyInB`) each hold at most
  `maxDetails` entries, with `listed` and `truncated`. Entries follow each
  input's storage order (x fastest, then z, then y), with A's walk producing
  `changed` and `onlyInA` and B's walk producing `onlyInB`.
- Work is proportional to the two volumes. The empty space between distant
  offsets is never visited, and world coordinates may go past the 32-bit
  range (an offset of 2147483647 plus a position) without wrapping.

## Limits and errors

Files are read with the same bounds as the schematic codec: 96 MiB of JSON,
8,388,608 cells and 65,536 palette entries by default. The file size is checked
before reading and again while reading, so an oversized file is never loaded
whole. Library callers can lower these through `readGeometryFile(path,
{ maxBytes, limits })`, `analyzeGeometry(value, limits)` and
`compareGeometries(a, b, { maxDetails, limits })`. Every input is validated
first, so raw objects are canonicalised before counting.

| Error                     | Cause                                                  |
| ------------------------- | ------------------------------------------------------ |
| `E_INPUT_FILE`            | Missing, unreadable or not a regular file              |
| `E_INPUT_TOO_LARGE`       | File larger than the byte limit                        |
| `E_JSON`                  | Not valid JSON                                         |
| `E_GEOMETRY`              | Invalid geometry, including unknown fields             |
| `E_TOO_LARGE`             | Volume or palette over the limit                       |
| `E_DATA_VERSION_MISMATCH` | The two inputs have different DataVersions             |
| `E_DETAIL_LIMIT`          | `maxDetails` not an integer from 0 to 10,000 (library) |

## Limitations

- Neutral geometry JSON only. Convert `.schem` files first with the schematics
  CLI.
- Block ids are compared as text. Nothing checks them against a Minecraft
  version, and modded air blocks count as blocks.
- No rotation, mirroring or alignment search: volumes are compared exactly
  where their offsets put them.
- Block entities, entities and biomes are not part of the geometry, so sign
  text or container contents never show up as differences.

## How it is tested

Tests run on GitHub CI. `tests/schematic-analysis.test.ts` checks
hand-calculated examples (asymmetric sizes, negative offsets, exact detail
positions), palette and property reordering, air versus outside-volume,
volumes at the int32 extremes, DataVersion mismatch, truncation, empty
geometry, and 400 seeded random comparisons against a naive position-map
oracle. `tests/schematic-analysis-cli.test.ts` covers the command line,
malformed input, configured limits, and that no file in the working folder
changes.
