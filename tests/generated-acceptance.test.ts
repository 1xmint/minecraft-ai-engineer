import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import type { Blueprint } from "../companion/src/types.js";
import {
  createGeneratedAcceptanceRequest,
  readGeneratedAcceptanceRequest,
  createGeneratedAcceptanceResponse,
  readGeneratedAcceptanceResponse,
  GENERATED_ACCEPTANCE_REQUEST,
  BOOLEAN_ACCEPTANCE_SCENARIO,
  BOOLEAN_ACCEPTANCE_REQUEST,
  BOOLEAN_ACCEPTANCE_BEHAVIOR,
  GENERATED_ACCEPTANCE_BEHAVIOR,
} from "../companion/src/generated-acceptance.js";
const now = 1_800_000_000_000;
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "123456789",
  runAttempt: 1,
  scenario: "generated-buffer-owner-confirmation-v1",
};
const context = {
  serverId: "synthetic-server",
  serverEpoch: "synthetic-epoch",
  projectId: "33333333-3333-4333-8333-333333333333",
  actorId: "11111111-1111-4111-8111-111111111111",
  connectionId: "22222222-2222-4222-8222-222222222222",
  requestId: "synthetic-request",
  projectRevision: 1,
  authorizationRevision: 1,
};
const request = (stage: "behavior" | "geometry") =>
  createGeneratedAcceptanceRequest({
    run,
    stage,
    context,
    worldId: "synthetic-world",
    nonce: "b".repeat(64),
    createdAt: now,
    behavior: stage === "behavior" ? null : GENERATED_ACCEPTANCE_BEHAVIOR,
  });
