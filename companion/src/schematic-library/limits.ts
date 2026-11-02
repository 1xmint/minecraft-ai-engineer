import type { SchematicLimits } from "../schematics/sponge.js";

/**
 * In-game imports are much smaller than the CLI's limits: the server keeps
 * every design in memory while a player works with it.
 */
export const IN_GAME_LIMITS: SchematicLimits = Object.freeze({
  maxCompressedBytes: 4 * 1024 * 1024,
  maxDecompressedBytes: 32 * 1024 * 1024,
  maxNbtDepth: 32,
  maxNbtTags: 1_000_000,
  maxVolume: 64 * 64 * 64,
  maxPaletteEntries: 4096,
});

/** The execution core's per-blueprint block cap; larger designs can be inspected but not placed. */
export const MAX_PLACEMENT_BLOCKS = 4096;
