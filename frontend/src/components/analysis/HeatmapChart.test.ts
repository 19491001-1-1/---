import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import HeatmapChart from './HeatmapChart.vue'
import { heatmap } from '@/analysis/experimentAnalysis'
import type { AnalysisCell } from '@/types/api'

const cell = (overrides: Partial<AnalysisCell> = {}): AnalysisCell => ({
  index: 0, instanceId: 'gcut1', searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 1,
  legal: true, utilization: 0.9, packedArea: 100, elapsedMilliseconds: 10, evaluations: 100,
  convergence: [], curveTruncated: false, ...overrides,
})

const mountHeat = (cells: AnalysisCell[]) => mount(HeatmapChart, {
  props: { heatmap: heatmap(cells, 'decoder', 'searchAlgorithm'), xLabel: '解码器', yLabel: '搜索器' },
})

describe('HeatmapChart', () => {
  it('没有结果时给出空状态而不是一张灰图', () => {
    const wrapper = mountHeat([])
    expect(wrapper.text()).toContain('还没有可比较的结果')
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('按行列渲染出格子，值以百分比显示', () => {
    const wrapper = mountHeat([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', utilization: 0.95 }),
      cell({ searchAlgorithm: 'SA', decoder: 'MAXRECTS', utilization: 0.8 }),
    ])

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('95.0')
    expect(wrapper.text()).toContain('80.0')
  })

  it('缺数据的格子显示破折号，且不可被误读成 0%', () => {
    const wrapper = mountHeat([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE' }),
      cell({ searchAlgorithm: 'SA', decoder: 'MAXRECTS' }),
    ])
    // ILS × MAXRECTS 与 SA × SKYLINE 两个组合没有数据，必须留空而不是补 0。
    expect(wrapper.findAll('.heat-cell.empty')).toHaveLength(2)
    expect(wrapper.find('.heat-cell.empty').text()).toContain('—')
  })

  it('只有一个组合时退化成 1×1 网格，不产生不存在的空格', () => {
    const wrapper = mountHeat([cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE' })])
    expect(wrapper.findAll('.heat-cell')).toHaveLength(1)
    expect(wrapper.findAll('.heat-cell.empty')).toHaveLength(0)
  })

  it('有非法结果时打标记，因为均值只算合法结果', () => {
    const wrapper = mountHeat([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 1, utilization: 0.9 }),
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 2, utilization: 0, legal: false }),
    ])

    expect(wrapper.findAll('.heat-cell small')).toHaveLength(1)
    expect(wrapper.find('.heat-cell').attributes('title')).toContain('合法 1/2')
  })

  it('点击格子回传行列名，供其他视图联动', async () => {
    const wrapper = mountHeat([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', utilization: 0.95 }),
      cell({ searchAlgorithm: 'SA', decoder: 'MAXRECTS', utilization: 0.8 }),
    ])

    await wrapper.findAll('.heat-cell')[0].trigger('click')
    // 第一个格子位于 (x=MAXRECTS, y=ILS)。
    expect(wrapper.emitted('pick')).toEqual([['MAXRECTS', 'ILS']])
  })

  it('所有格子取值相同时不因除零而崩', () => {
    const wrapper = mountHeat([
      cell({ searchAlgorithm: 'ILS', decoder: 'SKYLINE', utilization: 0.9 }),
      cell({ searchAlgorithm: 'SA', decoder: 'SKYLINE', utilization: 0.9 }),
    ])
    expect(wrapper.findAll('.heat-cell').length).toBeGreaterThan(0)
  })
})
