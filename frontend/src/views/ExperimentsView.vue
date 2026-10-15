<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiClientError } from '@/api/client'
import StatePanel from '@/components/shared/StatePanel.vue'
import ExperimentArtifacts from '@/components/shared/ExperimentArtifacts.vue'
import { experimentStatusLabel } from '@/experimentStatus'
import { useExperimentEvents } from '@/composables/useExperimentEvents'
import { useCatalogStore } from '@/stores/catalog'
import { isTerminal, type BudgetMode, type ExperimentPreview, type ExperimentRequest } from '@/types/api'

const catalog = useCatalogStore()
const route = useRoute()
const router = useRouter()
const form = reactive({
  name: `对比实验 ${new Date().toLocaleDateString('zh-CN')}`,
  instanceIds: [] as string[], searchAlgorithms: [] as string[], decoders: [] as string[],
  seedsText: '20261012, 20261013, 20261014', rotationAllowed: true,
  budgetMode: 'EVALUATIONS' as BudgetMode, maxEvaluations: 20_000, maxMilliseconds: 2_000, parallelism: 2,
})
const preview = ref<ExperimentPreview | null>(null)
const busy = ref(false)
const error = ref<string | null>(null)

const events = useExperimentEvents()
const task = events.task

watch(() => catalog.instances, (items) => {
  if (!form.instanceIds.length && items.length) form.instanceIds = items.slice(0, 2).map((item) => item.id)
}, { immediate: true })
watch(() => catalog.searchAlgorithms, (items) => {
  if (!form.searchAlgorithms.length && items.length) form.searchAlgorithms = items.slice(0, 2).map((item) => item.machineValue)
}, { immediate: true })
watch(() => catalog.decoders, (items) => {
  if (!form.decoders.length && items.length) form.decoders = items.slice(4, 6).map((item) => item.machineValue)
}, { immediate: true })
watch(() => form.budgetMode, (mode) => { if (mode === 'TIME_MS') form.parallelism = 1 })

const seeds = computed(() => [...new Set(form.seedsText.split(/[，,\s]+/).filter(Boolean).map(Number))]
  .filter((value) => Number.isSafeInteger(value) && value >= 0))
const localTaskCount = computed(() => form.instanceIds.length * form.searchAlgorithms.length * form.decoders.length * seeds.value.length)
const valid = computed(() => Boolean(form.name.trim() && localTaskCount.value > 0 && localTaskCount.value <= 500))

// 任务结束后可做的事：取消只对在跑的任务有意义；重试只对没算完的任务有意义。
const canCancel = computed(() => task.value !== null && !isTerminal(task.value))
const canRetry = computed(() => task.value !== null && isTerminal(task.value)
  && task.value.completedTasks < task.value.totalTasks)

function request(): ExperimentRequest {
  return {
    name: form.name.trim(), instanceIds: form.instanceIds, searchAlgorithms: form.searchAlgorithms,
    decoders: form.decoders, seeds: seeds.value, rotationAllowed: form.rotationAllowed,
    budgetMode: form.budgetMode,
    maxEvaluations: form.budgetMode === 'EVALUATIONS' ? form.maxEvaluations : null,
    maxMilliseconds: form.budgetMode === 'TIME_MS' ? form.maxMilliseconds : null,
    parallelism: form.parallelism,
  }
}

async function loadPreview() {
  if (!valid.value) return
  busy.value = true; error.value = null
  try { preview.value = await api.previewExperiment(request()) }
  catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法预览实验规模' }
  finally { busy.value = false }
}

async function start() {
  if (!valid.value) return
  busy.value = true; error.value = null
  try {
    preview.value = await api.previewExperiment(request())
    const created = await api.createExperiment(request())
    events.subscribe(created.id)
    await router.replace({ query: { ...route.query, task: created.id } })
    ElMessage.success('实验任务已进入后台队列')
  } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法启动实验' }
  finally { busy.value = false }
}

