// Real HTTP gateway and SQLite persistence for the multiplayer proof. Inference
// is unavailable: generated boundary fixtures are deterministic data, never SDK calls.
import { join } from "node:path";
import { writeFileSync } from "node:fs";
import { loadConfig } from "../../dist/companion/src/config.js";
import { MemoryStore } from "../../dist/companion/src/memory.js";
import { createCompanionServer } from "../../dist/companion/src/server.js";

if (process.env.CI !== "true" || !process.argv[2]) {
  throw new Error(
    "This fixture runs only in GitHub CI with a private config directory.",
  );
}
const config = loadConfig(process.argv[2]);
const store = new MemoryStore(join(config.directory, "projects.sqlite"));
const server = createCompanionServer({
  token: config.token,
  store,
  provider: {
    async proposeBehavior(input) {
      if (
        input.request !==
        "CI generated buffer: on after 12 game ticks, off after 16 game ticks."
      )
        throw new Error("Unexpected generated fixture request");
      return {
        behavior: {
          schemaVersion: 1,
          function: "buffer",
          risingGameTicks: 12,
          fallingGameTicks: 16,
        },
        explanation: "Synthetic native confirmation fixture; no inference.",
      };
    },
    async generateGeometry(input) {
      if (
        input.request !==
          "CI generated buffer: on after 12 game ticks, off after 16 game ticks." ||
        input.behavior.risingGameTicks !== 12 ||
        input.behavior.fallingGameTicks !== 16
      )
        throw new Error("Original request or confirmed intent changed");
      return {
        name: "Generated native buffer",
        explanation: "Two serial repeaters; physically untested preview.",
        input: { x: 0, y: 1, z: 0 },
        output: { x: 3, y: 1, z: 0 },
        blocks: [
          ...[0, 1, 2, 3].map((x) => ({
            pos: { x, y: 0, z: 0 },
            state: "minecraft:stone",
          })),
          {
            pos: { x: 0, y: 1, z: 0 },
            state: "minecraft:lever[face=floor,facing=east,powered=false]",
          },
          ...[1, 2].map((x) => ({
            pos: { x, y: 1, z: 0 },
            state:
              "minecraft:repeater[delay=3,facing=west,locked=false,powered=false]",
          })),
          {
            pos: { x: 3, y: 1, z: 0 },
            state: "minecraft:redstone_lamp[lit=false]",
          },
        ],
      };
    },
    async plan() {
      writeFileSync(
        join(config.directory, "inference-attempted"),
        "unexpected provider call",
      );
      throw new Error("Inference is disabled in public CI");
    },
  },
});
server.listen(config.port, "127.0.0.1", () =>
  process.stdout.write("Companion ready\n"),
);
server.on("error", () => {
  process.stderr.write("CI companion could not start\n");
  process.exitCode = 1;
});
const close = () => {
  server.close(() => store.close());
  server.closeAllConnections();
};
process.once("SIGTERM", close);
process.once("SIGINT", close);
