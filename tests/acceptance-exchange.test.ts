import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createAcceptanceRequest,
  readAcceptanceRequest,
  createAcceptanceResponse,
  readAcceptanceResponse,
} from "../companion/src/acceptance-exchange.js";

const now = 1_800_000_000_000;
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "123456789",
  runAttempt: 1,
  scenario: "signal-lamp-client-handoff-v1",
};
const context = {
  serverId: "synthetic-server",
  serverEpoch: "synthetic-epoch",
  projectId: "synthetic-project",
  actorId: "11111111-1111-4111-8111-111111111111",
  connectionId: "22222222-2222-4222-8222-222222222222",
  requestId: "synthetic-request",
  projectRevision: 1,
  authorizationRevision: 1,
};
const request = () =>
  createAcceptanceRequest({
    run,
    context,
    worldId: "synthetic-world",
    nonce: "b".repeat(64),
    createdAt: now,
  });
const plan = {
  template: "signal-lamp" as const,
  explanation:
    "The lever drives a delayed lamp. Actual testing is still required.",
  parameters: { delay: 1 },
  rotation: 0 as const,
};
const provenance = {
  model: "gpt-6-luna",
  sdkVersion: "0.164.0",
  inputTokens: 30,
  cachedInputTokens: 0,
  outputTokens: 20,
};

test("synthetic acceptance reconstructs the request locally and binds its runtime frame", () => {
  const created = request();
  const read = readAcceptanceRequest(
    JSON.parse(JSON.stringify(created)),
    run,
    now + 1,
  );
  assert.equal(read.context.actorId, context.actorId);
  assert.equal(read.context.connectionId, context.connectionId);
  assert.equal(read.input.projectId, context.projectId);
  assert.equal(read.input.worldId, "synthetic-world");
  assert.deepEqual(read.input.anchor, { x: 0, y: 100, z: 0 });
  assert.deepEqual(read.input.maxSize, { x: 3, y: 2, z: 1 });
  assert.equal(
    read.memory,
    JSON.stringify({
      project: { name: "Engineering project", projectRevision: 1 },
      tasks: [],
    }),
  );
  assert.match(read.input.request, /lamp/i);
  assert.match(read.digest, /^[a-f0-9]{64}$/u);
  assert.equal(JSON.stringify(created).includes("token"), false);
});

test("public nonce is correlation only: trusted run identity and strict synthetic shape are required", () => {
  for (const change of [
    { repository: "someone/else" },
    { workflow: ".github/workflows/ci.yml" },
    { sourceSha: "c".repeat(40) },
    { runId: "123456790" },
    { runAttempt: 2 },
    { scenario: "arbitrary-agent" },
  ])
    assert.throws(() =>
      readAcceptanceRequest(request(), { ...run, ...change }, now + 1),
    );
  for (const field of [
    "prompt",
    "memory",
    "input",
    "token",
    "path",
    "shell",
    "inventory",
  ])
    assert.throws(() =>
      readAcceptanceRequest(
        { ...request(), [field]: "not accepted" },
        run,
        now,
      ),
    );
  const altered = structuredClone(request());
  altered.context.actorId = "33333333-3333-4333-8333-333333333333";
  assert.throws(() => readAcceptanceRequest(altered, run, now));
  assert.throws(() =>
    readAcceptanceRequest({ ...request(), nonce: "x".repeat(64) }, run, now),
  );
  assert.throws(() => readAcceptanceRequest(request(), run, now - 1));
  assert.throws(() => readAcceptanceRequest(request(), run, now + 15 * 60_000));
});

test("a validated plan response belongs to one exact request, not another actor/revision/run", () => {
  const original = request();
  const response = createAcceptanceResponse(original, plan, provenance);
  assert.deepEqual(
    readAcceptanceResponse(response, original, now + 2).plan,
    plan,
  );
  for (const change of [
    { serverEpoch: "new-epoch" },
    { authorizationRevision: 2 },
    { projectRevision: 2 },
    { actorId: "33333333-3333-4333-8333-333333333333" },
    { connectionId: "44444444-4444-4444-8444-444444444444" },
  ]) {
    const other = createAcceptanceRequest({
      run,
      context: { ...context, ...change },
      worldId: "synthetic-world",
      nonce: "b".repeat(64),
      createdAt: now,
    });
    assert.throws(() => readAcceptanceResponse(response, other, now + 2));
  }
  const rerun = createAcceptanceRequest({
    run: { ...run, runAttempt: 2 },
    context,
    worldId: "synthetic-world",
    nonce: "b".repeat(64),
    createdAt: now,
  });
  assert.throws(() => readAcceptanceResponse(response, rerun, now + 2));
  assert.throws(() =>
    readAcceptanceResponse(response, original, now + 15 * 60_000),
  );
});

test("exchange rejects executable/credential-shaped extras and malformed model results", () => {
  const original = request(),
    response = createAcceptanceResponse(original, plan, provenance);
  assert.throws(() =>
    createAcceptanceResponse(
      original,
      { ...plan, template: "piston-door" },
      provenance,
    ),
  );
  assert.throws(() =>
    createAcceptanceResponse(
      original,
      { ...plan, parameters: { delay: 5 } },
      provenance,
    ),
  );
  assert.throws(() =>
    createAcceptanceResponse(
      original,
      { ...plan, shell: "anything" },
      provenance,
    ),
  );
  assert.throws(() =>
    createAcceptanceResponse(original, plan, { ...provenance, events: [] }),
  );
  assert.throws(() =>
    readAcceptanceResponse(
      { ...response, token: "never transported" },
      original,
      now,
    ),
  );
  assert.throws(() =>
    readAcceptanceResponse(
      { ...response, plan: { ...plan, explanation: "changed after receipt" } },
      original,
      now,
    ),
  );
});
