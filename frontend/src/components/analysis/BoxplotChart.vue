<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 每个算法一行；`label` 是显示名。 */
  series: { label: string; stats: import('@/analysis/experimentAnalysis').BoxplotStats }[]
}>()

const WIDTH = 720
const ROW_HEIGHT = 42
const PADDING = { left: 132, right: 34, top: 16, bottom: 30 }

/**
 * 把利用率映射到 x 像素。
 *
 * <p>值域取所有系列的并集再留 2 个百分点的边距，而不是各自归一化——各自归一化后
 * 每行的箱体宽度看起来一样，但横轴刻度其实互不相同，是最典型的「图形撒谎」。
 */
const domain = computed(() => {
  const values = props.series.flatMap((item) => [item.stats.min, item.stats.max, ...item.stats.outliers])
    .filter((value) => Number.isFinite(value))
  if (values.length === 0) return { low: 0, high: 1 }
  const low = Math.min(...values)
  const high = Math.max(...values)
  const margin = Math.max((high - low) * 0.08, 0.01)
  return { low: low - margin, high: high + margin }
})

const height = computed(() => props.series.length * ROW_HEIGHT + PADDING.top + PADDING.bottom)
const plotWidth = WIDTH - PADDING.left - PADDING.right

const scale = (value: number) => {
  const { low, high } = domain.value
  const ratio = high - low < 1e-9 ? 0.5 : (value - low) / (high - low)
  return PADDING.left + ratio * plotWidth
}

const rows = computed(() => props.series.map((item, index) => ({
  label: item.label,
  stats: item.stats,
  center: PADDING.top + index * ROW_HEIGHT + ROW_HEIGHT / 2,
})))

/** 五条纵向刻度线：0/25/50/75/100 的利用率，用来判断箱体落点。 */
const ticks = computed(() => {
  const { low, high } = domain.value
  const result: { x: number; label: string }[] = []
  for (let fraction = 0; fraction <= 1.0001; fraction += 0.25) {
    const value = low + (high - low) * fraction
    result.push({ x: scale(value), label: (value * 100).toFixed(1) })
  }
  return result
})
</script>

<template>
  <div class="boxplot">
    <p v-if="series.length === 0" class="heatmap-empty">还没有可比较的结果。</p>
    <!-- 用 SVG 手绘而不是 echarts 的 boxplot：只需画几十个矩形与线段，
         echarts 那条线要多引入一个图表类型和一个坐标系，为一个静态图不值得。 -->
    <svg v-else :viewBox="`0 0 ${WIDTH} ${height}`" role="img"
      aria-label="各算法利用率分布的箱线图" class="boxplot-svg">
      <g class="boxplot-grid">
        <line v-for="tick in ticks" :key="tick.label" :x1="tick.x" :x2="tick.x"
          :y1="PADDING.top" :y2="height - PADDING.bottom" />
      </g>
      <g v-for="row in rows" :key="row.label" class="boxplot-row">
        <text :x="PADDING.left - 10" :y="row.center + 4" text-anchor="end">{{ row.label }}</text>
        <!-- 须：从 Q1 到最小值、从 Q3 到最大值 -->
        <line :x1="scale(row.stats.min)" :x2="scale(row.stats.q1)" :y1="row.center" :y2="row.center" class="whisker" />
        <line :x1="scale(row.stats.q3)" :x2="scale(row.stats.max)" :y1="row.center" :y2="row.center" class="whisker" />
        <line :x1="scale(row.stats.min)" :x2="scale(row.stats.min)"
          :y1="row.center - 7" :y2="row.center + 7" class="whisker" />
        <line :x1="scale(row.stats.max)" :x2="scale(row.stats.max)"
          :y1="row.center - 7" :y2="row.center + 7" class="whisker" />
        <rect :x="scale(row.stats.q1)" :y="row.center - 11"
          :width="Math.max(1, scale(row.stats.q3) - scale(row.stats.q1))" height="22" class="box">
          <title>{{ row.label }}：中位 {{ (row.stats.median * 100).toFixed(2) }}%，样本 {{ row.stats.count }}</title>
        </rect>
        <line :x1="scale(row.stats.median)" :x2="scale(row.stats.median)"
          :y1="row.center - 11" :y2="row.center + 11" class="median" />
        <circle v-for="(outlier, index) in row.stats.outliers" :key="index"
          :cx="scale(outlier)" :cy="row.center" r="2.6" class="outlier" />
      </g>
      <g class="boxplot-axis">
        <text v-for="tick in ticks" :key="tick.label" :x="tick.x" :y="height - 10" text-anchor="middle">
          {{ tick.label }}%
        </text>
      </g>
    </svg>
  </div>
</template>
