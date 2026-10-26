import assert from "node:assert/strict";
import { test } from "node:test";
import { spawn } from "node:child_process";
import { mkdtemp, writeFile, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { preparePlannerLauncher } from "../companion/src/launcher.js";

test("restricted launcher preserves SDK streams and exact argument boundaries", async () => {
  const directory = await mkdtemp(join(tmpdir(), "mce-launcher-test-"));
  try {
    const launcher = await preparePlannerLauncher(directory);
    // Node is a fake native runtime, not a model. 'exec' is a local fixture
    // script; it records CLI flags and stdin without invoking Codex or auth.
    await writeFile(
      join(directory, "exec"),
      `let input='';process.stdin.on('data',chunk=>input+=chunk);process.stdin.on('end',()=>{process.stdout.write(JSON.stringify({args:process.argv.slice(2),input}));process.stderr.write('fixture-stderr');});`,
    );
    const unusual = [
      "--json",
      'quoted "value"',
      "trailing\\",
      "literal $() ` ; &",
    ];
    const child = spawn(launcher.executable, ["exec", ...unusual], {
      cwd: directory,
      env: { ...process.env, MCE_CODEX_BINARY: process.execPath },
      windowsHide: true,
    });
    let stdout = "",
      stderr = "";
    child.stdout.on("data", (chunk: Buffer) => {
      stdout += chunk.toString();
    });
    child.stderr.on("data", (chunk: Buffer) => {
      stderr += chunk.toString();
    });
    child.stdin.end("private test prompt\n");
    const code = await new Promise<number | null>((resolve, reject) => {
      child.once("error", reject);
      child.once("close", resolve);
    });
    assert.equal(code, 0, stderr);
    assert.deepEqual(JSON.parse(stdout), {
      args: [
        "--ignore-user-config",
        "--ignore-rules",
        "--ephemeral",
        ...unusual,
      ],
      input: "private test prompt\n",
    });
    assert.equal(stderr, "fixture-stderr");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test(
  "Windows cancellation kills the native planner and its descendants",
  { skip: process.platform !== "win32", timeout: 20_000 },
  async () => {
    const directory = await mkdtemp(join(tmpdir(), "mce-cancel-test-"));
    let child: ReturnType<typeof spawn> | undefined;
    let descendant = 0;
    try {
      const launcher = await preparePlannerLauncher(directory);
      await writeFile(
        join(directory, "exec"),
        `const{spawn}=require('node:child_process');const fs=require('node:fs');const descendant=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'ignore',windowsHide:true});fs.writeFileSync('pid.json',JSON.stringify({pid:descendant.pid}));setInterval(()=>{},1000);`,
      );
      child = spawn(launcher.executable, ["exec"], {
        cwd: directory,
        env: { ...process.env, MCE_CODEX_BINARY: process.execPath },
        windowsHide: true,
      });
      child.stdin?.end();
      for (let attempt = 0; attempt < 50; attempt++) {
        try {
          descendant = (
            JSON.parse(await readFile(join(directory, "pid.json"), "utf8")) as {
              pid: number;
            }
          ).pid;
          break;
        } catch {
          await new Promise((resolve) => setTimeout(resolve, 100));
        }
      }
      assert.ok(descendant > 0, "fixture descendant did not start");
      const exited = new Promise<void>((resolve) =>
        child!.once("close", () => resolve()),
      );
      child.kill();
      await exited;
      let running = true;
      for (let attempt = 0; attempt < 30; attempt++) {
        try {
          process.kill(descendant, 0);
          await new Promise((resolve) => setTimeout(resolve, 100));
        } catch {
          running = false;
          break;
        }
      }
      assert.equal(
        running,
        false,
        "planner descendant survived SDK cancellation",
      );
    } finally {
      child?.kill();
      if (descendant) {
        try {
          process.kill(descendant);
        } catch {
          /* already stopped */
        }
      }
      await rm(directory, { recursive: true, force: true });
    }
  },
);
