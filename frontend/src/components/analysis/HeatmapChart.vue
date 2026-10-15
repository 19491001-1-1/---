<script setup lang="ts">
import { computed } from 'vue'
import type { Heatmap } from '@/analysis/experimentAnalysis'

const props = defineProps<{
  heatmap: Heatmap
  xLabel: string
  yLabel: string
  /** 单元格点击回传行列名，父组件据此联动其他视图。 */
  selected?: string | null
}>()

defineEmits<{ pick: [x: string, y: string] }>()

/** 用一个连续色阶把利用率映射到颜色：低=浅、高=深蓝。 */
function color(value: number | null): string {
  if (value === null) return 'transparent'
  const span = props.heatmap.max - props.heatmap.min
  // 所有格子利用率相同时 span 为 0，此时一律取中间色，避免除零后全糊成一种颜色。
  const ratio = span < 1e-9 ? 0.5 : (value - props.heatmap.min) / span
  const lightness = 96 - ratio * 52
  return `hsl(207 62% ${lightness}%)`
}

const textTone = (value: number | null) => {
  if (value === null) return 'empty'
  const span = props.heatmap.max - props.heatmap.min
  const ratio = span < 1e-9 ? 0.5 : (value - props.heatmap.min) / span
  // 底色越深越要用白字：这是唯一能保证任何色阶下都读得清的判据。
  return ratio > 0.62 ? 'light' : 'dark'
}

const percent = (value: number | null) => value === null ? '—' : (value * 100).toFixed(1)

const hasData = computed(() => !props.heatmap.empty)
</script>

<template>
  <div class="heatmap">
    <p v-if="!hasData" class="heatmap-empty">还没有可比较的结果。</p>
    <div v-else class="heatmap-scroll">
      <table>
        <thead>
          <tr>
            <th class="corner"><span>{{ yLabel }} \ {{ xLabel }}</span></th>
            <th v-for="column in heatmap.xs" :key="column" scope="col">{{ column }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in heatmap.cells" :key="heatmap.ys[rowIndex]">
            <th scope="row">{{ heatmap.ys[rowIndex] }}</th>
            <td v-for="(cell, columnIndex) in row" :key="columnIndex">
              <button
                type="button"
                class="heat-cell"
                :class="[textTone(cell.meanUtilization), { active: selected === `${cell.y}|${cell.x}` }]"
                :style="{ background: color(cell.meanUtilization) }"
                :title="cell.count === 0 ? '这一格还没有结果'
                  : `${cell.y} × ${cell.x}：合法 ${cell.validCount}/${cell.count}，均值 ${percent(cell.meanUtilization)}%，最好 ${percent(cell.bestUtilization)}%`"
                @click="$emit('pick', cell.x, cell.y)"
              >
                <span>{{ percent(cell.meanUtilization) }}</span>
                <!-- 有失败但不非法的格子单独标出来：均值只算合法结果，不标会让「4/5 合法」
                     看起来和「5/5 全合法」一模一样。 -->
                <small v-if="cell.count > 0 && cell.validCount < cell.count">!</small>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="hasData" class="heatmap-legend">
      色阶：{{ percent(heatmap.min) }}% – {{ percent(heatmap.max) }}%（利用率，越高越深）；
      「—」表示该组合还没有结果；「!」表示有非法结果。
    </p>
  </div>
</template>
