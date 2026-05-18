<script setup lang="ts">
import { computed, ref } from 'vue'

export interface ChartPoint {
  label: string
  value: number
}

export interface ChartSeries {
  name: string
  color: string
  points: ChartPoint[]
}

export interface ChartTooltipItem {
  name: string
  value: number
  color?: string
}

const props = withDefaults(defineProps<{
  series: ChartSeries[]
  height?: number
  tooltipDetails?: Record<string, ChartTooltipItem[]>
}>(), {
  height: 260,
  tooltipDetails: () => ({}),
})

const width = 720
const padding = { top: 22, right: 28, bottom: 34, left: 56 }
const svgRef = ref<SVGSVGElement | null>(null)
const hoverIndex = ref<number | null>(null)

const labels = computed(() => props.series[0]?.points.map((point) => point.label) || [])
const maxValue = computed(() => {
  const values = props.series.flatMap((item) => item.points.map((point) => point.value))
  return Math.max(1, ...values)
})
const plotWidth = computed(() => width - padding.left - padding.right)
const plotHeight = computed(() => props.height - padding.top - padding.bottom)
const yTicks = computed(() => [maxValue.value, Math.round(maxValue.value / 2), 0])
const visibleLabels = computed(() => {
  if (labels.value.length <= 3) return labels.value
  return [labels.value[0], labels.value[Math.floor(labels.value.length / 2)], labels.value[labels.value.length - 1]]
})
const activeLabel = computed(() => (hoverIndex.value === null ? '' : labels.value[hoverIndex.value] || ''))
const activeSeriesItems = computed(() => {
  if (hoverIndex.value === null) return []
  return props.series.map((item) => ({
    name: item.name,
    value: item.points[hoverIndex.value!]?.value || 0,
    color: item.color,
  }))
})
const activeDetailItems = computed(() => {
  if (!activeLabel.value) return []
  return props.tooltipDetails[activeLabel.value] || []
})
const activeX = computed(() => (hoverIndex.value === null ? padding.left : x(hoverIndex.value)))
const tooltipStyle = computed(() => {
  const leftPercent = (activeX.value / width) * 100
  return {
    left: `${Math.min(86, Math.max(14, leftPercent))}%`,
  }
})

function x(index: number) {
  if (labels.value.length <= 1) return padding.left
  return padding.left + (index / (labels.value.length - 1)) * plotWidth.value
}

function y(value: number) {
  return padding.top + plotHeight.value - (value / maxValue.value) * plotHeight.value
}

function linePoints(item: ChartSeries) {
  return item.points.map((point, index) => `${x(index)},${y(point.value)}`).join(' ')
}

function labelX(label: string) {
  const index = labels.value.indexOf(label)
  return index >= 0 ? x(index) : padding.left
}

function tickY(value: number) {
  return y(value)
}

function formatNumber(value: number) {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return String(value)
}

function handleMouseMove(event: MouseEvent) {
  if (!svgRef.value || labels.value.length === 0) return
  const rect = svgRef.value.getBoundingClientRect()
  const svgX = ((event.clientX - rect.left) / rect.width) * width
  if (labels.value.length === 1) {
    hoverIndex.value = 0
    return
  }
  const ratio = (Math.min(width - padding.right, Math.max(padding.left, svgX)) - padding.left) / plotWidth.value
  hoverIndex.value = Math.min(labels.value.length - 1, Math.max(0, Math.round(ratio * (labels.value.length - 1))))
}

function clearHover() {
  hoverIndex.value = null
}
</script>

