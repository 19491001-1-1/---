import { ref, type Ref } from 'vue'
import { experimentEventsUrl } from '@/api/client'
import { isTerminal, type ExperimentTask } from '@/types/api'

/**
 * 订阅一个实验任务的 SSE 事件流，并在断线后自动重连。
 *
 * 重连由两部分配合完成：
 * 1. 每条事件都带 `id:` 序号，浏览器 EventSource 会记住最后收到的那个，
 *    重连时通过 `Last-Event-ID` 请求头自动带回来，服务端据此补发断线期间漏掉的事件。
 *    因此进度不会退回，也不会永远停在断线那一刻。
 * 2. 若浏览器没有自动重连（例如服务端重启后连接被彻底关闭），这里再做一层手动兜底，
 *    并按已收到的最大序号显式指定补发起点。
 *
 * 终止事件（`complete`）到达即收工：任务已经结束，没有继续订阅的理由。
 */
export interface ExperimentEventStreamOptions {
  /** 建连工厂，默认用全局 EventSource；测试可注入替身。 */
  createSource?: (url: string) => EventSource
  /** 断线后多久重连（毫秒）。测试里调小以免等待。 */
  reconnectDelayMs?: number
  /** 最多重连几次，避免对已下线的服务无限空转。 */
  maxReconnects?: number
}

export function useExperimentEvents(options: ExperimentEventStreamOptions = {}) {
  const createSource = options.createSource ?? ((url: string) => new EventSource(url))
  const reconnectDelayMs = options.reconnectDelayMs ?? 2_000
  const maxReconnects = options.maxReconnects ?? 5

  const task: Ref<ExperimentTask | null> = ref(null)
  const connected = ref(false)
  const reconnects = ref(0)

  let source: EventSource | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let currentId: string | null = null
  let lastEventId: string | null = null
  // 主动收尾的标记。终态到达或 dispose 之后，旧的连接对象仍可能回调 onerror，
  // 没有这个标记就会把「正常收工」误判成「断线」而重新建连。
  let settled = true

  const apply = (raw: string) => { task.value = JSON.parse(raw) as ExperimentTask }

  const close = () => {
    if (timer !== null) { clearTimeout(timer); timer = null }
    source?.close()
    source = null
    connected.value = false
  }

  const finish = () => { settled = true; close() }

  const open = (id: string) => {
    close()
    currentId = id
    settled = false

    const handle = (event: MessageEvent) => {
      if (event.lastEventId) lastEventId = event.lastEventId
      apply(event.data as string)
      // 收到终止事件就收工，不再重连。
      if (isTerminal(task.value)) finish()
    }

    const stream = createSource(experimentEventsUrl(id, lastEventId))
    source = stream
    stream.addEventListener('progress', handle as EventListener)
    stream.addEventListener('complete', handle as EventListener)
    stream.onopen = () => { connected.value = true; reconnects.value = 0 }
    stream.onerror = () => {
      /* 连接已断开：交给下面的重连逻辑，不在事件处理器里做决定 */
      connected.value = false
      if (settled) return
      if (task.value && isTerminal(task.value)) { finish(); return }
      if (reconnects.value >= maxReconnects) { finish(); return }
      reconnects.value += 1
      // 用本次连接自己的 id 重连，而不是去读可变的 currentId：
      // 重连回调触发前若用户已经订阅了别的任务，读 currentId 会连错任务。
      timer = setTimeout(() => open(id), reconnectDelayMs)
    }
  }

  /** 开始订阅；同一时刻只会有一个活跃连接。 */
  const subscribe = (id: string) => { lastEventId = null; reconnects.value = 0; open(id) }

  /**
   * 对同一个任务重新建立连接，回到「未收尾」状态；用于「重试」之后接上新一轮的事件流。
   * 这里刻意不因为任务曾经到过终态就拒绝——重试正是从一个终态重新开始的。
   */
  const resubscribe = () => {
    if (currentId === null) return
    lastEventId = null
    reconnects.value = 0
    open(currentId)
  }

  const dispose = () => { currentId = null; finish() }

  return { task, connected, reconnects, subscribe, resubscribe, dispose }
}
