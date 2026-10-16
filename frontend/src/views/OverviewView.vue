<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import StatePanel from '@/components/shared/StatePanel.vue'
import PageGuide from '@/components/shared/PageGuide.vue'
import { experimentStatusLabel } from '@/experimentStatus'
import { useCatalogStore } from '@/stores/catalog'
import type { ExperimentTask } from '@/types/api'

const catalog = useCatalogStore()
const recentTasks = ref<ExperimentTask[]>([])
const recentLoading = ref(true)
const metrics = computed(() => [
  { value: catalog.meta?.productSearchAlgorithms ?? '—', label: '产品搜索器', note: '优化物品顺序' },
  { value: catalog.meta?.productDecoders ?? '—', label: '产品解码器', note: '构造合法布局' },
  { value: catalog.meta?.productCombinations ?? '—', label: '自由组合', note: '搜索 × 解码' },
  { value: catalog.meta?.implementedAlgorithmEntries ?? '—', label: '历史入口', note: '可回归、可复现' },
])

onMounted(async () => {
  try {
    recentTasks.value = (await api.experiments()).slice(0, 5)
  } catch {
    // 概览的实验摘要是辅助信息，目录加载错误仍由页面顶部统一呈现。
  } finally {
    recentLoading.value = false
  }
})

const date = (value: string) => new Date(value).toLocaleString('zh-CN')
const percent = (value: number | null) => value === null ? '—' : `${(value * 100).toFixed(2)}%`
const overviewGuide = [
  { title:'这个平台解决什么', summary:'二维矩形选择与排布，不只是把所有物品硬塞进去。', body:'平台面对一张固定尺寸板材与一组候选矩形，在合法约束内尽量装入更大的总面积。搜索器探索物品优先级，解码器把优先级变成坐标；两层解耦后，算法差异才可以被公平观察。', points:['每件物品最多使用一次','只在允许时旋转 90°','结果必须不越界、不重叠','利用率是装入面积除以板材面积'] },
  { title:'五个页面怎样配合', summary:'求解用于观察，实验用于比较，记录与分析用于形成证据。', body:'先在单次求解里理解一个组合，再在对比实验中固定预算、扩展到多个实例和种子。后台任务进入实验记录，完成的逐格数据在结果分析中汇总成热力图、排名、分布和收敛曲线。', points:['算法档案解释方法思想','项目说明解释工程边界','实验记录跨服务重启保留','终态实验可以生成 CSV / SVG / HTML'] },
  { title:'怎样读懂结果', summary:'一次最好不代表稳定最好，耗时也必须放在同一环境比较。', body:'启发式算法不证明全局最优。严谨结论应固定版本、实例、旋转权限和预算，使用多个随机种子，并同时观察平均利用率、离散程度、合法结果比例和收敛速度。', points:['评价次数预算更利于算法公平比较','时间预算受机器负载影响','随机算法应观察多种子分布','所有布局都由独立验证器复核'] },
]
</script>

<template>
  <div class="overview-page">
    <section class="overview-hero">
      <div class="hero-copy">
        <p class="section-kicker">二维矩形排样 · 可复现实验</p>
        <h2>把搜索策略与空间构造<br /><span>拆开来，公平地比较。</span></h2>
        <p class="hero-description">
          搜索器负责寻找更好的物品顺序，解码器负责把顺序转换为合法布局。
          同一预算、同一种子、同一验证口径，让每次结果都站得住。
        </p>
        <div class="hero-actions">
          <RouterLink to="/solve" class="primary-link">开始单次求解 <span>→</span></RouterLink>
          <RouterLink to="/experiments" class="secondary-link">创建对比实验</RouterLink>
        </div>
      </div>
      <div class="hero-diagram" aria-label="排样求解流程">
        <div class="diagram-node source"><span>01</span><strong>问题实例</strong><small>矩形集合与容器</small></div>
        <div class="diagram-arrow">→</div>
        <div class="diagram-stack">
          <div class="diagram-node search"><span>02</span><strong>搜索优化器</strong><small>改进优先级顺序</small></div>
          <div class="diagram-node decoder"><span>03</span><strong>排样解码器</strong><small>生成坐标与旋转</small></div>
        </div>
        <div class="diagram-arrow">→</div>
        <div class="diagram-node output"><span>04</span><strong>实验结果</strong><small>布局、指标与轨迹</small></div>
      </div>
    </section>

    <StatePanel
      v-if="catalog.error"
      tone="error"
      title="后端服务暂不可用"
      :description="catalog.error"
      action-label="重新连接"
      @action="catalog.load(true)"
    />

    <section class="metric-strip" aria-label="平台能力摘要">
      <article v-for="metric in metrics" :key="metric.label" class="metric-item">
        <strong>{{ metric.value }}</strong>
        <div><span>{{ metric.label }}</span><small>{{ metric.note }}</small></div>
      </article>
    </section>
    <PageGuide label="快速入门" title="先理解工作流，再开始计算" introduction="四步建立正确使用顺序；下方三章可展开阅读完整口径。" :steps="['选择问题与算法','运行单次求解','建立公平实验','分析并归档结论']" :sections="overviewGuide" />

    <section class="overview-grid">
      <article class="capability-panel">
        <header class="section-heading">
          <div><p class="section-kicker">平台能力</p><h3>一套严谨的实验工作流</h3></div>
          <RouterLink to="/about">查看工程说明 →</RouterLink>
        </header>
        <div class="capability-list">
          <div><span>01</span><div><strong>自由组合</strong><p>6 种搜索器和 8 种解码器完全解耦，而非隐藏参数。</p></div></div>
          <div><span>02</span><div><strong>公平预算</strong><p>评价次数和墙钟时间使用互斥协议，口径不混用。</p></div></div>
          <div><span>03</span><div><strong>独立验证</strong><p>边界、重叠、旋转和物品身份由独立验证器复核。</p></div></div>
        </div>
      </article>

      <article class="recent-panel">
        <header class="section-heading"><div><p class="section-kicker">实验记录</p><h3>最近运行</h3></div><RouterLink to="/records">查看全部 →</RouterLink></header>
        <div v-if="recentLoading" class="clean-empty" aria-live="polite">
          <span aria-hidden="true">···</span><strong>正在读取实验记录</strong>
        </div>
        <div v-else-if="recentTasks.length === 0" class="clean-empty">
          <span aria-hidden="true">⊘</span>
          <strong>还没有可展示的实验</strong>
          <p>启动第一组对比实验后，这里会展示真实运行记录。</p>
          <RouterLink to="/experiments">创建对比实验</RouterLink>
        </div>
        <div v-else class="recent-task-list">
          <RouterLink v-for="task in recentTasks" :key="task.id" :to="{ path: '/records', query: { task: task.id } }">
            <span><strong>{{ task.name }}</strong><small>{{ date(task.createdAt) }}</small></span>
            <span><small>{{ task.completedTasks }}/{{ task.totalTasks }}</small><strong>{{ percent(task.bestUtilization) }}</strong></span>
            <i :class="task.status.toLowerCase()">{{ experimentStatusLabel(task.status) }}</i>
          </RouterLink>
        </div>
      </article>
    </section>
  </div>
</template>
