// Real HTTP gateway and SQLite persistence for the multiplayer proof. Inference
// is unavailable: generated boundary fixtures are deterministic data, never SDK calls.
import { join } from "node:path";
import { isDeepStrictEqual } from "node:util";
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import { setTimeout as delay } from "node:timers/promises";
import { loadConfig } from "../../dist/companion/src/config.js";
import { MemoryStore } from "../../dist/companion/src/memory.js";
import { createCompanionServer } from "../../dist/companion/src/server.js";

if (process.env.CI !== "true" || !process.argv[2]) {
  throw new Error(
    "This fixture runs only in GitHub CI with a private config directory.",
  );
}
// Recorded raw signed-in reply; replay is deterministic and never invokes inference.
const generatedFixture = JSON.parse(
  readFileSync(
    new URL("../../tests/fixtures/live-generated-buffer.json", import.meta.url),
    "utf8",
  ),
);
// Recorded successful structural reply; replay never invokes a model.
const structuralFixture = JSON.parse(
  readFileSync(
    new URL(
      "../../tests/fixtures/live-structural-buffer.json",
      import.meta.url,
    ),
    "utf8",
  ),
);
// Fixed request; recorded data below is replayed without signed-in inference.
const truthRequest =
  "CI generated AND: named inputs a and b; settle within 20 game ticks.";
// Exact successful signed-in Boolean reply; deterministic replay never invokes inference.
const truthFixture = JSON.parse(
  readFileSync(
    new URL("../../tests/fixtures/live-boolean-and.json", import.meta.url),
    "utf8",
  ),
);

