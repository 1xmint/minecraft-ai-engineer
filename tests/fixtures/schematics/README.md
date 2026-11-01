# Third-party schematic fixtures

These files were written by other programs. Tests read them with this
project's importer and, independently, with `prismarine-nbt`, so the importer
is not only checked against its own encoder. Tests check each SHA-256 first.

| File                              | Source                                                                                                                    | Commit                                     | SHA-256                                                            |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------ |
| `worldedit-fabric-7.3.schem`      | [hollow-cube/schem](https://github.com/hollow-cube/schem) `src/test/resources/regression/1_20_1_read_fail.schem`          | `dae7bb866abb1bfdfc122cb8856d92f73ace6e4d` | `460016176cf662e5078e1a45b73902455063c857a088bbcfe38c12b034cad136` |
| `hollowcube-2x2-chunk-vert.schem` | [hollow-cube/schem](https://github.com/hollow-cube/schem) `src/test/resources/2x2_chunk_vert.schem`                       | `9f235a0f14926067217c173ca802fcca58e6b97e` | `66849d2d8a83bc0d3ac7bc45b49f80e2f6d8a17f68a1d3bbc5a1dd06d6cb9922` |
| `schematic4j-sponge-v3.schem`     | [sandrohc/schematic4j](https://github.com/sandrohc/schematic4j) `src/test/resources/schematics/sponge/v3/sponge-v3.schem` | `a1e20298b67ad73d4ee9c9e04e973f6875e7052e` | `8098f37db5dd3cba385d16db36e9da2240cfbf2545a18bd6c02a34d14cf408d6` |

- `worldedit-fabric-7.3.schem` is a real WorldEdit 7.3.0-beta-02 Fabric export
  (DataVersion 3578) with six sign block entities.
- `hollowcube-2x2-chunk-vert.schem` is a 32x384x32 world slice (DataVersion
  in the file) with twelve chest block entities that carry loot-table data.
- `schematic4j-sponge-v3.schem` uses a flat root compound named `Schematic`
  and includes an entity (a creeper) and a campfire block entity.

## Licenses

hollow-cube/schem, MIT License, Copyright (c) 2022 Hollow Cube Contibutors.
sandrohc/schematic4j, MIT License, Copyright (c) 2021 Sandro Marques.

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
