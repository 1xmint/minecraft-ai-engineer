import {
  closeSync,
  constants,
  fstatSync,
  ftruncateSync,
  lstatSync,
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
  importSpongeSchematic,
  parseGeometryJson,
  stringifyGeometry,
  type ImportResult,
  type SchematicGeometry,
} from "../schematics/index.js";
import { GEOMETRY_TRANSFORMS, type GeometryTransform } from "./states.js";
import { transformGeometry } from "./transform.js";

export interface CliIo {
  readonly stdout: (text: string) => void;
  readonly stderr: (text: string) => void;
}

const USAGE = `Usage:
  schematic-transforms <operation> --from <schem|json> --to <schem|json> <input> <output>
      [--force] [--discard-block-entities] [--discard-biomes]

Operations: ${GEOMETRY_TRANSFORMS.join(", ")}
Formats are never guessed from file names. The discard options apply only to
--from schem and every discarded item is reported. The output is untrusted
geometry: it grants no ownership or permissions and is not verified to work in
game. Existing output files are never replaced unless --force is given, and the
output may never be the input file or a link to it.
`;

const FORMATS = ["schem", "json"] as const;
type Format = (typeof FORMATS)[number];
const SWITCHES = [
  "--force",
  "--discard-block-entities",
  "--discard-biomes",
] as const;

class UsageError extends Error {}

interface Args {
  operation: GeometryTransform;
  from: Format;
  to: Format;
  input: string;
  output: string;
  switches: ReadonlySet<string>;
}

function parseArgs(argv: readonly string[]): Args {
  const [operation, ...rest] = argv;
  if (
    operation === undefined ||
    !GEOMETRY_TRANSFORMS.includes(operation as GeometryTransform)
  )
    throw new UsageError(
      `the first argument must be one of ${GEOMETRY_TRANSFORMS.join(", ")}`,
    );
  const formats = new Map<string, Format>();
  const switches = new Set<string>();
  const paths: string[] = [];
  for (let i = 0; i < rest.length; i++) {
    const arg = rest[i]!;
    if (arg === "--from" || arg === "--to") {
      const value = rest[++i];
      if (!FORMATS.includes(value as Format))
        throw new UsageError(`${arg} must be followed by schem or json`);
      if (formats.has(arg)) throw new UsageError(`${arg} given more than once`);
      formats.set(arg, value as Format);
    } else if (arg.startsWith("-")) {
      if (!(SWITCHES as readonly string[]).includes(arg))
        throw new UsageError(`unknown option ${arg}`);
      if (switches.has(arg))
        throw new UsageError(`${arg} given more than once`);
      switches.add(arg);
    } else paths.push(arg);
  }
  const from = formats.get("--from");
  const to = formats.get("--to");
  if (from === undefined || to === undefined)
    throw new UsageError("both --from and --to are required");
  if (
    from !== "schem" &&
    (switches.has("--discard-block-entities") ||
      switches.has("--discard-biomes"))
  )
    throw new UsageError("the discard options apply only to --from schem");
  if (paths.length !== 2)
    throw new UsageError("exactly one input and one output path are required");
  return {
    operation: operation as GeometryTransform,
    from,
    to,
    input: paths[0]!,
    output: paths[1]!,
    switches,
  };
}

type Identity = { dev: bigint; ino: bigint };

function readBounded(
  path: string,
  maxBytes: number,
): { bytes: Buffer; identity: Identity } {
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

/** Rejects a symlink, non-file or alias of the input at `output`, and an existing file without --force. */
function checkOutput(output: string, force: boolean, source: Identity): void {
  const target = lstatSync(output, { bigint: true, throwIfNoEntry: false });
  if (target === undefined) return;
  if (target.isSymbolicLink())
    throw new UsageError("output path must not be a symbolic link");
  if (!target.isFile())
    throw new UsageError("output path must identify a regular file");
  if (target.dev === source.dev && target.ino === source.ino)
    throw new UsageError(
      "output path must differ from the input path (including file aliases)",
    );
  if (!force)
    throw new UsageError(
      `${output} already exists; pass --force to replace it`,
    );
}

function writeOutput(
  output: string,
  data: string | Uint8Array,
  force: boolean,
  source: Identity,
): void {
  let descriptor: number;
  try {
    descriptor = openSync(output, "wx");
  } catch (error) {
    if ((error as { code?: string }).code !== "EEXIST") throw error;
    checkOutput(output, force, source);
    // Open without truncating or following a link: check the handle before any destructive write.
    descriptor = openSync(
      output,
      constants.O_RDWR | (constants.O_NOFOLLOW ?? 0),
    );
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
  const args = parseArgs(argv);
  const force = args.switches.has("--force");
  if (resolve(args.input) === resolve(args.output))
    throw new UsageError("output path must differ from the input path");
  const source = readBounded(
    args.input,
    args.from === "schem"
      ? DEFAULT_SCHEMATIC_LIMITS.maxCompressedBytes
      : MAX_GEOMETRY_JSON_BYTES,
  );
  // Fail on a protected output before decoding; writeOutput checks again on the open handle.
  checkOutput(args.output, force, source.identity);

  let geometry: SchematicGeometry;
  let imported: ImportResult | undefined;
  if (args.from === "schem") {
    imported = importSpongeSchematic(source.bytes, {
      discardBlockEntities: args.switches.has("--discard-block-entities"),
      discardBiomes: args.switches.has("--discard-biomes"),
    });
    geometry = imported.geometry;
  } else
    geometry = parseGeometryJson(
      source.bytes.toString("utf8"),
      DEFAULT_SCHEMATIC_LIMITS,
    );

  // Transform and encode completely before the output is opened.
  const result = transformGeometry(geometry, args.operation);
  const data =
    args.to === "schem"
      ? exportSpongeSchematic(result)
      : stringifyGeometry(result);

  if (imported !== undefined) reportDiscarded(imported, io);
  writeOutput(args.output, data, force, source.identity);
  const { size } = result;
  io.stdout(
    `Wrote ${args.output} (${args.operation}, ${size.x}x${size.y}x${size.z}, ` +
      `${args.to === "schem" ? "Sponge schematic v3" : "geometry JSON"}, untrusted geometry only).\n`,
  );
}

/** Returns the exit code: 0 on success, 1 when the input or output is rejected, 2 for usage errors. */
export function runSchematicTransformsCli(
  argv: readonly string[],
  io: CliIo,
): number {
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
  process.exitCode = runSchematicTransformsCli(process.argv.slice(2), {
    stdout: (t) => process.stdout.write(t),
    stderr: (t) => process.stderr.write(t),
  });