async function cancel() {
  if (!task.value) return
  busy.value = true; error.value = null
  try {
    task.value = await api.cancelExperiment(task.value.id)
    ElMessage.success('已请求取消，已算完的结果会保留')
  } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法取消实验' }
  finally { busy.value = false }
}

async function retry() {
  if (!task.value) return
  busy.value = true; error.value = null
  try {
    task.value = await api.retryExperiment(task.value.id)
    // 重试会把任务放回队列，重新订阅以接上新一轮的进度。
    events.resubscribe()
    ElMessage.success('正在补齐未完成的任务')
  } catch (reason) { error.value = reason instanceof ApiClientError ? reason.message : '无法重试实验' }
  finally { busy.value = false }
}

function toggle(list: string[], value: string) {
  const index = list.indexOf(value)
  if (index >= 0) list.splice(index, 1); else list.push(value)
  preview.value = null
}

const percent = (value: number) => `${(value * 100).toFixed(2)}%`
onMounted(async () => {
  const id = typeof route.query.task === 'string' ? route.query.task : null
  if (!id) return
  busy.value = true
  try {
    task.value = await api.experiment(id)
    if (!isTerminal(task.value)) events.subscribe(id)
  } catch (reason) {
    error.value = reason instanceof ApiClientError ? reason.message : '无法恢复当前实验'
  } finally {
    busy.value = false
  }
})
onBeforeUnmount(() => events.dispose())
</script>

