<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { Placement } from '@/types/api'

const props = defineProps<{
  placements: Placement[]
  binWidth: number
  binHeight: number
  usedHeight: number
  legal: boolean
  selectedId: number | null
}>()

const emit = defineEmits<{ select: [itemId: number | null] }>()

const svg = ref<SVGSVGElement | null>(null)
const showIds = ref(true)
const showSizes = ref(false)
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const dragging = ref(false)
const dragOrigin = ref({ x: 0, y: 0, panX: 0, panY: 0 })

const palette = ['#4B7BEC', '#00A6A6', '#F08C46', '#6C63B7', '#27AE60', '#D95F76', '#277DA1', '#B7791F']
const colorFor = (id: number) => palette[Math.abs(id * 37) % palette.length]
const viewBox = computed(() => {
  const width = Math.max(props.binWidth / zoom.value, 1)
  const height = Math.max(props.binHeight / zoom.value, 1)
  return `${panX.value} ${panY.value} ${width} ${height}`
})

function fit() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}

function zoomBy(factor: number) {
  const next = Math.min(8, Math.max(0.7, zoom.value * factor))
  const oldWidth = props.binWidth / zoom.value
  const oldHeight = props.binHeight / zoom.value
  const newWidth = props.binWidth / next
  const newHeight = props.binHeight / next
  panX.value += (oldWidth - newWidth) / 2
  panY.value += (oldHeight - newHeight) / 2
  zoom.value = next
  clampPan()
}

function clampPan() {
  const width = props.binWidth / zoom.value
  const height = props.binHeight / zoom.value
  panX.value = Math.min(props.binWidth - width, Math.max(0, panX.value))
  panY.value = Math.min(props.binHeight - height, Math.max(0, panY.value))
}

function onWheel(event: WheelEvent) {
  event.preventDefault()
  zoomBy(event.deltaY < 0 ? 1.15 : 1 / 1.15)
}

function startDrag(event: PointerEvent) {
  if ((event.target as Element).closest('.packing-item')) return
  dragging.value = true
  dragOrigin.value = { x: event.clientX, y: event.clientY, panX: panX.value, panY: panY.value }
  svg.value?.setPointerCapture(event.pointerId)
}

function drag(event: PointerEvent) {
  if (!dragging.value || !svg.value) return
  const rect = svg.value.getBoundingClientRect()
  panX.value = dragOrigin.value.panX - (event.clientX - dragOrigin.value.x) * (props.binWidth / zoom.value) / rect.width
  panY.value = dragOrigin.value.panY - (event.clientY - dragOrigin.value.y) * (props.binHeight / zoom.value) / rect.height
  clampPan()
}

function stopDrag() {
  dragging.value = false
}

function exportSvg() {
  if (!svg.value) return
  const clone = svg.value.cloneNode(true) as SVGSVGElement
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  clone.setAttribute('viewBox', `0 0 ${props.binWidth} ${props.binHeight}`)
  const blob = new Blob([new XMLSerializer().serializeToString(clone)], { type: 'image/svg+xml' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `packing-${Date.now()}.svg`
  link.click()
  URL.revokeObjectURL(url)
}

watch(() => [props.binWidth, props.binHeight, props.placements.length], () => nextTick(fit))

defineExpose({ fit, zoomBy })
</script>

<template>
  <section class="canvas-panel" aria-label="排样布局画布">
    <header class="canvas-toolbar">
      <div class="tool-group">
        <button type="button" title="缩小" aria-label="缩小" @click="zoomBy(1 / 1.2)">−</button>
        <span>{{ Math.round(zoom * 100) }}%</span>
        <button type="button" title="放大" aria-label="放大" @click="zoomBy(1.2)">+</button>
        <button type="button" @click="fit">适应画布</button>
      </div>
      <div class="tool-group display-tools">
        <label><input v-model="showIds" type="checkbox" /> 编号</label>
        <label><input v-model="showSizes" type="checkbox" /> 尺寸</label>
        <button type="button" :disabled="placements.length === 0" @click="exportSvg">导出 SVG</button>
      </div>
    </header>

    <div v-if="placements.length === 0" class="canvas-empty">
      <div class="empty-blueprint" aria-hidden="true"><span></span><span></span><span></span></div>
      <strong>布局等待求解</strong>
      <p>选择实例、搜索器和解码器后开始运行</p>
    </div>
    <svg
      v-else
      ref="svg"
      class="packing-svg"
      :class="{ dragging }"
      :viewBox="viewBox"
      role="img"
      :aria-label="`排样布局，${placements.length} 个矩形，利用高度 ${usedHeight}`"
      @wheel="onWheel"
      @pointerdown="startDrag"
      @pointermove="drag"
      @pointerup="stopDrag"
      @pointercancel="stopDrag"
      @click.self="emit('select', null)"
    >
      <rect class="bin-background" width="100%" height="100%" />
      <g :transform="`translate(0 ${binHeight}) scale(1 -1)`">
        <rect v-if="usedHeight > 0" class="used-height-band" :width="binWidth" :height="usedHeight" />
        <g
          v-for="placement in placements"
          :key="placement.itemId"
          class="packing-item"
          :class="{ selected: selectedId === placement.itemId, invalid: !legal }"
          tabindex="0"
          role="button"
          :aria-label="`物品 ${placement.itemId}，${placement.placedWidth} 乘 ${placement.placedHeight}，坐标 ${placement.x}, ${placement.y}${placement.rotated ? '，已旋转' : ''}`"
          @click.stop="emit('select', placement.itemId)"
          @keydown.enter="emit('select', placement.itemId)"
        >
          <title>物品 {{ placement.itemId }}｜原始 {{ placement.originalWidth }}×{{ placement.originalHeight }}｜放置 {{ placement.placedWidth }}×{{ placement.placedHeight }}｜坐标 ({{ placement.x }}, {{ placement.y }})｜{{ placement.rotated ? '已旋转' : '未旋转' }}</title>
          <rect
            :x="placement.x"
            :y="placement.y"
            :width="placement.placedWidth"
            :height="placement.placedHeight"
            :fill="colorFor(placement.itemId)"
          />
          <g :transform="`translate(${placement.x + placement.placedWidth / 2} ${placement.y + placement.placedHeight / 2}) scale(1 -1)`">
            <text v-if="showIds && placement.placedWidth > binWidth * 0.045 && placement.placedHeight > binHeight * 0.035" class="item-id">{{ placement.itemId }}</text>
            <text v-if="showSizes && placement.placedWidth > binWidth * 0.09 && placement.placedHeight > binHeight * 0.075" class="item-size" y="12">{{ placement.placedWidth }}×{{ placement.placedHeight }}</text>
            <text v-if="placement.rotated && placement.placedWidth > binWidth * 0.055" class="rotation-mark" :x="placement.placedWidth / 2 - 10" :y="-placement.placedHeight / 2 + 12">↻</text>
          </g>
        </g>
      </g>
      <rect class="bin-border" width="100%" height="100%" />
    </svg>
    <footer class="canvas-legend">
      <span><i class="legend-box placed"></i>已放置 {{ placements.length }} 件</span>
      <span><i class="legend-box blank"></i>空白区域</span>
      <span v-if="!legal"><i class="legend-box illegal"></i>布局非法</span>
      <span class="canvas-hint">滚轮缩放 · 拖拽平移 · 点击查看</span>
    </footer>
  </section>
</template>
