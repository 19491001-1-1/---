import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConvergenceSeries } from '@/analysis/experimentAnalysis'

/**
 * 与单次求解页的 ConvergenceChart 同款做法：echarts 真实渲染依赖容器尺寸，
 * jsdom 里既慢又脆，因此换成替身，只盯住「传进 setOption 的是什么」。
 */
const chart = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }
const init = vi.fn((..._args: unknown[]) => chart)

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: (element: unknown, theme: unknown, options: unknown) => init(element, theme, options),
}))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({ GridComponent: {}, TooltipComponent: {}, LegendComponent: {} }))
vi.mock('echarts/renderers', () => ({ SVGRenderer: {} }))

const { default: ConvergenceCompareChart } = await import('./ConvergenceCompareChart.vue')

const series = (algorithm: string, points: [evaluations: number, area: number][]): ConvergenceSeries => ({
  algorithm,
  seed: 1,
  truncated: false,
  points: points.map(([evaluations, bestPackedArea], index) => ({
    step: index + 1, evaluations, bestPackedArea,
  })),
})

interface Option {
  series: { name: string; type: string; step: string; data: number[][]; lineStyle: { color: string } }[]
}

const optionOf = (call: number) => chart.setOption.mock.calls[call][0] as Option

describe('ConvergenceCompareChart', () => {
  beforeEach(() => { vi.clearAllMocks() })

  const mountChart = (items: ConvergenceSeries[], binArea = 10_000) =>
    mount(ConvergenceCompareChart, { props: { series: items, binArea } })

  it('没有轨迹时给出空状态而不是一张空图', () => {
    const wrapper = mountChart([])
    expect(wrapper.text()).toContain('还没有任何收敛轨迹')
    expect(init).not.toHaveBeenCalled()
  })

  it('把装入面积按板材面积换算成百分比，多条曲线共用一根纵轴', () => {
    mountChart([series('ILS', [[2_000, 2_000], [2_500, 2_500]]),
      series('SA', [[1_000, 1_000], [1_500, 1_500]])])

    const option = optionOf(0)
    expect(option.series.map((item) => item.name)).toEqual(['ILS', 'SA'])
    // 面积要换算成利用率，否则「板材大小不同」的实例之间没法比。
    expect(option.series[0].data).toEqual([[2_000, 20], [2_500, 25]])
    expect(option.series[1].data).toEqual([[1_000, 10], [1_500, 15]])
  })

  it('用阶梯线而不是折线：最优值在两次改进之间不上升', () => {
    mountChart([series('ILS', [[2_000, 2_000], [2_500, 2_500]])])

    expect(optionOf(0).series[0].type).toBe('line')
    expect(optionOf(0).series[0].step).toBe('end')
  })

  it('板材面积缺失时退回原始面积，曲线形状仍然正确', () => {
    mountChart([series('ILS', [[2_000, 2_000]])], 0)
    expect(optionOf(0).series[0].data).toEqual([[2_000, 2_000]])
  })

  it('颜色由算法名固定决定，系列增减时其余曲线不改色', () => {
    mountChart([series('ILS', [[2_000, 2_000]]), series('SA', [[1_000, 1_000]])])
    const colorOfIls = optionOf(0).series.find((item) => item.name === 'ILS')!.lineStyle.color

    // 把 SA 从系列里去掉——ILS 的颜色必须原地不动，否则读者得重新认一遍哪条是哪条。
    mountChart([series('ILS', [[2_000, 2_000]])])
    expect(optionOf(1).series[0].lineStyle.color).toBe(colorOfIls)
  })

  it('数据或板材面积变化时复用同一实例重绘', async () => {
    const wrapper = mountChart([series('ILS', [[2_000, 2_000]])])
    await wrapper.setProps({ binArea: 20_000 })
    await wrapper.setProps({ series: [series('ILS', [[2_000, 2_000]]), series('SA', [[1_000, 1_000]])] })

    expect(init).toHaveBeenCalledTimes(1)
    expect(optionOf(2).series).toHaveLength(2)
    // 面积换了以后纵轴口径也跟着换：20% → 10%。
    expect(optionOf(2).series[0].data).toEqual([[2_000, 10]])
  })

  it('有被抽样的曲线时必须明说，别让人以为算法只迭代了图上那么多次', () => {
    const plain = mountChart([series('ILS', [[2_000, 2_000]])])
    expect(plain.text()).not.toContain('抽样')

    const sampled = mountChart([{ ...series('ILS', [[2_000, 2_000]]), truncated: true }])
    expect(sampled.text()).toContain('抽样')
  })

  it('卸载时销毁实例，防止内存泄漏', () => {
    const wrapper = mountChart([series('ILS', [[2_000, 2_000]])])
    wrapper.unmount()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
