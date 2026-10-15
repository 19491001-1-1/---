<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { SearchPoint } from '@/types/api'

const props = defineProps<{ points: SearchPoint[] }>()
const host = ref<HTMLDivElement | null>(null)
use([LineChart, GridComponent, TooltipComponent, SVGRenderer])

let chart: ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  if (!host.value) return
  chart ??= init(host.value, undefined, { renderer: 'svg' })
  chart.setOption({
    animationDuration: 350,
    grid: { left: 56, right: 22, top: 22, bottom: 42 },
    tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => Number(value).toLocaleString('zh-CN') },
    xAxis: { type: 'value', name: '评价次数', nameLocation: 'middle', nameGap: 28, axisLine: { lineStyle: { color: '#9aa7b8' } } },
    yAxis: { type: 'value', name: '历史最优面积', axisLine: { show: false }, splitLine: { lineStyle: { color: '#e8edf3' } } },
    series: [{
      type: 'line',
      name: '历史最优',
      showSymbol: props.points.length < 20,
      symbolSize: 6,
      step: 'end',
      data: props.points.map((point) => [point.evaluations, point.bestPackedArea]),
      lineStyle: { color: '#1769aa', width: 2 },
      itemStyle: { color: '#f08c46' },
      areaStyle: { color: 'rgba(23, 105, 170, 0.08)' },
    }],
  })
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  if (host.value) observer.observe(host.value)
})
watch(() => props.points, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <div ref="host" class="convergence-chart" role="img" aria-label="历史最优面积随评价次数的收敛曲线"></div>
</template>
