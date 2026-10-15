import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BoxplotChart from './BoxplotChart.vue'
import { boxplot } from '@/analysis/experimentAnalysis'

const series = (label: string, values: number[]) => ({ label, stats: boxplot(values) })

describe('BoxplotChart', () => {
  it('没有系列时给出空状态', () => {
    const wrapper = mount(BoxplotChart, { props: { series: [] } })
    expect(wrapper.text()).toContain('还没有可比较的结果')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('每个算法一行，画出箱体、中位线与须', () => {
    const wrapper = mount(BoxplotChart, {
      props: { series: [series('ILS', [0.8, 0.85, 0.9, 0.95]), series('SA', [0.7, 0.75, 0.8])] },
    })

    expect(wrapper.findAll('.boxplot-row')).toHaveLength(2)
    // 每行一个箱体 + 一条中位线 + 四条须（两横两纵）。
    expect(wrapper.findAll('.box')).toHaveLength(2)
    expect(wrapper.findAll('.median')).toHaveLength(2)
    expect(wrapper.findAll('.whisker')).toHaveLength(8)
    expect(wrapper.text()).toContain('ILS')
  })

  it('横轴刻度按所有系列的并集归一，而不是各自归一化', () => {
    // 两行数值范围差很大；各自归一化会让两个箱体看起来一样宽，那是图形在撒谎。
    const wrapper = mount(BoxplotChart, {
      props: { series: [series('窄', [0.90, 0.91]), series('宽', [0.10, 0.90])] },
    })

    const boxes = wrapper.findAll('.box')
    const widthOf = (index: number) => Number(boxes[index].attributes('width'))
    expect(widthOf(0)).toBeLessThan(widthOf(1))
  })

  it('离群值画成独立的点', () => {
    const wrapper = mount(BoxplotChart, {
      props: { series: [series('ILS', [0.8, 0.81, 0.82, 0.83, 0.2])] },
    })
    expect(wrapper.findAll('.outlier').length).toBe(1)
  })

  it('箱体带样本数的提示，便于判断一行是不是只有一个点', () => {
    const wrapper = mount(BoxplotChart, { props: { series: [series('ILS', [0.9])] } })
    expect(wrapper.find('.box title').text()).toContain('样本 1')
  })
})
