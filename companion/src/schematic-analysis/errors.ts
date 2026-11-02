export type AnalysisErrorCode =
  "E_DATA_VERSION_MISMATCH" | "E_DETAIL_LIMIT" | "E_INPUT_FILE";

/** Analysis-specific rejections. Codec and validation errors pass through as SchematicError. */
export class AnalysisError extends Error {
  readonly code: AnalysisErrorCode;
  constructor(code: AnalysisErrorCode, message: string) {
    super(`${code}: ${message}`);
    this.name = "AnalysisError";
    this.code = code;
  }
}
