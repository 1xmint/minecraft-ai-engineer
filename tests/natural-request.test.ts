import test from "node:test";
import assert from "node:assert/strict";
import {
  validateNaturalReply,
  type NaturalCapabilities,
} from "../companion/src/natural-request.js";

const capabilities: NaturalCapabilities = new Map<
  import("../companion/src/natural-request.js").NaturalAction,
  ReadonlySet<string | null>
>([
  ["diagnose", new Set(["entrance"])],
  ["status", new Set([null])],
]);
const sources = new Set(["project", "component:entrance"]);
const reply = {
  schemaVersion: 1,
  kind: "action",
  action: "diagnose",
  componentId: "entrance",
  text: "Inspect the selected entrance.",
  basis: ["component:entrance"],
};

test("an inferred inspection is a frozen choice of an advertised target, not execution authority", () => {
  const input = { ...reply, basis: [...reply.basis] };
  const accepted = validateNaturalReply(input, capabilities, sources);
  input.basis[0] = "private:inventory";
  input.action = "build";
  assert.equal(accepted.action, "diagnose");
  assert.deepEqual(accepted.basis, ["component:entrance"]);
  assert.ok(Object.isFrozen(accepted) && Object.isFrozen(accepted.basis));
  assert.throws(() => validateNaturalReply(reply, new Map(), sources));
});

test("model output cannot invent commands, authority, targets, or supporting knowledge", () => {
  for (const patch of [
    { action: "build" },
    { action: "run_shell" },
    { componentId: "other" },
    { componentId: null },
    { action: "status" },
    { componentId: "entrance;op bob" },
    { command: "setblock 0 0 0 air" },
    { permissions: ["BUILD"] },
    { coordinates: { x: 0, y: 0, z: 0 } },
    { nonce: "approved" },
    { requiresDelegation: false },
    { verified: true },
    { schemaVersion: 2 },
    { basis: [] },
    { basis: ["private:inventory"] },
    { basis: ["project", "project"] },
    { basis: Array(13).fill("project") },
    { text: " " },
    { text: "x".repeat(801) },
    { text: "bad\u0000text" },
    { kind: "clarification" },
    { kind: "unsupported" },
  ])
    assert.throws(() =>
      validateNaturalReply({ ...reply, ...patch }, capabilities, sources),
    );
});

test("ambiguous and unsupported requests may return only bounded read-only prose", () => {
  for (const kind of ["clarification", "unsupported"]) {
    const accepted = validateNaturalReply(
      {
        schemaVersion: 1,
        kind,
        action: null,
        componentId: null,
        text: "Which component do you mean?",
        basis: [],
      },
      new Map(),
      sources,
    );
    assert.equal(accepted.action, null);
    assert.equal(accepted.componentId, null);
  }
  const status = validateNaturalReply(
    { ...reply, action: "status", componentId: null, basis: ["project"] },
    capabilities,
    sources,
  );
  assert.equal(status.action, "status");
});
