import { plannerConfig } from "../companion/src/provider.js";
import { makeReferenceBlueprint } from "../companion/src/blueprint.js";
import { discussionGrounding } from "../companion/src/discussion.js";
import test from "node:test";
import assert from "node:assert/strict";
import { validateDiscussionReply } from "../companion/src/discussion.js";

test("discussion is bounded read-only advice with references only to supplied project sources", () => {
  const reply = {
    schemaVersion: 1,
    kind: "explanation",
    text: "Your owner-authored goal asks for a six-block entrance; this is not a world measurement.",
    basis: ["note:one"],
  };
  const accepted = validateDiscussionReply(reply, new Set(["note:one"]));
  assert.equal(accepted.text, reply.text);
  assert.equal(Object.isFrozen(accepted), true);
  assert.equal(Object.isFrozen(accepted.basis), true);
  for (const patch of [
    { schemaVersion: 2 },
    { kind: "build" },
    { text: " " },
    { text: "x".repeat(2001) },
    { text: "bad\u0000text" },
    { basis: ["private:inventory"] },
    { basis: ["note:one", "note:one"] },
    { commands: ["setblock"] },
    { verified: "passed" },
    { authority: true },
  ])
    assert.throws(() =>
      validateDiscussionReply({ ...reply, ...patch }, new Set(["note:one"])),
    );
  assert.equal(
    validateDiscussionReply(
      {
        ...reply,
        kind: "clarification",
        text: "Which component do you mean?",
        basis: [],
      },
      new Set(),
    ).kind,
    "clarification",
  );
});

test("discussion supplies intended component sources and labels missing observation instead of inventing tests", () => {
  const blueprint = makeReferenceBlueprint({
    request: "reference",
    projectId: "project-a",
    worldId: "world-a",
    dimension: "minecraft:overworld",
    anchor: { x: 0, y: 100, z: 0 },
    rotation: 0,
  });
  const knowledge = {
    schemaVersion: 1 as const,
    revision: 0,
    notes: [],
    hasEarlier: false,
  };
  const grounding = discussionGrounding(
    "Entrance",
    1,
    blueprint,
    knowledge,
    "root",
  );
  const context = JSON.parse(grounding.text);
  assert.equal(context.worldEvidence, "not-inspected");
  assert.equal(context.selectedComponent, "root");
  assert.equal(grounding.availableBasis.has("component:root"), true);
  assert.equal(
    context.sources.find(
      (source: { id: string }) => source.id === "component:root",
    ).data.blocks.length,
    blueprint.blocks.length,
    "A standalone logical root contains every physical block group",
  );
  assert.throws(
    () =>
      discussionGrounding(
        "Entrance",
        1,
        blueprint,
        knowledge,
        blueprint.blocks[0]!.component,
      ),
    "Internal recipe groups are not selectable logical components",
  );
  assert.equal(
    context.sources.find(
      (source: { id: string }) => source.id === "component:root",
    ).kind,
    "intended-component",
  );
  assert.throws(() =>
    discussionGrounding(
      "Entrance",
      1,
      blueprint,
      knowledge,
      "other-project-component",
    ),
  );
  assert.equal(
    discussionGrounding("Empty", 0, undefined, knowledge).availableBasis.size,
    1,
  );
  const config = plannerConfig("discussion");
  assert.equal(config.forced_login_method, "chatgpt");
  assert.match(String(config.developer_instructions), /read-only/);
  assert.match(
    String(config.developer_instructions),
    /No fresh world inspection/,
  );
});
