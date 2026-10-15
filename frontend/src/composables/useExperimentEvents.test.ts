import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useExperimentEvents } from './useExperimentEvents'
import type { ExperimentTask } from '@/types/api'

/** 可编程的 EventSource 替身：测试手动触发事件与错误，不依赖真实网络。 */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  readonly url: string
  closed = false
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  private listeners = new Map<string, ((event: MessageEvent) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(name: string, handler: (event: MessageEvent) => void) {
    const list = this.listeners.get(name) ?? []
    list.push(handler)
    this.listeners.set(name, list)
  }

  close() { this.closed = true }

  emit(name: string, payload: unknown, lastEventId = '') {
    for (const handler of this.listeners.get(name) ?? []) {
      handler({ data: JSON.stringify(payload), lastEventId } as MessageEvent)
    }
  }

  fail() { this.onerror?.() }

  static latest() { return FakeEventSource.instances[FakeEventSource.instances.length - 1] }
  static reset() { FakeEventSource.instances = [] }
}

const task = (status: ExperimentTask['status']): ExperimentTask => ({
  id: 'task-1', name: '实验', status, createdAt: '2026-10-14T00:00:00Z',
  startedAt: null, completedAt: null, totalTasks: 4, completedTasks: 1,
  progressPercent: 25, currentTask: null, legalResults: 1, averageUtilization: 0.9,
  bestUtilization: 0.9, error: null, results: [],
})

describe('useExperimentEvents', () => {
  beforeEach(() => { FakeEventSource.reset(); vi.useRealTimers() })

  const setup = () => {
    const stream = useExperimentEvents({
      createSource: (url) => new FakeEventSource(url) as unknown as EventSource,
      reconnectDelayMs: 0,
      maxReconnects: 2,
    })
    return stream
  }

  it('订阅后按事件数据更新任务', () => {
    const stream = setup()
    stream.subscribe('task-1')

    FakeEventSource.latest().emit('progress', task('RUNNING'), '3')

    expect(stream.task.value?.status).toBe('RUNNING')
    expect(stream.task.value?.progressPercent).toBe(25)
  })

  it('首次订阅不带 lastEventId', () => {
    const stream = setup()
    stream.subscribe('task-1')

    expect(FakeEventSource.latest().url).not.toContain('lastEventId')
  })

  it('收到 complete 后关闭连接，不再重连', () => {
    const stream = setup()
    stream.subscribe('task-1')
    const source = FakeEventSource.latest()

    source.emit('complete', task('COMPLETED'), '9')

    expect(source.closed).toBe(true)
    expect(stream.task.value?.status).toBe('COMPLETED')
  })

  it('断线后重连并带上最后收到的事件序号', () => {
    vi.useFakeTimers()
    const stream = setup()
    stream.subscribe('task-1')
    const first = FakeEventSource.latest()
    first.emit('progress', task('RUNNING'), '7')

    first.fail()
    vi.runOnlyPendingTimers()

    const second = FakeEventSource.latest()
    expect(second).not.toBe(first)
    // 重连必须从第 7 号之后补发，否则进度会倒退或重复。
    expect(second.url).toContain('lastEventId=7')
    expect(stream.reconnects.value).toBe(1)
  })

  it('连续重连达到上限后放弃，避免对已下线的服务空转', () => {
    vi.useFakeTimers()
    const stream = setup()
    stream.subscribe('task-1')

    for (let attempt = 0; attempt < 3; attempt++) {
      FakeEventSource.latest().fail()
      vi.runOnlyPendingTimers()
    }

    expect(stream.reconnects.value).toBe(2)
    // 第三次失败后不再新建连接，连接数停在重连上限之内。
    expect(FakeEventSource.instances.length).toBeLessThanOrEqual(3)
  })

  it('任务已到终态时断线不再重连', () => {
    vi.useFakeTimers()
    const stream = setup()
    stream.subscribe('task-1')
    FakeEventSource.latest().emit('progress', task('COMPLETED'), '5')
    const countsBefore = FakeEventSource.instances.length

    FakeEventSource.latest().fail()
    vi.runOnlyPendingTimers()

    expect(FakeEventSource.instances.length).toBe(countsBefore)
  })

  it('任务到终态后 resubscribe 仍能重新接上（重试场景）', () => {
    const stream = setup()
    stream.subscribe('task-1')
    FakeEventSource.latest().emit('complete', task('CANCELLED'), '4')
    expect(stream.task.value?.status).toBe('CANCELLED')

    // 「重试」把任务放回队列，此时必须能重新订阅，否则进度条会永远停在取消那一刻。
    stream.resubscribe()

    const resumed = FakeEventSource.latest()
    expect(FakeEventSource.instances.length).toBe(2)
    // 新一轮从零开始，不能带上一轮最后的序号，否则会漏掉新一轮的开头事件。
    expect(resumed.url).not.toContain('lastEventId')
    resumed.emit('progress', task('RUNNING'), '5')
    expect(stream.task.value?.status).toBe('RUNNING')
  })

  it('主动收尾后旧连接的 error 回调不会触发重连', () => {
    vi.useFakeTimers()
    const stream = setup()
    stream.subscribe('task-1')
    const first = FakeEventSource.latest()
    first.emit('complete', task('COMPLETED'), '8')

    // 终态时我们已经 close 了，但连接对象仍可能异步回调 onerror；不能把它当成断线。
    first.fail()
    vi.runOnlyPendingTimers()

    expect(FakeEventSource.instances.length).toBe(1)
    expect(stream.reconnects.value).toBe(0)
  })

  it('dispose 会关闭连接并停止重连', () => {
    vi.useFakeTimers()
    const stream = setup()
    stream.subscribe('task-1')
    const source = FakeEventSource.latest()

    stream.dispose()
    source.fail()
    vi.runOnlyPendingTimers()

    expect(source.closed).toBe(true)
    expect(FakeEventSource.instances.length).toBe(1)
  })
})
