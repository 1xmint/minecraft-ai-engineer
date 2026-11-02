import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ConnectedTrace,
  readConnectedTrace,
} from "../companion/src/connected-trace.js";
import { RequestError } from "../companion/src/types.js";
import native from "./fixtures/half-adder-native.json" with { type: "json" };

function intent(index = 0) {
  return {
    graphDigest: native.graphDigest,
    a: index >= 2,
    b: index % 2 === 1,
  };
}
function ports(frame: (typeof native.cases)[number]["frames"][number]) {
  const values = frame.values.split(",").map((value) => JSON.parse(value));
  assert.equal(values.length, native.pins.length);
  return Object.fromEntries(
    native.pins.map((pin, index) => [pin, values[index]]),
  );
}
function complete(index = 0) {
  const c = native.cases[index]!;
  const trace = new ConnectedTrace(intent(index), c.startGameTick);
  for (const frame of c.frames) trace.sample(frame.gameTick, ports(frame));
  return trace;
}
// Malformed snapshots deliberately do not conform to the public readonly type.
function copy(value: unknown): any {
  return structuredClone(value);
}

test("connected codec replays four actual native arithmetic vectors with attenuated pins", () => {
  assert.equal(native.source.runId, "41371755386");
  assert.equal(native.cases.length, 4);
  assert.equal(new Set(native.pins).size, 20);
  for (let index = 0; index < 4; index++) {
    const c = native.cases[index]!;
    assert.deepEqual([c.a, c.b], [intent(index).a, intent(index).b]);
    assert.equal(c.frames.length, 56);
    const trace = complete(index);
    assert.equal(trace.passed(), true);
    assert.deepEqual(
      readConnectedTrace(trace.json(), intent(index)),
      trace.json(),
    );
  }
  assert.ok(
    native.cases.some((c) =>
      c.frames.some((frame) =>
        Object.values(ports(frame)).some(
          (value) => typeof value === "number" && value > 0 && value < 15,
        ),
      ),
    ),
    "Native input strengths include real attenuation, not only synthetic 0/15",
  );
});

test("partial and gapped connected evidence cannot certify a whole case", () => {
  const c = native.cases[0]!;
  const empty = new ConnectedTrace(intent(), c.startGameTick);
  assert.equal(empty.verdict(), "inconclusive");
  for (const omitted of [0, 10]) {
    const trace = new ConnectedTrace(intent(), c.startGameTick);
    c.frames.forEach((frame, index) => {
      if (index !== omitted) trace.sample(frame.gameTick, ports(frame));
    });
    assert.equal(trace.passed(), false);
    assert.equal(trace.verdict(), "inconclusive");
    assert.equal(
      readConnectedTrace(trace.json(), intent()).verdict,
      "inconclusive",
    );
    const invented = copy(trace.json());
    invented.verdict = "passed";
    assert.throws(() => readConnectedTrace(invented, intent()), RequestError);
  }
});

test("internal stable-pin failure beats matching receivers and stays failed", () => {
  const c = native.cases[0]!;
  const trace = new ConnectedTrace(intent(), c.startGameTick);
  for (const [index, frame] of c.frames.entries()) {
    const observed = ports(frame);
    if (index === 48) observed["n1:out"] = 0;
    trace.sample(frame.gameTick, observed);
  }
  assert.equal(trace.verdict(), "failed");
  assert.equal(trace.json().firstMismatch?.pin, "n1:out");
  assert.equal(trace.json().samples.at(-1)!.ports["sum-light:in"], false);
  assert.equal(trace.json().samples.at(-1)!.ports["carry-light:in"], false);
  assert.equal(readConnectedTrace(trace.json(), intent()).verdict, "failed");
  const invented = copy(trace.json());
  invented.verdict = "passed";
  invented.firstMismatch = null;
  assert.throws(() => readConnectedTrace(invented, intent()), RequestError);
});

test("control edits fail immediately while pre-settle internal transients are retained", () => {
  const c = native.cases[2]!;
  const transient = new ConnectedTrace(intent(2), c.startGameTick);
  const changedControl = new ConnectedTrace(intent(2), c.startGameTick);
  for (const [index, frame] of c.frames.entries()) {
    const observed = ports(frame);
    if (index === 10) observed["sum-light:in"] = false;
    transient.sample(frame.gameTick, observed);
    const edited = ports(frame);
    if (index === 0) edited["a:control"] = false;
    changedControl.sample(frame.gameTick, edited);
  }
  assert.equal(transient.passed(), true);
  assert.equal(changedControl.verdict(), "failed");
  assert.equal(changedControl.json().firstMismatch?.elapsedTicks, 0);
  assert.equal(changedControl.json().firstMismatch?.pin, "a:control");
});

