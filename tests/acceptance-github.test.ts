import assert from "node:assert/strict";
import { test } from "node:test";
import {
  requireAcceptanceRun,
  requireAcceptanceArtifact,
} from "../companion/src/acceptance-github.js";

const expected = {
  repository: "1xmint/minecraft-ai-engineer",
  workflow: ".github/workflows/live-acceptance.yml",
  sourceSha: "a".repeat(40),
  runId: "123",
  runAttempt: 2,
  scenario: "signal-lamp-client-handoff-v1",
};
const run = () => ({
  id: 123,
  run_attempt: 2,
  path: expected.workflow,
  head_sha: expected.sourceSha,
  head_branch: "main",
  event: "workflow_dispatch",
  status: "in_progress",
  conclusion: null,
  actor: { login: "1xmint" },
  triggering_actor: { login: "1xmint" },
  repository: { full_name: expected.repository },
  head_repository: { full_name: expected.repository },
});

test("only the explicit owner-dispatched reviewed request run may spend local quota", () => {
  assert.deepEqual(requireAcceptanceRun(run(), expected, "request"), {
    id: "123",
    attempt: 2,
    branch: "main",
  });
  for (const change of [
    { id: 124 },
    { run_attempt: 1 },
    { path: ".github/workflows/ci.yml" },
    { head_sha: "b".repeat(40) },
    { event: "pull_request" },
    { status: "completed", conclusion: "success" },
    { actor: { login: "someone" } },
    { triggering_actor: { login: "someone" } },
    { repository: { full_name: "someone/fork" } },
    { head_repository: { full_name: "someone/fork" } },
    { head_branch: "--unsafe" },
    { head_branch: "" },
  ])
    assert.throws(() =>
      requireAcceptanceRun({ ...run(), ...change }, expected, "request"),
    );
  assert.throws(() =>
    requireAcceptanceRun(
      run(),
      { ...expected, repository: "someone/fork" },
      "request",
    ),
  );
});

test("response transport requires a completed owner workflow at the same reviewed source", () => {
  const response = {
    ...run(),
    id: 456,
    run_attempt: 1,
    path: ".github/workflows/live-acceptance-response.yml",
    status: "completed",
    conclusion: "success",
  };
  assert.deepEqual(requireAcceptanceRun(response, expected, "response"), {
    id: "456",
    attempt: 1,
    branch: "main",
  });
  for (const change of [
    { path: expected.workflow },
    { status: "in_progress" },
    { conclusion: "failure" },
    { actor: { login: "someone" } },
    { triggering_actor: { login: "someone" } },
    { head_sha: "c".repeat(40) },
  ])
    assert.throws(() =>
      requireAcceptanceRun({ ...response, ...change }, expected, "response"),
    );
});

test("only one bounded unexpired artifact from the verified run and SHA is readable", () => {
  const artifact = {
    id: 789,
    name: "mce-live-request-123-2",
    expired: false,
    size_in_bytes: 4096,
    workflow_run: { id: 123, head_sha: expected.sourceSha },
  };
  assert.equal(
    requireAcceptanceArtifact(
      artifact,
      "123",
      expected.sourceSha,
      artifact.name,
    ),
    "789",
  );
  for (const change of [
    { id: 0 },
    { name: "other" },
    { expired: true },
    { size_in_bytes: 0 },
    { size_in_bytes: 16385 },
    { workflow_run: { id: 124, head_sha: expected.sourceSha } },
    { workflow_run: { id: 123, head_sha: "d".repeat(40) } },
  ])
    assert.throws(() =>
      requireAcceptanceArtifact(
        { ...artifact, ...change },
        "123",
        expected.sourceSha,
        artifact.name,
      ),
    );
});
