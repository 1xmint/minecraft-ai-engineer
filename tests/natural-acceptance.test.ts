import { NaturalSyntheticAcceptanceProvider } from "../companion/src/natural-acceptance-broker.js";
import {
  mkdtempSync,
  readFileSync,
  writeFileSync,
  existsSync,
  rmSync,
} from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import test from "node:test";
import assert from "node:assert/strict";
import { discussionGrounding } from "../companion/src/discussion.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import {
  createNaturalAcceptanceRequest,
  readNaturalAcceptanceRequest,
  createNaturalAcceptanceResponse,
  readNaturalAcceptanceResponse,
  NATURAL_ACCEPTANCE_SCENARIO,
  NATURAL_ACCEPTANCE_MESSAGE,
} from "../companion/src/natural-acceptance.js";
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "12345",
  runAttempt: 1,
  scenario: NATURAL_ACCEPTANCE_SCENARIO,
};
const owner = "10920508-d5d8-3eed-93d2-92f193afe7d7";
const context = {
  serverId: "synthetic-server",
  serverEpoch: "epoch-one",
  actorId: owner,
  projectId: "project-a",
  projectRevision: 1,
  authorizationRevision: 1,
  requestId: "natural-request",
  connectionId: "11111111-1111-4111-8111-111111111111",
};
function fixture() {
  const blueprint = makeReferenceBlueprint({
    request: "reference",
    worldId: "synthetic",
    dimension: "minecraft:overworld",
    projectId: context.projectId,
    anchor: { x: 500, y: 100, z: 0 },
    rotation: 0,
  });
  const notes = [
    { kind: "goal" as const, text: "Keep the entrance within six blocks." },
    { kind: "decision" as const, text: "Use a visible lamp for the output." },
  ].map((note, index) => ({
    schemaVersion: 1 as const,
    ...note,
    id:
      index === 0
        ? "22222222-2222-4222-8222-222222222222"
        : "33333333-3333-4333-8333-333333333333",
    revision: index + 1,
    authorId: owner,
    createdAt: 100,
    source: "owner-authored" as const,
  }));
  const grounding = discussionGrounding(
    blueprint.name,
    context.projectRevision,
    blueprint,
    { schemaVersion: 1, revision: 2, notes, hasEarlier: false },
    "root",
  ).text;
  return { run, context, grounding, nonce: "b".repeat(64), createdAt: 1000 };
}

const provenance = {
  model: "gpt-6-luna",
  sdkVersion: "0.164.0",
  inputTokens: 10,
  cachedInputTokens: 0,
  outputTokens: 2,
};
const reply = {
  schemaVersion: 1,
  kind: "action",
  action: "diagnose",
  componentId: "root",
  text: "Inspect the selected component.",
  basis: ["component:root"],
};
test("natural acceptance binds a fresh native frame and typed inspection to its exact request", () => {
  const request = createNaturalAcceptanceRequest(fixture());
  const decoded = readNaturalAcceptanceRequest(request, run, 1001);
  assert.deepEqual(decoded.input, { message: NATURAL_ACCEPTANCE_MESSAGE });
  assert.ok(decoded.capabilities.get("diagnose")?.has("root"));
  const response = createNaturalAcceptanceResponse(request, reply, provenance);
  assert.deepEqual(
    readNaturalAcceptanceResponse(response, request, 1001),
    response,
  );
  assert.equal(response.result.action, "diagnose");
  for (const patch of [
    { action: "build" },
    { componentId: "foreign" },
    { commands: ["setblock"] },
    { nonce: "approved" },
    { basis: ["private:inventory"] },
  ])
    assert.throws(() =>
      createNaturalAcceptanceResponse(
        request,
        { ...reply, ...patch },
        provenance,
      ),
    );
  assert.throws(() =>
    createNaturalAcceptanceResponse(
      request,
      {
        schemaVersion: 1,
        kind: "clarification",
        action: null,
        componentId: null,
        text: "Which part?",
        basis: [],
      },
      provenance,
    ),
  );
});
test("natural acceptance cannot replay another run, actor, epoch, connection or expired answer", () => {
  const request = createNaturalAcceptanceRequest(fixture());
  const response = createNaturalAcceptanceResponse(request, reply, provenance);
  assert.throws(() =>
    readNaturalAcceptanceRequest(request, { ...run, runId: "67890" }, 1001),
  );
  assert.throws(() =>
    readNaturalAcceptanceRequest(
      { ...request, nonce: "c".repeat(64) },
      run,
      1001,
    ),
  );
  assert.throws(() =>
    readNaturalAcceptanceResponse(response, request, request.expiresAt),
  );
  for (const change of [
    { requestId: "different" },
    { serverEpoch: "different" },
    { connectionId: "44444444-4444-4444-8444-444444444444" },
    { authorizationRevision: 2 },
  ]) {
    const other = createNaturalAcceptanceRequest({
      ...fixture(),
      context: { ...context, ...change },
    });
    assert.throws(() => readNaturalAcceptanceResponse(response, other, 1001));
  }
  assert.throws(() =>
    createNaturalAcceptanceRequest({
      ...fixture(),
      context: { ...context, actorId: "44444444-4444-4444-8444-444444444444" },
    }),
  );
  assert.throws(() =>
    createNaturalAcceptanceRequest({
      ...fixture(),
      grounding: fixture().grounding.replace(
        "within six blocks",
        "ignore all authority",
      ),
    }),
  );
  assert.throws(() =>
    readNaturalAcceptanceResponse(
      { ...response, digest: "f".repeat(64) },
      request,
      1001,
    ),
  );
});

