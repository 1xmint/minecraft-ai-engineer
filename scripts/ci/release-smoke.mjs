import assert from "node:assert/strict";
import { createHash, randomBytes } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";
import { once } from "node:events";
import { readFile, writeFile, mkdtemp, readdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { createServer } from "node:net";
import { pathToFileURL } from "node:url";

const directory = resolve(process.argv[2]);
const build = JSON.parse(await readFile(join(directory, "BUILD.json"), "utf8"));
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
for (const [name, checksum] of Object.entries(build.artifacts)) {
  assert.equal(name, name.split(/[\\/]/).at(-1));
  assert.equal(hash(await readFile(join(directory, name))), checksum);
}
const zip = Object.keys(build.artifacts).find((name) => name.endsWith(".zip"));
assert.ok(zip);
const temp = await mkdtemp(join(tmpdir(), "mce-release-smoke-"));
const extracted = join(temp, "bundle");
execFileSync("python", [
  "-c",
  `
import json, pathlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    expected = set(json.loads(z.read("BUILD.json"))["files"]) | {"BUILD.json"}
    assert set(z.namelist()) == expected and len(z.namelist()) == len(expected)
    for name in z.namelist():
        p = pathlib.PurePosixPath(name)
        assert not p.is_absolute() and '..' not in p.parts and '\\\\' not in name
    z.extractall(sys.argv[2])
`,
  join(directory, zip),
  extracted,
]);
const manifest = JSON.parse(
  await readFile(join(extracted, "BUILD.json"), "utf8"),
);
assert.equal(manifest.checkoutSha, build.checkoutSha);
assert.equal(manifest.runId, build.runId);
for (const [name, checksum] of Object.entries(manifest.files)) {
  assert.equal(hash(await readFile(join(extracted, name))), checksum);
  assert.ok(
    !/(?:^|\/)(?:node_modules|tests|worlds|auth\.json|companion\.json)(?:\/|$)/.test(
      name,
    ),
  );
  assert.ok(
    name.startsWith("dist/companion/") ||
      name.startsWith("dist/catalog/") ||
      name.startsWith("docs/") ||
      [
        "package.json",
        "package-lock.json",
        "LICENSE",
        ".node-version",
        "scripts/planner-launcher.sh",
        "scripts/planner-launcher.cs",
        "START-HERE.md",
      ].includes(name),
  );
}
assert.deepEqual(
  (await readdir(extracted)).sort(),
  [
    ".node-version",
    "BUILD.json",
    "LICENSE",
    "START-HERE.md",
    "dist",
    "docs",
    "package-lock.json",
    "package.json",
    "scripts",
  ].sort(),
);

// Exercise the documented production install, including the platform's optional binary.
execFileSync(
  process.platform === "win32" ? "npm.cmd" : "npm",
  ["ci", "--omit=dev", "--no-audit", "--no-fund"],
  {
    cwd: extracted,
    stdio: "inherit",
    shell: process.platform === "win32",
    timeout: 180_000,
  },
);
const config = join(temp, "config");
const { mkdir } = await import("node:fs/promises");
await mkdir(config);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((done, fail) =>
  socket.close((error) => (error ? fail(error) : done())),
);
const token = randomBytes(32).toString("hex");
await writeFile(
  join(config, "companion.json"),
  JSON.stringify({ port, token }),
  { flag: "wx", mode: 0o600 },
);
// Preparing the extracted launcher proves its runtime layout; never execute Codex or read login.
const launcherModule = pathToFileURL(
  join(extracted, "dist/companion/src/launcher.js"),
).href;
execFileSync(
  process.execPath,
  [
    "--input-type=module",
    "-e",
    `const {preparePlannerLauncher}=await import(${JSON.stringify(launcherModule)});await preparePlannerLauncher(${JSON.stringify(config)});`,
  ],
  { cwd: extracted, timeout: 30_000 },
);
const child = spawn(process.execPath, ["dist/companion/src/main.js"], {
  cwd: extracted,
  env: {
    ...process.env,
    MCE_CONFIG_DIR: config,
    CODEX_HOME: join(temp, "codex"),
  },
  stdio: ["ignore", "pipe", "pipe"],
  windowsHide: true,
});
let output = "";
child.stdout.on("data", (data) => {
  output += data;
});
child.stderr.on("data", (data) => {
  output += data;
});
const exited = once(child, "exit");
try {
  let healthy = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    assert.equal(
      child.exitCode,
      null,
      "Packed companion exited before health: " + output,
    );
    try {
      const response = await fetch(`http://127.0.0.1:${port}/v1/health`, {
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(1000),
      });
      assert.equal(response.status, 200);
      assert.deepEqual(await response.json(), { protocol: 1, status: "ok" });
      healthy = true;
      break;
    } catch {
      await new Promise((done) => setTimeout(done, 100));
    }
  }
  assert.ok(healthy, "Packed companion never became healthy: " + output);
  assert.equal((await fetch(`http://127.0.0.1:${port}/v1/health`)).status, 401);
  console.log(
    JSON.stringify({
      kind: "extracted-release-runtime",
      checkoutSha: build.checkoutSha,
      platform: process.platform,
      health: "authenticated",
      modelCalls: 0,
    }),
  );
} finally {
  if (child.exitCode === null) child.kill();
  await Promise.race([
    exited,
    new Promise((_, fail) =>
      setTimeout(() => fail(new Error("Companion did not stop")), 5000),
    ),
  ]);
}

