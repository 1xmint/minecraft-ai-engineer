export type SchematicErrorCode =
  | "E_INPUT_TOO_LARGE"
  | "E_NOT_GZIP"
  | "E_DECOMPRESS"
  | "E_DECOMPRESSED_TOO_LARGE"
  | "E_NBT_MALFORMED"
  | "E_NBT_TOO_DEEP"
  | "E_NBT_TOO_MANY_TAGS"
  | "E_NBT_TRAILING_DATA"
  | "E_ROOT"
  | "E_UNSUPPORTED_VERSION"
  | "E_FIELD"
  | "E_UNSUPPORTED_FIELD"
  | "E_TOO_LARGE"
  | "E_PALETTE"
  | "E_BLOCK_STATE"
  | "E_BLOCK_DATA"
  | "E_ENTITIES"
  | "E_BLOCK_ENTITIES"
  | "E_BIOMES"
  | "E_METADATA"
  | "E_GEOMETRY"
  | "E_JSON";

/** Every rejection carries a stable code and the field path that failed. */
export class SchematicError extends Error {
  readonly code: SchematicErrorCode;
  constructor(code: SchematicErrorCode, message: string) {
    super(`${code}: ${message}`);
    this.name = "SchematicError";
    this.code = code;
  }
}
