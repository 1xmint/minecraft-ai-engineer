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
import { SyntheticAcceptanceProvider } from "../companion/src/acceptance-broker.js";
import {
  createAcceptanceRequest,
  createAcceptanceResponse,
  readAcceptanceRequest,
} from "../companion/src/acceptance-exchange.js";
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "123",
  runAttempt: 1,
  scenario: "signal-lamp-client-handoff-v1",
};
const context = Object.freeze({
  serverId: "fixture",
  serverEpoch: "epoch",
  projectId: "project",
  actorId: "11111111-1111-4111-8111-111111111111",
  connectionId: "22222222-2222-4222-8222-222222222222",
  requestId: "request",
  projectRevision: 1,
  authorizationRevision: 1,
});
const now = 1_800_000_000_000;
const seed = createAcceptanceRequest({
  run,
  context,
  worldId: "world",
  nonce: "b".repeat(64),
  createdAt: now,
});
const fixture = readAcceptanceRequest(seed, run, now);
const plan = {
  template: "signal-lamp" as const,
  explanation: "Actual game testing is required.",
  parameters: { delay: 1 },
  rotation: 0 as const,
};
const provenance = {
  model: "gpt-6-luna",
  sdkVersion: "0.164.0",
  inputTokens: 10,
  cachedInputTokens: 0,
  outputTokens: 20,
};
function setup() {
  const directory = mkdtempSync(join(tmpdir(), "mce-acceptance-broker-"));
  let time = now;
  const provider = new SyntheticAcceptanceProvider({
    directory,
    run,
    clock: () => time,
    pollMs: 5,
  });
  return {
    directory,
    provider,
    setTime: (value: number) => {
      time = value;
    },
    close: () => rmSync(directory, { recursive: true, force: true }),
  };
}

test("CI broker returns a Plan through the provider only for its exact generated request", async () => {
  const f = setup();
  try {
    const pending = f.provider.plan(
      fixture.input,
      new AbortController().signal,
      fixture.memory,
      context,
    );
    const request = JSON.parse(
      readFileSync(join(f.directory, "request.json"), "utf8"),
    );
    assert.equal(
      readAcceptanceRequest(request, run, now).context.actorId,
      context.actorId,
    );
    writeFileSync(
      join(f.directory, "response.json"),
      JSON.stringify(createAcceptanceResponse(request, plan, provenance)),
    );
    assert.deepEqual(await pending, plan);
    const accepted = JSON.parse(
      readFileSync(join(f.directory, "accepted.json"), "utf8"),
    );
    assert.equal(accepted.requestDigest, request.digest);
    assert.equal(accepted.acceptedAt, now);
    assert.match(accepted.resultDigest, /^[a-f0-9]{64}$/u);
    await assert.rejects(
      f.provider.plan(
        fixture.input,
        new AbortController().signal,
        fixture.memory,
        context,
      ),
    );
  } finally {
    f.close();
  }
});

test("CI broker never publishes arbitrary requests or project memory", async () => {
  for (const change of [
    {
      input: { ...fixture.input, request: "private unapproved request" },
      memory: fixture.memory,
      authority: context,
    },
    {
      input: fixture.input,
      memory: "private unapproved memory",
      authority: context,
    },
    { input: fixture.input, memory: fixture.memory, authority: { ...context } },
  ]) {
    const f = setup();
    try {
      await assert.rejects(
        f.provider.plan(
          change.input,
          new AbortController().signal,
          change.memory,
          change.authority,
        ),
      );
      assert.equal(existsSync(join(f.directory, "request.json")), false);
      await assert.rejects(
        f.provider.plan(
          fixture.input,
          new AbortController().signal,
          fixture.memory,
          context,
        ),
      );
      assert.equal(existsSync(join(f.directory, "request.json")), false);
      assert.equal(
        readFileSync(
          join(f.directory, "unexpected-inference"),
          "utf8",
        ).includes("private"),
        false,
      );
    } finally {
      f.close();
    }
  }
});

test("CI broker rejects another request result without accepting or retrying inference", async () => {
  const f = setup();
  try {
    const pending = f.provider.plan(
      fixture.input,
      new AbortController().signal,
      fixture.memory,
      context,
    );
    const other = createAcceptanceRequest({
      run,
      context: { ...context, authorizationRevision: 2 },
      worldId: "world",
      nonce: "c".repeat(64),
      createdAt: now,
    });
    writeFileSync(
      join(f.directory, "response.json"),
      JSON.stringify(createAcceptanceResponse(other, plan, provenance)),
    );
    await assert.rejects(pending);
    assert.equal(existsSync(join(f.directory, "accepted.json")), false);
  } finally {
    f.close();
  }
});

test("CI broker discards cancellation and expired results without a live proof", async () => {
  for (const expiry of [false, true]) {
    const f = setup();
    try {
      const controller = new AbortController();
      const pending = f.provider.plan(
        fixture.input,
        controller.signal,
        fixture.memory,
        context,
      );
      if (expiry) f.setTime(now + 15 * 60_000);
      else controller.abort();
      await assert.rejects(pending);
      assert.equal(existsSync(join(f.directory, "accepted.json")), false);
    } finally {
      f.close();
    }
  }
});
