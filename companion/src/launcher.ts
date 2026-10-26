import { createRequire } from "node:module";
import { access, chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { constants } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { createHash } from "node:crypto";

const execFileAsync = promisify(execFile);
const require = createRequire(import.meta.url);

export async function preparePlannerLauncher(
  configDir: string,
): Promise<{ executable: string; binary: string }> {
  const triples: Record<string, string> = {
    "win32-x64": "x86_64-pc-windows-msvc",
    "win32-arm64": "aarch64-pc-windows-msvc",
    "linux-x64": "x86_64-unknown-linux-musl",
    "linux-arm64": "aarch64-unknown-linux-musl",
    "darwin-x64": "x86_64-apple-darwin",
    "darwin-arm64": "aarch64-apple-darwin",
  };
  const triple = triples[`${process.platform}-${process.arch}`];
  if (!triple) throw new Error("Unsupported Codex platform");
  const platformName = `@openai/codex-${process.platform === "win32" ? "win32" : process.platform}-${process.arch}`;
  const codexRequire = createRequire(
    require.resolve("@openai/codex/package.json"),
  );
  const vendor = path.join(
    path.dirname(codexRequire.resolve(`${platformName}/package.json`)),
    "vendor",
    triple,
  );
  let binary = "";
  for (const location of ["bin", "codex"]) {
    const candidate = path.join(
      vendor,
      location,
      process.platform === "win32" ? "codex.exe" : "codex",
    );
    try {
      await access(candidate, constants.X_OK);
      binary = candidate;
      break;
    } catch {
      /* next layout */
    }
  }
  if (!binary)
    throw new Error(
      "Pinned Codex binary missing; run npm ci including optional dependencies",
    );
  // Source lives in repo/scripts in both the TS source and compiled layouts.
  const scripts = fileURLToPath(new URL("../../../scripts/", import.meta.url));
  let source: Buffer;
  try {
    source = await readFile(
      path.join(
        scripts,
        process.platform === "win32"
          ? "planner-launcher.cs"
          : "planner-launcher.sh",
      ),
    );
  } catch {
    const sourceScripts = fileURLToPath(
      new URL("../../scripts/", import.meta.url),
    );
    source = await readFile(
      path.join(
        sourceScripts,
        process.platform === "win32"
          ? "planner-launcher.cs"
          : "planner-launcher.sh",
      ),
    );
  }
  const version = createHash("sha256")
    .update(source)
    .digest("hex")
    .slice(0, 16);
  const directory = path.join(configDir, "planner-launcher");
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const executable = path.join(
    directory,
    `launcher-${version}${process.platform === "win32" ? ".exe" : ""}`,
  );
  try {
    await access(executable, constants.X_OK);
    return { executable, binary };
  } catch {
    /* prepare below */
  }
  if (process.platform === "win32") {
    const sourcePath = path.join(directory, `launcher-${version}.cs`);
    await writeFile(sourcePath, source, { mode: 0o600 });
    const windows = process.env.SystemRoot ?? "C:\\Windows";
    const compiler = path.join(
      windows,
      "Microsoft.NET",
      "Framework64",
      "v4.0.30319",
      "csc.exe",
    );
    try {
      await execFileAsync(
        compiler,
        [
          "/nologo",
          "/optimize+",
          "/target:exe",
          `/out:${executable}`,
          sourcePath,
        ],
        { windowsHide: true, timeout: 30_000 },
      );
    } catch {
      throw new Error(
        "Cannot prepare the restricted planner launcher: .NET Framework 4 compiler is required on Windows",
      );
    }
  } else {
    await writeFile(executable, source, { mode: 0o700 });
    await chmod(executable, 0o700);
  }
  return { executable, binary };
}
