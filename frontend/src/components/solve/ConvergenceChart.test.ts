import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SearchPoint } from '@/types/api'

/**
 * echarts 真实渲染依赖 canvas/SVG 与容器尺寸，在 jsdom 里既慢又脆。
 * 这里把它换成替身，只盯住「传进 setOption 的是什么」——那才是这个组件的全部逻辑。
 */
const chart = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }
const init = vi.fn((..._args: unknown[]) => chart)

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: (element: unknown, theme: unknown, options: unknown) => init(element, theme, options),
}))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({ GridComponent: {}, TooltipComponent: {} }))
vi.mock('echarts/renderers', () => ({ SVGRenderer: {} }))

const { default: ConvergenceChart } = await import('./ConvergenceChart.vue')

const points = (count: number): SearchPoint[] =>
  Array.from({ length: count }, (_, index) => ({
    step: index + 1,
    evaluations: (index + 1) * 100,
    bestPackedArea: 5_000 - index * 10,
  }))

describe('ConvergenceChart', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时按评价次数与历史最优面积画阶梯线', async () => {
    const wrapper = mount(ConvergenceChart, { props: { points: points(5) } })

    expect(init).toHaveBeenCalledTimes(1)
    const option = chart.setOption.mock.calls[0][0] as {
      series: { type: string; step: string; data: number[][] }[]
    }
    expect(option.series[0].type).toBe('line')
    // 收敛曲线是「保持最优直到被超越」，用阶梯而不是折线才是对的。
    expect(option.series[0].step).toBe('end')
    expect(option.series[0].data).toEqual([[100, 5_000], [200, 4_990], [300, 4_980], [400, 4_970], [500, 4_960]])
    expect(wrapper.attributes('aria-label')).toContain('收敛曲线')
  })

  it('评价点很多时隐藏数据点标记，避免连成一条糊带', () => {
    mount(ConvergenceChart, { props: { points: points(20) } })

    const dense = chart.setOption.mock.calls[0][0] as { series: { showSymbol: boolean }[] }
    expect(dense.series[0].showSymbol).toBe(false)
  })

  it('数据变化时复用同一个实例重新绘制，而不是反复 init', async () => {
    const wrapper = mount(ConvergenceChart, { props: { points: points(3) } })
    await wrapper.setProps({ points: points(6) })

    expect(init).toHaveBeenCalledTimes(1)
    expect(chart.setOption).toHaveBeenCalledTimes(2)
    const latest = chart.setOption.mock.calls[1][0] as { series: { data: number[][] }[] }
    expect(latest.series[0].data).toHaveLength(6)
  })

  it('卸载时断开尺寸监听并销毁实例，防止内存泄漏', () => {
    const wrapper = mount(ConvergenceChart, { props: { points: points(2) } })
    wrapper.unmount()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
