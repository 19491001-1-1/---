import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ExperimentTask } from '@/types/api'

/**
 * 记录页是三件事的叠加：筛选、排序、以及「有任务还在跑就轮询」。
 *
 * <p>前两件是纯前端计算，第三件会自己起定时器。这三样各自都不难，凑在一起时最容易出的
 * 是「刷新把用户正在看的筛选/选中悄悄重置」以及「任务跑完了定时器还在转」——
 * 下面逐条钉住。
 */

const task = (overrides: Partial<ExperimentTask> = {}): ExperimentTask => ({
  id: 'task-1', name: '对比实验', status: 'COMPLETED', totalTasks: 8, completedTasks: 8,
  createdAt: '2026-10-14T00:00:00Z', startedAt: null, completedAt: null, error: null,
  progressPercent: 100, bestUtilization: 0.9, averageUtilization: 0.85, legalResults: 8,
  rotationAllowed: true, budgetMode: 'EVALUATIONS', currentTask: null, results: [],
  ...overrides,
} as unknown as ExperimentTask)

/**
 * fetch 替身：列表返回给定任务，别的端点（详情、产物清单）给一份无害的空响应。
 *
 * <p>列表每次返回**新的数组**，与真实 API 一致。若图省事复用同一个数组对象，
 * Vue 会因为引用没变而不重算派生状态，测出来的行为跟线上不一样。
 */
function stubFetch(list: ExperimentTask[] | (() => ExperimentTask[])) {
  const urls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    urls.push(url)
    const payload = url === '/api/experiments'
      ? [...(typeof list === 'function' ? list() : list)]
      : url.includes('/artifacts') ? [] : task()
    return {
      ok: true, status: 200, headers: new Headers(), json: async () => payload,
    } as unknown as Response
  }))
  return urls
}

const { default: RecordsView } = await import('./RecordsView.vue')

const names = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('.records-table button strong').map((node) => node.text())

describe('RecordsView', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

  const mountView = async () => {
    const wrapper = mount(RecordsView)
    await flushPromises()
    return wrapper
  }

  it('关键字按名称与标识筛选，大小写不敏感', async () => {
    stubFetch([
      task({ id: 'task-1', name: 'Skyline 实验' }),
      task({ id: 'TASK-2', name: '蚁群调参' }),
    ])
    const wrapper = await mountView()

    const input = wrapper.find('.records-toolbar input')
    await input.setValue('skyline')
    expect(names(wrapper)).toEqual(['Skyline 实验'])

    // 标识也要能搜到：长名字记不住时用户往往是照着 id 找的。
    await input.setValue('task-2')
    expect(names(wrapper)).toEqual(['蚁群调参'])

    await input.setValue('')
    expect(names(wrapper)).toHaveLength(2)
  })

  it('状态页签按终态与运行中分流，并显示各自计数', async () => {
    stubFetch([
      task({ id: 'task-1', name: '跑完了', status: 'COMPLETED' }),
      task({ id: 'task-2', name: '还在跑', status: 'RUNNING' }),
      task({ id: 'task-3', name: '被取消', status: 'CANCELLED' }),
    ])
    const wrapper = await mountView()

    const tab = (label: string) =>
      wrapper.findAll('.status-tabs button').find((button) => button.text().startsWith(label))!

    expect(tab('全部').text()).toContain('3')
    expect(tab('RUNNING').text()).toContain('1')

    await tab('RUNNING').trigger('click')
    expect(names(wrapper)).toEqual(['还在跑'])

    await tab('CANCELLED').trigger('click')
    expect(names(wrapper)).toEqual(['被取消'])
  })

  it('排序可切换升降序，且同值任务顺序稳定不抖动', async () => {
    stubFetch([
      task({ id: 'task-b', name: 'B', bestUtilization: 0.8 }),
      task({ id: 'task-a', name: 'A', bestUtilization: 0.8 }),
      task({ id: 'task-c', name: 'C', bestUtilization: 0.95 }),
    ])
    const wrapper = await mountView()

    const sortBy = (label: string) =>
      wrapper.findAll('.sort-controls button').find((button) => button.text().startsWith(label))!

    await sortBy('最佳利用率').trigger('click')
    // 默认降序：C(0.95) 在前；A 与 B 同为 0.8，靠 id 兜底排成稳定顺序。
    expect(names(wrapper)).toEqual(['C', 'A', 'B'])

    await sortBy('最佳利用率').trigger('click')
    // 再点一次变升序，但同值的相对顺序仍由 id 决定，不会翻过来。
    expect(names(wrapper)).toEqual(['A', 'B', 'C'])
  })

  it('没有符合筛选条件的记录时给出空状态而不是空白列表', async () => {
    stubFetch([task({ name: '唯一一条' })])
    const wrapper = await mountView()

    await wrapper.find('.records-toolbar input').setValue('不存在')
    expect(wrapper.text()).toContain('没有符合条件的记录')
  })

  it('有任务在跑时自动轮询，全部结束后停止', async () => {
    const running = [task({ id: 'task-1', status: 'RUNNING', completedTasks: 1 })]
    const urls = stubFetch(() => running)
    const wrapper = await mountView()
    const listCalls = () => urls.filter((url) => url === '/api/experiments').length
    const before = listCalls()

    // 到点自动再拉一次列表。
    await vi.advanceTimersByTimeAsync(3_000)
    expect(listCalls()).toBeGreaterThan(before)

    // 任务跑完：下一轮刷新拿到终态后，定时器必须自己停掉，不能一直空转。
    running[0] = task({ id: 'task-1', status: 'COMPLETED' })
    await vi.advanceTimersByTimeAsync(3_000)
    const afterTerminal = listCalls()
    await vi.advanceTimersByTimeAsync(9_000)
    expect(listCalls()).toBe(afterTerminal)
    wrapper.unmount()
  })

  it('任务全部结束时只拉一次，不起定时器', async () => {
    const urls = stubFetch([task({ status: 'COMPLETED' })])
    const wrapper = await mountView()

    expect(urls.filter((url) => url === '/api/experiments')).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(9_000)
    expect(urls.filter((url) => url === '/api/experiments')).toHaveLength(1)
    wrapper.unmount()
  })

  it('选中的记录被保留策略回收后自动清空详情，不留一份磁盘上不存在的旧结果', async () => {
    const list = [task({ id: 'task-1', name: '会被回收' })]
    stubFetch(() => list)
    const wrapper = await mountView()

    await wrapper.findAll('.records-table button')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('.record-detail h3').exists()).toBe(true)

    // 后台把这条记录删了：下次刷新时它从列表里消失，详情必须跟着清空。
    list.length = 0
    await wrapper.find('.sort-controls .refresh').trigger('click')
    await flushPromises()

    expect(wrapper.find('.record-detail h3').exists()).toBe(false)
    wrapper.unmount()
  })

  it('卸载时清掉定时器，不让它在后台继续发请求', async () => {
    const urls = stubFetch([task({ status: 'RUNNING' })])
    const wrapper = await mountView()

    wrapper.unmount()
    const afterUnmount = urls.length
    await vi.advanceTimersByTimeAsync(9_000)
    expect(urls.length).toBe(afterUnmount)
  })
})
