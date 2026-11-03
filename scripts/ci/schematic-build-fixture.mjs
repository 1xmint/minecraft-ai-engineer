import { writeFileSync } from "node:fs";
import { exportSpongeSchematic } from "../../dist/companion/src/schematics/index.js";
if (process.env.CI !== "true" || !process.argv[2])
  throw new Error("Create this disposable gameplay fixture only on GitHub CI.");
const blocks = [
  ...Array(16).fill(0),
  ...Array(16).fill(5),
  1,
  2,
  3,
  ...Array(29).fill(4),
];
const geometry = {
  format: "minecraft-ai-engineer.schematic-geometry",
  formatVersion: 1,
  dataVersion: 3578,
  size: { x: 32, y: 2, z: 1 },
  offset: { x: 0, y: 0, z: 0 },
  palette: [
    { name: "minecraft:cobblestone", properties: {} },
    {
      name: "minecraft:lever",
      properties: { face: "floor", facing: "east", powered: "false" },
    },
    {
      name: "minecraft:repeater",
      properties: {
        delay: "1",
        facing: "west",
        locked: "false",
        powered: "false",
      },
    },
    { name: "minecraft:redstone_lamp", properties: { lit: "false" } },
    { name: "minecraft:air", properties: {} },
    { name: "minecraft:oak_planks", properties: {} },
  ],
  blocks,
};
// Synthetic safe geometry exercises world execution; third-party codec proof uses
// the separate real WorldEdit fixture. This file is not independent codec evidence.
writeFileSync(process.argv[2], exportSpongeSchematic(geometry), { flag: "wx" });
