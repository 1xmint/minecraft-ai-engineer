// One synthetic CI provider; no SDK or sign-in is invoked in this process.
import { join } from "node:path";
import { loadConfig } from "../../dist/companion/src/config.js";
import { MemoryStore } from "../../dist/companion/src/memory.js";
import { createCompanionServer } from "../../dist/companion/src/server.js";
import { SyntheticAcceptanceProvider } from "../../dist/companion/src/acceptance-broker.js";
import { GeneratedSyntheticAcceptanceProvider } from "../../dist/companion/src/generated-acceptance-broker.js";
if (
  process.env.CI !== "true" ||
  process.env.GITHUB_ACTIONS !== "true" ||
  process.env.MCE_LIVE_ACCEPTANCE !== "true" ||
  process.env.GITHUB_REPOSITORY !== "1xmint/minecraft-ai-engineer" ||
  !process.argv[2] ||
  !process.env.MCE_ACCEPTANCE_EXCHANGE_DIR
)
  throw new Error("Manual synthetic CI acceptance only");
const config = loadConfig(process.argv[2]);
const store = new MemoryStore(join(config.directory, "projects.sqlite"));
const generated = process.env.MCE_GENERATED_ACCEPTANCE === "true";
const Provider = generated
  ? GeneratedSyntheticAcceptanceProvider
  : SyntheticAcceptanceProvider;
const provider = new Provider({
  directory: process.env.MCE_ACCEPTANCE_EXCHANGE_DIR,
  run: {
    repository: process.env.GITHUB_REPOSITORY,
    workflow: ".github/workflows/live-acceptance.yml",
    sourceSha: process.env.GITHUB_SHA,
    runId: process.env.GITHUB_RUN_ID,
    runAttempt: Number(process.env.GITHUB_RUN_ATTEMPT),
    scenario:
      process.env.MCE_STRUCTURAL_ACCEPTANCE === "true"
        ? "structural-buffer-owner-confirmation-v1"
        : generated
          ? "generated-buffer-owner-confirmation-v1"
          : "signal-lamp-client-handoff-v1",
  },
});
const server = createCompanionServer({
  token: config.token,
  store,
  provider,
  planTimeoutMs: 900_000,
});
server.listen(config.port, "127.0.0.1", () =>
  process.stdout.write("Companion ready\n"),
);
server.on("error", () => {
  process.stderr.write("Acceptance companion could not start\n");
  process.exitCode = 1;
});
const close = () => {
  server.close(() => store.close());
  server.closeAllConnections();
};
process.once("SIGTERM", close);
process.once("SIGINT", close);
