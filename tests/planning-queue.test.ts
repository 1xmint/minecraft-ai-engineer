import assert from "node:assert/strict";
import { test } from "node:test";
import { PlanningQueue } from "../companion/src/planning-queue.js";

test("model work is serialized and another player gets a turn before a repeat requester", async () => {
  const queue = new PlanningQueue();
  const order: string[] = [];
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let active = 0;
  let peak = 0;
  const work =
    (name: string, wait = false) =>
    async () => {
      active++;
      peak = Math.max(peak, active);
      order.push(name);
      if (wait) await gate;
      active--;
      return name;
    };
  const first = queue.run(
    "alice",
    new AbortController().signal,
    work("alice-first", true),
  );
  const repeat = queue.run(
    "alice",
    new AbortController().signal,
    work("alice-repeat"),
  );
  const other = queue.run("bob", new AbortController().signal, work("bob"));
  await Promise.resolve();
  assert.deepEqual(order, ["alice-first"]);
  release();
  assert.deepEqual(await Promise.all([first, repeat, other]), [
    "alice-first",
    "alice-repeat",
    "bob",
  ]);
  assert.deepEqual(order, ["alice-first", "bob", "alice-repeat"]);
  assert.equal(peak, 1);
});

test("cancelling queued work does not invoke the provider or consume its slot", async () => {
  const queue = new PlanningQueue();
  let release!: () => void;
  let called = false;
  const first = queue.run(
    "owner",
    new AbortController().signal,
    () =>
      new Promise<void>((resolve) => {
        release = resolve;
      }),
  );
  await Promise.resolve();
  const controller = new AbortController();
  const pending = queue.run("guest", controller.signal, async () => {
    called = true;
  });
  const rejected = assert.rejects(pending, /cancelled/);
  controller.abort();
  await rejected;
  release();
  await first;
  assert.equal(called, false);
});

test("a legacy immediate request cannot race an occupied inference slot", async () => {
  const queue = new PlanningQueue();
  let release!: () => void;
  let secondCalled = false;
  const first = queue.run(
    "owner",
    new AbortController().signal,
    () =>
      new Promise<void>((resolve) => {
        release = resolve;
      }),
  );
  await Promise.resolve();
  await assert.rejects(
    queue.run(
      "other",
      new AbortController().signal,
      async () => {
        secondCalled = true;
      },
      false,
    ),
    /queue is full/,
  );
  release();
  await first;
  assert.equal(secondCalled, false);
});
