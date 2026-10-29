import { DiscussionSyntheticAcceptanceProvider } from "../companion/src/discussion-acceptance-broker.js";
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
  createDiscussionAcceptanceRequest,
  readDiscussionAcceptanceRequest,
  createDiscussionAcceptanceResponse,
  readDiscussionAcceptanceResponse,
  DISCUSSION_ACCEPTANCE_SCENARIO,
  DISCUSSION_ACCEPTANCE_MESSAGE,
} from "../companion/src/discussion-acceptance.js";
const run = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "12345",
  runAttempt: 1,
  scenario: DISCUSSION_ACCEPTANCE_SCENARIO,
};
const owner = "10920508-d5d8-3eed-93d2-92f193afe7d7";
const context = {
  serverId: "synthetic-server",
  serverEpoch: "epoch-one",
  actorId: owner,
  projectId: "project-a",
  projectRevision: 1,
  authorizationRevision: 1,
  requestId: "discussion-request",
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
test("manual discussion exchange binds one fixed synthetic question, native sources, run and exact model reply", () => {
  const request = createDiscussionAcceptanceRequest(fixture());
  const decoded = readDiscussionAcceptanceRequest(request, run, 1001);
  assert.equal(decoded.input.message, DISCUSSION_ACCEPTANCE_MESSAGE);
  assert.equal(decoded.input.componentId, "root");
  assert.equal(decoded.memory, request.grounding);
  const response = createDiscussionAcceptanceResponse(
    request,
    {
      schemaVersion: 1,
      kind: "explanation",
      text: "The owner selected a visible lamp and a six-block entrance. These are requirements, not observed facts.",
      basis: [
        "project",
        "note:22222222-2222-4222-8222-222222222222",
        "note:33333333-3333-4333-8333-333333333333",
      ],
    },
    {
      model: "gpt-6-luna",
      sdkVersion: "0.164.0",
      inputTokens: 100,
      cachedInputTokens: 0,
      outputTokens: 30,
    },
  );
  assert.deepEqual(
    readDiscussionAcceptanceResponse(response, request, 1001),
    response,
  );
  for (const patch of [
    { requestDigest: "0".repeat(64) },
    { stage: "geometry" },
    { result: { ...response.result, basis: ["inventory:private"] } },
    { result: { ...response.result, verified: "passed" } },
    { digest: "c".repeat(64) },
  ])
    assert.throws(() =>
      readDiscussionAcceptanceResponse(
        { ...response, ...patch },
        request,
        1001,
      ),
    );
  assert.throws(() =>
    readDiscussionAcceptanceRequest(
      request,
      { ...run, sourceSha: "c".repeat(40) },
      1001,
    ),
  );
  assert.throws(() =>
    readDiscussionAcceptanceRequest(request, run, request.expiresAt),
  );
  assert.throws(() =>
    readDiscussionAcceptanceRequest(
      {
        ...request,
        context: {
          ...context,
          actorId: "44444444-4444-4444-8444-444444444444",
        },
      },
      run,
      1001,
    ),
  );
});
test("only exact synthetic project sources may cross the manual proof bridge", () => {
  for (const transform of [
    (source: any) => ({ ...source, inventory: [{ item: "private" }] }),
    (source: any) => ({ ...source, worldEvidence: "passed" }),
    (source: any) => ({ ...source, selectedComponent: "foreign" }),
    (source: any) => ({
      ...source,
      sources: source.sources.map((item: any) =>
        item.kind === "owner-authored-requirement"
          ? {
              ...item,
              data: { ...item.data, text: "A real user's private project." },
            }
          : item,
      ),
    }),
  ]) {
    const input = fixture();
    input.grounding = JSON.stringify(transform(JSON.parse(input.grounding)));
    assert.throws(() => createDiscussionAcceptanceRequest(input));
  }
  assert.throws(() =>
    createDiscussionAcceptanceRequest({
      ...fixture(),
      endpoint: "https://example.com",
    }),
  );
});

async function manualEnvironment(work: () => Promise<void>) {
  const fields = [
      "CI",
      "GITHUB_ACTIONS",
      "MCE_LIVE_ACCEPTANCE",
      "MCE_DISCUSSION_ACCEPTANCE",
    ],
    before = Object.fromEntries(fields.map((key) => [key, process.env[key]]));
  for (const key of fields) process.env[key] = "true";
  try {
    await work();
  } finally {
    for (const key of fields) {
      if (before[key] === undefined) delete process.env[key];
      else process.env[key] = before[key];
    }
  }
}
test("manual broker exchanges one bound reply and refuses a second dispatch without any SDK", async () =>
  manualEnvironment(async () => {
    const directory = mkdtempSync(join(tmpdir(), "mce-discussion-proof-"));
    try {
      const provider = new DiscussionSyntheticAcceptanceProvider({
          directory,
          run,
        }),
        input = fixture();
      const requestInput = {
        message: DISCUSSION_ACCEPTANCE_MESSAGE,
        componentId: "root",
      };
      const pending = provider.discuss(
        requestInput,
        new AbortController().signal,
        input.grounding,
        Object.freeze({ ...context }),
      );
      const request = JSON.parse(
        readFileSync(join(directory, "discussion", "request.json"), "utf8"),
      );
      const response = createDiscussionAcceptanceResponse(
        request,
        {
          schemaVersion: 1,
          kind: "explanation",
          text: "The owner selected a visible lamp and six-block entrance, without a measured pass.",
          basis: [
            "note:22222222-2222-4222-8222-222222222222",
            "note:33333333-3333-4333-8333-333333333333",
          ],
        },
        {
          model: "gpt-6-luna",
          sdkVersion: "0.164.0",
          inputTokens: 100,
          cachedInputTokens: 0,
          outputTokens: 20,
        },
      );
      writeFileSync(
        join(directory, "discussion", "response.json"),
        JSON.stringify(response),
      );
      assert.deepEqual(await pending, response.result);
      const accepted = JSON.parse(
        readFileSync(join(directory, "discussion", "accepted.json"), "utf8"),
      );
      assert.equal(accepted.resultDigest, response.digest);
      await assert.rejects(
        provider.discuss(
          requestInput,
          new AbortController().signal,
          input.grounding,
          Object.freeze({ ...context }),
        ),
      );
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  }));
test("manual broker cancellation never accepts a late response or authorizes another model attempt", async () =>
  manualEnvironment(async () => {
    const directory = mkdtempSync(join(tmpdir(), "mce-discussion-abort-"));
    try {
      const provider = new DiscussionSyntheticAcceptanceProvider({
          directory,
          run,
        }),
        controller = new AbortController();
      const pending = provider.discuss(
        { message: DISCUSSION_ACCEPTANCE_MESSAGE, componentId: "root" },
        controller.signal,
        fixture().grounding,
        Object.freeze({ ...context }),
      );
      const rejected = assert.rejects(pending);
      controller.abort();
      await rejected;
      assert.equal(
        existsSync(join(directory, "discussion", "accepted.json")),
        false,
      );
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  }));

test("the exact fresh signed-in reply retains both literal requirements and explicit uncertainty", () => {
  const fixture = JSON.parse(
    readFileSync(
      new URL("../../tests/fixtures/live-discussion.json", import.meta.url),
      "utf8",
    ),
  );
  const request = readDiscussionAcceptanceRequest(
    fixture.request,
    fixture.request.run,
    fixture.acceptedAt,
  );
  const response = readDiscussionAcceptanceResponse(
    fixture.response,
    fixture.request,
    fixture.acceptedAt,
  );
  assert.equal(
    request.run.sourceSha,
    "b858364b5880a4ade5476b89c8f162643a45442a",
  );
  assert.equal(request.run.runId, "40960496759");
  assert.equal(response.provenance.model, "gpt-6-luna");
  assert.equal(response.result.kind, "explanation");
  assert.match(
    response.result.text,
    /do not explain why visibility was preferred/,
  );
  assert.match(
    response.result.text,
    /no evidence that the circuit was built or tested/,
  );
  assert.equal(
    response.result.basis.filter((id) => id.startsWith("note:")).length,
    2,
  );
});
