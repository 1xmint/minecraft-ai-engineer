import { isDeepStrictEqual } from "node:util";
import { RequestError, integer, object } from "./types.js";
import {
  validateTruthTableBehavior,
  truthTableDigest,
  type TruthTableBehavior,
} from "./truth-table.js";

/** Replay server observations; stored verdicts never establish verification. */
export function validateSettledTrace(
  value: unknown,
  accepted: TruthTableBehavior,
  expectedRow: number,
  requirePass = false,
) {
  const behavior = validateTruthTableBehavior(accepted);
  const row = integer(
    expectedRow,
    "truth-table row",
    0,
    behavior.outputs.length - 1,
  );
  const v = object(value);
  const horizon = behavior.settleGameTicks + 8;
  if (!Array.isArray(v.samples) || v.samples.length > horizon + 1)
    throw new RequestError("Invalid bounded settled trace samples.");
  let last = -1,
    missing: string | undefined,
    mismatch: string | undefined;
  const samples = Array.from(v.samples).map((raw) => {
    const sample = object(raw);
    if (
      Object.keys(sample).length !== 2 ||
      !("elapsedTicks" in sample) ||
      !("observed" in sample) ||
      typeof sample.observed !== "boolean"
    )
      throw new RequestError("Invalid settled trace sample fields.");
    const elapsed = integer(sample.elapsedTicks, "elapsedTicks", 0, horizon);
    if (elapsed <= last) throw new RequestError("Trace samples must advance.");
    if (elapsed !== last + 1 && missing === undefined)
      missing = `missing game-tick sample after ${last}, next observed ${elapsed}`;
    if (
      elapsed >= behavior.settleGameTicks &&
      sample.observed !== behavior.outputs[row] &&
      mismatch === undefined
    )
      mismatch = `output expected ${behavior.outputs[row]}, observed ${sample.observed} at elapsed game tick ${elapsed}`;
    last = elapsed;
    return { elapsedTicks: elapsed, observed: sample.observed };
  });
  const expected = {
    schemaVersion: 1,
    behaviorDigest: truthTableDigest(behavior),
    row,
    expected: behavior.outputs[row],
    settleGameTicks: behavior.settleGameTicks,
    stableGameTicks: 8,
    samples,
    verdict:
      missing !== undefined
        ? "inconclusive"
        : mismatch !== undefined
          ? "failed"
          : last === horizon
            ? "passed"
            : "running",
    failure:
      missing !== undefined ? `inconclusive: ${missing}` : (mismatch ?? ""),
  };
  if (
    !isDeepStrictEqual(v, expected) ||
    (requirePass && expected.verdict !== "passed")
  )
    throw new RequestError(
      "Stored settled trace differs from observations or accepted behavior.",
    );
  return expected;
}
