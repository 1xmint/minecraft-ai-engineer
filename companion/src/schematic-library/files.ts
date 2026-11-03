import { createHash } from "node:crypto";
import {
  closeSync,
  constants,
  fstatSync,
  fsyncSync,
  lstatSync,
  mkdirSync,
  openSync,
  readdirSync,
  readSync,
  realpathSync,
  unlinkSync,
  writeSync,
} from "node:fs";
import { join, sep } from "node:path";
import { RequestError } from "../types.js";

/** A plain file stem: no path, no dots, no extension. ".schem" is added here. */
const NAME = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;
export const MAX_LISTED_FILES = 64;

export function schematicName(value: unknown): string {
  if (typeof value !== "string")
    throw new RequestError("Schematic name must be text.");
  const stem = value.toLowerCase().endsWith(".schem")
    ? value.slice(0, -".schem".length)
    : value;
  if (!NAME.test(stem))
    throw new RequestError(
      "Schematic names use only letters, digits, - and _ (at most 64), with no folder or other extension.",
    );
  return stem;
}

export interface LibraryFile {
  readonly name: string;
  readonly bytes: number;
}

/**
 * The companion's schematic folders. Players name files; they never supply
 * paths. Reads are bounded and refuse links; writes never replace a file.
 */
export class SchematicFiles {
  readonly library: string;
  readonly exports: string;
  constructor(root: string) {
    this.library = join(root, "library");
    this.exports = join(root, "exports");
    mkdirSync(this.library, { recursive: true, mode: 0o700 });
    mkdirSync(this.exports, { recursive: true, mode: 0o700 });
  }

  list(): { files: LibraryFile[]; total: number } {
    const files: LibraryFile[] = [];
    for (const entry of readdirSync(this.library, { withFileTypes: true })) {
      if (!entry.isFile() || !entry.name.toLowerCase().endsWith(".schem"))
        continue;
      const stem = entry.name.slice(0, -".schem".length);
      if (!NAME.test(stem)) continue;
      const stat = lstatSync(join(this.library, entry.name));
      files.push({ name: stem, bytes: stat.size });
    }
    files.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
    return { files: files.slice(0, MAX_LISTED_FILES), total: files.length };
  }

  /** Reads `<library>/<name>.schem` without following links, up to `maxBytes`. */
  read(name: string, maxBytes: number): { bytes: Buffer; sha256: string } {
    const path = join(this.library, `${name}.schem`);
    const link = lstatSync(path, { bigint: true, throwIfNoEntry: false });
    if (link === undefined)
      throw new RequestError(
        `There is no ${name}.schem in the companion's schematic library folder.`,
        404,
      );
    if (link.isSymbolicLink() || !link.isFile())
      throw new RequestError(
        `${name}.schem is a link or not a regular file, so it was not opened.`,
      );
    const root = realpathSync(this.library);
    if (!realpathSync(path).startsWith(root + sep))
      throw new RequestError(
        `${name}.schem resolves outside the library folder, so it was not opened.`,
      );
    const descriptor = openSync(
      path,
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    try {
      const opened = fstatSync(descriptor, { bigint: true });
      if (
        !opened.isFile() ||
        opened.dev !== link.dev ||
        opened.ino !== link.ino
      )
        throw new RequestError(
          `${name}.schem changed while it was being opened; try again.`,
          409,
        );
      if (opened.size > BigInt(maxBytes)) throw tooLarge(name, maxBytes);
      const chunks: Buffer[] = [];
      let total = 0;
      while (true) {
        const chunk = Buffer.allocUnsafe(Math.min(65536, maxBytes - total + 1));
        const count = readSync(descriptor, chunk, 0, chunk.length, null);
        if (count === 0) break;
        total += count;
        if (total > maxBytes) throw tooLarge(name, maxBytes);
        chunks.push(chunk.subarray(0, count));
      }
      const bytes = Buffer.concat(chunks, total);
      return {
        bytes,
        sha256: createHash("sha256").update(bytes).digest("hex"),
      };
    } finally {
      closeSync(descriptor);
    }
  }

  /** Creates `<exports>/<name>.schem`. An existing file or link is never touched. */
  create(name: string, data: Uint8Array): string {
    const file = `${name}.schem`;
    const root = realpathSync(this.exports);
    const path = join(root, file);
    let descriptor: number;
    try {
      descriptor = openSync(path, "wx", 0o600);
    } catch (error) {
      if ((error as { code?: string }).code === "EEXIST")
        throw new RequestError(
          `An export named ${file} already exists and was left unchanged. Choose another name.`,
          409,
        );
      throw error;
    }
    let written = false;
    try {
      let offset = 0;
      while (offset < data.byteLength)
        offset += writeSync(descriptor, data, offset);
      fsyncSync(descriptor);
      written = true;
    } finally {
      closeSync(descriptor);
      // The file was created by this call, so removing a partial write touches nothing else.
      if (!written) unlinkSync(path);
    }
    return file;
  }
}

function tooLarge(name: string, maxBytes: number): RequestError {
  return new RequestError(
    `${name}.schem is larger than the in-game limit of ${maxBytes} bytes, so it was not imported.`,
    413,
  );
}