<template>
  <div class="experiments-page">
    <header class="archive-hero"><div><p class="section-kicker">阶段 5 · 公平比较</p><h2>对比实验矩阵</h2></div><p>先预览组合规模，再以统一预算运行。进度通过 SSE 实时返回，断线会自动按事件序号续传。</p></header>
    <StatePanel v-if="error" tone="error" title="实验请求失败" :description="error" />
    <div class="experiment-builder">
      <section class="matrix-config">
        <label class="matrix-name"><span>实验名称</span><input v-model="form.name" maxlength="80" /></label>
        <div class="matrix-block"><header><strong>01 · 基准实例</strong><span>已选 {{ form.instanceIds.length }}</span></header><div class="choice-grid instances"><button v-for="item in catalog.instances" :key="item.id" type="button" :aria-pressed="form.instanceIds.includes(item.id)" :class="{ selected: form.instanceIds.includes(item.id) }" @click="toggle(form.instanceIds, item.id)"><strong>{{ item.name }}</strong><small>{{ item.itemCount }} 件 · {{ item.binWidth }}×{{ item.binHeight }}</small></button></div></div>
        <div class="matrix-block"><header><strong>02 · 搜索优化器</strong><span>已选 {{ form.searchAlgorithms.length }}</span></header><div class="choice-grid"><button v-for="item in catalog.searchAlgorithms" :key="item.machineValue" type="button" :aria-pressed="form.searchAlgorithms.includes(item.machineValue)" :class="{ selected: form.searchAlgorithms.includes(item.machineValue) }" @click="toggle(form.searchAlgorithms, item.machineValue)"><strong>{{ item.chineseName }}</strong><small>{{ item.machineValue }}</small></button></div></div>
        <div class="matrix-block"><header><strong>03 · 排样解码器</strong><span>已选 {{ form.decoders.length }}</span></header><div class="choice-grid"><button v-for="item in catalog.decoders" :key="item.machineValue" type="button" :aria-pressed="form.decoders.includes(item.machineValue)" :class="{ selected: form.decoders.includes(item.machineValue) }" @click="toggle(form.decoders, item.machineValue)"><strong>{{ item.chineseName }}</strong><small>{{ item.machineValue }}</small></button></div></div>
      </section>
      <aside class="experiment-sidebar">
        <h3>运行协议</h3>
        <label class="plain-field"><span>随机种子（逗号分隔）</span><input v-model="form.seedsText" /></label>
        <div class="budget-tabs" role="radiogroup" aria-label="预算模式"><button type="button" role="radio" :aria-checked="form.budgetMode === 'EVALUATIONS'" :class="{ active: form.budgetMode === 'EVALUATIONS' }" @click="form.budgetMode = 'EVALUATIONS'">评价次数</button><button type="button" role="radio" :aria-checked="form.budgetMode === 'TIME_MS'" :class="{ active: form.budgetMode === 'TIME_MS' }" @click="form.budgetMode = 'TIME_MS'">时间预算</button></div>
        <label v-if="form.budgetMode === 'EVALUATIONS'" class="plain-field"><span>每任务评价次数</span><input v-model.number="form.maxEvaluations" type="number" min="1" max="5000000" /></label>
        <label v-else class="plain-field"><span>每任务毫秒数</span><input v-model.number="form.maxMilliseconds" type="number" min="1" max="300000" /></label>
        <label class="plain-field"><span>并行度</span><input v-model.number="form.parallelism" type="number" min="1" max="8" :disabled="form.budgetMode === 'TIME_MS'" /></label>
        <label class="check-line"><input v-model="form.rotationAllowed" type="checkbox" /> 允许矩形旋转 90°</label>
        <div class="task-equation"><span>{{ form.instanceIds.length }} 实例 × {{ form.searchAlgorithms.length }} 搜索器 × {{ form.decoders.length }} 解码器 × {{ seeds.length }} 种子</span><strong>{{ localTaskCount }} 个任务</strong></div>
        <div v-if="preview" class="preview-result"><strong>后端校验通过</strong><span>{{ preview.estimatedWork }}</span><p v-for="warning in preview.warnings" :key="warning">{{ warning }}</p></div>
        <div class="experiment-actions"><button type="button" :disabled="!valid || busy" @click="loadPreview">预览规模</button><button type="button" class="primary" :disabled="!valid || busy" @click="start">{{ busy ? '处理中' : '启动实验' }}</button></div>
      </aside>
    </div>
    <section v-if="task" class="live-task">
      <header><div><p class="section-kicker">实时任务</p><h3>{{ task.name }}</h3></div><span :class="['task-status', task.status.toLowerCase()]">{{ experimentStatusLabel(task.status) }}</span></header>
      <div class="progress-track" role="progressbar" aria-label="实验进度" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="task.progressPercent"><i :style="{ width: `${task.progressPercent}%` }"></i></div>
      <div class="progress-facts"><strong>{{ task.completedTasks }} / {{ task.totalTasks }}</strong><span>{{ task.progressPercent.toFixed(1) }}%</span><span>{{ task.currentTask ?? '等待调度' }}</span></div>
      <div v-if="task.completedTasks" class="live-metrics"><div><strong>{{ task.legalResults }}</strong><span>合法结果</span></div><div><strong>{{ percent(task.averageUtilization) }}</strong><span>平均利用率</span></div><div><strong>{{ task.bestUtilization === null ? '—' : percent(task.bestUtilization) }}</strong><span>最佳利用率</span></div></div>
      <!-- 只有 FAILED 才是「失败」；已取消的任务也可能带说明（例如服务重启），
           把它渲染成红色失败面板会让用户以为实验算错了。 -->
      <StatePanel v-if="task.status === 'FAILED'" tone="error" title="实验执行失败"
        :description="task.error || '部分任务执行失败，可重试补齐。'" />
      <StatePanel v-else-if="task.status === 'CANCELLED'" title="实验已取消"
        :description="task.error || '已算完的结果都已保留。若想补齐剩余组合，点击「重试未完成」即可从中断处继续。'" />
      <div class="live-actions">
        <button v-if="canCancel" type="button" :disabled="busy" @click="cancel">取消实验</button>
        <button v-if="canRetry" type="button" class="primary" :disabled="busy" @click="retry">重试未完成</button>
      </div>
      <ExperimentArtifacts :task-id="task.id" :terminal="isTerminal(task)"
        :has-results="task.completedTasks > 0" />
    </section>
  </div>
</template>
