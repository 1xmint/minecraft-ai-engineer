import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { mkdtemp, rm } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import type { AddressInfo } from "node:net";
import { request as httpRequest } from "node:http";
import { MemoryStore } from "../companion/src/memory.js";
import { createCompanionServer } from "../companion/src/server.js";
import {
  makeReferenceBlueprint,
  rotatePosition,
  rotateBlockState,
} from "../companion/src/blueprint.js";
import {
  validateDesignRequest,
  type Blueprint,
} from "../companion/src/types.js";

const token = "test-only-local-token-0123456789abcdef";
const request = {
  request: "Teach me a small lamp controlled by a lever",
  worldId: "test-world",
  dimension: "minecraft:overworld",
  anchor: { x: 10, y: 64, z: -10 },
  rotation: 0 as const,
};
let directory: string;
let store: MemoryStore;
let server: ReturnType<typeof createCompanionServer>;
let base: string;
let calls = 0;

before(async () => {
  directory = await mkdtemp(path.join(os.tmpdir(), "mce-tests-"));
  store = new MemoryStore(
    path.join(directory, "projects.sqlite"),
    path.join(directory, "blueprints"),
  );
  server = createCompanionServer({
    token,
    store,
    provider: {
      async plan() {
        calls++;
        return {
          template: "signal-lamp" as const,
          explanation: "The lever powers a repeater, which powers the lamp.",
        };
      },
    },
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});

after(async () => {
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
  store.close();
  await rm(directory, { recursive: true, force: true });
});

async function post(
  route: string,
  body: unknown,
  extra: Record<string, string> = {},
) {
  return fetch(`${base}${route}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...extra,
    },
    body: JSON.stringify(body),
  });
}

test("health is authenticated and never invokes a model", async () => {
  const previous = calls;
  assert.equal((await fetch(`${base}/v1/health`)).status, 401);
  const response = await fetch(`${base}/v1/health`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.equal(response.status, 200);
  assert.equal(((await response.json()) as { protocol: number }).protocol, 1);
  assert.equal(calls, previous);
});

test("browser and rebinding requests cannot invoke planning", async () => {
  const previous = calls;
  assert.ok(
    (await post("/v1/design", request, { Origin: "http://malicious.invalid" }))
      .status >= 400,
  );
  // fetch normalizes Host to the URL; use a real native HTTP request to prove
  // the server rejects the malicious Host header that would reach it.
  const status = await new Promise<number>((resolve, reject) => {
    const exchange = httpRequest(
      `${base}/v1/design`,
      {
        method: "POST",
        headers: {
          Host: "malicious.invalid",
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      },
      (response) => {
        response.resume();
        response.on("end", () => resolve(response.statusCode ?? 0));
      },
    );
    exchange.once("error", reject);
    exchange.end(JSON.stringify(request));
  });
  assert.equal(status, 403);
  assert.equal(calls, previous);
});

test("invalid coordinates, rotations and excessive regions are rejected before inference", async () => {
  const previous = calls;
  for (const invalid of [
    { ...request, rotation: 45 },
    { ...request, anchor: { x: 0.5, y: 64, z: 0 } },
    { ...request, request: "" },
    { ...request, worldId: "" },
    { ...request, request: "x".repeat(20_000) },
  ]) {
    assert.ok((await post("/v1/design", invalid)).status >= 400);
  }
  assert.equal(calls, previous);
});

test("a plan is executable, scoped, versioned and explicitly untested", async () => {
  const response = await post("/v1/design", request);
  assert.equal(response.status, 200);
  const result = (await response.json()) as { blueprint: Blueprint };
  assert.equal(result.blueprint.worldId, request.worldId);
  assert.equal(result.blueprint.dimension, request.dimension);
  assert.equal(result.blueprint.verification, "untested");
  assert.equal(result.blueprint.provenance.template, "signal-lamp");
  assert.ok(
    result.blueprint.blocks.some(
      (block: { state: string }) =>
        block.state.includes("repeater") && block.state.includes("facing=west"),
    ),
  );
  assert.equal(result.blueprint.stages.length, 3);
  const projects = await fetch(`${base}/v1/projects?worldId=test-world`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.equal(projects.status, 200);
  assert.ok(
    ((await projects.json()) as { projects: unknown[] }).projects.length > 0,
  );
});

test("projects from another save are not included", async () => {
  const response = await fetch(`${base}/v1/projects?worldId=another-save`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.deepEqual(
    ((await response.json()) as { projects: unknown[] }).projects,
    [],
  );
});

test("JSON syntax errors and unknown routes return controlled errors", async () => {
  const response = await fetch(`${base}/v1/design`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: "{",
  });
  assert.ok(response.status >= 400 && response.status < 500);
  assert.equal(
    (await post("/v1/execute-shell", { command: "echo ignored" })).status,
    404,
  );
});

test("rotation changes geometry and directional states together", () => {
  assert.deepEqual(rotatePosition({ x: 1, y: 2, z: 3 }, 90), {
    x: -3,
    y: 2,
    z: 1,
  });
  assert.match(
    rotateBlockState(
      "minecraft:repeater[delay=1,facing=east,locked=false,powered=false]",
      90,
    ),
    /facing=south/,
  );
  assert.match(
    rotateBlockState(
      "minecraft:repeater[delay=1,facing=east,locked=false,powered=false]",
      270,
    ),
    /facing=north/,
  );
});

test("reference and request parsing retain intended state, not verification claims", () => {
  const parsed = validateDesignRequest(request);
  const blueprint = makeReferenceBlueprint(parsed);
  assert.equal(blueprint.schemaVersion, 1);
  assert.equal(blueprint.verification, "untested");
  assert.equal(blueprint.blocks.length, 6);
  assert.throws(() =>
    validateDesignRequest({ ...request, dimension: "../escape" }),
  );
});
