import { createHash } from "node:crypto";
import { RequestError } from "./types.js";

export interface TruthTableBehavior {
  readonly schemaVersion: 2;
  readonly function: "truth-table";
  readonly inputs: readonly string[];
  readonly outputs: readonly boolean[];
  readonly settleGameTicks: number;
}
export function validateTruthTableBehavior(value: unknown): TruthTableBehavior {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new RequestError("Expected truth table.");
  const v = value as Record<string, unknown>;
  const keys = [
    "schemaVersion",
    "function",
    "inputs",
    "outputs",
    "settleGameTicks",
  ];
  if (
    Object.keys(v).length !== keys.length ||
    Object.keys(v).some((k) => !keys.includes(k)) ||
    v.schemaVersion !== 2 ||
    v.function !== "truth-table" ||
    !Array.isArray(v.inputs) ||
    v.inputs.length < 1 ||
    v.inputs.length > 4 ||
    Array.from(v.inputs).some(
      (n) =>
        typeof n !== "string" ||
        !/^[a-z][a-z0-9_]{0,31}$/.test(n) ||
        n === "output",
    ) ||
    new Set(v.inputs).size !== v.inputs.length ||
    !Array.isArray(v.outputs) ||
    v.outputs.length !== 2 ** v.inputs.length ||
    Array.from(v.outputs).some((b) => typeof b !== "boolean") ||
    !Number.isInteger(v.settleGameTicks) ||
    (v.settleGameTicks as number) < 0 ||
    (v.settleGameTicks as number) > 128
  )
    throw new RequestError("Invalid complete truth table.");
  return Object.freeze({
    schemaVersion: 2,
    function: "truth-table",
    inputs: Object.freeze([...v.inputs] as string[]),
    outputs: Object.freeze([...v.outputs] as boolean[]),
    settleGameTicks: v.settleGameTicks as number,
  });
}
export function truthTableDigest(value: TruthTableBehavior): string {
  const v = validateTruthTableBehavior(value);
  return createHash("sha256")
    .update(
      `truth-v2|${v.inputs.join(",")}|${v.outputs.map((b) => (b ? "1" : "0")).join("")}|${v.settleGameTicks}`,
    )
    .digest("hex");
}
export function truthTableSummary(value: TruthTableBehavior): string {
  const v = validateTruthTableBehavior(value);
  return (
    `Intended truth table (settle within ${v.settleGameTicks} game ticks; then stable for 8 game ticks):` +
    v.outputs
      .map(
        (output, row) =>
          "\n" +
          v.inputs
            .map(
              (name, i) =>
                `${name}=${row & (1 << (v.inputs.length - i - 1)) ? "on" : "off"}`,
            )
            .join(", ") +
          ` -> output=${output ? "on" : "off"}`,
      )
      .join("")
  );
}
