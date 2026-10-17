<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiClientError } from '@/api/client'
import StatePanel from '@/components/shared/StatePanel.vue'
import HeatmapChart from '@/components/analysis/HeatmapChart.vue'
import BoxplotChart from '@/components/analysis/BoxplotChart.vue'
import ConvergenceCompareChart from '@/components/analysis/ConvergenceCompareChart.vue'
import PageGuide from '@/components/shared/PageGuide.vue'
import { experimentStatusLabel } from '@/experimentStatus'
import {
  boxplot, convergenceSeries, heatmap, summarizeByAlgorithm, winnersByInstance,
  HEAT_DIMENSION_LABEL, type HeatDimension,
} from '@/analysis/experimentAnalysis'
import { terminalStatuses, type ExperimentAnalysis, type ExperimentTask } from '@/types/api'

const tasks = ref<ExperimentTask[]>([])
const analysis = ref<ExperimentAnalysis | null>(null)
const selectedId = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const xDimension = ref<HeatDimension>('searchAlgorithm')
const yDimension = ref<HeatDimension>('decoder')
const selectedInstance = ref<string>('')
const picked = ref<string | null>(null)

const DIMENSIONS: HeatDimension[] = ['searchAlgorithm', 'decoder', 'instanceId']

// 横纵轴相同时热力图只剩一条对角线，没有比较意义。用户把某一轴切到另一轴当前值时，
// 自动把另一轴换到本轴切换前的维度，相当于交换两轴，既保持选择有效也不吞掉操作。
watch(xDimension, (next, previous) => {
  if (next === yDimension.value) yDimension.value = previous
})
watch(yDimension, (next, previous) => {
  if (next === xDimension.value) xDimension.value = previous
})

async function loadTasks() {
  loading.value = true; error.value = null
  try {
    tasks.value = await api.experiments()
    // 默认选最近一个终态任务：分析视图要拿逐格轨迹，选一个还在跑的任务会满屏都是空。
    const preferred = tasks.value.find((task) => terminalStatuses.includes(task.status) && task.completedTasks > 0)
      ?? tasks.value.find((task) => task.completedTasks > 0)
    if (!selectedId.value && preferred) await select(preferred.id)
  } catch (reason) {
    error.value = reason instanceof ApiClientError ? reason.message : '无法加载实验记录'
  } finally {
    loading.value = false
  }
}

async function select(id: string) {
  selectedId.value = id
  picked.value = null
  try {
    analysis.value = await api.experimentAnalysis(id)
    const instances = Object.keys(analysis.value.instances)
    if (!instances.includes(selectedInstance.value)) selectedInstance.value = instances[0] ?? ''
    scheduleRefresh()
  } catch (reason) {
    analysis.value = null
    error.value = reason instanceof ApiClientError ? reason.message : '无法加载分析数据'
  }
}

/** 任务跑完后重新拉一次，让热力图与曲线把新到的格子补上。 */
async function refresh() {
  if (selectedId.value) await select(selectedId.value)
}

let refreshTimer: number | null = null
function scheduleRefresh() {
  if (refreshTimer !== null) { window.clearTimeout(refreshTimer); refreshTimer = null }
  if (!analysis.value || terminalStatuses.includes(analysis.value.status)) return
  refreshTimer = window.setTimeout(() => { void refresh() }, 3_000)
}

const cells = computed(() => analysis.value?.cells ?? [])
const summaries = computed(() => summarizeByAlgorithm(cells.value))
const winners = computed(() => winnersByInstance(cells.value))
const algorithmCount = computed(() => new Set(cells.value.map((cell) => `${cell.searchAlgorithm}/${cell.decoder}`)).size)
const boxes = computed(() => summaries.value.map((summary) => ({
  label: summary.algorithm,
  stats: boxplot(cells.value
    .filter((cell) => cell.searchAlgorithm === summary.algorithm && cell.legal)
    .map((cell) => cell.utilization)),
})))
const grid = computed(() => heatmap(cells.value, xDimension.value, yDimension.value))
const binArea = computed(() => {
  const instance = analysis.value?.instances[selectedInstance.value]
  return instance ? instance.binWidth * instance.binHeight : 0
})
const curves = computed(() => convergenceSeries(cells.value, selectedInstance.value))
const missing = computed(() => Math.max(0, (analysis.value?.totalTasks ?? 0) - cells.value.length))