const provenance = {
  model: "gpt-6-luna",
  sdkVersion: "0.164.0",
  inputTokens: 30,
  cachedInputTokens: 0,
  outputTokens: 20,
};
const proposal = {
  behavior: {
    schemaVersion: 1,
    function: "buffer",
    risingGameTicks: 12,
    fallingGameTicks: 16,
  },
  explanation: "Accept observable buffer timing before geometry.",
};
const geometry = {
  name: "Raw model buffer",
  explanation: "Untested serial wiring.",
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
test("generated acceptance reconstructs bounded native inputs separately for behavior and geometry", () => {
  for (const stage of ["behavior", "geometry"] as const) {
    const envelope = request(stage),
      read = readGeneratedAcceptanceRequest(
        JSON.parse(JSON.stringify(envelope)),
        run,
        now + 1,
      );
    assert.equal(read.stage, stage);
    assert.equal(read.context.connectionId, context.connectionId);
    assert.equal(read.input.request, GENERATED_ACCEPTANCE_REQUEST);
    assert.deepEqual(read.input.anchor, { x: 200, y: 100, z: 0 });
    assert.deepEqual(read.input.maxSize, { x: 4, y: 2, z: 1 });
    assert.equal(read.input.projectId, context.projectId);
    assert.equal(
      read.memory,
      JSON.stringify({
        project: { name: "Engineering project", projectRevision: 1 },
        tasks: [],
      }),
    );
    if (stage === "geometry")
      assert.deepEqual(read.behavior, GENERATED_ACCEPTANCE_BEHAVIOR);
    else assert.equal(read.behavior, null);
    const result = stage === "behavior" ? proposal : geometry,
      response = createGeneratedAcceptanceResponse(
        envelope,
        result,
        provenance,
      );
    assert.deepEqual(
      readGeneratedAcceptanceResponse(response, envelope, now + 2).result,
      result,
    );
  }
});
test("generated exchange rejects changed stages, intent, frame, expiry and result claims", () => {
  const behavior = request("behavior"),
    generated = request("geometry"),
    response = createGeneratedAcceptanceResponse(
      behavior,
      proposal,
      provenance,
    );
  assert.throws(() =>
    readGeneratedAcceptanceResponse(response, generated, now + 1),
  );
  for (const changed of [
    { ...behavior, stage: "geometry" },
    {
      ...behavior,
      context: {
        ...context,
        connectionId: "44444444-4444-4444-8444-444444444444",
      },
    },
    { ...behavior, extra: "command" },
    {
      ...generated,
      behavior: { ...GENERATED_ACCEPTANCE_BEHAVIOR, risingGameTicks: 10 },
    },
  ])
    assert.throws(() => readGeneratedAcceptanceRequest(changed, run, now + 1));
  assert.throws(() =>
    readGeneratedAcceptanceRequest(
      behavior,
      { ...run, sourceSha: "c".repeat(40) },
      now + 1,
    ),
  );
  assert.throws(() => readGeneratedAcceptanceRequest(behavior, run, now - 1));
  assert.throws(() =>
    readGeneratedAcceptanceRequest(behavior, run, behavior.expiresAt),
  );
  assert.throws(() =>
    createGeneratedAcceptanceRequest({
      run,
      stage: "geometry",
      context: { ...context, connectionId: undefined },
      worldId: "synthetic-world",
      nonce: "b".repeat(64),
      createdAt: now,
      behavior: GENERATED_ACCEPTANCE_BEHAVIOR,
    }),
  );
  assert.throws(() =>
    createGeneratedAcceptanceResponse(
      generated,
      { ...geometry, verification: "passed" },
      provenance,
    ),
  );
  assert.throws(() =>
    createGeneratedAcceptanceResponse(
      generated,
      { ...geometry, blocks: geometry.blocks.filter((b) => b.pos.y !== 0) },
      provenance,
    ),
  );
  assert.throws(() =>
    readGeneratedAcceptanceResponse(
      {
        ...response,
        result: {
          ...proposal,
          behavior: { ...proposal.behavior, risingGameTicks: 10 },
        },
      },
      behavior,
      now + 1,
    ),
  );
});

test("structural acceptance reconstructs only the fixed existing-circuit revision scenario", () => {
  const structuralRun = {
    ...run,
    scenario: "structural-buffer-owner-confirmation-v1",
  };
  const envelope = createGeneratedAcceptanceRequest({
    run: structuralRun,
    stage: "geometry",
    context: { ...context, projectRevision: 2 },
    worldId: "synthetic-world",
    nonce: "c".repeat(64),
    createdAt: now,
    behavior: GENERATED_ACCEPTANCE_BEHAVIOR,
  });
  const read = readGeneratedAcceptanceRequest(envelope, structuralRun, now + 1);
  assert.equal(read.run.scenario, structuralRun.scenario);
  assert.deepEqual(read.input.maxSize, { x: 5, y: 2, z: 1 });
  assert.match(read.input.request, /three serial two-tick repeaters/);
  assert.deepEqual(JSON.parse(read.memory), {
    project: {
      name: "Synthetic serial buffer before revision",
      projectRevision: 2,
    },
    tasks: [
      {
        id: context.projectId,
        status: "COMPLETED",
        detail:
          "Actual Minecraft buffer test passed: stable off/on/off, rising 12 and falling 16 game ticks",
      },
    ],
  });
  assert.throws(
    () => readGeneratedAcceptanceRequest(envelope, run, now + 1),
    /identity changed/,
  );
  const wider = {
    ...geometry,
    output: { x: 4, y: 1, z: 0 },
    blocks: [
      ...Array.from({ length: 5 }, (_, x) => ({
        pos: { x, y: 0, z: 0 },
        state: "minecraft:stone",
      })),
      geometry.blocks[4]!,
      ...Array.from({ length: 3 }, (_, n) => ({
        pos: { x: n + 1, y: 1, z: 0 },
        state:
          "minecraft:repeater[delay=2,facing=west,locked=false,powered=false]",
      })),
      {
        pos: { x: 4, y: 1, z: 0 },
        state: "minecraft:redstone_lamp[lit=false]",
      },
    ],
  };
  const response = createGeneratedAcceptanceResponse(
    envelope,
    wider,
    provenance,
  );
  assert.deepEqual(
    readGeneratedAcceptanceResponse(response, envelope, now + 1).result,
    wider,
  );
  assert.throws(
    () =>
      createGeneratedAcceptanceResponse(request("geometry"), wider, provenance),
    /fit|bound|space|size|dimension/i,
  );
});

test("Boolean acceptance reconstructs one fixed AND intent and its own bounded workspace", () => {
  const booleanRun = { ...run, scenario: BOOLEAN_ACCEPTANCE_SCENARIO };
  const envelope = createGeneratedAcceptanceRequest({
    run: booleanRun,
    stage: "geometry",
    context,
    worldId: "synthetic-world",
    nonce: "d".repeat(64),
    createdAt: now,
    behavior: BOOLEAN_ACCEPTANCE_BEHAVIOR,
  });
  const read = readGeneratedAcceptanceRequest(envelope, booleanRun, now + 1);
  assert.equal(read.input.request, BOOLEAN_ACCEPTANCE_REQUEST);
  assert.equal(read.input.rotation, 0);
  assert.deepEqual(read.input.anchor, { x: 400, y: 100, z: 0 });
  assert.deepEqual(read.input.maxSize, { x: 6, y: 3, z: 4 });
  assert.deepEqual(read.behavior, BOOLEAN_ACCEPTANCE_BEHAVIOR);
  assert.deepEqual(JSON.parse(read.memory), {
    project: { name: "Engineering project", projectRevision: 1 },
    tasks: [],
  });
  assert.throws(() => readGeneratedAcceptanceRequest(envelope, run, now + 1));
  for (const behavior of [
    GENERATED_ACCEPTANCE_BEHAVIOR,
    { ...BOOLEAN_ACCEPTANCE_BEHAVIOR, outputs: [false, true, true, true] },
    { ...BOOLEAN_ACCEPTANCE_BEHAVIOR, settleGameTicks: 21 },
  ])
    assert.throws(() =>
      createGeneratedAcceptanceRequest({
        run: booleanRun,
        stage: "geometry",
        context,
        worldId: "synthetic-world",
        nonce: "d".repeat(64),
        createdAt: now,
        behavior,
      }),
    );
  const fixture = JSON.parse(
    readFileSync(
      new URL(
        "../../mod/src/main/resources/fixtures/generated-and.json",
        import.meta.url,
      ),
      "utf8",
    ),
  ) as Blueprint;
  const raw = {
    name: fixture.name,
    explanation: fixture.explanation,
    blocks: fixture.blocks.map((b) => ({ pos: b.pos, state: b.state })),
    inputs: fixture.ports
      .filter((p) => p.kind === "input")
      .map((p) => ({ name: p.name, pos: p.pos })),
    output: fixture.ports.find((p) => p.kind === "output")!.pos,
  };
  const reply = createGeneratedAcceptanceResponse(envelope, raw, provenance);
  assert.deepEqual(
    readGeneratedAcceptanceResponse(reply, envelope, now + 1).result,
    raw,
  );
  assert.throws(() =>
    createGeneratedAcceptanceResponse(request("geometry"), raw, provenance),
  );
  assert.throws(() =>
    createGeneratedAcceptanceResponse(envelope, geometry, provenance),
  );
  assert.throws(() =>
    readGeneratedAcceptanceResponse(reply, request("geometry"), now + 1),
  );
});
