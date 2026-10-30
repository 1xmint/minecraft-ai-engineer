import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  validateShutterTrace,
  shutterTraceDigest,
} from "../companion/src/shutter-trace.js";
const fixture = (name: string) =>
  JSON.parse(
    readFileSync(
      new URL(`../../tests/fixtures/${name}.json`, import.meta.url),
      "utf8",
    ),
  );
test("shared synthetic mechanical trace binds exact accepted phase, frame and identity", () => {
  const design = fixture("shutter-design"),
    trace = fixture("shutter-trace");
  assert.equal(
    validateShutterTrace(trace, design, true, true).verdict,
    "passed",
  );
  assert.equal(shutterTraceDigest(design, true), trace.intentionDigest);
  assert.throws(() => validateShutterTrace(trace, design, false, true));
  for (const patch of [
    { revision: 3 },
    { id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc" },
    { anchor: { x: 101, y: 100, z: 100 } },
  ])
    assert.throws(() =>
      validateShutterTrace(trace, { ...design, ...patch }, true, true),
    );
  for (const patch of [
    { stableGameTicks: 1 },
    { settleGameTicks: 1 },
    { authority: true },
    { intentionDigest: "forged" },
    { expected: "open" },
  ])
    assert.throws(() =>
      validateShutterTrace({ ...trace, ...patch }, design, true, true),
    );
});
test("matching endpoint, tick gaps, wrong aperture and entities cannot retain a mechanical pass", () => {
  const design = fixture("shutter-design"),
    trace = fixture("shutter-trace");
  const changes = [
    (v: any) => (v.samples = [v.samples.at(-1)]),
    (v: any) => v.samples.splice(9, 1),
    (v: any) => (v.samples[12].states.aperture = "minecraft:air"),
    (v: any) => (v.samples[2].states.aperture = "minecraft:glass"),
    (v: any) => (v.samples[8].entities.front = "moving-piston"),
    (v: any) => (v.samples[0].entities.base = "other"),
    (v: any) => (v.samples[3].moved.base = "minecraft:stone"),
    (v: any) => (v.samples[4].elapsedTicks = 3),
    (v: any) => (v.samples[4].elapsedTicks = 4.5),
    (v: any) =>
      (v.samples[4].states.base =
        "minecraft:piston[extended=true,facing=south]"),
  ];
  for (const change of changes) {
    const value = structuredClone(trace);
    change(value);
    assert.throws(() => validateShutterTrace(value, design, true, true));
  }
  const partial = {
    ...trace,
    samples: trace.samples.slice(0, 4),
    verdict: "running",
  };
  assert.equal(validateShutterTrace(partial, design, true).verdict, "running");
  assert.throws(() => validateShutterTrace(partial, design, true, true));
});
