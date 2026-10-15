import type { AnalysisCell, SearchPoint } from '@/types/api'

/**
 * 分析视图的纯计算层：把后端给的逐格结果聚合成热力图、排名、箱线图和收敛序列。
 *
 * <p>刻意做成不依赖 Vue 的纯函数：这些统计口径是「结论」而不是「渲染」，
 * 单测里可以直接喂一组构造好的数字断言结果，不必挂载组件。
 *
 * <p>排名口径与核心层 {@code ExperimentArtifacts.averageRank} 保持一致：名次 = 1 + 同一实例下
 * 严格更优的算法数（并列同名次、并列之后跳号），再按实例取平均。两边算法必须一致，
 * 否则「页面上看到的名次」和「下载的报告里的名次」会对不上，而这正是最难解释的一类差异。
 */

/** 按算法聚合出的一行统计。字段名与报告里的「算法总览」表逐列对应。 */
export interface AlgorithmSummary {
  algorithm: string
  runs: number
  validRuns: number
  meanUtilization: number
  bestUtilization: number
  worstUtilization: number
  meanMilliseconds: number
  meanEvaluations: number
  /** 平均名次，越小越好。口径见文件头注释。 */
  meanRank: number
}

/** 箱线图的五个数与离群值。 */
export interface BoxplotStats {
  min: number
  q1: number
  median: number
  q3: number
  max: number
  mean: number
  count: number
  /** 超出「上须 / 下须」的点，用原始利用率值表示。 */
  outliers: number[]
}

const mean = (values: number[]) =>
  values.length === 0 ? 0 : values.reduce((total, value) => total + value, 0) / values.length

/**
 * 分位数，用线性插值（与 numpy 默认、Excel 的 PERCENTILE.INC 同一套）。
 *
 * <p>不自己发明一套口径：箱线图的四分位数有三种常见定义，选哪种都行，但必须写死并注释，
 * 否则「同一个数据换个库画出来箱体不一样」会是永久的困惑来源。
 */
export function quantile(sorted: number[], fraction: number): number {
  if (sorted.length === 0) return 0
  if (sorted.length === 1) return sorted[0]
  const position = (sorted.length - 1) * fraction
  const lower = Math.floor(position)
  const upper = Math.ceil(position)
  if (lower === upper) return sorted[lower]
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}

/**
 * 按 `algorithm` 聚合。
 *
 * <p>只统计**合法**的结果参与均值：非法结果在 Web 路径下利用率可能是 0 或残缺值，
 * 混进去会把一个偶尔失败的算法算成「平均很差」，而真正的问题是它失败了。
 * `runs` 与 `validRuns` 分开保留，让界面能显示「合法 7/8」而不是直接丢掉失败信息。
 */
