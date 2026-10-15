import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalysisCell, ExperimentAnalysis, ExperimentTask } from '@/types/api'

/**
 * 分析页是四张图与一份逐格结果的粘合层，本身没什么算法，但有一件事必须钉死：
 * 它**只为被选中的那一个任务**请求分析数据，并且把同一个响应同时喂给热力图、排名、
 * 箱线图与收敛对比。任何一处接错，表现都是「图上有东西但不对」，而不是报错。
 */

const task = (overrides: Partial<ExperimentTask> = {}): ExperimentTask => ({
  id: 'task-1', name: '对比实验', status: 'COMPLETED', totalTasks: 2, completedTasks: 2,
  createdAt: '2026-10-14T00:00:00Z', startedAt: null, completedAt: null, error: null,
  progressPercent: 100, bestUtilization: 0.9, averageUtilization: 0.85, legalResults: 2,
  rotationAllowed: true, budgetMode: 'EVALUATIONS', currentTask: null, results: [],
  ...overrides,
} as unknown as ExperimentTask)

const cell = (overrides: Partial<AnalysisCell> = {}): AnalysisCell => ({
  index: 0, instanceId: 'gcut1', searchAlgorithm: 'ILS', decoder: 'SKYLINE', seed: 1,
  legal: true, utilization: 0.9, packedArea: 100, elapsedMilliseconds: 10, evaluations: 100,
  convergence: [{ step: 1, evaluations: 100, bestPackedArea: 7_000 }], curveTruncated: false,
  ...overrides,
})

const analysis = (overrides: Partial<ExperimentAnalysis> = {}): ExperimentAnalysis => ({
  id: 'task-1', name: '对比实验', status: 'COMPLETED', totalTasks: 2, completedTasks: 2,
  rotationAllowed: true, budgetMode: 'EVALUATIONS', results: [],
  cells: [
    cell({ index: 0, searchAlgorithm: 'ILS', utilization: 0.95 }),
    cell({ index: 1, searchAlgorithm: 'SA', decoder: 'MAXRECTS', utilization: 0.8 }),
  ],
  instances: {
    gcut1: { id: 'gcut1', name: 'gcut1', binWidth: 250, binHeight: 100, itemCount: 10 },
    gcut5: { id: 'gcut5', name: 'gcut5', binWidth: 200, binHeight: 150, itemCount: 20 },
  },
  ...overrides,
})

/** 记录请求 URL 的 fetch 替身；分析页只需 GET，因此不关心 method。 */
function stubFetch(tasks: ExperimentTask[], detail: ExperimentAnalysis | (() => ExperimentAnalysis)) {
  const urls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    urls.push(url)
    const payload = url.endsWith('/analysis')
      ? (typeof detail === 'function' ? detail() : detail)
      : tasks
    return {
      ok: true, status: 200, headers: new Headers(), json: async () => payload,
    } as unknown as Response
  }))
  return urls
}

const { default: AnalysisView } = await import('./AnalysisView.vue')

describe('AnalysisView', () => {
  beforeEach(() => { vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} }) })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

  it('默认选中最近的终态任务并只拉它的分析数据', async () => {
    const urls = stubFetch([task({ id: 'task-1' })], analysis())
    mount(AnalysisView)
    await flushPromises()

    expect(urls).toContain('/api/experiments/task-1/analysis')
    // 任务列表本身也不该被重复拉：一次挂载一次列表请求。
    expect(urls.filter((url) => url === '/api/experiments')).toHaveLength(1)
  })

  it('没有可选任务时给出空状态，不发分析请求', async () => {
    const urls = stubFetch([], analysis())
    const wrapper = mount(AnalysisView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可分析的实验')
    expect(urls.some((url) => url.endsWith('/analysis'))).toBe(false)
  })

  it('四张图共用同一份结果：热力图、排名、箱线图与收敛对比同时出现', async () => {
    stubFetch([task()], analysis())
    const wrapper = mount(AnalysisView)
    await flushPromises()

    // 排名表按利用率降序：ILS 0.95 在 SA 0.80 之前。
    const names = wrapper.findAll('.rank-table tbody tr td.name').map((node) => node.text())
    expect(names).toEqual(['ILS', 'SA'])
    // 热力图给出 2×2 网格（ILS/SA × MAXRECTS/SKYLINE）。
    expect(wrapper.findAll('.heat-cell')).toHaveLength(4)
    // 箱线图每个算法一行，收敛对比拿到当前实例的曲线。
    expect(wrapper.findAll('.boxplot-row')).toHaveLength(2)
    expect(wrapper.find('.convergence-canvas').exists()).toBe(true)
  })

  it('切换实验会按新 id 重新请求，而不是复用上一份数据', async () => {
    const urls = stubFetch(
      [task({ id: 'task-1' }), task({ id: 'task-2', name: '第二个实验' })],
      analysis(),
    )
    const wrapper = mount(AnalysisView)
    await flushPromises()

    await wrapper.find('.analysis-toolbar select').setValue('task-2')
    await flushPromises()

    expect(urls).toContain('/api/experiments/task-2/analysis')
  })

  it('横纵轴不会落到同一维度，冲突选择会交换两轴', async () => {
    stubFetch([task()], analysis())
    const wrapper = mount(AnalysisView)
    await flushPromises()

    const selects = wrapper.findAll('.analysis-toolbar select')
    // 初始为纵轴解码器、横轴搜索器；把横轴选成解码器时，纵轴应换成原横轴搜索器。
    await selects[2].setValue('decoder')

    expect((selects[1].element as HTMLSelectElement).value).toBe('searchAlgorithm')
    expect((selects[2].element as HTMLSelectElement).value).toBe('decoder')
  })

  it('任务未跑完时如实标出缺格，而不是假装数据完整', async () => {
    stubFetch([task({ completedTasks: 1, totalTasks: 4 })],
      analysis({ completedTasks: 1, totalTasks: 4 }))
    const wrapper = mount(AnalysisView)
    await flushPromises()

    // 2 格已到、4 格总数 → 提示还差 2 个。
    expect(wrapper.text()).toContain('还有 2 个没有结果')
  })

  it('后端报错时展示错误面板并提供重试', async () => {
    stubFetch([task()], () => { throw new Error('boom') })
    const wrapper = mount(AnalysisView)
    await flushPromises()

    expect(wrapper.text()).toContain('分析数据加载失败')
  })

  it('非终态实验自动补齐分析数据，进入终态后停止轮询', async () => {
    vi.useFakeTimers()
    let current = analysis({ status: 'RUNNING', completedTasks: 1, totalTasks: 2 })
    const urls = stubFetch([task({ status: 'RUNNING', completedTasks: 1 })], () => current)
    const wrapper = mount(AnalysisView)
    await flushPromises()
    const analysisCalls = () => urls.filter((url) => url.endsWith('/analysis')).length
    expect(analysisCalls()).toBe(1)

    await vi.advanceTimersByTimeAsync(3_000)
    expect(analysisCalls()).toBe(2)

    current = analysis({ status: 'COMPLETED', completedTasks: 2, totalTasks: 2 })
    await vi.advanceTimersByTimeAsync(3_000)
    expect(analysisCalls()).toBe(3)
    await vi.advanceTimersByTimeAsync(9_000)
    expect(analysisCalls()).toBe(3)

    wrapper.unmount()
    vi.useRealTimers()
  })
})
