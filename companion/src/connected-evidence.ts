import { readConnectedTrace } from "./connected-trace.js";

/** Historical observations only. Scope, authority and original-input restoration are separate. */
export function readConnectedEvidence(
  value: unknown,
  graphDigest: string,
  complete: boolean,
) {
  if (typeof graphDigest !== "string" || !/^[a-f0-9]{64}$/u.test(graphDigest))
    throw new TypeError("Invalid connected graph digest");
  if (
    !Array.isArray(value) ||
    value.length > 8 ||
    (complete && value.length !== 8)
  )
    throw new TypeError(
      "Connected suite needs exactly eight ordered cases to complete",
    );
  // Require ordinary dense JSON arrays, rejecting sparse and extra-key buffers.
  if (
    Object.keys(value).length !== value.length ||
    Object.keys(value).some((key, index) => key !== String(index))
  )
    throw new TypeError("Connected cases must be a dense JSON array");
  let previousEnd = -1;
  return value.map((raw, index) => {
    const vector = index < 4 ? index : 7 - index;
    const trace = readConnectedTrace(raw, {
      graphDigest,
      a: (vector & 2) !== 0,
      b: (vector & 1) !== 0,
    });
    if (trace.startGameTick <= previousEnd)
      throw new TypeError("Connected case clocks overlap or go backward");
    if ((complete || index < value.length - 1) && trace.verdict !== "passed")
      throw new TypeError(
        "Earlier or complete connected cases must have all consecutive settled observations",
      );
    previousEnd = trace.startGameTick + 55;
    return trace;
  });
}
