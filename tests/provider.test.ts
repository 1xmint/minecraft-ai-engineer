import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtempSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { MemoryStore } from "../companion/src/memory.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import {
  PLANNER_CONFIG,
  plannerEnvironment,
  validatePlan,
} from "../companion/src/provider.js";
import { loadConfig } from "../companion/src/config.js";
import {
  validateCheckpoint,
  validateObservation,
  type DesignRequest,
} from "../companion/src/types.js";

const input: DesignRequest = {
  request: "Build a delayed lamp",
  worldId: "world-a",
  dimension: "minecraft:overworld",
  anchor: { x: 1, y: 64, z: 1 },
  rotation: 0,
};

test("planner does not inherit API credentials, proxy endpoints, node hooks or companion token", () => {
  const env = plannerEnvironment("/pinned/codex", "/empty", {
    HOME: "/person",
    CODEX_HOME: "/person/.codex",
    OPENAI_API_KEY: "do-not-forward",
    CODEX_API_KEY: "do-not-forward",
    NODE_OPTIONS: "--import malicious",
    HTTP_PROXY: "secret-proxy",
    MCE_TOKEN: "private",
    GITHUB_TOKEN: "private",
  });
  assert.equal(env.HOME, "/person");
  assert.equal(env.CODEX_HOME, "/person/.codex");
  assert.equal(env.MCE_CODEX_BINARY, "/pinned/codex");
  assert.equal(env.MCE_PLANNER_DIR, "/empty");
  for (const key of [
    "OPENAI_API_KEY",
    "CODEX_API_KEY",
    "NODE_OPTIONS",
    "HTTP_PROXY",
    "MCE_TOKEN",
    "GITHUB_TOKEN",
  ])
    assert.equal(env[key], undefined);
  assert.equal(PLANNER_CONFIG.forced_login_method, "chatgpt");
  assert.equal(PLANNER_CONFIG.model_provider, "openai");
  assert.equal(PLANNER_CONFIG.web_search, "disabled");
  const features = PLANNER_CONFIG.features as Record<string, unknown>;
  for (const feature of [
    "shell_tool",
    "unified_exec",
    "apps",
    "plugins",
    "hooks",
    "codex_hooks",
    "plugin_hooks",
    "apply_patch_freeform",
    "view_image",
    "multi_agent",
    "js_repl",
    "browser_use",
  ])
    assert.equal(features[feature], false);
});

test("structured planning cannot smuggle a block list, runnable script or verification into a template decision", () => {
  assert.throws(() =>
    validatePlan({ template: "arbitrary-shell", explanation: "Run this" }),
  );
  assert.throws(() =>
    validatePlan({ template: "signal-lamp", explanation: "" }),
  );
  assert.throws(() =>
    validatePlan({ template: "signal-lamp", explanation: "x".repeat(4001) }),
  );
  const decision = validatePlan({
    template: "signal-lamp",
    explanation: "Explain the circuit",
    command: "delete-world",
    blocks: ["arbitrary"],
    verification: "passed",
  });
  assert.deepEqual(Object.keys(decision).sort(), ["explanation", "template"]);
  const blueprint = makeReferenceBlueprint(input, decision.explanation);
  assert.equal(blueprint.verification, "untested");
  assert.equal(blueprint.blocks.length, 6);
});

test("durable designs and checkpoints survive service restart with world boundaries intact", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-memory-"));
  try {
    const db = join(dir, "memory.sqlite"),
      exports = join(dir, "blueprints");
    let store = new MemoryStore(db, exports);
    const first = store.saveDesign(input, makeReferenceBlueprint(input));
    store.checkpoint({
      worldId: input.worldId,
      dimension: input.dimension,
      projectId: first.projectId,
      taskId: "task-1",
      status: "PAUSED",
      detail: "Stopped after two placements",
      observed: { placed: 2 },
    });
    store.close();
    store = new MemoryStore(db, exports);
    assert.equal(store.projects(input.worldId)[0]?.status, "PAUSED");
    assert.equal(store.blueprint(first.projectId)?.verification, "untested");
    assert.match(
      store.context(first.projectId, input.worldId, input.dimension),
      /Stopped after two placements/u,
    );
    const second = store.saveDesign(
      { ...input, projectId: first.projectId },
      makeReferenceBlueprint({ ...input, projectId: first.projectId }),
    );
    assert.equal(second.revision, 2);
    assert.equal(store.blueprint(first.projectId, 1)?.id, first.id);
    assert.equal(
      JSON.parse(
        readFileSync(join(exports, `${first.projectId}-r1.json`), "utf8"),
      ).id,
      first.id,
    );
    assert.throws(() =>
      store.checkpoint({
        worldId: "world-b",
        dimension: input.dimension,
        projectId: first.projectId,
        taskId: "task-1",
        status: "EXECUTING",
        detail: "cross-world attempt",
      }),
    );
    assert.throws(() =>
      store.context(first.projectId, input.worldId, "minecraft:the_nether"),
    );
    assert.deepEqual(store.projects("world-b"), []);
    const another = store.saveDesign(input, makeReferenceBlueprint(input));
    assert.throws(() =>
      store.checkpoint({
        worldId: input.worldId,
        dimension: input.dimension,
        projectId: another.projectId,
        taskId: "task-1",
        status: "EXECUTING",
        detail: "steal task",
      }),
    );
    assert.equal(
      store
        .audit(first.projectId)
        .filter((entry) => entry.kind === "checkpoint").length,
      1,
    );
    store.close();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("observations report exact differences while intended and verified state stay separate", () => {
  const store = new MemoryStore(":memory:");
  try {
    const region = { min: { x: 0, y: 64, z: 0 }, max: { x: 0, y: 64, z: 0 } };
    const first = validateObservation({
      worldId: "world-a",
      dimension: "minecraft:overworld",
      region,
      blocks: [
        {
          pos: region.min,
          state:
            "minecraft:repeater[delay=1,facing=west,locked=false,powered=false]",
        },
      ],
      tick: 100,
    });
    assert.deepEqual(store.recordObservation(first), {
      changed: 0,
      previousTick: null,
    });
    assert.deepEqual(
      store.recordObservation({
        ...first,
        blocks: [{ pos: region.min, state: "minecraft:air" }],
        tick: 102,
      }),
      { changed: 1, previousTick: 100 },
    );
    assert.throws(() =>
      validateObservation({
        ...first,
        blocks: [...first.blocks, ...first.blocks],
      }),
    );
    assert.throws(() =>
      validateObservation({
        ...first,
        blocks: [{ pos: { x: 1, y: 64, z: 0 }, state: "minecraft:air" }],
      }),
    );
    assert.throws(() =>
      validateObservation({
        ...first,
        region: { min: { x: 0, y: 0, z: 0 }, max: { x: 99, y: 99, z: 99 } },
      }),
    );
    assert.throws(() =>
      validateCheckpoint({
        worldId: "world-a",
        dimension: "minecraft:overworld",
        projectId: "p",
        taskId: "t",
        status: "RUN_ANYTHING",
        detail: "invalid",
      }),
    );
  } finally {
    store.close();
  }
});

test("private local companion token is stable across restarts and malformed config fails closed", () => {
  const dir = mkdtempSync(join(tmpdir(), "mce-config-"));
  try {
    const first = loadConfig(dir),
      second = loadConfig(dir);
    assert.equal(first.token, second.token);
    assert.match(first.token, /^[a-f0-9]{64}$/u);
    assert.equal(first.port, 43127);
    writeFileSync(join(dir, "companion.json"), "{broken-json");
    assert.throws(() => loadConfig(dir));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
