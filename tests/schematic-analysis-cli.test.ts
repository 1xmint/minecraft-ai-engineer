import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import {
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test, type TestContext } from "node:test";
import { fileURLToPath } from "node:url";
import { runAnalysisCli } from "../companion/src/schematic-analysis/cli.js";
import {
  AnalysisError,
  readGeometryFile,
  type ReadOptions,
} from "../companion/src/schematic-analysis/index.js";
import { GEOMETRY_FORMAT } from "../companion/src/schematics/index.js";

function geometry(dataVersion: number, blocks: number[]) {
  return {
    format: GEOMETRY_FORMAT,
    formatVersion: 1,
    dataVersion,
    size: { x: blocks.length, y: 1, z: 1 },
    offset: { x: -3, y: 64, z: 9 },
    palette: [
      { name: "minecraft:air", properties: {} },
      { name: "minecraft:stone", properties: {} },
    ],
    blocks,
  };
}

function cli(argv: string[], read: ReadOptions = {}) {
  let out = "";
  let err = "";
  const code = runAnalysisCli(
    argv,
    { stdout: (t) => (out += t), stderr: (t) => (err += t) },
    read,
  );
  return { code, out, err };
}

function workspace(t: TestContext) {
  const dir = mkdtempSync(join(tmpdir(), "mce-analysis-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const file = (name: string, content: unknown) => {
    const path = join(dir, name);
    writeFileSync(
      path,
      typeof content === "string" ? content : JSON.stringify(content),
    );
    return path;
  };
  const snapshot = () =>
    readdirSync(dir)
      .sort()
      .map((n) => {
        const s = statSync(join(dir, n));
        return `${n}:${s.size}:${s.mtimeMs}`;
      });
  return { dir, file, snapshot };
}

test("CLI analyzes and compares files, printing JSON and writing nothing", (t) => {
  const w = workspace(t);
  const a = w.file("a.json", geometry(3578, [1, 1, 0, 1]));
  const b = w.file("b.json", geometry(3578, [1, 0, 0, 0, 1]));
  const before = w.snapshot();

  const analyzed = cli(["analyze", a]);
  assert.equal(analyzed.code, 0, analyzed.err);
  const report = JSON.parse(analyzed.out);
  assert.equal(report.totalCells, 4);
  assert.equal(report.airCells, 1);
  assert.equal(report.nonAirCells, 3);
  assert.deepEqual(report.blockStates, [
    { key: "minecraft:stone", count: 3 },
    { key: "minecraft:air", count: 1 },
  ]);

  // Overlap x -3..0: changed at x=-2 and x=0; x=1 is only in B.
  const compared = cli(["compare", a, b, "--max-details", "1"]);
  assert.equal(compared.code, 0, compared.err);
  const diff = JSON.parse(compared.out);
  assert.deepEqual(diff.counts, {
    identical: 2,
    changed: 2,
    onlyInA: { cells: 0, air: 0, nonAir: 0 },
    onlyInB: { cells: 1, air: 0, nonAir: 1 },
  });
  assert.deepEqual(diff.details.changed, {
    listed: 1,
    truncated: true,
    items: [
      {
        pos: { x: -2, y: 64, z: 9 },
        a: "minecraft:stone",
        b: "minecraft:air",
      },
    ],
  });
  assert.equal(diff.maxDetails, 1);
  assert.equal(cli(["compare", a, b]).code, 0);
  assert.deepEqual(w.snapshot(), before);
});

test("CLI rejects bad input with exit 1 and bad usage with exit 2", (t) => {
  const w = workspace(t);
  const good = w.file("good.json", geometry(3578, [1, 0]));
  const other = w.file("other.json", geometry(3700, [1, 0]));
  const broken = w.file("broken.json", "{ not json");
  const claims = w.file("claims.json", {
    ...geometry(3578, [1]),
    verified: true,
  });
  const folder = join(w.dir, "folder");
  mkdirSync(folder);
  const before = w.snapshot();

  const rejected: [string[], RegExp][] = [
    [["compare", good, other], /E_DATA_VERSION_MISMATCH/],
    [["analyze", broken], /E_JSON/],
    [["analyze", claims], /E_GEOMETRY/],
    [["analyze", join(w.dir, "missing.json")], /E_INPUT_FILE/],
    [["analyze", folder], /E_INPUT_FILE/],
  ];
  for (const [argv, message] of rejected) {
    const r = cli(argv);
    assert.equal(r.code, 1, argv.join(" "));
    assert.match(r.err, message);
    assert.equal(r.out, "");
  }

  const usage = [
    [],
    ["inspect", good],
    ["analyze"],
    ["analyze", good, good],
    ["compare", good],
    ["analyze", good, "--max-details", "3"],
    ["compare", good, good, "--max-details"],
    ["compare", good, good, "--max-details", "abc"],
    ["compare", good, good, "--max-details", "-1"],
    ["compare", good, good, "--max-details", "10001"],
    ["compare", good, good, "--force"],
    ["compare", good, good, "--out", "x.json"],
  ];
  for (const argv of usage) {
    const r = cli(argv);
    assert.equal(r.code, 2, argv.join(" "));
    assert.match(r.err, /Usage:/);
  }
  assert.deepEqual(w.snapshot(), before);
});

test("configured byte and geometry limits are enforced while reading", (t) => {
  const w = workspace(t);
  const path = w.file("g.json", geometry(3578, [1, 0, 1]));
  const bytes = statSync(path).size;

  assert.equal(readGeometryFile(path, { maxBytes: bytes }).blocks.length, 3);
  assert.throws(
    () => readGeometryFile(path, { maxBytes: bytes - 1 }),
    /E_INPUT_TOO_LARGE/,
  );
  assert.throws(
    () =>
      readGeometryFile(path, {
        limits: { maxVolume: 2, maxPaletteEntries: 10 },
      }),
    /E_TOO_LARGE/,
  );
  assert.throws(
    () => readGeometryFile(join(w.dir, "nope.json")),
    (e: unknown) => e instanceof AnalysisError && e.code === "E_INPUT_FILE",
  );

  const small = cli(["analyze", path], { maxBytes: 40 });
  assert.equal(small.code, 1);
  assert.match(small.err, /E_INPUT_TOO_LARGE/);
  const narrow = cli(["compare", path, path], {
    limits: { maxVolume: 10, maxPaletteEntries: 1 },
  });
  assert.equal(narrow.code, 1);
  assert.match(narrow.err, /E_TOO_LARGE/);
});

test("compiled CLI runs as a standalone program", (t) => {
  const w = workspace(t);
  const a = w.file("a.json", geometry(3578, [1, 1, 0]));
  const b = w.file("b.json", geometry(3578, [0, 1, 0]));
  const inProcess = cli(["compare", a, b]);
  assert.equal(inProcess.code, 0, inProcess.err);
  const program = fileURLToPath(
    new URL("../companion/src/schematic-analysis/cli.js", import.meta.url),
  );
  const out = execFileSync(process.execPath, [program, "compare", a, b], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  assert.deepEqual(JSON.parse(out), JSON.parse(inProcess.out));
});
