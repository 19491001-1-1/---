import { describe, expect, it } from 'vitest'
import { boxplot, convergenceSeries, heatmap, quantile, summarizeByAlgorithm } from './experimentAnalysis'
import type { AnalysisCell } from '@/types/api'

/** 造一个分析格子；只写关心的字段，其余给个无害默认值。 */
const cell = (overrides: Partial<AnalysisCell> = {}): AnalysisCell => ({
  index: 0, instanceId: 'gcut1', searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 1,
  legal: true, utilization: 0.9, packedArea: 100, elapsedMilliseconds: 10, evaluations: 100,
  convergence: [], curveTruncated: false, ...overrides,
})

describe('quantile', () => {
  it('用线性插值，与 numpy 默认口径一致', () => {
    expect(quantile([1, 2, 3, 4], 0.5)).toBeCloseTo(2.5)
    expect(quantile([1, 2, 3, 4], 0.25)).toBeCloseTo(1.75)
    expect(quantile([1, 2, 3, 4], 0)).toBe(1)
    expect(quantile([1, 2, 3, 4], 1)).toBe(4)
  })

  it('单点与空集不除零', () => {
    expect(quantile([5], 0.75)).toBe(5)
    expect(quantile([], 0.5)).toBe(0)
  })
})

describe('summarizeByAlgorithm', () => {
  it('非法结果不参与均值，但计入总数', () => {
    const summaries = summarizeByAlgorithm([
      cell({ searchAlgorithm: 'ILS', utilization: 0.9 }),
      cell({ searchAlgorithm: 'ILS', utilization: 0.8 }),
      // 非法格的利用率往往是残缺值，混进均值会把「偶尔失败」算成「平均很差」。
      cell({ searchAlgorithm: 'ILS', utilization: 0, legal: false }),
    ])

    expect(summaries).toHaveLength(1)
    expect(summaries[0].runs).toBe(3)
    expect(summaries[0].validRuns).toBe(2)
    expect(summaries[0].meanUtilization).toBeCloseTo(0.85)
    expect(summaries[0].worstUtilization).toBeCloseTo(0.8)
  })

  it('名次按同实例均值算，与核心报告口径一致（并列同名次、之后跳号）', () => {
    // gcut1 上 ILS 0.95 > SA 0.90 > GA 0.80，名次应为 1 / 2 / 3。
    const summaries = summarizeByAlgorithm([
      cell({ instanceId: 'gcut1', searchAlgorithm: 'ILS', utilization: 0.95 }),
      cell({ instanceId: 'gcut1', searchAlgorithm: 'SA', utilization: 0.9 }),
      cell({ instanceId: 'gcut1', searchAlgorithm: 'GA', utilization: 0.8 }),
    ])
    const rankOf = (algorithm: string) =>
      summaries.find((summary) => summary.algorithm === algorithm)!.meanRank

    expect(rankOf('ILS')).toBe(1)
    expect(rankOf('SA')).toBe(2)
    expect(rankOf('GA')).toBe(3)
    expect(summaries.map((summary) => summary.algorithm)).toEqual(['ILS', 'SA', 'GA'])
  })

  it('并列时同名次，且后续名次跳号——与「1 + 严格更优数」的定义一致', () => {
    const summaries = summarizeByAlgorithm([
      cell({ searchAlgorithm: 'A', utilization: 0.9 }),
      cell({ searchAlgorithm: 'B', utilization: 0.9 }),
      cell({ searchAlgorithm: 'C', utilization: 0.5 }),
    ])
    const rankOf = (algorithm: string) =>
      summaries.find((summary) => summary.algorithm === algorithm)!.meanRank

    expect(rankOf('A')).toBe(1)
    expect(rankOf('B')).toBe(1)
    // 两个人并列第一，第三名的名次是 3 而不是 2（体育排名口径）。
    expect(rankOf('C')).toBe(3)
  })

  it('多个种子先聚成同实例均值再排名，不会因为多跑一次就把名次算重', () => {
    // ILS 在 gcut1 上跑了两次（0.9、0.8，均值 0.85），SA 一次 0.9（均值 0.9）。
    // 若按逐格排名，ILS 会因为那条 0.9 与 SA 并列第一而拉高平均名次。
    const summaries = summarizeByAlgorithm([
      cell({ searchAlgorithm: 'ILS', seed: 1, utilization: 0.9 }),
      cell({ searchAlgorithm: 'ILS', seed: 2, utilization: 0.8 }),
      cell({ searchAlgorithm: 'SA', seed: 1, utilization: 0.9 }),
    ])
    const rankOf = (algorithm: string) =>
      summaries.find((summary) => summary.algorithm === algorithm)!.meanRank

    expect(rankOf('SA')).toBe(1)
    expect(rankOf('ILS')).toBe(2)
  })

  it('全部非法的算法不报错，均值归零且不参与名次', () => {
    const summaries = summarizeByAlgorithm([
      cell({ searchAlgorithm: 'BROKEN', utilization: 0, legal: false }),
      cell({ searchAlgorithm: 'ILS', utilization: 0.9 }),
    ])

    expect(summaries.find((s) => s.algorithm === 'BROKEN')!.meanUtilization).toBe(0)
    expect(summaries.find((s) => s.algorithm === 'ILS')!.meanRank).toBe(1)
  })

  it('空输入返回空列表', () => {
    expect(summarizeByAlgorithm([])).toEqual([])
  })
})

