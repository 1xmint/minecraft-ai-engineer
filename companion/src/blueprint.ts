import { makeMechanismBlueprint, mechanism } from "./catalog.js";
import type { Blueprint, DesignRequest } from "./types.js";
export { rotatePosition, rotateBlockState } from "./geometry.js";
export { validateDesignRequest } from "./types.js";

/** Compatibility entry point; recipe data lives only in the shared catalog. */
export function makeReferenceBlueprint(
  input: DesignRequest,
  explanation = mechanism("signal-lamp").explanation,
): Blueprint {
  return {
    ...makeMechanismBlueprint(input, { template: "signal-lamp", explanation }),
    anchor: { ...input.anchor },
  };
}