<template>
  <div class="chart-shell">
    <div v-if="!series.length || !labels.length" class="empty-chart">暂无数据</div>
    <template v-else>
      <svg
        ref="svgRef"
        :viewBox="`0 0 ${width} ${height}`"
        class="line-chart"
        role="img"
        @mousemove="handleMouseMove"
        @mouseleave="clearHover"
      >
        <g class="grid">
          <line
            v-for="tick in yTicks"
            :key="tick"
            :x1="padding.left"
            :x2="width - padding.right"
            :y1="tickY(tick)"
            :y2="tickY(tick)"
          />
        </g>
        <g class="axis-labels">
          <text
            v-for="tick in yTicks"
            :key="`label-${tick}`"
            :x="padding.left - 10"
            :y="tickY(tick) + 4"
            text-anchor="end"
          >
            {{ formatNumber(tick) }}
          </text>
          <text
            v-for="label in visibleLabels"
            :key="label"
            :x="labelX(label)"
            :y="height - 10"
            text-anchor="middle"
          >
            {{ label }}
          </text>
        </g>
        <polyline
          v-for="item in series"
          :key="item.name"
          :points="linePoints(item)"
          :stroke="item.color"
          fill="none"
          stroke-width="3"
          stroke-linejoin="round"
          stroke-linecap="round"
        />
        <g v-if="hoverIndex !== null" class="hover-layer">
          <line
            :x1="activeX"
            :x2="activeX"
            :y1="padding.top"
            :y2="height - padding.bottom"
          />
          <circle
            v-for="item in series"
            :key="`point-${item.name}`"
            :cx="activeX"
            :cy="y(item.points[hoverIndex]?.value || 0)"
            r="4"
            :fill="item.color"
          />
        </g>
      </svg>
      <div v-if="hoverIndex !== null" class="tooltip" :style="tooltipStyle">
        <div class="tooltip-title">{{ activeLabel }}</div>
        <div v-for="item in activeSeriesItems" :key="`series-${item.name}`" class="tooltip-row">
          <span class="tooltip-name">
            <span class="legend-color" :style="{ background: item.color }" />
            {{ item.name }}
          </span>
          <strong>{{ item.value.toLocaleString() }}</strong>
        </div>
        <template v-if="activeDetailItems.length">
          <div class="tooltip-divider" />
          <div class="tooltip-subtitle">模型消耗</div>
          <div v-for="item in activeDetailItems" :key="`detail-${item.name}`" class="tooltip-row">
            <span class="tooltip-name">
              <span class="legend-color" :style="{ background: item.color || '#64748b' }" />
              {{ item.name }}
            </span>
            <strong>{{ item.value.toLocaleString() }}</strong>
          </div>
        </template>
      </div>
      <div class="legend">
        <span v-for="item in series" :key="item.name" class="legend-item">
          <span class="legend-color" :style="{ background: item.color }" />
          {{ item.name }}
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.chart-shell {
  width: 100%;
  min-height: 260px;
  position: relative;
}

.line-chart {
  width: 100%;
  height: auto;
  display: block;
}

.grid line {
  stroke: #e5e7eb;
  stroke-width: 1;
}

.axis-labels text {
  fill: #6b7280;
  font-size: 12px;
}

.hover-layer line {
  stroke: #94a3b8;
  stroke-width: 1.5;
  stroke-dasharray: 4 4;
}

.hover-layer circle {
  stroke: #ffffff;
  stroke-width: 2;
}

.tooltip {
  position: absolute;
  top: 8px;
  z-index: 2;
  min-width: 220px;
  max-width: min(320px, calc(100% - 24px));
  transform: translateX(-50%);
  padding: 10px 12px;
  color: #111827;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #d1d5db;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  pointer-events: none;
}

.tooltip-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.tooltip-subtitle {
  color: #4b5563;
  font-size: 12px;
  margin-bottom: 4px;
}

.tooltip-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 22px;
  font-size: 12px;
}

.tooltip-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tooltip-name .legend-color {
  flex: 0 0 auto;
}

.tooltip-divider {
  height: 1px;
  margin: 7px 0;
  background: #e5e7eb;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  padding: 0 8px 4px 8px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.legend-color {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.empty-chart {
  height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
}
</style>
