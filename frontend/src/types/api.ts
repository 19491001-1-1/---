export type BudgetMode = 'EVALUATIONS' | 'TIME_MS'

export interface Meta {
  projectName: string
  version: string
  javaVersion: string
  productSearchAlgorithms: number
  productDecoders: number
  productCombinations: number
  implementedAlgorithmEntries: number
  searchDisplayWeightPercent: number
  packingDisplayWeightPercent: number
  capabilities: string[]
}

export interface SearchAlgorithm {
  machineValue: string
  chineseName: string
  englishName: string
  description: string
  stochastic: boolean
  supportedBudgetModes: BudgetMode[]
}

export interface PackingDecoder {
  machineValue: string
  chineseName: string
  englishName: string
  description: string
  rotationSupported: boolean
  spaceStructure: string
}

export interface PackingInstanceSummary {
  id: string
  name: string
  itemCount: number
  binWidth: number
  binHeight: number
  totalItemArea: number
  source: string
}

export interface PackingItem {
  id: number
  width: number
  height: number
  area: number
}

export interface PackingInstanceDetail extends PackingInstanceSummary {
  binArea: number
  items: PackingItem[]
}

export interface SolveRequest {
  instanceId: string
  searchAlgorithm: string
  decoder: string
  seed: number
  rotationAllowed: boolean
  budgetMode: BudgetMode
  maxEvaluations: number | null
  maxMilliseconds: number | null
}

export interface Placement {
  itemId: number
  originalWidth: number
  originalHeight: number
  x: number
  y: number
  placedWidth: number
  placedHeight: number
  rotated: boolean
  area: number
}

export interface SearchPoint {
  step: number
  evaluations: number
  bestPackedArea: number
}

export interface SolveResult {
  runId: string
  status: 'SUCCEEDED' | 'INVALID'
  completedAt: string
  instanceId: string
  searchAlgorithm: string
  decoder: string
  seed: number
  rotationAllowed: boolean
  legal: boolean
  validationErrors: string[]
  packedArea: number
  binArea: number
  utilization: number
  usedHeight: number
  elapsedMilliseconds: number
  evaluations: number
  cacheQueries: number | null
  cacheHits: number | null
  cacheHitRatePercent: number | null
  terminationReason: string
  placements: Placement[]
  convergence: SearchPoint[]
  algorithmStatistics: Record<string, unknown>
}

export interface ApiErrorPayload {
  code: string
  message: string
  fieldErrors: Record<string, string>
  requestId: string
  timestamp: string
}

export interface ExperimentRequest {
  name: string
  instanceIds: string[]
  searchAlgorithms: string[]
  decoders: string[]
  seeds: number[]
  rotationAllowed: boolean
  budgetMode: BudgetMode
  maxEvaluations: number | null
  maxMilliseconds: number | null
  parallelism: number
}

export interface ExperimentPreview {
  taskCount: number
  instanceCount: number
  searchAlgorithmCount: number
  decoderCount: number
  seedCount: number
  parallelism: number
  estimatedWork: string
  warnings: string[]
}

export interface ExperimentResultRow {
  instanceId: string
  searchAlgorithm: string
  decoder: string
  seed: number
  legal: boolean
  utilization: number
  packedArea: number
  elapsedMilliseconds: number
  evaluations: number
}

export interface ExperimentTask {
  id: string
  name: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  totalTasks: number
  completedTasks: number
  progressPercent: number
  currentTask: string | null
  legalResults: number
  averageUtilization: number
  bestUtilization: number | null
  error: string | null
  results: ExperimentResultRow[]
}

/**
 * 实验产物清单里的一项。
 *
 * `name` 是相对产物根目录的路径，可能带子目录（例如 `convergence/gcut1.svg`）。
 * 它同时充当下载时的钥匙：只有服务端清单里列出的名字才能被下载。
 */
export interface ExperimentArtifact {
  name: string
  sizeBytes: number
  contentType: string
}

/** 分析视图的单格数据：标量指标 + 该格收敛轨迹 + 它在结果数组里的下标。 */
export interface AnalysisCell {
  index: number
  instanceId: string
  searchAlgorithm: string
  decoder: string
  seed: number
  legal: boolean
  utilization: number
  packedArea: number
  elapsedMilliseconds: number
  evaluations: number
  convergence: SearchPoint[]
  /** 轨迹点数超限被抽样过。UI 需要如实告诉用户，否则会以为算法真的只迭代了这么少。 */
  curveTruncated: boolean
}

/** 分析视图需要的实例几何。 */
export interface AnalysisInstance {
  id: string
  name: string
  binWidth: number
  binHeight: number
  itemCount: number
}

/** 一个任务的完整分析数据。注意 `cells` 只含已完成的格子，因此不是稠密的。 */
export interface ExperimentAnalysis {
  id: string
  name: string
  status: ExperimentTask['status']
  totalTasks: number
  completedTasks: number
  rotationAllowed: boolean
  budgetMode: BudgetMode
  results: ExperimentResultRow[]
  cells: AnalysisCell[]
  instances: Record<string, AnalysisInstance>
}

/** 任务是否已经结束。终态任务的 SSE 流只会补发一个 complete 事件后关闭。 */
export const terminalStatuses: ExperimentTask['status'][] = ['COMPLETED', 'FAILED', 'CANCELLED']

export const isTerminal = (task: ExperimentTask | null): boolean =>
  task !== null && terminalStatuses.includes(task.status)
