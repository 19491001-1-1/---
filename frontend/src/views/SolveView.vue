<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { useRoute } from 'vue-router'
import { api, ApiClientError } from '@/api/client'
import PackingCanvas from '@/components/solve/PackingCanvas.vue'
import StatePanel from '@/components/shared/StatePanel.vue'
import PageGuide from '@/components/shared/PageGuide.vue'
import { useSolveForm } from '@/composables/useSolveForm'
import { useCatalogStore } from '@/stores/catalog'
import type { PackingInstanceDetail, Placement, SolveResult } from '@/types/api'

const catalog = useCatalogStore()
const route = useRoute()
const ConvergenceChart = defineAsyncComponent(() => import('@/components/solve/ConvergenceChart.vue'))
const { form, valid, reset, buildRequest } = useSolveForm()
const running = ref(false)
const result = ref<SolveResult | null>(null)
const instanceDetail = ref<PackingInstanceDetail | null>(null)
const selectedId = ref<number | null>(null)
const requestError = ref<string | null>(null)
const advancedOpen = ref(false)

const selectedSummary = computed(() => catalog.instances.find((item) => item.id === form.instanceId) ?? null)
const selectedAlgorithm = computed(() => catalog.searchAlgorithms.find((item) => item.machineValue === form.searchAlgorithm) ?? null)
const selectedDecoder = computed(() => catalog.decoders.find((item) => item.machineValue === form.decoder) ?? null)
const selectedPlacement = computed<Placement | null>(() => result.value?.placements.find((item) => item.itemId === selectedId.value) ?? null)
const statisticEntries = computed(() => Object.entries(result.value?.algorithmStatistics ?? {}))

watch(
  () => catalog.instances,
  (instances) => {
    if (!form.instanceId && instances.length > 0) form.instanceId = instances[0].id
  },
  { immediate: true },
)

watch(
  [() => catalog.searchAlgorithms, () => catalog.decoders, () => route.query],
  ([searchAlgorithms, decoders, query]) => {
    const search = typeof query.search === 'string' ? query.search : null
    const decoder = typeof query.decoder === 'string' ? query.decoder : null
    if (search && searchAlgorithms.some((item) => item.machineValue === search)) form.searchAlgorithm = search
    if (decoder && decoders.some((item) => item.machineValue === decoder)) form.decoder = decoder
  },
  { immediate: true },
)

watch(() => form.instanceId, async (id) => {
  instanceDetail.value = null
  result.value = null
  selectedId.value = null
  if (!id) return
  try { instanceDetail.value = await api.instance(id) } catch { /* catalog error is shown at page level */ }
}, { immediate: true })

async function runSolve() {
  if (!valid.value || running.value) return
  running.value = true
  requestError.value = null
  selectedId.value = null
  try {
    result.value = await api.solve(buildRequest())
    ElMessage.success(result.value.legal ? '求解完成，布局已通过独立验证' : '求解完成，但布局未通过验证')
  } catch (error: unknown) {
    requestError.value = error instanceof ApiClientError ? error.message : '求解过程发生未知错误'
  } finally {
    running.value = false
  }
}

function resetForm() {
  reset(catalog.instances[0]?.id ?? '')
  result.value = null
  selectedId.value = null
  requestError.value = null
}

const percent = (value: number) => `${(value * 100).toFixed(2)}%`
const number = (value: number) => value.toLocaleString('zh-CN')
const solveGuide = [
  {title:'四项配置分别决定什么',summary:'实例定义题目，搜索器负责探索，解码器负责落位，预算决定搜索深度。',body:'问题实例决定板材和物品；搜索优化器产生候选顺序；排样解码器按顺序构造几何布局；预算限制候选评价数量或运行时间。随机种子只影响随机搜索过程，固定种子可重复同一路径。',points:['旋转开关会改变合法候选空间','搜索器和解码器可以独立组合','评价次数与时间预算不能同时启用','更大预算通常更充分，但不保证严格更优']},
  {title:'运行后应该看哪里',summary:'先看合法性和利用率，再看布局细节、收敛与专有统计。',body:'合法布局是结果成立的前提。利用率说明板材面积利用程度；装入面积是其绝对量；评价次数和耗时说明计算成本；终止原因解释搜索为何结束。点击画布矩形可核对身份、坐标、尺寸和旋转状态。',points:['绿色合法标记来自后端独立验证','使用高度不等同于利用率','收敛曲线记录历史最好面积','缓存命中率只反映重复候选利用情况']},
  {title:'什么时候改用对比实验',summary:'单次求解适合理解，不适合单独支撑算法优劣结论。',body:'如果要判断哪个搜索器或解码器更好，应把多个组合放进同一实验，用相同实例、预算、旋转权限和种子运行。随机算法至少使用多个种子，随后在分析页比较分布而不是只挑最好的一次。',points:['探索布局：使用本页','比较组合：使用对比实验','复查原始格子：使用实验记录','形成统计结论：使用结果分析']},
]
</script>

