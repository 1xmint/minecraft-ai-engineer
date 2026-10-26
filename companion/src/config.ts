import { randomBytes } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync, chmodSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";

export interface CompanionConfig {
  directory: string;
  token: string;
  port: number;
}
export function configDirectory(env: NodeJS.ProcessEnv = process.env): string {
  return resolve(
    env.MCE_CONFIG_DIR ??
      (process.platform === "win32" && env.APPDATA
        ? join(env.APPDATA, "minecraft-ai-engineer")
        : join(
            env.XDG_CONFIG_HOME ?? join(homedir(), ".config"),
            "minecraft-ai-engineer",
          )),
  );
}
export function loadConfig(directory = configDirectory()): CompanionConfig {
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  const file = join(directory, "companion.json");
  let saved: { token: string; port: number };
  try {
    saved = JSON.parse(readFileSync(file, "utf8")) as typeof saved;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT")
      throw new Error(
        "Invalid companion.json. Preserve and repair it before starting.",
      );
    saved = { token: randomBytes(32).toString("hex"), port: 43127 };
    writeFileSync(file, JSON.stringify(saved, null, 2), {
      mode: 0o600,
      flag: "wx",
    });
  }
  if (
    !/^[a-f0-9]{64}$/u.test(saved.token) ||
    !Number.isInteger(saved.port) ||
    saved.port < 1024 ||
    saved.port > 65535
  )
    throw new Error("Invalid companion configuration.");
  if (process.platform !== "win32") {
    chmodSync(directory, 0o700);
    chmodSync(file, 0o600);
  }
  return { directory, token: saved.token, port: saved.port };
}
