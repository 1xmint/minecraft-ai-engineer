import {
  readNaturalAcceptanceRequest,
  readNaturalAcceptanceResponse,
} from "../../dist/companion/src/natural-acceptance.js";
import {
  readDiscussionAcceptanceRequest,
  readDiscussionAcceptanceResponse,
} from "../../dist/companion/src/discussion-acceptance.js";
// GitHub transports bounded synthetic data; credentials never enter an artifact.
import { execFileSync } from "node:child_process";
import {
  readFileSync,
  writeFileSync,
  renameSync,
  existsSync,
  mkdirSync,
} from "node:fs";
import { join, resolve } from "node:path";
import { setTimeout } from "node:timers/promises";
import {
  readAcceptanceRequest as readCatalogRequest,
  readAcceptanceResponse as readCatalogResponse,
} from "../../dist/companion/src/acceptance-exchange.js";
import {
  requireAcceptanceRun,
  requireAcceptanceArtifact,
} from "../../dist/companion/src/acceptance-github.js";
import {
  readGeneratedAcceptanceRequest,
  readGeneratedAcceptanceResponse,
} from "../../dist/companion/src/generated-acceptance.js";
const stage = process.argv[3] ?? process.env.MCE_ACCEPTANCE_STAGE ?? "catalog";
if (
  !["catalog", "behavior", "geometry", "discussion", "natural"].includes(stage)
)
  throw new Error("Unknown acceptance stage");
const natural = stage === "natural",
  discussion = stage === "discussion",
  generated = stage === "behavior" || stage === "geometry",
  suffix = stage !== "catalog" ? `-${stage}` : "";
const readAcceptanceRequest = natural
  ? readNaturalAcceptanceRequest
  : discussion
    ? readDiscussionAcceptanceRequest
    : generated
      ? readGeneratedAcceptanceRequest
      : readCatalogRequest;
const readAcceptanceResponse = natural
  ? readNaturalAcceptanceResponse
  : discussion
    ? readDiscussionAcceptanceResponse
    : generated
      ? readGeneratedAcceptanceResponse
      : readCatalogResponse;
const repo = "1xmint/minecraft-ai-engineer",
  base = `repos/${repo}/`,
  directory =
    stage !== "catalog"
      ? resolve("mod/build/live-exchange", stage)
      : resolve("mod/build/live-exchange"),
  statusFile = resolve("mod/build/live-status");
