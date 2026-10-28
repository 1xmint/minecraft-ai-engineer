/** Data-only provenance checks. GitHub API authentication belongs to the caller. */
import type { AcceptanceRun } from "./acceptance-exchange.js";
import { object, RequestError } from "./types.js";

function fail(): never {
  throw new RequestError("Untrusted synthetic acceptance transport.", 409);
}
function id(value: unknown): string {
  if (typeof value === "number") {
    if (!Number.isSafeInteger(value) || value < 1) fail();
    return String(value);
  }
  if (typeof value !== "string" || !/^[1-9][0-9]{0,19}$/u.test(value)) fail();
  return value;
}
function attempt(value: unknown): number {
  if (
    typeof value !== "number" ||
    !Number.isInteger(value) ||
    value < 1 ||
    value > 1000
  )
    fail();
  return value;
}
function sha(value: unknown): string {
  if (typeof value !== "string" || !/^[a-f0-9]{40}$/u.test(value)) fail();
  return value;
}
export function requireAcceptanceRun(
  value: unknown,
  expected: AcceptanceRun,
  kind: "request" | "response",
): { id: string; attempt: number; branch: string } {
  if (
    expected.repository !== "1xmint/minecraft-ai-engineer" ||
    expected.workflow !== ".github/workflows/live-acceptance.yml" ||
    expected.scenario !== "signal-lamp-client-handoff-v1" ||
    (kind !== "request" && kind !== "response")
  )
    fail();
  const expectedId = id(expected.runId),
    expectedAttempt = attempt(expected.runAttempt);
  const v = object(value),
    runId = id(v.id),
    runAttempt = attempt(v.run_attempt);
  const branch = v.head_branch;
  if (
    typeof branch !== "string" ||
    branch.length > 255 ||
    !/^[A-Za-z0-9][A-Za-z0-9._/-]*$/u.test(branch) ||
    branch.includes("..") ||
    branch.includes("//") ||
    branch.endsWith("/") ||
    branch
      .split("/")
      .some(
        (part) =>
          part.startsWith(".") || part.endsWith(".") || part.endsWith(".lock"),
      ) ||
    v.path !==
      (kind === "request"
        ? expected.workflow
        : ".github/workflows/live-acceptance-response.yml") ||
    sha(v.head_sha) !== sha(expected.sourceSha) ||
    v.event !== "workflow_dispatch" ||
    object(v.actor).login !== "1xmint" ||
    object(v.triggering_actor).login !== "1xmint" ||
    object(v.repository).full_name !== expected.repository ||
    object(v.head_repository).full_name !== expected.repository
  )
    fail();
  if (kind === "request") {
    if (
      runId !== expectedId ||
      runAttempt !== expectedAttempt ||
      v.status !== "in_progress" ||
      v.conclusion !== null
    )
      fail();
  } else if (
    runId === expectedId ||
    v.status !== "completed" ||
    v.conclusion !== "success"
  )
    fail();
  return { id: runId, attempt: runAttempt, branch };
}
export function requireAcceptanceArtifact(
  value: unknown,
  runId: string,
  sourceSha: string,
  name: string,
): string {
  const v = object(value),
    parent = object(v.workflow_run);
  if (
    typeof name !== "string" ||
    !/^mce-live-(request|response)-[1-9][0-9]{0,19}-[1-9][0-9]{0,3}$/u.test(
      name,
    ) ||
    v.name !== name ||
    v.expired !== false ||
    typeof v.size_in_bytes !== "number" ||
    !Number.isInteger(v.size_in_bytes) ||
    v.size_in_bytes < 1 ||
    v.size_in_bytes > 16384 ||
    id(parent.id) !== id(runId) ||
    sha(parent.head_sha) !== sha(sourceSha)
  )
    fail();
  return id(v.id);
}
