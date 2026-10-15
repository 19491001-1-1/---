import type {
  ApiErrorPayload,
  Meta,
  PackingDecoder,
  PackingInstanceDetail,
  PackingInstanceSummary,
  SearchAlgorithm,
  SolveRequest,
  SolveResult,
  ExperimentPreview,
  ExperimentRequest,
  ExperimentTask,
  ExperimentArtifact,
  ExperimentAnalysis,
} from '@/types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly code = 'NETWORK_ERROR',
    readonly fieldErrors: Record<string, string> = {},
    readonly requestId?: string,
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiClientError('无法连接后端服务，请确认 Spring Boot 已启动')
  }
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiErrorPayload | null
    throw new ApiClientError(
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
      payload?.code ?? 'HTTP_ERROR',
      payload?.fieldErrors ?? {},
      payload?.requestId,
    )
  }
  return response
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return (await send(path, init)).json() as Promise<T>
}

export const api = {
  meta: () => request<Meta>('/api/meta'),
  searchAlgorithms: () => request<SearchAlgorithm[]>('/api/algorithms/search'),
  decoders: () => request<PackingDecoder[]>('/api/algorithms/decoders'),
  instances: () => request<PackingInstanceSummary[]>('/api/instances'),
  instance: (id: string) => request<PackingInstanceDetail>(`/api/instances/${encodeURIComponent(id)}`),
  solve: (body: SolveRequest) =>
    request<SolveResult>('/api/solve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
  previewExperiment: (body: ExperimentRequest) =>
    request<ExperimentPreview>('/api/experiments/preview', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    }),
  createExperiment: (body: ExperimentRequest) =>
    request<ExperimentTask>('/api/experiments', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    }),
  experiments: () => request<ExperimentTask[]>('/api/experiments'),
  experiment: (id: string) => request<ExperimentTask>(`/api/experiments/${encodeURIComponent(id)}`),
  experimentAnalysis: (id: string) =>
    request<ExperimentAnalysis>(`/api/experiments/${encodeURIComponent(id)}/analysis`),
  cancelExperiment: (id: string) =>
    request<ExperimentTask>(`/api/experiments/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
  retryExperiment: (id: string) =>
    request<ExperimentTask>(`/api/experiments/${encodeURIComponent(id)}/retry`, { method: 'POST' }),
  experimentArtifacts: (id: string) =>
    request<ExperimentArtifact[]>(`/api/experiments/${encodeURIComponent(id)}/artifacts`),
  generateExperimentArtifacts: (id: string) =>
    request<ExperimentArtifact[]>(`/api/experiments/${encodeURIComponent(id)}/artifacts`, {
      method: 'POST',
    }),
  /** 下载单个产物：拿回二进制与文件名，由调用方决定怎么落盘。 */
  downloadExperimentArtifact: async (id: string, name: string): Promise<ArtifactDownload> => {
    const response = await send(
      `/api/experiments/${encodeURIComponent(id)}/artifacts/${encodeArtifactPath(name)}`,
      { headers: { Accept: '*/*' } },
    )
    return {
      blob: await response.blob(),
      filename: filenameOf(response.headers.get('Content-Disposition'), name),
      contentType: response.headers.get('Content-Type') ?? 'application/octet-stream',
    }
  },
}

export interface ArtifactDownload {
  blob: Blob
  filename: string
  contentType: string
}

/**
 * 产物名可能带子目录（`convergence/gcut1.svg`），因此逐段编码、保留分隔符。
 * 整串丢给 `encodeURIComponent` 会把 `/` 编成 `%2F`，那在服务端是一个普通字符，
 * 拼不出原始路径。
 */
export const encodeArtifactPath = (name: string) =>
  name.split('/').map(encodeURIComponent).join('/')

/**
 * 从 `Content-Disposition` 里取服务端建议的文件名；缺失时退回到产物名本身，
 * 并始终只取最后一段——文件名里再带目录会变成路径拼接。
 */
export const filenameOf = (disposition: string | null, fallback: string): string => {
  // `[^";]*` 而不是 `+`：`filename=""` 这种空值时正则仍应命中，再由后面的兜底接管，
  // 否则会走到「整串都不像文件名」的分支上去。
  const matched = disposition?.match(/filename\s*=\s*"?([^";]*)"?/i)
  const raw = (matched?.[1] ?? '').trim() || fallback
  const lastSegment = raw.split('/').pop()?.trim() || fallback
  return lastSegment.replace(/["\\]/g, '').trim() || 'artifact'
}

/**
 * 触发浏览器下载并回收对象 URL。
 *
 * <p>之所以走 Blob 而不是给 `<a href>` 直接指到接口地址：产物里既有 HTML 也有 SVG，
 * 直接导航等于在当前页把用户实验目录里的内容当同源页面打开；而且接口不保证
 * 同源部署（`VITE_API_BASE_URL` 可以指向别的域名），那时 `<a download>` 会被忽略。
 */
export const saveArtifact = (download: ArtifactDownload) => {
  const url = URL.createObjectURL(download.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = download.filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/**
 * 事件流地址。`lastEventId` 用于手动重连时指定补发起点；
 * 浏览器 EventSource 自己重连时会通过 `Last-Event-ID` 请求头自动带上最后收到的序号。
 */
export const experimentEventsUrl = (id: string, lastEventId?: string | null) => {
  const base = `${API_BASE}/api/experiments/${encodeURIComponent(id)}/events`
  return lastEventId ? `${base}?lastEventId=${encodeURIComponent(lastEventId)}` : base
}
