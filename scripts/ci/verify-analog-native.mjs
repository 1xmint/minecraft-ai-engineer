import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateAnalogTrace } from "../../dist/companion/src/analog-trace.js";

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
const groups = { compare: [], subtract: [] };
const marker = "ENGINEER_ANALOG_TRANSFER ";
for (const line of readFileSync(log, "utf8").split(/\r?\n/)) {
  const start = line.indexOf(marker);
  if (start < 0) continue;
  const value = JSON.parse(line.slice(start + marker.length).trim());
  assert.ok(value?.mode === "compare" || value?.mode === "subtract");
  groups[value.mode].push(value);
}
for (const mode of ["compare", "subtract"]) {
  assert.equal(
    groups[mode].length,
    cases.length,
    `${mode}: every native case must arrive exactly once`,
  );
  for (let index = 0; index < cases.length; index++)
    assert.equal(
      validateAnalogTrace(
        groups[mode][index],
        { mode, requested: cases[index] },
        true,
      ).verdict,
      "passed",
    );
}
console.log(
  JSON.stringify({
    kind: "native-analog-cross-runtime-replay",
    windows: 36,
    stableSamples: 108,
    modes: ["compare", "subtract"],
    authority: "none; one transfer window is not a whole-circuit pass",
  }),
);
