// Real HTTP gateway and SQLite persistence for the multiplayer proof. Inference
// is unavailable: generated boundary fixtures are deterministic data, never SDK calls.
import { join } from "node:path";
import { isDeepStrictEqual } from "node:util";
import { writeFileSync, readFileSync } from "node:fs";
import { loadConfig } from "../../dist/companion/src/config.js";
import { MemoryStore } from "../../dist/companion/src/memory.js";
import { createCompanionServer } from "../../dist/companion/src/server.js";

if (process.env.CI !== "true" || !process.argv[2]) {
  throw new Error(
    "This fixture runs only in GitHub CI with a private config directory.",
  );
}
// Recorded raw signed-in reply; replay is deterministic and never invokes inference.
const generatedFixture = JSON.parse(
  readFileSync(
    new URL("../../tests/fixtures/live-generated-buffer.json", import.meta.url),
    "utf8",
  ),
);
// Recorded successful structural reply; replay never invokes a model.
const structuralFixture = JSON.parse(
  readFileSync(
    new URL(
      "../../tests/fixtures/live-structural-buffer.json",
      import.meta.url,
    ),
    "utf8",
  ),
);
// Hand-authored Boolean fixture; this branch never invokes signed-in inference.
const truthRequest =
  "CI generated AND: named inputs a and b; settle within 20 game ticks.";
const truthFixture = JSON.parse(
  readFileSync(
    new URL(
      "../../mod/src/main/resources/fixtures/generated-and.json",
      import.meta.url,
    ),
    "utf8",
  ),
);
const config = loadConfig(process.argv[2]);
const store = new MemoryStore(join(config.directory, "projects.sqlite"));
const server = createCompanionServer({
  token: config.token,
  store,
  // Fixed synthetic stages can burst across several projects in this suite.
  // This provider cannot invoke the SDK; production retains its default4/minute.
  actorPlansPerMinute: 12,
  provider: {
    async proposeBehavior(input) {
      if (input.request === truthRequest)
        return {
          behavior: structuredClone(truthFixture.behavior),
          explanation:
            "Confirm complete AND behavior and20-game-tick settling before raw geometry.",
        };
      if (
        ![
          "CI generated buffer: on after 12 game ticks, off after 16 game ticks.",
          "CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.",
        ].includes(input.request)
      )
        throw new Error("Unexpected generated fixture request");
      return structuredClone(generatedFixture.proposal);
    },
    async generateGeometry(input) {
      if (input.request === truthRequest) {
        if (!isDeepStrictEqual(input.behavior, truthFixture.behavior))
          throw new Error("Confirmed Boolean intent changed");
        return {
          name: truthFixture.name,
          explanation: truthFixture.explanation,
          blocks: truthFixture.blocks.map((b) => ({
            pos: { ...b.pos },
            state: b.state,
          })),
          inputs: truthFixture.ports
            .filter((p) => p.kind === "input")
            .map((p) => ({ name: p.name, pos: { ...p.pos } })),
          output: {
            ...truthFixture.ports.find((p) => p.kind === "output").pos,
          },
        };
      }

      if (
        ![
          "CI generated buffer: on after 12 game ticks, off after 16 game ticks.",
          "CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.",
        ].includes(input.request) ||
        input.behavior.risingGameTicks !== 12 ||
        input.behavior.fallingGameTicks !== 16
      )
        throw new Error("Original request or confirmed intent changed");
      if (input.request.startsWith("CI structural buffer:"))
        return structuredClone(structuralFixture.geometry);
      return structuredClone(generatedFixture.geometry);
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
