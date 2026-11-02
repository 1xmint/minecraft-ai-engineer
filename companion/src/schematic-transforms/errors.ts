export type TransformErrorCode =
  "E_TRANSFORM_ARGUMENT" | "E_UNSUPPORTED_STATE" | "E_COORDINATE_RANGE";

export class TransformError extends Error {
  readonly code: TransformErrorCode;

  constructor(code: TransformErrorCode, message: string) {
    super(`${code}: ${message}`);
    this.name = "TransformError";
    this.code = code;
  }
}