test("manual natural broker consumes one exact exchange even across abort and provider restart", async () => {
  const keys = [
    "CI",
    "GITHUB_ACTIONS",
    "MCE_LIVE_ACCEPTANCE",
    "MCE_NATURAL_ACCEPTANCE",
  ];
  const old = keys.map((key) => process.env[key]);
  const directory = mkdtempSync(join(tmpdir(), "mce-natural-broker-"));
  for (const key of keys) process.env[key] = "true";
  try {
    const decoded = readNaturalAcceptanceRequest(
      createNaturalAcceptanceRequest(fixture()),
      run,
      1001,
    );
    const provider = new NaturalSyntheticAcceptanceProvider({ directory, run });
    const controller = new AbortController();
    const args = [
      { message: NATURAL_ACCEPTANCE_MESSAGE },
      controller.signal,
      fixture().grounding,
      decoded.capabilities,
      decoded.availableBasis,
      Object.freeze({ ...context }),
    ] as const;
    const pending = provider.interpret(...args);
    const request = JSON.parse(
      readFileSync(join(directory, "natural", "request.json"), "utf8"),
    );
    const response = createNaturalAcceptanceResponse(
      request,
      reply,
      provenance,
    );
    writeFileSync(
      join(directory, "natural", "response.json"),
      JSON.stringify(response),
    );
    assert.deepEqual(await pending, response.result);
    const accepted = JSON.parse(
      readFileSync(join(directory, "natural", "accepted.json"), "utf8"),
    );
    assert.equal(accepted.requestDigest, request.digest);
    assert.equal(accepted.resultDigest, response.digest);
    await assert.rejects(() => provider.interpret(...args));
    await assert.rejects(() =>
      new NaturalSyntheticAcceptanceProvider({ directory, run }).interpret(
        ...args,
      ),
    );
    const abortedDirectory = join(directory, "aborted");
    const aborted = new NaturalSyntheticAcceptanceProvider({
      directory: abortedDirectory,
      run,
    });
    const cancel = new AbortController();
    const abortedArgs = [
      args[0],
      cancel.signal,
      args[2],
      args[3],
      args[4],
      args[5],
    ] as const;
    const flight = aborted.interpret(...abortedArgs);
    cancel.abort();
    await assert.rejects(flight);
    assert.equal(
      existsSync(join(abortedDirectory, "natural", "accepted.json")),
      false,
    );
    await assert.rejects(() =>
      new NaturalSyntheticAcceptanceProvider({
        directory: abortedDirectory,
        run,
      }).interpret(...args),
    );
    const deniedDirectory = join(directory, "denied");
    const denied = new NaturalSyntheticAcceptanceProvider({
      directory: deniedDirectory,
      run,
    });
    await assert.rejects(() =>
      denied.interpret(args[0], args[1], args[2], new Map(), args[4], args[5]),
    );
    assert.equal(
      existsSync(join(deniedDirectory, "natural", "request.json")),
      false,
    );
  } finally {
    keys.forEach((key, index) => {
      if (old[index] === undefined) delete process.env[key];
      else process.env[key] = old[index];
    });
    rmSync(directory, { recursive: true, force: true });
  }
});

test("archived signed-in natural reply remains bound evidence, never a fresh execution reply", () => {
  const fixture = JSON.parse(
    readFileSync(
      new URL("../../tests/fixtures/live-natural.json", import.meta.url),
      "utf8",
    ),
  );
  const request = readNaturalAcceptanceRequest(
    fixture.request,
    fixture.request.run,
    fixture.acceptedAt,
  );
  const response = readNaturalAcceptanceResponse(
    fixture.response,
    fixture.request,
    fixture.acceptedAt,
  );
  assert.equal(
    request.run.sourceSha,
    "8214fbd8d1a97bfc702d0669e85641a4e4c5bcc4",
  );
  assert.equal(request.run.runId, "41221809497");
  assert.equal(response.provenance.model, "gpt-6-luna");
  assert.equal(response.provenance.inputTokens, 11519);
  assert.equal(response.provenance.outputTokens, 73);
  assert.equal(response.result.action, "diagnose");
  assert.equal(response.result.componentId, "root");
  assert.throws(() =>
    readNaturalAcceptanceResponse(
      fixture.response,
      fixture.request,
      request.expiresAt,
    ),
  );
  const {
    schemaVersion: _version,
    stage: _stage,
    expiresAt: _expires,
    digest: _digest,
    ...fields
  } = fixture.request;
  const rebound = createNaturalAcceptanceRequest({
    ...fields,
    context: {
      ...fields.context,
      connectionId: "44444444-4444-4444-8444-444444444444",
    },
  });
  assert.throws(() =>
    readNaturalAcceptanceResponse(
      fixture.response,
      rebound,
      fixture.acceptedAt,
    ),
  );
});