/** 点热力图格子就把实例与算法筛到那一格上，三个视图共用同一份筛选。 */
function pick(x: string, y: string) {
  picked.value = `${y}|${x}`
  if (xDimension.value === 'instanceId') selectedInstance.value = x
  else if (yDimension.value === 'instanceId') selectedInstance.value = y
}

const percent = (value: number) => `${(value * 100).toFixed(2)}%`
const number = (value: number) => value.toFixed(value >= 100 ? 0 : 1)
const analysisGuide = [
  {title:'热力图回答什么',summary:'快速发现搜索器、解码器或实例之间的高低区域与交互关系。',body:'横轴和纵轴可在搜索器、解码器、实例之间切换；同维度不能同时占据两轴。每个格子聚合对应条件下已有合法运行的利用率，颜色用于相对比较。点击包含实例维度的格子会同步切换收敛曲线实例。',points:['空白可能表示任务尚未完成','颜色深浅应配合数值读取','聚合值不能替代逐格原始数据','运行中的任务会自动补齐图格']},
  {title:'排名与箱线图怎样读',summary:'排名看综合相对位置，箱线图看稳定性与离散程度。',body:'算法排名按共同任务条件计算平均名次，平均名次越小越好；同时列出均值、最好、最差、耗时和合法运行数。箱线图用中位数和四分位范围描述多次运行分布，须线采用 1.5 倍四分位距规则。',points:['最好值高不代表整体稳定','有效运行不足时排名可信度下降','箱体越窄通常表示波动越小','耗时只应在同一运行环境比较']},
  {title:'收敛曲线怎样比较',summary:'曲线展示同一实例上历史最好解随评价推进的变化。',body:'纵向高度对应相对板材面积的利用率，横向推进来自候选评价过程。为避免同一组合重复种子堆叠，当前视图每格选择评价次数最多的一次运行作为代表。比较时必须确认预算模式相同；时间预算与评价预算不可直接混读。',points:['上升越早表示较快发现好解','平台期可能代表搜索暂时停滞','最终点决定该代表运行的终值','超长轨迹抽样时仍保留最后一点']},
]

onBeforeUnmount(() => { if (refreshTimer !== null) window.clearTimeout(refreshTimer) })
void loadTasks()
</script>

