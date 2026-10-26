// Real HTTP gateway and SQLite persistence for the multiplayer proof. Inference
// is deliberately unavailable: CI never receives a personal login or API key.
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
