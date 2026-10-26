import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { join } from "node:path";
import { loadConfig } from "./config.js";
import { preparePlannerLauncher } from "./launcher.js";
import { MemoryStore } from "./memory.js";
import {
  CodexPlanningProvider,
  plannerEnvironment,
  DEFAULT_PLANNER_MODEL,
} from "./provider.js";
import { createCompanionServer } from "./server.js";

const execFileAsync = promisify(execFile);
async function main(): Promise<void> {
  const command = process.argv[2] ?? "start";
  if (!["start", "doctor"].includes(command))
    throw new Error("Usage: npm start, or npm run doctor [-- --probe]");
  const config = loadConfig();
  const model = process.env.MCE_MODEL;
  if (model && !/^[a-zA-Z0-9_.-]{1,80}$/u.test(model))
    throw new Error("Invalid MCE_MODEL.");
  const provider = new CodexPlanningProvider({
    configDir: config.directory,
    ...(model ? { model } : {}),
  });
  if (command === "doctor") {
    console.log(
      `Node: ${process.version}; configuration: ${config.directory}; protocol: 1`,
    );
    const launcher = await preparePlannerLauncher(config.directory);
    const env = plannerEnvironment(launcher.binary, config.directory);
    const version = await execFileAsync(launcher.binary, ["--version"], {
      env,
      windowsHide: true,
      timeout: 10_000,
    });
    console.log(`Pinned runtime: ${version.stdout.trim()}`);
    const login = await execFileAsync(launcher.binary, ["login", "status"], {
      env,
      windowsHide: true,
      timeout: 10_000,
    });
    if (!/ChatGPT/iu.test(`${login.stdout} ${login.stderr}`))
      throw new Error(
        "Sign in to Codex with ChatGPT. API key authentication is not supported by this project.",
      );
    console.log(
      "Authentication: ChatGPT subscription. No API keys are forwarded. Launcher ignores inherited config and rules.",
    );
    console.log(
      `Model: ${model ?? DEFAULT_PLANNER_MODEL}; actual account availability requires --probe.`,
    );
    if (process.argv.includes("--probe")) {
      const result = await provider.plan(
        {
          request:
            "Please design a small lamp controlled by a lever, with a repeater delay.",
          worldId: "doctor-probe",
          dimension: "minecraft:overworld",
          anchor: { x: 0, y: 64, z: 0 },
          rotation: 0,
        },
        new AbortController().signal,
      );
      if (result.template !== "signal-lamp")
        throw new Error(
          "Planner did not select the expected reference template.",
        );
      console.log(
        `Live structured reasoning: ${JSON.stringify(result)}; usage: ${JSON.stringify(provider.lastUsage)}`,
      );
    } else
      console.log(
        "No model request was made. Add --probe for one bounded subscription request.",
      );
    return;
  }
  const store = new MemoryStore(
    join(config.directory, "projects.sqlite"),
    join(config.directory, "blueprints"),
  );
  const server = createCompanionServer({
    token: config.token,
    store,
    provider,
  });
  server.on("error", () => {
    console.error(
      "The companion could not bind its loopback port. Close another instance and retry.",
    );
    store.close();
    process.exitCode = 1;
  });
  server.listen(config.port, "127.0.0.1", () =>
    console.log(
      `Minecraft AI Engineer is ready on loopback port ${config.port}. Open a single-player world and use /engineer help. Config: ${config.directory}`,
    ),
  );
  const stop = () => {
    server.close(() => store.close());
    server.closeAllConnections();
  };
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
}
main().catch(() => {
  console.error(
    "Companion setup or Codex access failed. Check the documented prerequisites, ChatGPT sign-in, account model access and usage limits. No API fallback or automatic retry was attempted.",
  );
  process.exitCode = 1;
});
