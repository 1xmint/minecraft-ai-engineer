#!/usr/bin/env node
// Manual, local-only synthetic acceptance. No credentials or SDK events leave this PC.
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import {
  mkdir,
  mkdtemp,
  readFile,
  lstat,
  open,
  rm,
  chmod,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { inflateRawSync } from "node:zlib";

const exec = promisify(execFile);
const repository = "1xmint/minecraft-ai-engineer";
const root = fileURLToPath(new URL("../../", import.meta.url));
const responseWorkflow = ".github/workflows/live-acceptance-response.yml";
function check(ok, message) {
  if (!ok) throw new Error(message);
}
async function command(binary, args, options = {}) {
  try {
    return (
      await exec(binary, args, {
        cwd: root,
        windowsHide: true,
        timeout: 30_000,
        maxBuffer: 1024 * 1024,
        ...options,
      })
    ).stdout;
  } catch {
    throw new Error(
      `Local ${binary} operation failed; inspect sign-in, connectivity, or reviewed source separately.`,
    );
  }
}
function apiArgs(path, extra = []) {
  return [
    "api",
    "--hostname",
    "github.com",
    "-H",
    "Accept: application/vnd.github+json",
    "-H",
    "X-GitHub-Api-Version: 2026-03-10",
    `repos/${repository}/${path}`,
    ...extra,
  ];
}
async function api(path) {
  return JSON.parse(await command("gh", apiArgs(path)));
}
async function boundedJson(path, maximum) {
  const stat = await lstat(path);
  check(
    stat.isFile() &&
      !stat.isSymbolicLink() &&
      stat.size > 0 &&
      stat.size <= maximum,
    "Expected one bounded regular JSON file.",
  );
  return JSON.parse(await readFile(path, "utf8"));
}
// Never extract archive-controlled paths. Accept exactly one regular request.json,
// checking both central and local headers before inflating bounded bytes in memory.
function requestBytes(zip) {
  check(
    zip.length >= 22 && zip.length <= 32768,
    "Invalid request archive size.",
  );
  const end = zip.length - 22;
  check(
    zip.readUInt32LE(end) === 0x06054b50 &&
      zip.readUInt16LE(end + 20) === 0 &&
      zip.readUInt16LE(end + 4) === 0 &&
      zip.readUInt16LE(end + 6) === 0 &&
      zip.readUInt16LE(end + 8) === 1 &&
      zip.readUInt16LE(end + 10) === 1,
    "Expected a single-file request archive.",
  );
  const central = zip.readUInt32LE(end + 16),
    centralSize = zip.readUInt32LE(end + 12);
  check(
    central + centralSize === end &&
      centralSize >= 46 &&
      central + 46 <= end &&
      zip.readUInt32LE(central) === 0x02014b50,
    "Invalid request archive directory.",
  );
  const flags = zip.readUInt16LE(central + 8),
    method = zip.readUInt16LE(central + 10);
  const compressed = zip.readUInt32LE(central + 20),
    size = zip.readUInt32LE(central + 24);
  const nameLength = zip.readUInt16LE(central + 28),
    extraLength = zip.readUInt16LE(central + 30),
    commentLength = zip.readUInt16LE(central + 32);
  const attributes = zip.readUInt32LE(central + 38),
    offset = zip.readUInt32LE(central + 42);
  const name = zip
    .subarray(central + 46, central + 46 + nameLength)
    .toString("utf8");
  const mode = (attributes >>> 16) & 0xf000;
  check(
    name === "request.json" &&
      centralSize === 46 + nameLength + extraLength + commentLength &&
      zip.readUInt16LE(central + 34) === 0 &&
      (mode === 0 || mode === 0x8000) &&
      (attributes & 0x10) === 0 &&
      (flags & ~0x0808) === 0 &&
      [0, 8].includes(method) &&
      size > 0 &&
      size <= 16384 &&
      offset === 0 &&
      offset + 30 <= central &&
      zip.readUInt32LE(offset) === 0x04034b50,
    "Invalid request archive entry.",
  );
  const localNameLength = zip.readUInt16LE(offset + 26),
    localExtraLength = zip.readUInt16LE(offset + 28);
  const start = offset + 30 + localNameLength + localExtraLength;
  check(
    zip.readUInt16LE(offset + 6) === flags &&
      zip.readUInt16LE(offset + 8) === method &&
      zip
        .subarray(offset + 30, offset + 30 + localNameLength)
        .toString("utf8") === name &&
      start + compressed <= central &&
      central - (start + compressed) === (flags & 8 ? 16 : 0),
    "Invalid request archive local entry.",
  );
  if (flags & 8)
    check(
      zip.readUInt32LE(start + compressed) === 0x08074b50 &&
        zip.readUInt32LE(start + compressed + 8) === compressed &&
        zip.readUInt32LE(start + compressed + 12) === size,
      "Invalid request archive descriptor.",
    );
  else
    check(
      zip.readUInt32LE(offset + 14) === zip.readUInt32LE(central + 16) &&
        zip.readUInt32LE(offset + 18) === compressed &&
        zip.readUInt32LE(offset + 22) === size,
      "Invalid request archive lengths.",
    );
  const data =
    method === 0
      ? zip.subarray(start, start + compressed)
      : inflateRawSync(zip.subarray(start, start + compressed), {
          maxOutputLength: 4096,
        });
  check(data.length === size, "Invalid request archive content length.");
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++)
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  check(
    (crc ^ 0xffffffff) >>> 0 === zip.readUInt32LE(central + 16),
    "Invalid request archive checksum.",
  );
  return data;
}
async function privateWrite(path, value) {
  const handle = await open(path, "wx", 0o600);
  try {
    await handle.writeFile(JSON.stringify(value));
    await handle.sync();
  } finally {
    await handle.close();
  }
  if (process.platform !== "win32") {
    const directory = await open(dirname(path), "r");
    try {
      await directory.sync();
    } finally {
      await directory.close();
    }
  }
}
async function main() {
  check(
    !process.env.CI && !process.env.GITHUB_ACTIONS,
    "This responder is manual and local only; CI inference is forbidden.",
  );
  const args = process.argv.slice(2),
    options = {};
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i];
    check(
      [
        "--run",
        "--attempt",
        "--sha",
        "--model",
        "--config",
        "--publish",
        "--stage",
        "--scenario",
      ].includes(key) &&
        args[i + 1] &&
        !Object.hasOwn(options, key),
      "Use --run ID --attempt N --sha SHA40 --model MODEL [--config DIR] [--publish FILE].",
    );
    options[key] = args[i + 1];
  }
  check(
    /^[1-9][0-9]{0,19}$/u.test(options["--run"] ?? "") &&
      /^[1-9][0-9]{0,3}$/u.test(options["--attempt"] ?? "") &&
      Number(options["--attempt"]) <= 1000 &&
      /^[a-f0-9]{40}$/u.test(options["--sha"] ?? "") &&
      /^[a-z0-9][a-z0-9._-]{0,95}$/u.test(options["--model"] ?? ""),
    "Explicit valid run, attempt, reviewed SHA and model are required.",
  );
  const stage = options["--stage"] ?? "catalog";
  check(
    ["catalog", "behavior", "geometry", "discussion"].includes(stage),
    "Stage must be catalog, behavior, geometry or discussion.",
  );
  const scenario =
    options["--scenario"] ?? (stage === "catalog" ? "catalog" : "generated");
  check(
    ["catalog", "generated", "structural", "boolean", "discussion"].includes(
      scenario,
    ) && (stage === "catalog") === (scenario === "catalog"),
    "Scenario must match its catalog, generated, structural, boolean or discussion stage.",
  );
  check(
    (stage === "discussion") === (scenario === "discussion"),
    "Discussion stage requires the discussion scenario.",
  );
  const discussion = stage === "discussion",
    generated = stage === "behavior" || stage === "geometry",
    suffix = stage !== "catalog" ? `-${stage}` : "";
  const expected = {
    repository,
    workflow: ".github/workflows/live-acceptance.yml",
    sourceSha: options["--sha"],
    runId: options["--run"],
    runAttempt: Number(options["--attempt"]),
    scenario: discussion
      ? "project-discussion-owner-notes-v1"
      : scenario === "boolean"
        ? "generated-boolean-owner-confirmation-v1"
        : scenario === "structural"
          ? "structural-buffer-owner-confirmation-v1"
          : generated
            ? "generated-buffer-owner-confirmation-v1"
            : "signal-lamp-client-handoff-v1",
  };
  check(
    (
      await command("git", ["status", "--porcelain", "--untracked-files=all"])
    ).trim() === "" &&
      (await command("git", ["rev-parse", "HEAD"])).trim() ===
        expected.sourceSha,
    "Use a clean reviewed local HEAD exactly matching --sha.",
  );
  const remote = (await command("git", ["remote", "get-url", "origin"])).trim();
  check(
    [
      `https://github.com/${repository}.git`,
      `https://github.com/${repository}`,
      `git@github.com:${repository}.git`,
      `ssh://git@github.com/${repository}.git`,
    ].includes(remote),
    "The reviewed checkout must have the fixed GitHub repository as origin.",
  );
  // Compile the verified source before loading any ignored dist output. Never use
  // npm shell wrappers or lifecycle scripts at this quota boundary.
  await command(
    process.execPath,
    [
      join(root, "node_modules", "typescript", "bin", "tsc"),
      "-p",
      join(root, "tsconfig.json"),
    ],
    { timeout: 120_000 },
  );
  const sdk = await boundedJson(
    join(root, "node_modules", "@openai", "codex-sdk", "package.json"),
    16384,
  );
  const cli = await boundedJson(
    join(root, "node_modules", "@openai", "codex", "package.json"),
    16384,
  );
  check(
    sdk.version === "0.164.0" && cli.version === "0.164.0",
    "Install the reviewed pinned SDK and CLI versions before acceptance.",
  );
  const { configDirectory } =
    await import("../../dist/companion/src/config.js");
  const { CodexPlanningProvider } =
    await import("../../dist/companion/src/provider.js");
  const catalogExchange =
    await import("../../dist/companion/src/acceptance-exchange.js");
  const generatedExchange =
    await import("../../dist/companion/src/generated-acceptance.js");
  const discussionExchange =
    await import("../../dist/companion/src/discussion-acceptance.js");
  const readAcceptanceRequest = discussion
    ? discussionExchange.readDiscussionAcceptanceRequest
    : generated
      ? generatedExchange.readGeneratedAcceptanceRequest
      : catalogExchange.readAcceptanceRequest;
  const createAcceptanceResponse = discussion
    ? discussionExchange.createDiscussionAcceptanceResponse
    : generated
      ? generatedExchange.createGeneratedAcceptanceResponse
      : catalogExchange.createAcceptanceResponse;
  const readAcceptanceResponse = discussion
    ? discussionExchange.readDiscussionAcceptanceResponse
    : generated
      ? generatedExchange.readGeneratedAcceptanceResponse
      : catalogExchange.readAcceptanceResponse;
  const { requireAcceptanceRun, requireAcceptanceArtifact } =
    await import("../../dist/companion/src/acceptance-github.js");
  async function liveRun() {
    return requireAcceptanceRun(
      await api(`actions/runs/${expected.runId}`),
      expected,
      "request",
    );
  }
  const run = await liveRun();
  async function reviewedBranch() {
    const commit = await api(`commits/${encodeURIComponent(run.branch)}`);
    check(
      commit.sha === expected.sourceSha,
      "Request branch must still point at the reviewed source before inference or dispatch.",
    );
  }
  await reviewedBranch();
  const workflow = await api("actions/workflows/live-acceptance-response.yml");
  check(
    workflow.state === "active" && workflow.path === responseWorkflow,
    "Response workflow must already exist and be active on GitHub before spending quota.",
  );
  const localWorkflow = await command("git", [
    "show",
    `HEAD:${responseWorkflow}`,
  ]);
  const remoteWorkflow = await api(
    `contents/${responseWorkflow}?ref=${expected.sourceSha}`,
  );
  check(
    remoteWorkflow.type === "file" &&
      remoteWorkflow.encoding === "base64" &&
      Buffer.from(remoteWorkflow.content, "base64").toString("utf8") ===
        localWorkflow,
    "Response workflow must match the reviewed source.",
  );
  const listing = await api(
    `actions/runs/${expected.runId}/artifacts?per_page=100`,
  );
  check(
    Array.isArray(listing.artifacts) &&
      listing.total_count === listing.artifacts.length &&
      listing.total_count <= 100,
    "Artifact listing is incomplete or unbounded.",
  );
  const matches = listing.artifacts.filter(
    (artifact) =>
      artifact.name ===
      `mce-live-request-${expected.runId}-${expected.runAttempt}${suffix}`,
  );
  check(
    matches.length === 1,
    "Expected exactly one request artifact for this run attempt.",
  );
  const artifactId = requireAcceptanceArtifact(
    matches[0],
    expected.runId,
    expected.sourceSha,
    `mce-live-request-${expected.runId}-${expected.runAttempt}${suffix}`,
  );
  const archive = await command(
    "gh",
    apiArgs(`actions/artifacts/${artifactId}/zip`),
    { encoding: "buffer", maxBuffer: 32768 },
  );
  const temporary = await mkdtemp(join(tmpdir(), "mce-acceptance-"));
  let request;
  try {
    await chmod(temporary, 0o700);
    const file = join(temporary, "request.json");
    const handle = await open(file, "wx", 0o600);
    try {
      await handle.writeFile(requestBytes(archive));
    } finally {
      await handle.close();
    }
    request = readAcceptanceRequest(
      await boundedJson(file, discussion ? 16384 : 4096),
      expected,
      Date.now(),
    );
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
  const directory = resolve(options["--config"] ?? configDirectory()),
    receipts = join(directory, "acceptance");
  await mkdir(receipts, { recursive: true, mode: 0o700 });
  check(
    !(await lstat(receipts)).isSymbolicLink(),
    "Acceptance receipt directory must not be a symlink.",
  );
  await chmod(receipts, 0o700);
  let response, resultPath;
  if (options["--publish"]) {
    resultPath = resolve(options["--publish"]);
    response = readAcceptanceResponse(
      await boundedJson(resultPath, 8192),
      requestEnvelope(request),
      Date.now(),
    );
    check(
      response.provenance.model === options["--model"],
      "Saved response model does not match --model.",
    );
  } else {
    // Recheck immediately before the durable, exclusive no-retry quota boundary.
    await liveRun();
    await reviewedBranch();
    check(
      (
        await command("git", ["status", "--porcelain", "--untracked-files=all"])
      ).trim() === "" &&
        (await command("git", ["rev-parse", "HEAD"])).trim() ===
          expected.sourceSha,
      "Reviewed checkout changed during preflight.",
    );
    readAcceptanceRequest(requestEnvelope(request), expected, Date.now());
    check(
      !generated || request.stage === stage,
      "Request artifact belongs to another generated stage.",
    );
    await privateWrite(join(receipts, `${request.digest}.started.json`), {
      requestDigest: request.digest,
      startedAt: Date.now(),
      model: options["--model"],
    });
    const { RequestError } = await import("../../dist/companion/src/types.js");
    const provider = new CodexPlanningProvider({
      configDir: directory,
      model: options["--model"],
      timeoutMs: Math.min(90_000, request.expiresAt - Date.now()),
      onUntrustedResult: async (value) => {
        const bytes = JSON.stringify(value).length;
        await privateWrite(join(receipts, `${request.digest}.untrusted.json`), {
          schemaVersion: 1,
          stage,
          requestDigest: request.digest,
          ...(bytes <= 65536 ? { result: value } : { oversize: true, bytes }),
        });
      },
    });
    check(
      !generated || request.stage === stage,
      "Request artifact belongs to another generated stage.",
    );
    const method = discussion
      ? "discuss"
      : stage === "behavior"
        ? "proposeBehavior"
        : stage === "geometry"
          ? "generateGeometry"
          : "plan";
    try {
      const plan = await provider[method](
        request.input,
        new AbortController().signal,
        request.memory,
      );
      const usage = provider.lastUsage;
      check(
        usage && typeof usage === "object",
        "Provider did not return token usage; no response was published.",
      );
      response = createAcceptanceResponse(requestEnvelope(request), plan, {
        model: options["--model"],
        sdkVersion: sdk.version,
        inputTokens: usage.input_tokens,
        cachedInputTokens: usage.cached_input_tokens,
        outputTokens: usage.output_tokens,
      });
      response = readAcceptanceResponse(
        response,
        requestEnvelope(request),
        Date.now(),
      );
      resultPath = join(receipts, `${request.digest}.response.json`);
      await privateWrite(resultPath, response);
      console.log(`Validated response saved: ${resultPath}`);
    } catch (error) {
      const failure = {
        schemaVersion: 1,
        stage,
        requestDigest: request.digest,
        category: provider.lastDiagnostic,
        usage: provider.lastUsage,
        error:
          error instanceof RequestError
            ? error.message
            : "Local validation or publication preparation failed.",
      };
      console.error(
        `Local acceptance failure (${failure.category}): ${failure.error}`,
      );
      await privateWrite(
        join(receipts, `${request.digest}.failure.json`),
        failure,
      );
      throw error;
    }
  }
  const current = await liveRun();
  await reviewedBranch();
  check(
    current.branch === run.branch,
    "Request branch changed before publishing.",
  );
  response = readAcceptanceResponse(
    response,
    requestEnvelope(request),
    Date.now(),
  );
  const body = JSON.stringify({
    ref: run.branch,
    inputs: {
      request_run: expected.runId,
      request_attempt: String(expected.runAttempt),
      source_sha: expected.sourceSha,
      result: JSON.stringify(response),
      stage,
      scenario,
    },
  });
  const input = join(
    receipts,
    `${request.digest}.${process.pid}.dispatch.json`,
  );
  await privateWrite(input, JSON.parse(body));
  try {
    await command(
      "gh",
      apiArgs("actions/workflows/live-acceptance-response.yml/dispatches", [
        "--method",
        "POST",
        "--input",
        input,
      ]),
    );
  } finally {
    await rm(input, { force: true });
  }
  console.log(
    `Response dispatched for request ${expected.runId} attempt ${expected.runAttempt}. Await GitHub acceptance evidence.`,
  );
}
function requestEnvelope({ input: _input, memory: _memory, ...envelope }) {
  return envelope;
}
main().catch(() => {
  // Never print raw gh responses, SDK events, environment or nested error payloads.
  console.error(
    "Acceptance responder stopped. Check reviewed HEAD, explicit arguments, GitHub provenance/workflow availability, artifact expiry, and local sign-in. A started receipt forbids another model call; use --publish FILE only for an already saved valid response.",
  );
  process.exitCode = 1;
});