describe('boxplot', () => {
  it('五数与均值', () => {
    const stats = boxplot([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(stats.median).toBeCloseTo(5.5)
    expect(stats.q1).toBeCloseTo(3.25)
    expect(stats.q3).toBeCloseTo(7.75)
    expect(stats.mean).toBeCloseTo(5.5)
    expect(stats.count).toBe(10)
  })

  it('须取落界内的实际数据点，离群值单独列出', () => {
    // Q1=2, Q3=4, IQR=2 → 上界 7；100 远超上界，应进离群值。
    const stats = boxplot([1, 2, 3, 4, 5, 100])
    expect(stats.max).toBeLessThan(100)
    expect(stats.outliers).toEqual([100])
  })

  it('空输入给出一组零而不是 NaN', () => {
    expect(boxplot([])).toEqual({
      min: 0, q1: 0, median: 0, q3: 0, max: 0, mean: 0, count: 0, outliers: [],
    })
  })

  it('全部相同时不把正常点误判成离群（IQR 为 0）', () => {
    const stats = boxplot([0.9, 0.9, 0.9])
    expect(stats.outliers).toEqual([])
    expect(stats.min).toBeCloseTo(0.9)
    expect(stats.max).toBeCloseTo(0.9)
  })
})

describe('heatmap', () => {
  const matrix = [
    cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', utilization: 0.95 }),
    cell({ searchAlgorithm: 'ILS', decoder: 'MAXRECTS', utilization: 0.9 }),
    cell({ searchAlgorithm: 'SA', decoder: 'SKYLINE', utilization: 0.8 }),
  ]

  it('按两个维度交叉，行列都是排序后的全集', () => {
    const grid = heatmap(matrix, 'decoder', 'searchAlgorithm')
    expect(grid.xs).toEqual(['MAXRECTS', 'SKYLINE'])
    expect(grid.ys).toEqual(['ILS', 'SA'])
    expect(grid.cells[0][1].meanUtilization).toBeCloseTo(0.95)
    expect(grid.empty).toBe(false)
  })

  it('缺组合保留为空格子而不是从表里删掉，否则整张图会错位', () => {
    const grid = heatmap(matrix, 'decoder', 'searchAlgorithm')
    // SA × MAXRECTS 没有数据。
    expect(grid.cells[1][0].count).toBe(0)
    expect(grid.cells[1][0].meanUtilization).toBeNull()
  })

  it('同一格内多格取均值，并记录非法数', () => {
    const grid = heatmap([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 1, utilization: 0.9 }),
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 2, utilization: 0.8 }),
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 3, utilization: 0, legal: false }),
    ], 'decoder', 'searchAlgorithm')

    expect(grid.cells[0][0].count).toBe(3)
    expect(grid.cells[0][0].validCount).toBe(2)
    expect(grid.cells[0][0].meanUtilization).toBeCloseTo(0.85)
  })

  it('全无合法结果时标记为空表，色阶不除零', () => {
    const grid = heatmap([cell({ legal: false, utilization: 0 })], 'decoder', 'searchAlgorithm')
    expect(grid.empty).toBe(true)
    expect(grid.min).toBe(0)
    expect(grid.max).toBe(0)
  })

  it('空输入不炸', () => {
    expect(heatmap([], 'decoder', 'searchAlgorithm').xs).toEqual([])
  })
})

describe('convergenceSeries', () => {
  const point = (evaluations: number, area: number) => ({ step: evaluations, evaluations, bestPackedArea: area })

  it('同一算法多个种子时取轨迹最长的一次，避免图缺一段', () => {
    const series = convergenceSeries([
      cell({ searchAlgorithm: 'ILS', seed: 1, convergence: [point(10, 1)] }),
      cell({ searchAlgorithm: 'ILS', seed: 2, convergence: [point(10, 1), point(20, 2), point(30, 3)] }),
    ], 'gcut1')

    expect(series).toHaveLength(1)
    expect(series[0].seed).toBe(2)
    expect(series[0].points).toHaveLength(3)
  })

  it('只取指定实例，且没有轨迹的格子不产生空系列', () => {
    const series = convergenceSeries([
      cell({ instanceId: 'gcut1', searchAlgorithm: 'ILS', convergence: [point(10, 1)] }),
      cell({ instanceId: 'gcut2', searchAlgorithm: 'SA', convergence: [point(10, 1)] }),
      cell({ instanceId: 'gcut1', searchAlgorithm: 'SA', convergence: [] }),
    ], 'gcut1')

    expect(series.map((item) => item.algorithm)).toEqual(['ILS'])
  })

  it('如实带出抽样标记', () => {
    const series = convergenceSeries([
      cell({ searchAlgorithm: 'ILS', convergence: [point(10, 1)], curveTruncated: true }),
    ], 'gcut1')

    expect(series[0].truncated).toBe(true)
  })
})
