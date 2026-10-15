<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ConvergenceSeries } from '@/analysis/experimentAnalysis'

const props = defineProps<{
  series: ConvergenceSeries[]
  /** 实例的板材面积，用来把「装入面积」换算成可比的利用率百分比。 */
  binArea: number
}>()

const host = ref<HTMLDivElement | null>(null)
use([LineChart, GridComponent, TooltipComponent, LegendComponent, SVGRenderer])

let chart: ECharts | null = null
let observer: ResizeObserver | null = null

/**
 * 调色板按固定顺序取，不按系列当前顺序取。
 *
 * <p>这样「取消勾选某个算法」或「某个算法算完了」导致系列增减时，剩下几条线的颜色不会
 * 整体挪位——颜色是读者用来记住「哪条是哪条」的唯一线索，一变就得重新认一遍。
 */
const PALETTE = ['#1769aa', '#eb8441', '#288a63', '#c54b50', '#7c5cbf', '#0f8b8d', '#b8860b', '#a03d7a']
const colorOf = (algorithm: string) => {
  let hash = 0
  for (let index = 0; index < algorithm.length; index++) {
    hash = (hash * 31 + algorithm.charCodeAt(index)) >>> 0
  }
  return PALETTE[hash % PALETTE.length]
}

/** 装入面积 → 利用率百分比。板材面积缺失时退回原始面积，至少曲线形状是对的。 */
const toPercent = (packedArea: number) =>
  props.binArea > 0 ? (packedArea / props.binArea) * 100 : packedArea

function render() {
  if (!host.value) return
  chart ??= init(host.value, undefined, { renderer: 'svg' })
  chart.setOption({
    animationDuration: 350,
    grid: { left: 62, right: 22, top: 42, bottom: 46 },
    legend: { top: 6, type: 'scroll', textStyle: { fontSize: 11 } },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => `${Number(value).toFixed(2)}%`,
    },
    xAxis: {
      type: 'value', name: '评价次数', nameLocation: 'middle', nameGap: 28,
      axisLine: { lineStyle: { color: '#9aa7b8' } },
    },
    yAxis: {
      type: 'value', name: '历史最优利用率', nameLocation: 'middle', nameGap: 40,
      scale: true, axisLabel: { formatter: '{value}%' },
      axisLine: { show: false }, splitLine: { lineStyle: { color: '#e8edf3' } },
    },
    series: props.series.map((item) => ({
      type: 'line',
      name: item.algorithm,
      // 收敛曲线是「保持最优直到被超越」，必须用阶梯而不是折线：
      // 折线会在两次改进之间画出一条并不存在的「逐渐变好」。
      step: 'end',
      showSymbol: false,
      data: item.points.map((point) => [point.evaluations, toPercent(point.bestPackedArea)]),
      lineStyle: { color: colorOf(item.algorithm), width: 2 },
      itemStyle: { color: colorOf(item.algorithm) },
    })),
  }, { notMerge: true })
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  if (host.value) observer.observe(host.value)
})
watch(() => [props.series, props.binArea], render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <div class="convergence-compare">
    <p v-if="series.length === 0" class="heatmap-empty">这个实例还没有任何收敛轨迹。</p>
    <template v-else>
      <div ref="host" class="convergence-canvas" role="img"
        aria-label="各算法在同一实例上的历史最优利用率收敛曲线对比"></div>
      <!-- 抽样过就必须说：否则用户会以为算法真的只迭代了图上那么少的次数。 -->
      <p v-if="series.some((item) => item.truncated)" class="heatmap-legend">
        标记「抽样」的曲线点数超过上限，已等距抽样（末点保留），形状与终点不变。
      </p>
    </template>
  </div>
</template>
