import assert from "node:assert/strict";
import { request } from "node:http";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { MemoryStore } from "../companion/src/memory.js";
import { createCompanionServer } from "../companion/src/server.js";

test("overlapping streamed request bodies can acquire only one subscription planning slot", async () => {
  const store = new MemoryStore(":memory:");
  const token = "test-concurrency-token-0123456789abcdef";
  let calls = 0,
    release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const server = createCompanionServer({
    token,
    store,
    provider: {
      async plan() {
        calls++;
        await gate;
        return {
          template: "signal-lamp",
          explanation: "The lever controls a delayed lamp.",
        };
      },
    },
  });
  let arrived = 0,
    arrivedResolve!: () => void;
  const bothArrived = new Promise<void>((resolve) => {
    arrivedResolve = resolve;
  });
  server.on("request", () => {
    if (++arrived === 2) arrivedResolve();
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = (server.address() as AddressInfo).port;
  const document = JSON.stringify({
    request: "Build a lamp",
    worldId: "concurrency-world",
    dimension: "minecraft:overworld",
    anchor: { x: 0, y: 64, z: 0 },
    rotation: 0,
  });
  const stream = () => {
    let responseResolve!: (status: number) => void,
      responseReject!: (error: Error) => void;
    const response = new Promise<number>((resolve, reject) => {
      responseResolve = resolve;
      responseReject = reject;
    });
    const outgoing = request(
      {
        host: "127.0.0.1",
        port,
        path: "/v1/design",
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      },
      (incoming) => {
        incoming.resume();
        incoming.once("end", () => responseResolve(incoming.statusCode!));
      },
    );
    outgoing.once("error", responseReject);
    outgoing.write(document.slice(0, 1));
    return { outgoing, response };
  };
  const first = stream(),
    second = stream();
  let timeout: ReturnType<typeof setTimeout> | undefined;
  try {
    await bothArrived;
    first.outgoing.end(document.slice(1));
    second.outgoing.end(document.slice(1));
    const earliest = await Promise.race([
      first.response,
      second.response,
      new Promise<number>((_resolve, reject) => {
        timeout = setTimeout(
          () =>
            reject(
              new Error(
                "Both planning requests entered the provider while the first was pending.",
              ),
            ),
          2000,
        );
      }),
    ]);
    assert.equal(earliest, 409);
    assert.equal(calls, 1);
    release();
    assert.deepEqual(
      (await Promise.all([first.response, second.response])).sort(),
      [200, 409],
    );
  } finally {
    if (timeout) clearTimeout(timeout);
    release();
    first.outgoing.end();
    second.outgoing.end();
    await Promise.allSettled([first.response, second.response]);
    await new Promise<void>((resolve) => server.close(() => resolve()));
    store.close();
  }
});
