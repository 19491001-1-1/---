<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiClientError } from '@/api/client'
import StatePanel from '@/components/shared/StatePanel.vue'
import ExperimentArtifacts from '@/components/shared/ExperimentArtifacts.vue'
import { experimentStatusLabel } from '@/experimentStatus'
import { isTerminal, terminalStatuses, type ExperimentTask } from '@/types/api'

const tasks = ref<ExperimentTask[]>([])
const route = useRoute()
const selected = ref<ExperimentTask | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const keyword = ref('')
const statusFilter = ref<'ALL' | ExperimentTask['status']>('ALL')
type SortKey = 'createdAt' | 'progressPercent' | 'bestUtilization' | 'name'
const sortKey = ref<SortKey>('createdAt')
const sortDescending = ref(true)

const SORT_LABEL: Record<SortKey, string> = {
  createdAt: '创建时间', progressPercent: '进度', bestUtilization: '最佳利用率', name: '名称',
}
const STATUS_FILTERS: ExperimentTask['status'][] = ['QUEUED', 'RUNNING', ...terminalStatuses]

async function load() {
  loading.value = true; error.value = null
  try {
    tasks.value = await api.experiments()
    // 选中的那条可能在这次刷新里被保留策略回收了，此时必须清掉，
    // 否则详情区会一直显示一份磁盘上已经不存在的结果。
    if (selected.value && !tasks.value.some((task) => task.id === selected.value?.id)) {
      selected.value = null
    }
  } catch (reason) {
    error.value = reason instanceof ApiClientError ? reason.message : '无法加载实验记录'
  } finally {
    loading.value = false
  }
}

async function open(task: ExperimentTask) {
  try {
    selected.value = await api.experiment(task.id)
  } catch (reason) {
    error.value = reason instanceof ApiClientError ? reason.message : '无法加载实验详情'
  }
}

const visible = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  const filtered = tasks.value.filter((task) => {
    if (statusFilter.value !== 'ALL' && task.status !== statusFilter.value) return false
    if (!needle) return true
    return task.name.toLowerCase().includes(needle) || task.id.toLowerCase().includes(needle)
  })
  const direction = sortDescending.value ? -1 : 1
  return [...filtered].sort((left, right) => {
    // 排序必须留一个确定的兜底键：两个任务的进度经常完全相同，
    // 只按进度排会让它们的相对顺序随底层数组变化而抖动，看起来像列表在乱跳。
    const primary = compare(left, right, sortKey.value)
    if (primary !== 0) return primary * direction
    return left.id.localeCompare(right.id)
  })
})

function compare(left: ExperimentTask, right: ExperimentTask, key: SortKey): number {
  if (key === 'name') return left.name.localeCompare(right.name)
  if (key === 'createdAt') return left.createdAt.localeCompare(right.createdAt)
  if (key === 'progressPercent') return left.progressPercent - right.progressPercent
  return (left.bestUtilization ?? -1) - (right.bestUtilization ?? -1)
}

function toggleSort(key: SortKey) {
  if (sortKey.value === key) sortDescending.value = !sortDescending.value
  else { sortKey.value = key; sortDescending.value = true }
}

/**
 * 运行中的任务靠轮询刷新。
 *
 * <p>这里刻意不复用实验页那套 SSE：记录页可能同时列着十几个任务，每个都开一条事件流
 * 是拿十几个长连接换一个「进度小数点后一位更实时」，不划算。轮询间隔也只在真的有
 * 非终态任务时才启动，全部结束就自动停。
 */
let timer: number | null = null

const running = computed(() => tasks.value.some((task) => !isTerminal(task)))
function schedule() {
  if (timer !== null) { window.clearTimeout(timer); timer = null }
  if (!running.value) return
  // 用一次性定时器串行轮询：如果一次请求超过 3 秒，setInterval 会在上一轮尚未结束时
  // 再发下一轮，慢网络下请求会越积越多。每轮完成后再安排下一轮可天然避免重叠。
  timer = window.setTimeout(async () => {
    timer = null
    await load()
    if (selected.value) await open(selected.value)
    // 每轮之后重新评估一次：任务全部跑完时不再安排下一轮。
    schedule()
  }, 3_000)
}