const config = loadConfig(process.argv[2]);
const store = new MemoryStore(join(config.directory, "projects.sqlite"));
const server = createCompanionServer({
  token: config.token,
  store,
  // Fixed synthetic stages can burst across several projects in this suite.
  // This provider cannot invoke the SDK; production retains its default4/minute.
  actorPlansPerMinute: 24,
  serverPlansPerHour: 64,
  provider: {
    async interpret(input, _signal, context, capabilities) {
      const source = JSON.parse(context);
      if (source.worldEvidence !== "not-inspected")
        throw new Error("Invented natural-request world evidence");
      if (input.message === "Make that other one smaller.")
        return {
          schemaVersion: 1,
          kind: "clarification",
          action: null,
          componentId: null,
          text: "Which component do you mean? Name it or choose its lesson first.",
          basis: ["project"],
        };
      if (input.message === "Gather survival materials for this machine.")
        return {
          schemaVersion: 1,
          kind: "unsupported",
          action: null,
          componentId: null,
          text: "Survival gathering is not available yet; this request has not started world work.",
          basis: ["project"],
        };
      if (input.message === "CI cancel this interpretation.") {
        const coordination = join(config.directory, "..", "coordination");
        writeFileSync(
          join(coordination, "natural-request-dispatched"),
          "ready",
        );
        const deadline = Date.now() + 10_000;
        while (!_signal.aborted && Date.now() < deadline) await delay(20);
        if (!_signal.aborted)
          throw new Error("Exact stop did not cancel interpretation transport");
        writeFileSync(
          join(coordination, "natural-request-cancelled.done"),
          "passed",
        );
        return {
          schemaVersion: 1,
          kind: "clarification",
          action: null,
          componentId: null,
          text: "CI obsolete interpretation must never display",
          basis: [],
        };
      }
      if (input.message === "CI follow up on selected project status.") {
        const recent = source.sources.find(
          (entry) => entry.id === "recent-action",
        );
        if (
          recent?.kind !== "server-dispatched-intention" ||
          recent.data.action !== "status" ||
          recent.data.componentId !== null ||
          recent.data.result !== "not-supplied" ||
          recent.data.authority !== "none"
        )
          throw new Error(
            "Follow-up lacks the bounded previous action intention",
          );
        return {
          schemaVersion: 1,
          kind: "action",
          action: "status",
          componentId: null,
          text: "Synthetic grounded follow-up.",
          basis: ["recent-action"],
        };
      }
      const action =
        input.message === "CI choose a one-cell sticky shutter." ||
        input.message === "CI cancel a pending shutter selection."
          ? "plan"
          : input.message ===
              "CI generated buffer: on after 12 game ticks, off after 16 game ticks."
            ? "design"
            : input.message === "CI report selected project status."
              ? "status"
              : input.message === "CI propose stopping the selected project."
                ? "stop"
                : input.message === "CI build selected lamp."
                  ? "build"
                  : input.message === "CI test selected lamp."
                    ? "test"
                    : input.message === "CI undo selected lamp."
                      ? "undo"
                      : null;
      const componentId =
        action === "build" || action === "test" ? "root" : null;
      if (!action || !capabilities.get(action)?.has(componentId))
        throw new Error("Unexpected natural-request fixture choice");
      return {
        schemaVersion: 1,
        kind: "action",
        action,
        componentId,
        text: "Synthetic typed choice; not model inference.",
        basis: ["project"],
      };
    },
    async discuss(input, _signal, context) {
      const source = JSON.parse(context);
      if (source.worldEvidence !== "not-inspected")
        throw new Error("Discussion fixture has invented world evidence");
      if (input.message === "CI cancelled entrance discussion.") {
        const coordination = join(config.directory, "..", "coordination");
        if (source.knowledgeRevision !== 2 || !existsSync(coordination))
          throw new Error("Unexpected cancellation fixture scope");
        writeFileSync(join(coordination, "discussion-dispatched"), "ready");
        const deadline = Date.now() + 10_000;
        while (!_signal.aborted && Date.now() < deadline) await delay(20);
        if (!_signal.aborted)
          throw new Error("Native stop failed to cancel discussion transport");
        writeFileSync(
          join(coordination, "discussion-cancelled.done"),
          "passed",
        );
        // Deliberately ignore abort and return late; gateway must still reject it.
        return {
          schemaVersion: 1,
          kind: "explanation",
          text: "CI obsolete discussion reply must never display.",
          basis: ["project"],
        };
      }
      const expected =
        input.message === "CI explain entrance decision."
          ? {
              revision: 2,
              kind: "decision",
              text: "Use a visible lamp for the output.",
            }
          : input.message === "CI explain entrance constraint."
            ? {
                revision: 3,
                kind: "constraint",
                text: "Preserve the entrance clearance.",
              }
            : null;
      if (!expected || source.knowledgeRevision !== expected.revision)
        throw new Error(
          "Unexpected discussion fixture request or knowledge version",
        );
      const note = source.sources.find(
        (item) =>
          item.kind === "owner-authored-requirement" &&
          item.data.kind === expected.kind &&
          item.data.text === expected.text,
      );
      if (!note)
        throw new Error("Discussion lacks the explicit owner requirement");
      return {
        schemaVersion: 1,
        kind: "explanation",
        text: `Owner-authored ${expected.kind}: ${expected.text} This is a requirement, not a measured world fact.`,
        basis: ["project", note.id],
      };
    },
    async proposeBehavior(input) {
      if (
        input.request ===
        "CI generated buffer: on after 12 game ticks, off after 16 game ticks."
      )
        writeFileSync(
          join(
            config.directory,
            "..",
            "coordination",
            "natural-design-behavior-dispatched.done",
          ),
          "passed",
        );
      if (input.request === truthRequest)
        return structuredClone(truthFixture.proposal);
      if (
        ![
          "CI generated buffer: on after 12 game ticks, off after 16 game ticks.",
          "CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.",
        ].includes(input.request)
      )
        throw new Error("Unexpected generated fixture request");
      return structuredClone(generatedFixture.proposal);
    },
    async generateGeometry(input) {
      if (input.request === truthRequest) {
        if (!isDeepStrictEqual(input.behavior, truthFixture.proposal.behavior))
          throw new Error("Confirmed Boolean intent changed");
        return structuredClone(truthFixture.geometry);
      }

      if (
        ![
          "CI generated buffer: on after 12 game ticks, off after 16 game ticks.",
          "CI structural buffer: replace the two repeaters with three two-tick repeaters; keep 12/16 game-tick behavior.",
        ].includes(input.request) ||
        input.behavior.risingGameTicks !== 12 ||
        input.behavior.fallingGameTicks !== 16
      )
        throw new Error("Original request or confirmed intent changed");
      if (input.request.startsWith("CI structural buffer:"))
        return structuredClone(structuralFixture.geometry);
      return structuredClone(generatedFixture.geometry);
    },
    async plan(input, signal) {
      if (input.request === "CI cancel a pending shutter selection.") {
        const coordination = join(config.directory, "..", "coordination");
        writeFileSync(
          join(coordination, "natural-catalog-late-dispatched"),
          "ready",
        );
        const deadline = Date.now() + 10_000;
        while (!signal.aborted && Date.now() < deadline) await delay(20);
        if (!signal.aborted)
          throw new Error(
            "Changed project selection did not cancel catalog transport",
          );
        writeFileSync(
          join(coordination, "natural-catalog-cancelled.done"),
          "passed",
        );
        return {
          template: "sticky-shutter",
          explanation: "CI obsolete catalog selection",
          parameters: { delay: 1 },
          rotation: 0,
        };
      }
      if (input.request === "CI choose a one-cell sticky shutter.") {
        writeFileSync(
          join(
            config.directory,
            "..",
            "coordination",
            "natural-catalog-dispatched.done",
          ),
          "passed",
        );
        return {
          template: "sticky-shutter",
          explanation:
            "Synthetic canonical shutter preview; untested and no construction authorized.",
          parameters: { delay: 1 },
          rotation: 0,
        };
      }
      writeFileSync(
        join(config.directory, "inference-attempted"),
        "unexpected provider call",
      );
      throw new Error("Inference is disabled in public CI");
    },
  },
});
server.listen(config.port, "127.0.0.1", () =>
  process.stdout.write("Companion ready\n"),
);
server.on("error", () => {
  process.stderr.write("CI companion could not start\n");
  process.exitCode = 1;
});
const close = () => {
  server.close(() => store.close());
  server.closeAllConnections();
};
process.once("SIGTERM", close);
process.once("SIGINT", close);
