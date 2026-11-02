import { pathToFileURL } from "node:url";
import { analyzeGeometry } from "./analysis.js";
import {
  DEFAULT_MAX_DETAILS,
  MAX_DETAILS_LIMIT,
  compareGeometries,
} from "./compare.js";
import { readGeometryFile, type ReadOptions } from "./io.js";

export interface CliIo {
  readonly stdout: (text: string) => void;
  readonly stderr: (text: string) => void;
}

const USAGE = `Usage:
  schematic-analysis analyze <in.json>
  schematic-analysis compare <a.json> <b.json> [--max-details N]

Reads neutral geometry JSON and prints a JSON report to stdout. Never writes
files. Reports are block counts and differences only: not crafting costs,
inventory, circuit reliability or functional verification.
--max-details is 0 to ${MAX_DETAILS_LIMIT} (default ${DEFAULT_MAX_DETAILS}); counts are always exact.
`;

class UsageError extends Error {}

function parseArgs(argv: readonly string[]): {
  command: string;
  paths: string[];
  maxDetails: number;
} {
  const [command, ...rest] = argv;
  if (command !== "analyze" && command !== "compare")
    throw new UsageError("expected the command analyze or compare");
  const paths: string[] = [];
  let maxDetails = DEFAULT_MAX_DETAILS;
  for (let i = 0; i < rest.length; i++) {
    const arg = rest[i]!;
    if (arg === "--max-details" && command === "compare") {
      const value = rest[++i];
      if (
        value === undefined ||
        !/^\d{1,5}$/.test(value) ||
        Number(value) > MAX_DETAILS_LIMIT
      )
        throw new UsageError(
          `--max-details needs an integer from 0 to ${MAX_DETAILS_LIMIT}`,
        );
      maxDetails = Number(value);
    } else if (arg.startsWith("-")) {
      throw new UsageError(`unknown option ${arg}`);
    } else paths.push(arg);
  }
  const arity = command === "analyze" ? 1 : 2;
  if (paths.length !== arity)
    throw new UsageError(
      `${command} takes ${arity} input file${arity === 1 ? "" : "s"}`,
    );
  return { command, paths, maxDetails };
}

/**
 * Returns the exit code: 0 on success, 1 when an input is rejected, 2 for
 * usage errors. `read` lets callers lower the byte and geometry limits.
 */
export function runAnalysisCli(
  argv: readonly string[],
  io: CliIo,
  read: ReadOptions = {},
): number {
  let args;
  try {
    args = parseArgs(argv);
  } catch (error) {
    io.stderr(`${(error as Error).message}\n\n${USAGE}`);
    return 2;
  }
  try {
    const inputs = args.paths.map((p) => readGeometryFile(p, read));
    const report =
      args.command === "analyze"
        ? analyzeGeometry(inputs[0], read.limits)
        : compareGeometries(inputs[0], inputs[1], {
            maxDetails: args.maxDetails,
            ...(read.limits ? { limits: read.limits } : {}),
          });
    io.stdout(`${JSON.stringify(report, null, 2)}\n`);
    return 0;
  } catch (error) {
    io.stderr(`${error instanceof Error ? error.message : String(error)}\n`);
    return 1;
  }
}

if (
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(process.argv[1]).href
)
  process.exitCode = runAnalysisCli(process.argv.slice(2), {
    stdout: (t) => process.stdout.write(t),
    stderr: (t) => process.stderr.write(t),
  });