<template>
  <div class="analysis-page">
    <header class="archive-hero">
      <div><p class="section-kicker">阶段 6 · 结果研判</p><h2>结果分析</h2></div>
      <p>热力图、排名、分布与收敛对比共用同一份逐格结果。数据直接取自后端，与下载的 CSV / HTML 报告同源。</p>
    </header>
    <PageGuide label="分析导读" title="从颜色、分布和轨迹形成完整判断" introduction="四种视图各自回答不同问题，不能只看其中一个最好值。" :steps="['选择实验','设定比较维度','交叉阅读四类图表','回到记录复查原始格子']" :sections="analysisGuide" />

    <StatePanel v-if="error" tone="error" title="分析数据加载失败" :description="error" action-label="重试" @action="loadTasks" />
    <StatePanel v-else-if="!loading && tasks.length === 0" title="暂无可分析的实验"
      description="从“对比实验”页面启动一组实验后，结果会出现在这里。" />

    <template v-else>
      <section class="analysis-toolbar">
        <label class="plain-field"><span>实验</span>
          <select :value="selectedId ?? ''" @change="select(($event.target as HTMLSelectElement).value)">
            <option v-for="task in tasks" :key="task.id" :value="task.id">
              {{ task.name }} · {{ task.completedTasks }}/{{ task.totalTasks }} · {{ experimentStatusLabel(task.status) }}
            </option>
          </select>
        </label>
        <label class="plain-field"><span>热力图纵轴</span>
          <select v-model="yDimension">
            <option v-for="dimension in DIMENSIONS" :key="dimension" :value="dimension"
              :disabled="dimension === xDimension">
              {{ HEAT_DIMENSION_LABEL[dimension] }}
            </option>
          </select>
        </label>
        <label class="plain-field"><span>热力图横轴</span>
          <select v-model="xDimension">
            <option v-for="dimension in DIMENSIONS" :key="dimension" :value="dimension"
              :disabled="dimension === yDimension">
              {{ HEAT_DIMENSION_LABEL[dimension] }}
            </option>
          </select>
        </label>
        <label class="plain-field"><span>收敛对比实例</span>
          <select v-model="selectedInstance">
            <option v-for="instance in Object.keys(analysis?.instances ?? {})" :key="instance" :value="instance">
              {{ instance }}
            </option>
          </select>
        </label>
        <button type="button" :disabled="!selectedId" @click="refresh">刷新</button>
      </section>

      <p v-if="analysis" class="analysis-note">
        已完成 <strong>{{ analysis.completedTasks }}</strong> / {{ analysis.totalTasks }} 个组合
        <span v-if="missing > 0">（还有 {{ missing }} 个没有结果，图上的空缺会随任务推进自动补齐）</span>
        · 预算口径 <code>{{ analysis.budgetMode }}</code>
        · <span v-if="analysis.rotationAllowed">允许旋转</span><span v-else>禁止旋转</span>
      </p>

      <section class="analysis-grid">
        <article class="analysis-card wide">
          <header><h3>逐实例最优配置</h3><span>{{ missing > 0 ? '当前已有结果 · 暂定' : '已完成结果' }} · {{ algorithmCount }} 个搜索器 × 解码器配置 · 按合法运行均值判定</span></header>
          <div class="table-scroll">
            <table class="rank-table winner-table">
              <thead><tr><th>实例</th><th>最优搜索器 × 解码器</th><th>平均利用率</th><th>最好</th><th>标准差</th><th>合法运行</th></tr></thead>
              <tbody><tr v-for="winner in winners" :key="`${winner.instanceId}-${winner.algorithm}`">
                <td class="name">{{ winner.instanceId }}</td>
                <td><strong>{{ winner.algorithm }}</strong><small v-if="winner.tied">（并列）</small></td>
                <td>{{ percent(winner.meanUtilization) }}</td><td>{{ percent(winner.bestUtilization) }}</td>
                <td>{{ percent(winner.standardDeviation) }}</td><td class="ok">{{ winner.validRuns }}/{{ winner.runs }}</td>
              </tr></tbody>
            </table>
          </div>
        </article>

        <article class="analysis-card wide">
          <header><h3>{{ HEAT_DIMENSION_LABEL[yDimension] }} × {{ HEAT_DIMENSION_LABEL[xDimension] }} 利用率热力图</h3></header>
          <HeatmapChart :heatmap="grid" :x-label="HEAT_DIMENSION_LABEL[xDimension]"
            :y-label="HEAT_DIMENSION_LABEL[yDimension]" :selected="picked" @pick="pick" />
        </article>

        <article class="analysis-card">
          <header><h3>算法排名</h3><span>平均名次越小越好</span></header>
          <div class="table-scroll">
            <table class="rank-table algorithm-rank-table">
              <thead><tr>
                <th>#</th><th>算法</th><th>平均名次</th><th>平均利用率</th>
                <th>最好</th><th>最差</th><th>平均ms</th><th>合法</th>
              </tr></thead>
              <tbody>
                <tr v-for="(summary, index) in summaries" :key="summary.algorithm"
                  :class="{ dim: summary.validRuns === 0 }">
                  <td>{{ index + 1 }}</td>
                  <td class="name">{{ summary.algorithm }}</td>
                  <td><strong>{{ number(summary.meanRank) }}</strong></td>
                  <td>{{ percent(summary.meanUtilization) }}</td>
                  <td>{{ percent(summary.bestUtilization) }}</td>
                  <td>{{ percent(summary.worstUtilization) }}</td>
                  <td>{{ number(summary.meanMilliseconds) }}</td>
                  <td :class="summary.validRuns === summary.runs ? 'ok' : 'bad'">
                    {{ summary.validRuns }}/{{ summary.runs }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>

        <article class="analysis-card">
          <header><h3>利用率分布</h3><span>箱线图 · 须为 1.5× 四分位距</span></header>
          <BoxplotChart :series="boxes" />
        </article>

        <article class="analysis-card wide">
          <header><h3>收敛曲线对比 · {{ selectedInstance || '—' }}</h3>
            <span>每格取该算法评价次数最多的一次运行</span></header>
          <ConvergenceCompareChart :series="curves" :bin-area="binArea" />
        </article>
      </section>
    </template>
  </div>
</template>
