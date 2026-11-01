import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { runSchematicsCli } from "../companion/src/schematics/cli.js";

const fixture = fileURLToPath(
  new URL(
    "../../tests/fixtures/schematics/worldedit-fabric-7.3.schem",
    import.meta.url,
  ),
);

function cli(...argv: string[]): { code: number; out: string; err: string } {
  let out = "";
  let err = "";
  const code = runSchematicsCli(argv, {
    stdout: (t) => (out += t),
    stderr: (t) => (err += t),
  });
  return { code, out, err };
}

test("CLI imports, refuses to overwrite without --force, and exports", (t) => {
  const dir = mkdtempSync(join(tmpdir(), "mce-schematics-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const json = join(dir, "lamp.json");
  const schem = join(dir, "lamp.schem");

  const refused = cli("import", fixture, json);
  assert.equal(refused.code, 1);
  assert.match(refused.err, /E_BLOCK_ENTITIES/);

  const imported = cli("import", fixture, json, "--discard-block-entities");
  assert.equal(imported.code, 0, imported.err);
  assert.match(imported.out, /untrusted geometry only/);
  assert.match(imported.err, /Discarded 6 block entities/);
  assert.match(imported.err, /minecraft:\w*sign at \d+,\d+,\d+\n/);
  const written = readFileSync(json, "utf8");
  assert.equal(JSON.parse(written).size.x, 15);

  writeFileSync(json, "keep me");
  const clash = cli("import", fixture, json, "--discard-block-entities");
  assert.equal(clash.code, 2);
  assert.match(clash.err, /already exists; pass --force/);
  assert.equal(readFileSync(json, "utf8"), "keep me");
  assert.equal(
    cli("import", fixture, json, "--discard-block-entities", "--force").code,
    0,
  );
  assert.equal(readFileSync(json, "utf8"), written);

  const exported = cli("export", json, schem);
  assert.equal(exported.code, 0, exported.err);
  assert.equal(cli("export", json, schem).code, 2);
  assert.equal(cli("export", json, schem, "--force").code, 0);
  const same = cli("export", json, json, "--force");
  assert.equal(same.code, 2);
  assert.match(same.err, /must differ from the input/);
  assert.equal(readFileSync(json, "utf8"), written);

  const inspected = cli("inspect", schem);
  assert.equal(inspected.code, 0, inspected.err);
  const summary = JSON.parse(inspected.out);
  assert.equal(
    summary.trust,
    "untrusted geometry only: no ownership, permissions or functional verification",
  );
  assert.deepEqual(summary.size, { x: 15, y: 16, z: 20 });
  assert.deepEqual(summary.offset, { x: -14, y: 0, z: -19 });
  assert.equal(summary.dataVersion, 3578);
  assert.deepEqual(summary.discarded.blockEntities, []);
});

test("CLI reports usage and input errors with distinct exit codes", (t) => {
  const dir = mkdtempSync(join(tmpdir(), "mce-schematics-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const bad = join(dir, "bad.json");
  writeFileSync(bad, JSON.stringify({ format: "x", owner: "me" }));

  assert.equal(cli().code, 2);
  assert.equal(cli("convert", fixture).code, 2);
  assert.equal(cli("inspect", fixture, "--force").code, 2);
  assert.equal(cli("import", fixture).code, 2);
  const rejected = cli("export", bad, join(dir, "out.schem"));
  assert.equal(rejected.code, 1);
  assert.match(rejected.err, /E_GEOMETRY/);
  assert.equal(cli("inspect", join(dir, "missing.schem")).code, 1);
  assert.equal(cli("inspect", bad).code, 1);
});

test("compiled CLI runs as a standalone program", () => {
  const inProcess = cli("inspect", fixture, "--discard-block-entities");
  assert.equal(inProcess.code, 0, inProcess.err);
  const program = fileURLToPath(
    new URL("../companion/src/schematics/cli.js", import.meta.url),
  );
  const out = execFileSync(
    process.execPath,
    [program, "inspect", fixture, "--discard-block-entities"],
    {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  assert.deepEqual(JSON.parse(out), JSON.parse(inProcess.out));
});
