import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiClientError, encodeArtifactPath, filenameOf } from './client'

/** 把 fetch 换成一个可编程的替身，并记录每次调用的 URL 与 init。 */
function stubFetch(response: Partial<Response> & { json?: () => unknown; blob?: () => unknown }) {
  const calls: { url: string; init?: RequestInit }[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    return {
      ok: true, status: 200,
      headers: new Headers({ 'Content-Type': 'application/octet-stream' }),
      json: async () => ({}), blob: async () => new Blob(['x']),
      ...response,
    } as unknown as Response
  }))
  return calls
}

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('把后端错误码与字段错误原样带出来', async () => {
    stubFetch({
      ok: false, status: 400,
      json: async () => ({ code: 'VALIDATION_FAILED', message: '预算不合法', fieldErrors: { budgetValid: '必须为正' } }),
    })

    const failure = await api.createExperiment({} as never).catch((reason: unknown) => reason)
    expect(failure).toBeInstanceOf(ApiClientError)
    expect((failure as ApiClientError).code).toBe('VALIDATION_FAILED')
    expect((failure as ApiClientError).fieldErrors.budgetValid).toBe('必须为正')
  })

  it('错误响应体不是 JSON 时退回到通用文案，而不是抛解析异常', async () => {
    stubFetch({ ok: false, status: 502, json: async () => { throw new SyntaxError('not json') } })

    const failure = await api.experiments().catch((reason: unknown) => reason)
    expect((failure as ApiClientError).code).toBe('HTTP_ERROR')
    expect((failure as ApiClientError).message).toContain('502')
  })

  it('连不上后端时给出可操作的提示', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('failed to fetch') }))

    const failure = await api.meta().catch((reason: unknown) => reason)
    expect((failure as ApiClientError).code).toBe('NETWORK_ERROR')
    expect((failure as ApiClientError).message).toContain('Spring Boot')
  })

  it('产物下载走 blob 而不是 json，并取回服务端建议的文件名', async () => {
    const calls = stubFetch({
      blob: async () => new Blob(['csv']),
      headers: new Headers({ 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="runs.csv"' }),
    })

    const download = await api.downloadExperimentArtifact('task-1', 'convergence/gcut1.svg')

    expect(calls[0].url).toBe('/api/experiments/task-1/artifacts/convergence/gcut1.svg')
    // 产物是二进制/文本而不是 JSON，用 response.json() 会把 CSV 直接解析崩。
    expect(calls[0].init?.headers).toMatchObject({ Accept: '*/*' })
    expect(download.filename).toBe('runs.csv')
    expect(download.contentType).toBe('text/csv')
  })

  it('产物名逐段编码，分隔符保持不变', () => {
    expect(encodeArtifactPath('convergence/gcut1.svg')).toBe('convergence/gcut1.svg')
    expect(encodeArtifactPath('a/b c.svg')).toBe('a/b%20c.svg')
    // 没有分隔符时就等价于整串编码。
    expect(encodeArtifactPath('带空格 的名字.csv')).toBe(encodeURIComponent('带空格 的名字.csv'))
  })

  it('文件名解析只取最后一段，避免服务端返回值变成路径拼接', () => {
    expect(filenameOf('attachment; filename="runs.csv"', 'fallback')).toBe('runs.csv')
    expect(filenameOf('attachment; filename=../../etc/passwd', 'fallback')).toBe('passwd')
    expect(filenameOf('attachment', 'nested/runs.csv')).toBe('runs.csv')
    expect(filenameOf(null, 'plain.csv')).toBe('plain.csv')
    expect(filenameOf('attachment; filename="   "', 'runs.csv')).toBe('runs.csv')
  })
})