export function summarizeByAlgorithm(cells: AnalysisCell[]): AlgorithmSummary[] {
  const groups = new Map<string, AnalysisCell[]>()
  for (const cell of cells) {
    const list = groups.get(cell.searchAlgorithm) ?? []
    list.push(cell)
    groups.set(cell.searchAlgorithm, list)
  }

  const summaries: AlgorithmSummary[] = []
  for (const [algorithm, group] of groups) {
    const valid = group.filter((cell) => cell.legal)
    const utilization = valid.map((cell) => cell.utilization)
    summaries.push({
      algorithm,
      runs: group.length,
      validRuns: valid.length,
      meanUtilization: mean(utilization),
      bestUtilization: utilization.length === 0 ? 0 : Math.max(...utilization),
      worstUtilization: utilization.length === 0 ? 0 : Math.min(...utilization),
      meanMilliseconds: mean(valid.map((cell) => cell.elapsedMilliseconds)),
      meanEvaluations: mean(valid.map((cell) => cell.evaluations)),
      meanRank: 0,
    })
  }

  // 名次要按「实例 × 算法」先聚成一次运行的均值，再逐实例排——直接拿逐格结果排名的话，
  // 某个实例多跑了一个种子就会把名次算重，与报告口径不符。
  const perInstance = new Map<string, Map<string, number[]>>()
  for (const cell of cells) {
    if (!cell.legal) continue
    const byAlgorithm = perInstance.get(cell.instanceId) ?? new Map<string, number[]>()
    const values = byAlgorithm.get(cell.searchAlgorithm) ?? []
    values.push(cell.utilization)
    byAlgorithm.set(cell.searchAlgorithm, values)
    perInstance.set(cell.instanceId, byAlgorithm)
  }

  const rankTotals = new Map<string, { total: number; count: number }>()
  for (const byAlgorithm of perInstance.values()) {
    const means = [...byAlgorithm].map(([algorithm, values]) => ({ algorithm, value: mean(values) }))
    for (const entry of means) {
      const strictlyBetter = means.filter((other) => other.value > entry.value + 1e-9).length
      const accumulator = rankTotals.get(entry.algorithm) ?? { total: 0, count: 0 }
      accumulator.total += 1 + strictlyBetter
      accumulator.count += 1
      rankTotals.set(entry.algorithm, accumulator)
    }
  }

  return summaries
    .map((summary) => {
      const accumulator = rankTotals.get(summary.algorithm)
      return {
        ...summary,
        meanRank: accumulator && accumulator.count > 0 ? accumulator.total / accumulator.count : 0,
      }
    })
    .sort((left, right) => {
      // 并列时按「平均名次 → 更好利用率 → 算法名」逐级比较，保证顺序稳定可复现，
      // 不会因为 Map 的插入顺序变化而让表格每次刷新都换个排法。
      if (Math.abs(left.meanRank - right.meanRank) > 1e-9) return left.meanRank - right.meanRank
      if (Math.abs(right.meanUtilization - left.meanUtilization) > 1e-9) {
        return right.meanUtilization - left.meanUtilization
      }
      return left.algorithm.localeCompare(right.algorithm)
    })
}

/**
 * 箱线图的须：Tukey 的 1.5 倍四分位距。
 *
 * <p>须取「落在 1.5×IQR 之内的**实际数据点**」而不是算出来的边界值——把须画到边界值上，
 * 会让「最近的点离边界还有一段距离」看起来像是有一个不存在的观测值。
 */
export function boxplot(values: number[]): BoxplotStats {
  if (values.length === 0) {
    return { min: 0, q1: 0, median: 0, q3: 0, max: 0, mean: 0, count: 0, outliers: [] }
  }
  const sorted = [...values].sort((left, right) => left - right)
  const q1 = quantile(sorted, 0.25)
  const median = quantile(sorted, 0.5)
  const q3 = quantile(sorted, 0.75)
  const spread = q3 - q1
  const lowFence = q1 - 1.5 * spread
  const highFence = q3 + 1.5 * spread
  const inside = sorted.filter((value) => value >= lowFence && value <= highFence)
  return {
    min: inside.length === 0 ? sorted[0] : inside[0],
    q1,
    median,
    q3,
    max: inside.length === 0 ? sorted[sorted.length - 1] : inside[inside.length - 1],
    mean: mean(sorted),
    count: sorted.length,
    outliers: sorted.filter((value) => value < lowFence || value > highFence),
  }
}

/** 一个热力格子：落在同一个 (x, y) 组合上的所有结果的聚合。 */
export interface HeatCell {
  x: string
  y: string
  /** 合法结果的均值利用率；没有合法结果时为 null，渲染成「无数据」而不是 0%。 */
  meanUtilization: number | null
  bestUtilization: number | null
  count: number
  validCount: number
}

export interface Heatmap {
  xs: string[]
  ys: string[]
  /** 按 `ys` 行优先、`xs` 列序排列，行内长度与 `xs` 等长。 */
  cells: HeatCell[][]
  min: number
  max: number
  /** 全表没有任何合法结果，界面应显示空状态而不是一张纯灰的图。 */
  empty: boolean
}

/** 热力图可以对比的两个维度。 */
export type HeatDimension = 'searchAlgorithm' | 'decoder' | 'instanceId'

