import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useCatalogStore } from './catalog'
import { ApiClientError } from '@/api/client'
import type { AdvancedAlgorithm, Meta, PackingDecoder, PackingInstanceSummary, SearchAlgorithm } from '@/types/api'

const meta: Meta = {
  projectName: '二维排样', version: '1.0.0', javaVersion: '17', productSearchAlgorithms: 6,
  productDecoders: 8, productCombinations: 48, implementedAlgorithmEntries: 46,
  searchDisplayWeightPercent: 60, packingDisplayWeightPercent: 40, capabilities: [],
}
const algorithms: SearchAlgorithm[] = [{
  machineValue: 'ILS', chineseName: '迭代局部搜索', englishName: 'ILS',
  description: '', guide: '详细说明', stochastic: true, supportedBudgetModes: ['EVALUATIONS'],
}]
const decoders: PackingDecoder[] = [{
  machineValue: 'SKYLINE', chineseName: '天际线', englishName: 'Skyline',
  description: '', guide: '详细说明', rotationSupported: true, spaceStructure: 'skyline',
}]
const advancedAlgorithms: AdvancedAlgorithm[] = [{
  machineValue: 'SHELF', displayName: 'Shelf-NF', family: 'Shelf',
  representative: 'SHELF_BAF_GLOBAL', representativeName: 'Shelf-BAF-GLOBAL',
  idea: '按输入顺序执行 Next-Fit 层架构造', stochastic: false,
}]
const instances: PackingInstanceSummary[] = [{
  id: 'gcut1', name: 'gcut1', itemCount: 10, binWidth: 250, binHeight: 250,
  totalItemArea: 100, source: 'builtin',
}]

/** 用一个可编程的 fetch 替身驱动四个并行请求，按 URL 分派返回值。 */
function stubCatalogFetch(overrides: Record<string, Partial<Response>> = {}) {
  const calls: string[] = []
  const payloads: Record<string, unknown> = {
    '/api/meta': meta,
    '/api/algorithms/search': algorithms,
    '/api/algorithms/decoders': decoders,
    '/api/algorithms/advanced': advancedAlgorithms,
    '/api/instances': instances,
  }
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    calls.push(url)
    const override = overrides[url]
    if (override) return { ok: true, status: 200, headers: new Headers(), ...override } as unknown as Response
    return {
      ok: true, status: 200, headers: new Headers(),
      json: async () => payloads[url],
    } as unknown as Response
  }))
  return calls
}

describe('catalog store', () => {
  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => vi.unstubAllGlobals())

  it('一次加载把五份目录数据都装进来', async () => {
    stubCatalogFetch()
    const store = useCatalogStore()

    await store.load()

    expect(store.meta?.version).toBe('1.0.0')
    expect(store.searchAlgorithms).toHaveLength(1)
    expect(store.decoders).toHaveLength(1)
    expect(store.advancedAlgorithms).toHaveLength(1)
    expect(store.instances).toHaveLength(1)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('已加载过就不再重复请求，force 才会重新拉', async () => {
    const calls = stubCatalogFetch()
    const store = useCatalogStore()

    await store.load()
    expect(calls).toHaveLength(5)

    await store.load()
    expect(calls, '目录是进程级静态数据，重复进入页面不该再打后端').toHaveLength(5)

    await store.load(true)
    expect(calls).toHaveLength(10)
  })

  it('后端返回错误时记录文案，并允许下次重试', async () => {
    stubCatalogFetch({ '/api/meta': { ok: false, status: 500, json: async () => ({ code: 'BOOM', message: '目录加载失败' }) } })
    const store = useCatalogStore()

    await store.load()

    expect(store.error).toBe('目录加载失败')
    // loaded 必须保持 false：否则页面会认为目录已经就绪，再也不会重试。
    expect(store.loaded).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('非 API 异常时给出兜底文案而不是 undefined', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('network down') }))
    const store = useCatalogStore()

    await store.load()

    expect(store.error).toContain('Spring Boot')
    expect(store.loaded).toBe(false)
  })

  it('非 ApiClientError 的异常走未知错误兜底文案', async () => {
    // fetch 本身不抛（ApiClientError 在 client 里被包了一层），这里直接让 json() 炸，
    // 走的才是 store 里那条 `instanceof ApiClientError` 为假的兜底分支。
    stubCatalogFetch({ '/api/meta': { json: async () => { throw new Error('boom') } } })
    const store = useCatalogStore()

    await store.load()

    expect(store.error).toBe('加载平台数据时发生未知错误')
    expect(ApiClientError).toBeDefined()
  })
})
