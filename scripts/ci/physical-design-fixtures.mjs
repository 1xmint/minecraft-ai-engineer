import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { exportSpongeSchematic } from "../../dist/companion/src/schematics/index.js";
if (process.env.CI !== "true" || !process.argv[2])
  throw new Error(
    "Create these disposable gameplay fixtures only on GitHub CI.",
  );
const geometry = (palette, blocks, x) => ({
  format: "minecraft-ai-engineer.schematic-geometry",
  formatVersion: 1,
  dataVersion: 3578,
  size: { x, y: 2, z: 1 },
  offset: { x: 0, y: 0, z: 0 },
  palette,
  blocks,
});
const stone = { name: "minecraft:stone", properties: {} };
const air = { name: "minecraft:air", properties: {} };
// Five blocks the companion can place by hand.
const slab = geometry(
  [
    stone,
    air,
    {
      name: "minecraft:lever",
      properties: { face: "floor", facing: "east", powered: "false" },
    },
    { name: "minecraft:redstone_lamp", properties: { lit: "false" } },
  ],
  [0, 0, 0, 2, 1, 3],
  3,
);
// A safe design with one block the companion has no placement rule for.
const relay = geometry(
  [
    stone,
    air,
    {
      name: "minecraft:repeater",
      properties: {
        delay: "1",
        facing: "west",
        locked: "false",
        powered: "false",
      },
    },
  ],
  [0, 2],
  1,
);
writeFileSync(
  join(process.argv[2], "slab.schem"),
  exportSpongeSchematic(slab),
  { flag: "wx" },
);
writeFileSync(
  join(process.argv[2], "relay.schem"),
  exportSpongeSchematic(relay),
  { flag: "wx" },
);