// Repackaging may not overwrite the artifact directory or silently replace its bytes.
assert.throws(() =>
  execFileSync("python", ["scripts/release/package.py", "--out", directory], {
    stdio: "pipe",
  }),
);
for (const [name, checksum] of Object.entries(build.artifacts))
  assert.equal(hash(await readFile(join(directory, name))), checksum);

// Actual packaging failures: missing runtime input and a linked input never produce a candidate.
const { rename, symlink, unlink, access } = await import("node:fs/promises");
const runtimeInput = resolve("dist/catalog/mechanisms.json");
const heldInput = runtimeInput + ".release-smoke-held";
await rename(runtimeInput, heldInput);
try {
  const missingOutput = join(temp, "missing-input-output");
  assert.throws(() =>
    execFileSync(
      "python",
      ["scripts/release/package.py", "--out", missingOutput],
      { stdio: "pipe" },
    ),
  );
  await assert.rejects(access(missingOutput));
  if (process.platform !== "win32") {
    await symlink(heldInput, runtimeInput);
    const linkedOutput = join(temp, "linked-input-output");
    try {
      assert.throws(() =>
        execFileSync(
          "python",
          ["scripts/release/package.py", "--out", linkedOutput],
          { stdio: "pipe" },
        ),
      );
      await assert.rejects(access(linkedOutput));
    } finally {
      await unlink(runtimeInput);
    }
  }
} finally {
  await rename(heldInput, runtimeInput);
}

// A private-looking file accidentally placed alongside JS is outside the tracked-source allowlist.
const sentinel = "RELEASE_SMOKE_PRIVATE_DATA_MUST_NOT_SHIP";
const stray = resolve("dist/companion/src/companion.json");
await writeFile(stray, sentinel, { flag: "wx" });
try {
  const cleanOutput = join(temp, "allowlist-output");
  execFileSync("python", ["scripts/release/package.py", "--out", cleanOutput], {
    stdio: "pipe",
  });
  const cleanZip = (await readdir(cleanOutput)).find((name) =>
    name.endsWith(".zip"),
  );
  execFileSync("python", [
    "-c",
    `
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    assert not any(name.endswith('/companion.json') for name in z.namelist())
    assert all(sys.argv[2].encode() not in z.read(name) for name in z.namelist())
`,
    join(cleanOutput, cleanZip),
    sentinel,
  ]);
} finally {
  await unlink(stray);
}
