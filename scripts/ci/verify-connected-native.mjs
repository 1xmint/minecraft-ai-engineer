import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { readConnectedTrace } from "../../dist/companion/src/connected-trace.js";
import { readConnectedIntention } from "../../dist/companion/src/connected-intention.js";

if (process.env.CI !== "true")
  throw new Error("Replay native observations on GitHub CI, not Josh's PC.");
if (!process.argv[2]) throw new Error("Supply this run's native GameTest log.");
const lines = readFileSync(process.argv[2], "utf8").split(/\r?\n/);
const reports = (marker) =>
  lines.flatMap((line) => {
    const index = line.indexOf(marker);
    return index < 0 ? [] : [JSON.parse(line.slice(index + marker.length))];
  });
const canonical = (value) => {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object")
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`)
      .join(",")}}`;
  return JSON.stringify(value);
};
const rotations = new Set([
  "NONE",
  "CLOCKWISE_90",
  "CLOCKWISE_180",
  "COUNTERCLOCKWISE_90",
]);
const vectors = [0, 1, 2, 3, 3, 2, 1, 0];
const seen = new Set();
for (const report of reports("ENGINEER_HALF_ADDER_PROBE ")) {
  assert.ok(rotations.has(report.rotation) && !seen.has(report.rotation));
  seen.add(report.rotation);
  assert.equal(report.schemaVersion, 1);
  assert.equal(
    report.authority,
    "native prototype only; no admitted project or functional pass",
  );
  assert.equal(report.cells, 436);
  assert.equal(report.geometry.length, report.cells);
  assert.equal(report.samples.length, 448);
  assert.equal(report.cases.length, 8);
  const digest = createHash("sha256")
    .update(
      `connected-half-adder-v1|minecraft=26.3|${canonical(report.graph)}|${canonical(report.geometry)}`,
    )
    .digest("hex");
  assert.equal(report.graphDigest, digest);
  for (const [index, vector] of vectors.entries()) {
    const a = (vector & 2) !== 0;
    const b = (vector & 1) !== 0;
    const trace = readConnectedTrace(report.cases[index], {
      graphDigest: digest,
      a,
      b,
    });
    assert.equal(trace.verdict, "passed");
    const frames = report.samples.slice(index * 56, (index + 1) * 56);
    assert.deepEqual(
      trace.samples,
      frames.map((frame, elapsedTicks) => {
        assert.equal(frame.case, index);
        assert.equal(frame.vector, vector);
        assert.equal(frame.a, a);
        assert.equal(frame.b, b);
        assert.equal(frame.elapsedTicks, elapsedTicks);
        assert.equal(Object.keys(frame.ports).length, 20);
        return { gameTick: frame.gameTick, elapsedTicks, ports: frame.ports };
      }),
    );
  }
}
assert.deepEqual(seen, rotations);
const intentions = reports("ENGINEER_CONNECTED_INTENTION ");
assert.equal(intentions.length, 4);
const angles = new Set();
for (const raw of intentions) {
  const intention = readConnectedIntention(raw, {
    worldId: "test",
    dimension: "minecraft:overworld",
    projectId: "db1175eb-1559-4cca-a740-d63f224a4068",
  });
  assert.ok(!angles.has(intention.rotation));
  angles.add(intention.rotation);
}
assert.deepEqual(angles, new Set([0, 90, 180, 270]));
assert.deepEqual(reports("ENGINEER_HALF_ADDER_NEGATIVE "), [
  {
    vector: 2,
    expectedSum: true,
    observedSum: false,
    observedCarry: false,
    authority: "none",
  },
]);
console.log(
  JSON.stringify({
    kind: "actual-native-connected-cross-runtime-replay",
    rotations: 4,
    cases: 32,
    frames: 1792,
    authority: "fixture observations only; no project pass",
  }),
);
