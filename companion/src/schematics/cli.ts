import {
  closeSync,
  fstatSync,
  ftruncateSync,
  openSync,
  readSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import {
  DEFAULT_SCHEMATIC_LIMITS,
  MAX_GEOMETRY_JSON_BYTES,
  SchematicError,
  exportSpongeSchematic,
  geometryDigest,
  importSpongeSchematic,
  parseGeometryJson,
  stringifyGeometry,
  type ImportResult,
} from "./index.js";

export interface CliIo {
  readonly stdout: (text: string) => void;
  readonly stderr: (text: string) => void;
}

const USAGE = `Usage:
  schematics import <in.schem> <out.json> [--force] [--discard-block-entities] [--discard-biomes]
  schematics export <in.json> <out.schem> [--force]
  schematics inspect <in.schem> [--discard-block-entities] [--discard-biomes]

Imported geometry is untrusted shape data. It grants no ownership or permissions
and is not verified to work in game. Existing output files are never replaced
unless --force is given.
`;

const FLAGS: Record<string, readonly string[]> = {
  import: ["--force", "--discard-block-entities", "--discard-biomes"],
  export: ["--force"],
  inspect: ["--discard-block-entities", "--discard-biomes"],
};
const ARITY: Record<string, number> = { import: 2, export: 2, inspect: 1 };

class UsageError extends Error {}

interface InputFile {
  bytes: Buffer;
  identity: { dev: bigint; ino: bigint };
}

function readBounded(path: string, maxBytes: number): InputFile {
  if (!statSync(path).isFile())
    throw new UsageError("input path must identify a regular file");
  const descriptor = openSync(path, "r");
  try {
    const identity = fstatSync(descriptor, { bigint: true });
    if (!identity.isFile())
      throw new UsageError("input path must identify a regular file");
    if (identity.size > BigInt(maxBytes))
      throw new SchematicError(
        "E_INPUT_TOO_LARGE",
        `${path} exceeds the limit of ${maxBytes} bytes`,
      );
    const chunks: Buffer[] = [];
    let total = 0;
    while (true) {
      const chunk = Buffer.allocUnsafe(Math.min(65536, maxBytes - total + 1));
      const count = readSync(descriptor, chunk, 0, chunk.length, null);
      if (count === 0) break;
      total += count;
      if (total > maxBytes)
        throw new SchematicError(
          "E_INPUT_TOO_LARGE",
          `${path} exceeds the limit of ${maxBytes} bytes`,
        );
      chunks.push(chunk.subarray(0, count));
    }
    return { bytes: Buffer.concat(chunks, total), identity };
  } finally {
    closeSync(descriptor);
  }
}

function writeOutput(
  input: string,
  output: string,
  data: string | Uint8Array,
  force: boolean,
  source: InputFile["identity"],
): void {
  if (resolve(input) === resolve(output))
    throw new UsageError("output path must differ from the input path");
  let descriptor: number;
  try {
    descriptor = openSync(output, "wx");
  } catch (error) {
    if ((error as { code?: string }).code !== "EEXIST") throw error;
    if (!force)
      throw new UsageError(
        `${output} already exists; pass --force to replace it`,
      );
    // Open without truncating: check the actual file handle before any destructive write.
    descriptor = openSync(output, "r+");
  }
  try {
    const target = fstatSync(descriptor, { bigint: true });
    if (!target.isFile())
      throw new UsageError("output path must identify a regular file");
    if (target.dev === source.dev && target.ino === source.ino)
      throw new UsageError(
        "output path must differ from the input path (including file aliases)",
      );
    ftruncateSync(descriptor, 0);
    writeFileSync(descriptor, data);
  } finally {
    closeSync(descriptor);
  }
}

function summary(result: ImportResult): object {
  const { geometry, discarded, rootLayout } = result;
  return {
    trust:
      "untrusted geometry only: no ownership, permissions or functional verification",
    dataVersion: geometry.dataVersion,
    size: geometry.size,
    offset: geometry.offset,
    paletteEntries: geometry.palette.length,
    digest: geometryDigest(geometry),
    rootLayout,
    ...(geometry.untrustedMetadata
      ? { untrustedMetadata: geometry.untrustedMetadata }
      : {}),
    discarded,
  };
}

function reportDiscarded(result: ImportResult, io: CliIo): void {
  const { blockEntities, biomes, metadataKeys } = result.discarded;
  if (blockEntities.length > 0) {
    io.stderr(
      `Discarded ${blockEntities.length} block entities (their data was not read):\n`,
    );
    for (const e of blockEntities)
      io.stderr(`  ${e.id} at ${e.pos.join(",")}\n`);
  }
  if (biomes) io.stderr("Discarded biome data.\n");
  if (metadataKeys.length > 0)
    io.stderr(`Discarded metadata fields: ${metadataKeys.join(", ")}\n`);
}

function run(argv: readonly string[], io: CliIo): void {
  const [command, ...rest] = argv;
  if (command === undefined || !(command in FLAGS))
    throw new UsageError("unknown command");
  const flags = rest.filter((a) => a.startsWith("--"));
  const paths = rest.filter((a) => !a.startsWith("--"));
  for (const f of flags)
    if (!FLAGS[command]!.includes(f))
      throw new UsageError(`unknown option ${f}`);
  if (paths.length !== ARITY[command])
    throw new UsageError(`${command} takes ${ARITY[command]} path(s)`);
  const force = flags.includes("--force");
  const options = {
    discardBlockEntities: flags.includes("--discard-block-entities"),
    discardBiomes: flags.includes("--discard-biomes"),
  };
  const input = paths[0]!;
  if (command === "export") {
    const source = readBounded(input, MAX_GEOMETRY_JSON_BYTES);
    const text = source.bytes.toString("utf8");
    const bytes = exportSpongeSchematic(
      parseGeometryJson(text, DEFAULT_SCHEMATIC_LIMITS),
    );
    writeOutput(input, paths[1]!, bytes, force, source.identity);
    io.stdout(
      `Wrote ${paths[1]} (${bytes.byteLength} bytes, Sponge schematic v3).\n`,
    );
    return;
  }
  const source = readBounded(
    input,
    DEFAULT_SCHEMATIC_LIMITS.maxCompressedBytes,
  );
  const result = importSpongeSchematic(source.bytes, options);
  reportDiscarded(result, io);
  if (command === "inspect") {
    io.stdout(`${JSON.stringify(summary(result), null, 2)}\n`);
    return;
  }
  writeOutput(
    input,
    paths[1]!,
    stringifyGeometry(result.geometry),
    force,
    source.identity,
  );
  const { size } = result.geometry;
  io.stdout(
    `Wrote ${paths[1]} (${size.x}x${size.y}x${size.z}, untrusted geometry only).\n`,
  );
}

/** Returns the exit code: 0 on success, 1 when the input or output is rejected, 2 for usage errors. */
export function runSchematicsCli(argv: readonly string[], io: CliIo): number {
  try {
    run(argv, io);
    return 0;
  } catch (error) {
    if (error instanceof UsageError) {
      io.stderr(`${error.message}\n\n${USAGE}`);
      return 2;
    }
    io.stderr(`${error instanceof Error ? error.message : String(error)}\n`);
    return 1;
  }
}

if (
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(process.argv[1]).href
)
  process.exitCode = runSchematicsCli(process.argv.slice(2), {
    stdout: (t) => process.stdout.write(t),
    stderr: (t) => process.stderr.write(t),
  });
