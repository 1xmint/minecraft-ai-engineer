import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateAnalogEvidence } from "../../dist/companion/src/analog-evidence.js";
import { validateAnalogTrace } from "../../dist/companion/src/analog-trace.js";
import { validateAnalogIntention } from "../../dist/companion/src/analog-intention.js";

if (process.env.CI !== "true")
  throw new Error("Replay native observations on GitHub CI, not Josh's PC.");
const [, , log] = process.argv;
if (!log) throw new Error("Supply this run's native GameTest log.");
const cases = [
  [0, 0, 0],
  [1, 0, 0],
  [15, 0, 0],
  [8, 3, 0],
  [8, 0, 3],
  [8, 8, 0],
  [8, 0, 8],
  [8, 9, 0],
  [8, 0, 9],
  [8, 3, 7],
  [8, 7, 3],
  [3, 15, 0],
  [15, 14, 1],
  [15, 1, 14],
  [1, 1, 1],
  [0, 15, 15],
  [15, 15, 15],
  [0, 0, 0],
];
const rotations = [
  "NONE",
  "CLOCKWISE_90",
  "CLOCKWISE_180",
  "COUNTERCLOCKWISE_90",
];
const lines = readFileSync(log, "utf8").split(/\r?\n/);
const suiteModes = new Set();
const suiteMarker = "ENGINEER_ANALOG_SUITE ";
for (const line of lines) {
  const start = line.indexOf(suiteMarker);
  if (start < 0) continue;
  const raw = JSON.parse(line.slice(start + suiteMarker.length).trim());
  assert.ok(raw.mode === "compare" || raw.mode === "subtract");
  assert.ok(!suiteModes.has(raw.mode));
  assert.equal(validateAnalogEvidence(raw.cases, raw.mode, true).length, 66);
  suiteModes.add(raw.mode);
}
assert.equal(
  suiteModes.size,
  2,
  "Both actual native 66-case suites must pass independent replay",
);
const intentionKeys = new Set();
const intentionMarker = "ENGINEER_ANALOG_INTENTION ";
const nativeIdentity = "db1175eb-1559-4cca-a740-d63f224a4068";
for (const line of lines) {
  const start = line.indexOf(intentionMarker);
  if (start < 0) continue;
  const raw = JSON.parse(line.slice(start + intentionMarker.length).trim());
  const value = validateAnalogIntention(raw, {
    worldId: "test",
    dimension: "minecraft:overworld",
    projectId: nativeIdentity,
  });
  assert.equal(value.id, nativeIdentity);
  assert.equal(value.revision, 1);
  assert.deepEqual(value.anchor, { x: 24, y: 2, z: 24 });
  assert.ok([0, 90, 180, 270].includes(value.rotation));
  assert.ok(["compare", "subtract"].includes(value.analog.mode));
  const key = `${value.analog.mode}/${value.rotation}`;
  assert.ok(
    !intentionKeys.has(key),
    "Every built native analog intention appears exactly once",
  );
  intentionKeys.add(key);
}
assert.equal(
  intentionKeys.size,
  8,
  "Both modes in all four rotations must use companion-readable canonical intention",
);
const groups = Object.fromEntries(
  rotations.map((rotation) => [rotation, { compare: [], subtract: [] }]),
);
const marker = "ENGINEER_ANALOG_TRANSFER ";
for (const line of lines) {
  let start = line.indexOf(marker);
  let rotation = "NONE";
  let payload;
  if (start >= 0) payload = line.slice(start + marker.length).trim();
  else {
    const rotatedMarker = "ENGINEER_ANALOG_ROTATED_TRANSFER ";
    start = line.indexOf(rotatedMarker);
    if (start < 0) continue;
    const rest = line.slice(start + rotatedMarker.length).trim();
    const split = rest.indexOf(" ");
    rotation = rest.slice(0, split);
    assert.ok(rotations.includes(rotation) && rotation !== "NONE");
    payload = rest.slice(split + 1);
  }
  const value = JSON.parse(payload);
  assert.ok(value?.mode === "compare" || value?.mode === "subtract");
  groups[rotation][value.mode].push(value);
}
for (const rotation of rotations)
  for (const mode of ["compare", "subtract"]) {
    assert.equal(
      groups[rotation][mode].length,
      cases.length,
      `${rotation}/${mode}: every native case must arrive exactly once`,
    );
    for (let index = 0; index < cases.length; index++)
      assert.equal(
        validateAnalogTrace(
          groups[rotation][mode][index],
          { mode, requested: cases[index] },
          true,
        ).verdict,
        "passed",
      );
  }
console.log(
  JSON.stringify({
    kind: "native-analog-cross-runtime-replay",
    nativeSuiteCases: 132,
    canonicalIntentions: intentionKeys.size,
    windows: 144,
    stableSamples: 432,
    modes: ["compare", "subtract"],
    rotations,
    authority: "none; one transfer window is not a whole-circuit pass",
  }),
);