export const HEAT_DIMENSION_LABEL: Record<HeatDimension, string> = {
  searchAlgorithm: '搜索器',
  decoder: '解码器',
  instanceId: '实例',
}

const dimensionOf = (cell: AnalysisCell, dimension: HeatDimension): string => {
  if (dimension === 'searchAlgorithm') return cell.searchAlgorithm
  if (dimension === 'decoder') return cell.decoder
  return cell.instanceId
}

/**
 * 两个维度做交叉表。空出来的组合保留为 `count: 0` 的格子而不是从表里删掉——
 * 删掉会让行列对不齐，界面拼出来的热力图会整体错位一格，且错得毫无声息。
 */
export function heatmap(cells: AnalysisCell[], x: HeatDimension, y: HeatDimension): Heatmap {
  const xs = [...new Set(cells.map((cell) => dimensionOf(cell, x)))].sort()
  const ys = [...new Set(cells.map((cell) => dimensionOf(cell, y)))].sort()
  const buckets = new Map<string, AnalysisCell[]>()
  for (const cell of cells) {
    const key = `${dimensionOf(cell, x)}\u0000${dimensionOf(cell, y)}`
    const list = buckets.get(key) ?? []
    list.push(cell)
    buckets.set(key, list)
  }

  let min = Number.POSITIVE_INFINITY
  let max = Number.NEGATIVE_INFINITY
  const grid = ys.map((row) => xs.map((column) => {
    const bucket = buckets.get(`${column}\u0000${row}`) ?? []
    const valid = bucket.filter((cell) => cell.legal).map((cell) => cell.utilization)
    if (valid.length > 0) {
      min = Math.min(min, Math.min(...valid))
      max = Math.max(max, Math.max(...valid))
    }
    return {
      x: column,
      y: row,
      meanUtilization: valid.length === 0 ? null : mean(valid),
      bestUtilization: valid.length === 0 ? null : Math.max(...valid),
      count: bucket.length,
      validCount: valid.length,
    }
  }))

  const empty = !Number.isFinite(min)
  return { xs, ys, cells: grid, min: empty ? 0 : min, max: empty ? 0 : max, empty }
}

/**
 * 收敛曲线对比：每个算法一条线，取该算法在指定实例下的**代表性一格**。
 *
 * <p>多个种子时取评价次数最多的那一格：曲线越往后越长，能展示的收敛过程越完整；
 * 取第一条则可能碰上早早因为达到预算上限而结束的那次，图上会缺一大段。
 *
 * <p>返回的是原始点集，不做抽样也不插值——后端已经按 2000 点上限抽过样了
 * （见 `ExperimentArtifactService.MAX_ANALYSIS_POINTS`），前端再抽一次只会多损失细节。
 */
export interface ConvergenceSeries {
  algorithm: string
  seed: number
  truncated: boolean
  points: SearchPoint[]
}

export function convergenceSeries(cells: AnalysisCell[], instanceId: string): ConvergenceSeries[] {
  const best = new Map<string, AnalysisCell>()
  for (const cell of cells) {
    if (cell.instanceId !== instanceId) continue
    if (cell.convergence.length === 0) continue
    const current = best.get(cell.searchAlgorithm)
    if (!current || cell.convergence.length > current.convergence.length) {
      best.set(cell.searchAlgorithm, cell)
    }
  }
  return [...best]
    .map(([algorithm, cell]) => ({
      algorithm,
      seed: cell.seed,
      truncated: cell.curveTruncated,
      points: cell.convergence,
    }))
    .sort((left, right) => left.algorithm.localeCompare(right.algorithm))
}

/**
 * 按实例列出「哪些算法还没算出结果」。
 *
 * <p>热力图里缺格子是正常的（任务还在跑），但用户需要一眼看出「这是还在跑」还是
 * 「这一格算失败了」，而不是对着一张有空洞的图猜。
 */
export function missingPairs(cells: AnalysisCell[], totalTasks: number): number {
  return Math.max(0, totalTasks - cells.length)
}
