import assert from "node:assert/strict";
import {
  linkSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  truncateSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { runSchematicsCli } from "../companion/src/schematics/cli.js";
import {
  DEFAULT_SCHEMATIC_LIMITS,
  GEOMETRY_FORMAT,
  exportSpongeSchematic,
  validateGeometry,
} from "../companion/src/schematics/index.js";

function geometry() {
  return {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion: 1,
    size: { x: 1, y: 1, z: 1 },
    offset: { x: 0, y: 0, z: 0 },
    palette: [{ name: "minecraft:stone", properties: {} }],
    blocks: [0],
    untrustedMetadata: { requiredMods: ["minecraft"] },
  };
}

test("schematic API rejects sparse palettes and metadata rather than accepting missing values", () => {
  const palette = geometry();
  delete palette.palette[0];
  assert.throws(
    () => validateGeometry(palette, DEFAULT_SCHEMATIC_LIMITS),
    /E_GEOMETRY/,
  );
  assert.throws(() => exportSpongeSchematic(palette), /E_GEOMETRY/);
  const metadata = geometry();
  delete metadata.untrustedMetadata.requiredMods[0];
  assert.throws(
    () => validateGeometry(metadata, DEFAULT_SCHEMATIC_LIMITS),
    /E_GEOMETRY/,
  );
  assert.throws(() => exportSpongeSchematic(metadata), /E_GEOMETRY/);
});

test("forced schematic output cannot truncate its input through a hard-link alias", (t) => {
  const directory = mkdtempSync(join(tmpdir(), "mce-schematic-alias-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const input = join(directory, "source.json"),
    alias = join(directory, "alias.schem");
  const original = JSON.stringify(geometry());
  writeFileSync(input, original);
  linkSync(input, alias);
  let error = "";
  const code = runSchematicsCli(["export", input, alias, "--force"], {
    stdout: () => {},
    stderr: (text) => {
      error += text;
    },
  });
  assert.equal(code, 2);
  assert.match(error, /must differ from the input/);
  assert.equal(readFileSync(input, "utf8"), original);
  assert.equal(readFileSync(alias, "utf8"), original);
});

test("schematic CLI rejects an oversized file before conversion", (t) => {
  const directory = mkdtempSync(join(tmpdir(), "mce-schematic-limit-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const input = join(directory, "oversized.schem");
  writeFileSync(input, "");
  truncateSync(input, DEFAULT_SCHEMATIC_LIMITS.maxCompressedBytes + 1);
  let error = "";
  const code = runSchematicsCli(["inspect", input], {
    stdout: () => {},
    stderr: (text) => {
      error += text;
    },
  });
  assert.equal(code, 1);
  assert.match(error, /E_INPUT_TOO_LARGE/);
});
