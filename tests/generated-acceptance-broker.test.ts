import assert from "node:assert/strict";
import { test } from "node:test";
import {
  mkdtempSync,
  readFileSync,
  writeFileSync,
  existsSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { GeneratedSyntheticAcceptanceProvider } from "../companion/src/generated-acceptance-broker.js";
import {
  createGeneratedAcceptanceRequest,
  readGeneratedAcceptanceRequest,
  createGeneratedAcceptanceResponse,
  GENERATED_ACCEPTANCE_BEHAVIOR,
} from "../companion/src/generated-acceptance.js";
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "123",
  runAttempt: 1,
  scenario: "generated-buffer-owner-confirmation-v1",
};
const context = Object.freeze({
  serverId: "fixture",
  serverEpoch: "epoch",
  projectId: "33333333-3333-4333-8333-333333333333",
  actorId: "11111111-1111-4111-8111-111111111111",
  connectionId: "22222222-2222-4222-8222-222222222222",
  requestId: "request",
  projectRevision: 1,
  authorizationRevision: 1,
});
const provenance = {
  model: "gpt-6-luna",
  sdkVersion: "0.164.0",
  inputTokens: 10,
  cachedInputTokens: 0,
  outputTokens: 20,
};
const proposal = {
  behavior: GENERATED_ACCEPTANCE_BEHAVIOR,
  explanation: "The lamp follows the lever with the requested observed timing.",
};
const raw = {
  name: "Raw model response",
  explanation: "Untested wiring.",
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
    { pos: { x: 3, y: 1, z: 0 }, state: "minecraft:redstone_lamp[lit=false]" },
  ],
};
function fixture(stage: "behavior" | "geometry", scenarioRun = run) {
  const now = Date.now();
  return readGeneratedAcceptanceRequest(
    createGeneratedAcceptanceRequest({
      run: scenarioRun,
      stage,
      context,
      worldId: "world",
      nonce: "b".repeat(64),
      createdAt: now,
      behavior: stage === "behavior" ? null : GENERATED_ACCEPTANCE_BEHAVIOR,
    }),
    scenarioRun,
    now,
  );
}
async function setup(
  action: (
    provider: GeneratedSyntheticAcceptanceProvider,
    directory: string,
  ) => Promise<void>,
  scenarioRun = run,
) {
  const keys = [
      "CI",
      "GITHUB_ACTIONS",
      "MCE_LIVE_ACCEPTANCE",
      "MCE_GENERATED_ACCEPTANCE",
    ],
    before = keys.map((key) => process.env[key]);
  keys.forEach((key) => (process.env[key] = "true"));
  const directory = mkdtempSync(join(tmpdir(), "mce-generated-broker-"));
  try {
    await action(
      new GeneratedSyntheticAcceptanceProvider({ directory, run: scenarioRun }),
      directory,
    );
  } finally {
    rmSync(directory, { recursive: true, force: true });
    keys.forEach((key, index) => {
      if (before[index] === undefined) delete process.env[key];
      else process.env[key] = before[index];
    });
  }
}
for (const scenarioRun of [
  run,
  { ...run, scenario: "structural-buffer-owner-confirmation-v1" },
])
  test(`manual broker keeps two separately receipted stages on the same native basis: ${scenarioRun.scenario}`, async () =>
    setup(async (provider, directory) => {
      const behavior = fixture("behavior", scenarioRun);
      const first = provider.proposeBehavior(
        behavior.input,
        new AbortController().signal,
        behavior.memory,
        context,
      );
      const request = JSON.parse(
        readFileSync(join(directory, "behavior", "request.json"), "utf8"),
      );
      writeFileSync(
        join(directory, "behavior", "response.json"),
        JSON.stringify(
          createGeneratedAcceptanceResponse(request, proposal, provenance),
        ),
      );
      assert.deepEqual(await first, proposal);
      const geometry = fixture("geometry", scenarioRun),
        authority = Object.freeze({ ...context, requestId: "second-request" });
      const second = provider.generateGeometry(
        { ...geometry.input, behavior: GENERATED_ACCEPTANCE_BEHAVIOR },
        new AbortController().signal,
        geometry.memory,
        authority,
      );
      const next = JSON.parse(
        readFileSync(join(directory, "geometry", "request.json"), "utf8"),
      );
      assert.notEqual(next.digest, request.digest);
      writeFileSync(
        join(directory, "geometry", "response.json"),
        JSON.stringify(
          createGeneratedAcceptanceResponse(next, raw, provenance),
        ),
      );
      assert.deepEqual(await second, raw);
      for (const stage of ["behavior", "geometry"])
        assert.equal(
          JSON.parse(
            readFileSync(join(directory, stage, "accepted.json"), "utf8"),
          ).stage,
          stage,
        );
      await assert.rejects(
        provider.generateGeometry(
          { ...geometry.input, behavior: GENERATED_ACCEPTANCE_BEHAVIOR },
          new AbortController().signal,
          geometry.memory,
          authority,
        ),
      );
      assert.ok(existsSync(join(directory, "unexpected-inference")));
    }, scenarioRun));

test("manual broker rejects geometry before a behavior response and untrusted input before publishing a request", async () =>
  setup(async (provider, directory) => {
    const geometry = fixture("geometry");
    await assert.rejects(
      provider.generateGeometry(
        { ...geometry.input, behavior: GENERATED_ACCEPTANCE_BEHAVIOR },
        new AbortController().signal,
        geometry.memory,
        context,
      ),
    );
    assert.ok(!existsSync(join(directory, "geometry", "request.json")));
    const behavior = fixture("behavior");
    await assert.rejects(
      provider.proposeBehavior(
        { ...behavior.input, request: "Read private inventory" },
        new AbortController().signal,
        behavior.memory,
        context,
      ),
    );
    assert.ok(!existsSync(join(directory, "behavior", "request.json")));
  }));