const date = (value: string) => new Date(value).toLocaleString('zh-CN')
const percent = (value: number | null) => value === null ? '—' : `${(value * 100).toFixed(2)}%`
const statusCounts = computed(() => {
  const counts: Record<string, number> = { ALL: tasks.value.length }
  for (const status of STATUS_FILTERS) counts[status] = 0
  for (const task of tasks.value) counts[task.status] = (counts[task.status] ?? 0) + 1
  return counts
})

onMounted(async () => {
  await load()
  const requestedId = typeof route.query.task === 'string' ? route.query.task : null
  const requested = requestedId ? tasks.value.find((task) => task.id === requestedId) : null
  if (requested) await open(requested)
  schedule()
})
onBeforeUnmount(() => { if (timer !== null) window.clearTimeout(timer) })
</script>

<template><div class="records-page">
  <header class="archive-hero"><div><p class="section-kicker">真实运行 · 跨重启保留</p><h2>实验记录</h2></div><p>记录与逐格结果都落在后端存储目录里，重启后自动恢复；终态任务可生成 CSV / HTML / SVG 产物并逐个下载。</p></header>

  <StatePanel v-if="error" tone="error" title="记录加载失败" :description="error" action-label="重试" @action="load" />
  <StatePanel v-else-if="!loading && tasks.length === 0" title="暂无实验记录" description="从“对比实验”页面启动第一组真实实验后，记录会立即出现在这里。" />

  <template v-else>
    <section class="records-toolbar">
      <label class="plain-field"><span>筛选名称或标识</span>
        <input v-model="keyword" placeholder="输入关键字" />
      </label>
      <div class="status-tabs">
        <button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">
          全部 {{ statusCounts.ALL }}
        </button>
        <button v-for="status in STATUS_FILTERS" :key="status" type="button"
          :class="{ active: statusFilter === status }" @click="statusFilter = status">
          {{ experimentStatusLabel(status) }} {{ statusCounts[status] }}
        </button>
      </div>
      <div class="sort-controls">
        <span>排序</span>
        <button v-for="(label, key) in SORT_LABEL" :key="key" type="button"
          :class="{ active: sortKey === key }" @click="toggleSort(key as SortKey)">
          {{ label }}<i v-if="sortKey === key">{{ sortDescending ? '↓' : '↑' }}</i>
        </button>
        <button type="button" class="refresh" :disabled="loading" @click="load">
          {{ running ? '自动刷新中' : '刷新' }}
        </button>
      </div>
    </section>

    <StatePanel v-if="visible.length === 0" title="没有符合条件的记录"
      description="试试放宽筛选条件，或清空关键字。" />

    <div v-else class="records-layout">
      <section class="records-table"><header><span>名称 / 创建时间</span><span>进度</span><span>最佳利用率</span><span>状态</span></header><button v-for="item in visible" :key="item.id" type="button" :class="{ active: selected?.id === item.id }" @click="open(item)"><span><strong>{{ item.name }}</strong><small>{{ date(item.createdAt) }}</small></span><span>{{ item.completedTasks }}/{{ item.totalTasks }}</span><span>{{ percent(item.bestUtilization) }}</span><i :class="item.status.toLowerCase()">{{ experimentStatusLabel(item.status) }}</i></button></section>
      <aside class="record-detail"><template v-if="selected"><p class="section-kicker">结果明细</p><h3>{{ selected.name }}</h3><div class="record-summary"><span>平均利用率 <strong>{{ percent(selected.averageUtilization) }}</strong></span><span>合法结果 <strong>{{ selected.legalResults }}/{{ selected.completedTasks }}</strong></span></div><div class="result-list"><div v-for="row in selected.results" :key="`${row.instanceId}-${row.searchAlgorithm}-${row.decoder}-${row.seed}`"><span>{{ row.instanceId }} · {{ row.searchAlgorithm }}</span><small>{{ row.decoder }} · seed {{ row.seed }}</small><strong>{{ percent(row.utilization) }}</strong></div></div>
    <!-- 跨重启的记录同样能回看产物：产物躺在任务目录里，与内存中的任务状态无关。 -->
    <ExperimentArtifacts :task-id="selected.id" :terminal="isTerminal(selected)"
      :has-results="selected.completedTasks > 0" /></template><p v-else>选择左侧记录查看逐组合结果。</p></aside>
    </div>
  </template>
</div></template>
