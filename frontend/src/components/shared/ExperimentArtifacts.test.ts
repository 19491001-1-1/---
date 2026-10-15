import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExperimentArtifacts from './ExperimentArtifacts.vue'
import type { ExperimentArtifact } from '@/types/api'

const manifest: ExperimentArtifact[] = [
  { name: 'runs.csv', sizeBytes: 2048, contentType: 'text/csv' },
  // 带子目录的名字：下载 URL 必须逐段编码而不是把 `/` 编成 %2F。
  { name: 'convergence/gcut1.svg', sizeBytes: 5_242_880, contentType: 'image/svg+xml' },
]

/** 记录每一次 fetch 的替身；顺带把 Blob/URL 这两个 jsdom 里不存在的东西补上。 */
function stubFetch(response: Partial<Response> & { json?: () => unknown; blob?: () => unknown }) {
  const calls: { url: string; init?: RequestInit }[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    return {
      ok: true, status: 200, headers: new Headers(),
      json: async () => manifest, blob: async () => new Blob(['x']),
      ...response,
    } as unknown as Response
  }))
  return calls
}

describe('ExperimentArtifacts', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = vi.fn(() => 'blob:mock')
      static revokeObjectURL = vi.fn()
    })
  })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

  const mountPanel = (props: Partial<InstanceType<typeof ExperimentArtifacts>['$props']> = {}) =>
    mount(ExperimentArtifacts, {
      props: { taskId: 'task-1', terminal: true, hasResults: true, ...props },
    })

  it('挂载时只拉清单，不触发生成', async () => {
    const calls = stubFetch({})
    const wrapper = mountPanel()
    await flushPromises()

    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/experiments/task-1/artifacts')
    // 生成写磁盘，必须由用户显式点击；GET 保持无副作用。
    expect(calls[0].init?.method).toBeUndefined()
    expect(wrapper.text()).toContain('runs.csv')
  })

  it('没有任务时不发任何请求并给出提示', async () => {
    const calls = stubFetch({})
    const wrapper = mountPanel({ taskId: null, hasResults: false })
    await flushPromises()

    expect(calls).toHaveLength(0)
    expect(wrapper.text()).toContain('没有产出任何结果')
  })

  it('任务未结束时禁用生成，并用 POST 触发生成', async () => {
    const calls = stubFetch({})
    const running = mountPanel({ terminal: false })
    await flushPromises()
    expect(running.findAll('.artifact-actions button')[0].attributes('disabled')).toBeDefined()

    const finished = mountPanel({ terminal: true })
    await flushPromises()
    await finished.findAll('.artifact-actions button')[0].trigger('click')
    await flushPromises()

    const generated = calls.filter((call) => call.init?.method === 'POST')
    expect(generated).toHaveLength(1)
    expect(generated[0].url).toBe('/api/experiments/task-1/artifacts')
  })

  it('下载带子目录的产物时逐段编码，并回收对象 URL', async () => {
    const calls = stubFetch({})
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.findAll('.artifact-list button')[1].trigger('click')
    await flushPromises()

    const download = calls.find((call) => call.url.includes('convergence'))!
    // `/` 必须保留成路径分隔符：编成 %2F 在服务端只是一个普通字符，拼不出原始路径。
    expect(download.url).toBe('/api/experiments/task-1/artifacts/convergence/gcut1.svg')
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
  })

  it('换任务时清空上一个任务的清单，避免张冠李戴', async () => {
    stubFetch({})
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('runs.csv')

    await wrapper.setProps({ taskId: 'task-2' })
    await flushPromises()
    expect(wrapper.text()).toContain('runs.csv')

    await wrapper.setProps({ taskId: null })
    expect(wrapper.text()).not.toContain('runs.csv')
  })

  it('后端报错时展示错误文案而不是空清单', async () => {
    stubFetch({ ok: false, status: 500, json: async () => ({ code: 'BOOM', message: '产物目录不可写' }) })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('产物目录不可写')
  })

  it('按数量级展示文件大小', async () => {
    stubFetch({})
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('5.00 MB')
  })
})
