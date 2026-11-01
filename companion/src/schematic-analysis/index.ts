export {
  AIR_BLOCK_IDS,
  ANALYSIS_NOTICE,
  analyzeGeometry,
  type Count,
  type GeometryAnalysis,
} from "./analysis.js";
export {
  DEFAULT_MAX_DETAILS,
  MAX_DETAILS_LIMIT,
  compareGeometries,
  type ChangedCell,
  type CompareOptions,
  type DetailList,
  type GeometryComparison,
  type OneSidedCell,
  type OneSidedCount,
  type VolumeSummary,
} from "./compare.js";
export { AnalysisError, type AnalysisErrorCode } from "./errors.js";
export { readGeometryFile, type ReadOptions } from "./io.js";