<template>
  <div class="solve-page">
    <div class="solve-intro">
      <div><p class="section-kicker">独立选择 · 同一验证</p><h2>组合一次可复现的排样求解</h2></div>
      <p><strong>搜索器</strong>寻找顺序，<strong>解码器</strong>生成布局。两者独立选择，结果由后端统一复核。</p>
    </div>
    <PageGuide label="求解导读" title="配置、运行、验证、解释" introduction="求解区保持专注；完整口径放在可展开说明中，需要时再阅读。" :steps="['选择实例','组合搜索与解码','设定预算和种子','复核布局与指标']" :sections="solveGuide" />

    <StatePanel v-if="catalog.error" tone="error" title="无法加载求解配置" :description="catalog.error" action-label="重试" @action="catalog.load(true)" />
    <StatePanel v-if="requestError" tone="error" title="求解请求失败" :description="requestError" action-label="重试" @action="runSolve" />

    <div class="solve-workbench">
      <aside class="config-pane">
        <section class="config-section">
          <div class="section-number">01</div>
          <div class="config-content">
            <label class="field-label" for="instance-select">问题实例</label>
            <el-select id="instance-select" v-model="form.instanceId" class="full-control" :loading="catalog.loading" placeholder="选择 GCUT 实例">
              <el-option v-for="item in catalog.instances" :key="item.id" :label="`${item.name} · ${item.itemCount} 件`" :value="item.id" />
            </el-select>
            <dl v-if="selectedSummary" class="instance-facts">
              <div><dt>矩形数量</dt><dd>{{ selectedSummary.itemCount }} 件</dd></div>
              <div><dt>容器尺寸</dt><dd>{{ selectedSummary.binWidth }} × {{ selectedSummary.binHeight }}</dd></div>
              <div><dt>矩形总面积</dt><dd>{{ number(selectedSummary.totalItemArea) }}</dd></div>
              <div><dt>数据来源</dt><dd>{{ selectedSummary.source }}</dd></div>
            </dl>
          </div>
        </section>

        <section class="config-section">
          <div class="section-number">02</div>
          <div class="config-content">
            <label class="field-label" for="search-select">搜索优化器</label>
            <el-select id="search-select" v-model="form.searchAlgorithm" class="full-control">
              <el-option v-for="item in catalog.searchAlgorithms" :key="item.machineValue" :label="`${item.chineseName} · ${item.englishName}`" :value="item.machineValue" />
            </el-select>
            <div v-if="selectedAlgorithm" class="selection-note">
              <span :class="['type-dot', { stochastic: selectedAlgorithm.stochastic }]"></span>
              <p>{{ selectedAlgorithm.description }}<small>{{ selectedAlgorithm.stochastic ? '随机搜索 · 固定种子可复现' : '确定性算法' }}</small></p>
            </div>
            <p v-if="form.searchAlgorithm === 'ADAPTIVE_GA'" class="selection-note" data-testid="adaptive-mutation-rule">
              变异概率依据变异前子代自身的装入面积计算，参照当前父代种群的平均面积和最大面积。预评分未命中缓存计入评价预算；子代改变后需重新评价。
            </p>
          </div>
        </section>

        <section class="config-section">
          <div class="section-number">03</div>
          <div class="config-content">
            <label class="field-label" for="decoder-select">排样解码器</label>
            <el-select id="decoder-select" v-model="form.decoder" class="full-control">
              <el-option v-for="item in catalog.decoders" :key="item.machineValue" :label="`${item.chineseName} · ${item.englishName}`" :value="item.machineValue" />
            </el-select>
            <div v-if="selectedDecoder" class="selection-note decoder-note">
              <span class="structure-mark" aria-hidden="true"></span>
              <p>{{ selectedDecoder.description }}<small>空间结构：{{ selectedDecoder.spaceStructure }}</small></p>
            </div>
          </div>
        </section>

        <section class="config-section budget-section">
          <div class="section-number">04</div>
          <div class="config-content">
            <span class="field-label">运行参数</span>
            <div class="budget-tabs" role="radiogroup" aria-label="预算模式">
              <button type="button" role="radio" :aria-checked="form.budgetMode === 'EVALUATIONS'" :class="{ active: form.budgetMode === 'EVALUATIONS' }" @click="form.budgetMode = 'EVALUATIONS'">评价次数</button>
              <button type="button" role="radio" :aria-checked="form.budgetMode === 'TIME_MS'" :class="{ active: form.budgetMode === 'TIME_MS' }" @click="form.budgetMode = 'TIME_MS'">时间预算</button>
            </div>
            <el-input-number v-if="form.budgetMode === 'EVALUATIONS'" v-model="form.maxEvaluations" :min="1" :max="5000000" :step="1000" controls-position="right" class="full-control" aria-label="评价次数" />
            <el-input-number v-else v-model="form.maxMilliseconds" :min="1" :max="300000" :step="500" controls-position="right" class="full-control" aria-label="时间预算，毫秒" />
            <div class="two-fields">
              <label><span>随机种子</span><el-input-number v-model="form.seed" :min="0" :max="2147483647" :controls="false" /></label>
              <label class="switch-field"><span>允许旋转</span><el-switch v-model="form.rotationAllowed" /></label>
            </div>
            <button type="button" class="advanced-toggle" @click="advancedOpen = !advancedOpen">
              <span>高级设置</span><span>{{ advancedOpen ? '−' : '+' }}</span>
            </button>
            <div v-if="advancedOpen" class="advanced-note">
              当前缓存策略由后端 `PACKING_CACHE_CAPACITY` 控制。缓存对高重复度搜索更有效，不暗示对所有算法均有加速。
            </div>
          </div>
        </section>

        <div class="config-actions">
          <button class="run-button" type="button" :disabled="!valid || running" @click="runSolve">
            <span v-if="running" class="button-spinner"></span>{{ running ? '正在求解' : '开始求解' }}
          </button>
          <button class="reset-button" type="button" :disabled="running" @click="resetForm">恢复默认</button>
        </div>
      </aside>

      <PackingCanvas
        class="layout-pane"
        :placements="result?.placements ?? []"
        :bin-width="instanceDetail?.binWidth ?? selectedSummary?.binWidth ?? 1"
        :bin-height="instanceDetail?.binHeight ?? selectedSummary?.binHeight ?? 1"
        :used-height="result?.usedHeight ?? 0"
        :legal="result?.legal ?? true"
        :selected-id="selectedId"
        @select="selectedId = $event"
      />

      <aside class="result-pane">
        <div class="result-heading"><div><p class="section-kicker">运行结果</p><h3>求解摘要</h3></div><span v-if="result" :class="['status-pill', result.legal ? 'success' : 'danger']">{{ result.legal ? '合法布局' : '验证失败' }}</span></div>
        <div v-if="!result" class="result-empty"><span>←</span><p>完成左侧配置并运行后，这里将显示真实指标与矩形详情。</p></div>
        <template v-else>
          <div class="primary-metric"><span>容器利用率</span><strong>{{ percent(result.utilization) }}</strong><div><i :style="{ width: `${Math.min(result.utilization * 100, 100)}%` }"></i></div></div>
          <dl class="result-metrics">
            <div><dt>装入面积</dt><dd>{{ number(result.packedArea) }}</dd></div>
            <div><dt>容器面积</dt><dd>{{ number(result.binArea) }}</dd></div>
            <div><dt>使用高度</dt><dd>{{ result.usedHeight }}</dd></div>
            <div><dt>运行时间</dt><dd>{{ number(result.elapsedMilliseconds) }} ms</dd></div>
            <div><dt>评价次数</dt><dd>{{ number(result.evaluations) }}</dd></div>
            <div><dt>终止原因</dt><dd>{{ result.terminationReason }}</dd></div>
          </dl>
          <div class="cache-row" v-if="result.cacheQueries !== null"><span>缓存命中</span><strong>{{ number(result.cacheHits ?? 0) }} / {{ number(result.cacheQueries) }}</strong><small>{{ (result.cacheHitRatePercent ?? 0).toFixed(2) }}%</small></div>

          <section class="selection-detail">
            <header><h4>矩形详情</h4><span>{{ selectedPlacement ? `#${selectedPlacement.itemId}` : '未选择' }}</span></header>
            <dl v-if="selectedPlacement">
              <div><dt>原始尺寸</dt><dd>{{ selectedPlacement.originalWidth }} × {{ selectedPlacement.originalHeight }}</dd></div>
              <div><dt>放置尺寸</dt><dd>{{ selectedPlacement.placedWidth }} × {{ selectedPlacement.placedHeight }}</dd></div>
              <div><dt>坐标</dt><dd>({{ selectedPlacement.x }}, {{ selectedPlacement.y }})</dd></div>
              <div><dt>旋转</dt><dd>{{ selectedPlacement.rotated ? '是（90°）' : '否' }}</dd></div>
              <div><dt>面积</dt><dd>{{ number(selectedPlacement.area) }}</dd></div>
            </dl>
            <p v-else>点击画布中的矩形查看位置、尺寸与旋转状态。</p>
          </section>
        </template>
      </aside>
    </div>

    <section v-if="result" class="analysis-section">
      <header class="section-heading"><div><p class="section-kicker">搜索过程</p><h3>收敛轨迹与专有统计</h3></div><span>{{ result.searchAlgorithm }} × {{ result.decoder }}</span></header>
      <div class="analysis-grid">
        <ConvergenceChart :points="result.convergence" />
        <div class="statistics-table">
          <div v-for="([key, value]) in statisticEntries" :key="key"><span>{{ key }}</span><strong>{{ typeof value === 'number' ? number(value) : String(value) }}</strong></div>
          <div v-if="statisticEntries.length === 0" class="no-statistics">当前算法没有额外专有统计。</div>
        </div>
      </div>
    </section>
  </div>
</template>
