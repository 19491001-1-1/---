import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PackingCanvas from './PackingCanvas.vue'
import type { Placement } from '@/types/api'

const placement: Placement = {
  itemId: 7,
  originalWidth: 30,
  originalHeight: 20,
  x: 4,
  y: 6,
  placedWidth: 20,
  placedHeight: 30,
  rotated: true,
  area: 600,
}

describe('PackingCanvas', () => {
  it('无结果时展示明确空状态', () => {
    const wrapper = mount(PackingCanvas, {
      props: { placements: [], binWidth: 100, binHeight: 100, usedHeight: 0, legal: true, selectedId: null },
    })

    expect(wrapper.text()).toContain('布局等待求解')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('点击矩形时发送物品编号', async () => {
    const wrapper = mount(PackingCanvas, {
      props: { placements: [placement], binWidth: 100, binHeight: 100, usedHeight: 36, legal: true, selectedId: null },
    })

    await wrapper.find('.packing-item').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[7]])
    expect(wrapper.get('.packing-item').attributes('aria-label')).toContain('已旋转')
  })
})