function check(ok) {
  if (!ok)
    throw new Error(
      "Synthetic acceptance transport rejected data or lifecycle",
    );
}
check(
  process.env.CI === "true" &&
    process.env.GITHUB_ACTIONS === "true" &&
    process.env.GITHUB_REPOSITORY === repo &&
    process.env.GITHUB_ACTOR === "1xmint",
);
const mode = process.argv[2];
function api(route) {
  return JSON.parse(
    execFileSync(
      "gh",
      [
        "api",
        "--hostname",
        "github.com",
        "-H",
        "X-GitHub-Api-Version: 2026-03-10",
        base + route,
      ],
      {
        encoding: "utf8",
        maxBuffer: 2_000_000,
        stdio: ["ignore", "pipe", "pipe"],
      },
    ),
  );
}
function data(artifact, parent, sha, name, file, max) {
  const id = requireAcceptanceArtifact(artifact, parent, sha, name);
  const zip = execFileSync(
    "gh",
    [
      "api",
      "--hostname",
      "github.com",
      "-H",
      "X-GitHub-Api-Version: 2026-03-10",
      base + `actions/artifacts/${id}/zip`,
    ],
    { maxBuffer: 16384, stdio: ["ignore", "pipe", "pipe"] },
  );
  const script =
    "import sys,io,zipfile,stat\nz=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()))\na=z.infolist()\nassert len(a)==1\ni=a[0]\nassert i.filename==sys.argv[1] and not i.is_dir() and i.file_size<=int(sys.argv[2]) and not (i.flag_bits&1)\nm=(i.external_attr>>16)&0o170000\nassert m in (0,stat.S_IFREG)\nb=z.read(i)\nassert len(b)<=int(sys.argv[2])\nsys.stdout.buffer.write(b)";
  return JSON.parse(
    execFileSync("python3", ["-c", script, file, String(max)], {
      input: zip,
      maxBuffer: max,
      stdio: ["pipe", "pipe", "pipe"],
    }).toString("utf8"),
  );
}
function envelope(run, attempt, sha) {
  return {
    repository: repo,
    workflow: ".github/workflows/live-acceptance.yml",
    sourceSha: sha,
    runId: String(run),
    runAttempt: Number(attempt),
    scenario: natural
      ? "natural-engineering-owner-action-v1"
      : discussion
        ? "project-discussion-owner-notes-v1"
        : process.env.MCE_BOOLEAN_ACCEPTANCE === "true"
          ? "generated-boolean-owner-confirmation-v1"
          : process.env.MCE_STRUCTURAL_ACCEPTANCE === "true"
            ? "structural-buffer-owner-confirmation-v1"
            : generated
              ? "generated-buffer-owner-confirmation-v1"
              : "signal-lamp-client-handoff-v1",
  };
}
function own() {
  return envelope(
    process.env.GITHUB_RUN_ID,
    process.env.GITHUB_RUN_ATTEMPT,
    process.env.GITHUB_SHA,
  );
}
function childAlive() {
  if (existsSync(resolve("mod/build/live-exchange/unexpected-inference")))
    throw new Error(
      "Synthetic provider refused the fixed scenario before the exchange completed",
    );
  if (existsSync(statusFile))
    throw new Error("Native acceptance stopped before the exchange completed");
}
function readRequest() {
  const raw = JSON.parse(readFileSync(join(directory, "request.json"), "utf8"));
  readAcceptanceRequest(raw, own(), Date.now());
  check(stage === "catalog" || raw.stage === stage);
  return raw;
}
async function main() {
  if (mode === "wait-request") {
    const deadline = Date.now() + 10 * 60_000;
    while (!existsSync(join(directory, "request.json"))) {
      childAlive();
      check(Date.now() < deadline);
      await setTimeout(1000);
    }
    readRequest();
    return;
  }
  if (mode === "wait-response") {
    const request = readRequest(),
      name = `mce-live-response-${request.run.runId}-${request.run.runAttempt}${suffix}`;
    while (true) {
      childAlive();
      readAcceptanceRequest(request, own(), Date.now());
      requireAcceptanceRun(
        api(`actions/runs/${request.run.runId}`),
        request.run,
        "request",
      );
      const listing = api(`actions/artifacts?name=${name}&per_page=100`);
      check(Array.isArray(listing.artifacts) && listing.total_count <= 100);
      for (const artifact of listing.artifacts) {
        let parent;
        try {
          parent = requireAcceptanceRun(
            api(`actions/runs/${artifact.workflow_run.id}`),
            request.run,
            "response",
          );
        } catch {
          continue;
        }
        const result = data(
          artifact,
          parent.id,
          request.run.sourceSha,
          name,
          "response.json",
          8192,
        );
        readAcceptanceResponse(result, request, Date.now());
        writeFileSync(join(directory, "response.tmp"), JSON.stringify(result));
        renameSync(
          join(directory, "response.tmp"),
          join(directory, "response.json"),
        );
        return;
      }
      await setTimeout(3000);
    }
  }
  if (mode === "publish-response") {
    const event = JSON.parse(
        readFileSync(process.env.GITHUB_EVENT_PATH, "utf8"),
      ),
      inputs = event.inputs;
    check(
      inputs.scenario ===
        (natural
          ? "natural"
          : discussion
            ? "discussion"
            : generated
              ? process.env.MCE_BOOLEAN_ACCEPTANCE === "true"
                ? "boolean"
                : process.env.MCE_STRUCTURAL_ACCEPTANCE === "true"
                  ? "structural"
                  : "generated"
              : "catalog"),
    );
    const expected = envelope(
      inputs.request_run,
      inputs.request_attempt,
      inputs.source_sha,
    );
    check(expected.sourceSha === process.env.GITHUB_SHA);
    requireAcceptanceRun(
      api(`actions/runs/${expected.runId}`),
      expected,
      "request",
    );
    const name = `mce-live-request-${expected.runId}-${expected.runAttempt}${suffix}`,
      listing = api(`actions/runs/${expected.runId}/artifacts?per_page=100`);
    check(Array.isArray(listing.artifacts) && listing.total_count <= 100);
    const matches = listing.artifacts.filter((a) => a.name === name);
    check(matches.length === 1);
    const request = data(
      matches[0],
      expected.runId,
      expected.sourceSha,
      name,
      "request.json",
      natural || discussion ? 16384 : 4096,
    );
    readAcceptanceRequest(request, expected, Date.now());
    const result = JSON.parse(inputs.result);
    readAcceptanceResponse(result, request, Date.now());
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, "response.json"), JSON.stringify(result));
    return;
  }
  if (mode === "wait-finish") {
    const deadline = Date.now() + 30 * 60_000;
    while (!existsSync(statusFile)) {
      check(Date.now() < deadline);
      await setTimeout(2000);
    }
    check(readFileSync(statusFile, "utf8").trim() === "0");
    if (discussion || natural) {
      const request = JSON.parse(
          readFileSync(join(directory, "request.json"), "utf8"),
        ),
        result = JSON.parse(
          readFileSync(join(directory, "response.json"), "utf8"),
        ),
        accepted = JSON.parse(
          readFileSync(join(directory, "accepted.json"), "utf8"),
        );
      readAcceptanceRequest(request, own(), accepted.acceptedAt);
      readAcceptanceResponse(result, request, accepted.acceptedAt);
      check(
        accepted.requestDigest === request.digest &&
          accepted.resultDigest === result.digest &&
          accepted.stage === stage,
      );
      for (const marker of [
        "knowledge-no-world-changes.done",
        "knowledge-bob.done",
        "knowledge-bob-denied.done",
        "knowledge-recovery.done",
        ...(natural
          ? ["live-natural-model.done", "live-natural-recovery.done"]
          : ["live-discussion-model.done", "live-discussion-recovery.done"]),
      ])
        check(
          readFileSync(
            `mod/build/multiplayer-evidence/coordination/${marker}`,
            "utf8",
          ).trim() === "passed",
        );
      return;
    }
    if (generated) {
      const envelopes = [];
      for (const phase of ["behavior", "geometry"]) {
        const location = resolve("mod/build/live-exchange", phase),
          request = JSON.parse(
            readFileSync(join(location, "request.json"), "utf8"),
          ),
          result = JSON.parse(
            readFileSync(join(location, "response.json"), "utf8"),
          ),
          accepted = JSON.parse(
            readFileSync(join(location, "accepted.json"), "utf8"),
          );
        readGeneratedAcceptanceRequest(request, own(), accepted.acceptedAt);
        readGeneratedAcceptanceResponse(result, request, accepted.acceptedAt);
        check(
          request.stage === phase &&
            accepted.stage === phase &&
            accepted.requestDigest === request.digest &&
            accepted.resultDigest === result.digest,
        );
        envelopes.push({ request, result, accepted });
      }
      check(
        JSON.stringify(envelopes[0].result.result.behavior) ===
          JSON.stringify(envelopes[1].request.behavior) &&
          envelopes[0].accepted.acceptedAt <= envelopes[1].request.createdAt,
      );
      for (const marker of process.env.MCE_BOOLEAN_ACCEPTANCE === "true"
        ? [
            "truth-interrupted.done",
            "truth-recovery.done",
            "live-boolean-model.done",
            "live-boolean-recovery.done",
          ]
        : [
            "generated-confirmation.done",
            "generated-confirmation-recovery.done",
            "live-generated-model.done",
            "player-generated-section.done",
            ...(process.env.MCE_STRUCTURAL_ACCEPTANCE === "true"
              ? ["live-structural-model.done", "live-structural-recovery.done"]
              : []),
          ])
        check(
          readFileSync(
            `mod/build/multiplayer-evidence/coordination/${marker}`,
            "utf8",
          ).trim() === "passed",
        );
      return;
    }
    const proof = JSON.parse(
      readFileSync(join(directory, "accepted.json"), "utf8"),
    );
    const request = JSON.parse(
      readFileSync(join(directory, "request.json"), "utf8"),
    );
    readAcceptanceRequest(request, own(), proof.acceptedAt);
    const result = JSON.parse(
        readFileSync(join(directory, "response.json"), "utf8"),
      ),
      accepted = JSON.parse(
        readFileSync(join(directory, "accepted.json"), "utf8"),
      );
    readAcceptanceResponse(result, request, accepted.acceptedAt);
    check(
      accepted.requestDigest === request.digest &&
        accepted.resultDigest === result.digest,
    );
    check(
      readFileSync(
        "mod/build/multiplayer-evidence/coordination/live-model-design.done",
        "utf8",
      ).trim() === "passed",
    );
    return;
  }
  throw new Error("Unknown acceptance transport action");
}
main().catch(() => {
  console.error(
    "Synthetic acceptance transport failed; no credentials or raw payloads were logged.",
  );
  process.exitCode = 1;
});