test("connected traces cannot rebind intent, loosen timing or import authority", () => {
  const valid = complete().json();
  assert.throws(
    () =>
      readConnectedTrace(valid, { ...intent(), graphDigest: "f".repeat(64) }),
    RequestError,
  );
  assert.throws(() => readConnectedTrace(valid, intent(2)), RequestError);
  for (const mutate of [
    (v: any) => (v.owner = "trusted"),
    (v: any) => (v.samples[0].actorId = "owner"),
    (v: any) => (v.samples[0].ports.verified = true),
    (v: any) => (v.settleGameTicks = 0),
    (v: any) => (v.stableSamples = 1),
    (v: any) => (v.samples[1].elapsedTicks = 2),
    (v: any) => (v.samples[1].gameTick = v.samples[0].gameTick),
    (v: any) => (v.samples[0].ports["a:control"] = 0),
    (v: any) => (v.samples[0].ports["n1:out"] = "15"),
    (v: any) => (v.samples[0].ports["n1:out"] = 16),
    (v: any) => (v.samples[0].ports["n1:out"] = 0.5),
    (v: any) => (v.samples[0].ports["n1:out"] = Number.NaN),
    (v: any) => (v.startGameTick = Number.MAX_SAFE_INTEGER),
    (v: any) =>
      (v.firstMismatch = {
        elapsedTicks: 48,
        gameTick: v.startGameTick + 48,
        pin: "n1:out",
        expected: true,
        observed: 0,
      }),
  ]) {
    const value = copy(valid);
    mutate(value);
    assert.throws(() => readConnectedTrace(value, intent()), RequestError);
  }
});

test("rejected live samples leave captured clocks, gap state and observations unchanged", () => {
  const c = native.cases[0]!;
  const trace = new ConnectedTrace(intent(), c.startGameTick);
  for (const frame of c.frames.slice(0, 2))
    trace.sample(frame.gameTick, ports(frame));
  const before = trace.json();
  const invalid = ports(c.frames[3]!);
  delete invalid["sum:out"];
  assert.throws(
    () => trace.sample(c.frames[3]!.gameTick, invalid),
    RequestError,
  );
  assert.deepEqual(trace.json(), before);
  assert.throws(
    () => trace.sample(c.frames[1]!.gameTick, ports(c.frames[1]!)),
    RequestError,
  );
  for (const frame of c.frames.slice(2))
    trace.sample(frame.gameTick, ports(frame));
  assert.equal(trace.passed(), true);
});

test("non-JSON, sparse, oversized and extra-property sample arrays reject", () => {
  const valid = complete().json();
  const sparse = copy(valid);
  sparse.samples = new Array(1);
  assert.throws(() => readConnectedTrace(sparse, intent()), RequestError);
  const extra = copy(valid);
  extra.samples.owner = "owner";
  assert.throws(() => readConnectedTrace(extra, intent()), RequestError);
  let enumerated = false;
  const huge = copy(valid);
  huge.samples = new Proxy(new Array(57), {
    ownKeys() {
      enumerated = true;
      throw new Error("Do not enumerate oversized evidence");
    },
  });
  assert.throws(() => readConnectedTrace(huge, intent()), RequestError);
  assert.equal(enumerated, false);
  const getters = copy(valid);
  let getterUsed = false;
  Object.defineProperty(getters.samples[0].ports, "a:control", {
    enumerable: true,
    get() {
      getterUsed = true;
      return false;
    },
  });
  assert.throws(() => readConnectedTrace(getters, intent()), RequestError);
  assert.equal(getterUsed, false);
});

test("exported connected snapshots cannot change captured evidence", () => {
  const trace = complete();
  const snapshot = trace.json();
  assert.ok(Object.isFrozen(snapshot));
  assert.ok(Object.isFrozen(snapshot.samples));
  assert.ok(Object.isFrozen(snapshot.samples[0]!.ports));
  assert.throws(() => {
    (snapshot.samples[0]!.ports as any)["n1:out"] = 0;
  }, TypeError);
  const changed = copy(snapshot);
  changed.samples[0].ports["n1:out"] = 0;
  assert.equal(trace.passed(), true);
  assert.equal(
    trace.json().samples[0]!.ports["n1:out"],
    snapshot.samples[0]!.ports["n1:out"],
  );
});
