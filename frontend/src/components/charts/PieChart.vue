<script setup lang="ts">
import { computed, ref } from 'vue'

export interface PieItem {
  name: string
  totalTokens: number
  requestCount: number
}

interface PieSegment extends PieItem {
  color: string
  percent: number
  start: number
  end: number
  path: string
}

const props = defineProps<{
  items: PieItem[]
}>()

const colors = ['#2563eb', '#16a34a', '#f97316', '#9333ea', '#dc2626', '#0891b2', '#64748b', '#ca8a04']
const shellRef = ref<HTMLElement | null>(null)
const activeSegment = ref<PieSegment | null>(null)
const tooltipPosition = ref({ left: 90, top: 12 })

const total = computed(() => props.items.reduce((sum, item) => sum + item.totalTokens, 0))
const segments = computed(() => {
  let start = 0
  return props.items.map((item, index) => {
    const percent = total.value > 0 ? (item.totalTokens / total.value) * 100 : 0
    const segment = {
      ...item,
      color: colors[index % colors.length],
      percent,
      start,
      end: start + percent,
      path: describeSlice(90, 90, 90, (start / 100) * 360, ((start + percent) / 100) * 360),
    }
    start += percent
    return segment
  })
})
const tooltipStyle = computed(() => {
  return {
    left: `${tooltipPosition.value.left}px`,
    top: `${tooltipPosition.value.top}px`,
  }
})

function formatNumber(value: number) {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return String(value)
}

function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180
  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians),
  }
}

function describeSlice(centerX: number, centerY: number, radius: number, startAngle: number, endAngle: number) {
  const start = polarToCartesian(centerX, centerY, radius, startAngle)
  const end = polarToCartesian(centerX, centerY, radius, endAngle)
  const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0
  return [
    `M ${centerX} ${centerY}`,
    `L ${start.x} ${start.y}`,
    `A ${radius} ${radius} 0 ${largeArcFlag} 1 ${end.x} ${end.y}`,
    'Z',
  ].join(' ')
}

function setActiveSegment(segment: PieSegment, event: MouseEvent) {
  activeSegment.value = segment
  updateTooltipPosition(event)
}

function updateTooltipPosition(event: MouseEvent) {
  if (!shellRef.value) return
  const rect = shellRef.value.getBoundingClientRect()
  const left = event.clientX - rect.left + 14
  const top = event.clientY - rect.top + 14
  const maxLeft = Math.max(12, rect.width - 110)
  const minLeft = Math.min(110, maxLeft)
  tooltipPosition.value = {
    left: Math.min(Math.max(left, minLeft), maxLeft),
    top: Math.min(Math.max(top, 12), rect.height - 12),
  }
}

function clearActiveSegment() {
  activeSegment.value = null
}
</script>

<template>
  <div ref="shellRef" class="pie-shell">
    <div v-if="total <= 0" class="empty-pie">&#26242;&#26080;&#25968;&#25454;</div>
    <template v-else>
      <div class="pie" @mouseleave="clearActiveSegment">
        <svg class="pie-svg" viewBox="0 0 180 180" aria-hidden="true">
          <template v-for="item in segments" :key="item.name">
            <circle
              v-if="item.percent >= 99.999"
              cx="90"
              cy="90"
              r="90"
              :fill="item.color"
              class="pie-segment"
              :class="{ active: activeSegment?.name === item.name }"
              @mouseenter="setActiveSegment(item, $event)"
              @mousemove="updateTooltipPosition"
            />
            <path
              v-else
              :d="item.path"
              :fill="item.color"
              class="pie-segment"
              :class="{ active: activeSegment?.name === item.name }"
              @mouseenter="setActiveSegment(item, $event)"
              @mousemove="updateTooltipPosition"
            />
          </template>
        </svg>
        <div class="pie-inner">
          <strong>{{ formatNumber(total) }}</strong>
          <span>tokens</span>
        </div>
      </div>
      <div class="pie-legend">
        <div
          v-for="item in segments"
          :key="item.name"
          class="pie-row"
          :class="{ active: activeSegment?.name === item.name }"
          @mouseenter="setActiveSegment(item, $event)"
          @mousemove="updateTooltipPosition"
          @mouseleave="clearActiveSegment"
        >
          <span class="pie-color" :style="{ background: item.color }" />
          <span class="pie-name">{{ item.name }}</span>
          <span class="pie-value">{{ item.percent.toFixed(1) }}%</span>
        </div>
      </div>
      <div v-if="activeSegment" class="pie-tooltip" :style="tooltipStyle">
        <div class="tooltip-title">
          <span class="pie-color" :style="{ background: activeSegment.color }" />
          <span>{{ activeSegment.name }}</span>
        </div>
        <div class="tooltip-row">
          <span>Token</span>
          <strong>{{ activeSegment.totalTokens.toLocaleString() }}</strong>
        </div>
        <div class="tooltip-row">
          <span>&#35831;&#27714;&#25968;</span>
          <strong>{{ activeSegment.requestCount.toLocaleString() }}</strong>
        </div>
        <div class="tooltip-row">
          <span>&#21344;&#27604;</span>
          <strong>{{ activeSegment.percent.toFixed(1) }}%</strong>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pie-shell {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
  min-height: 210px;
  position: relative;
}

.pie {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
}

.pie-svg {
  position: absolute;
  inset: 0;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  overflow: hidden;
}

.pie-segment {
  cursor: pointer;
  transition: filter 0.16s ease, transform 0.16s ease;
  transform-box: fill-box;
  transform-origin: center;
}

.pie-segment.active {
  filter: brightness(1.08);
  transform: scale(1.02);
}

.pie-inner {
  width: 108px;
  height: 108px;
  border-radius: 50%;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px #e5e7eb;
  pointer-events: none;
  position: relative;
  z-index: 1;
}

.pie-inner strong {
  color: #111827;
  font-size: 20px;
  line-height: 1.1;
}

.pie-inner span {
  color: #6b7280;
  font-size: 12px;
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.pie-row {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  color: #374151;
  font-size: 13px;
  min-height: 24px;
  padding: 2px 4px;
  border-radius: 6px;
  cursor: default;
}

.pie-row.active {
  background: #f3f4f6;
}

.pie-color {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.pie-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pie-value {
  color: #6b7280;
  font-variant-numeric: tabular-nums;
}

.pie-tooltip {
  position: absolute;
  z-index: 3;
  min-width: 190px;
  max-width: min(280px, calc(100% - 24px));
  transform: translate(-50%, 0);
  padding: 10px 12px;
  color: #111827;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid #d1d5db;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  pointer-events: none;
}

.tooltip-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 7px;
  font-weight: 600;
}

.tooltip-title span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tooltip-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 22px;
  color: #374151;
  font-size: 12px;
}

.tooltip-row strong {
  color: #111827;
  font-variant-numeric: tabular-nums;
}

.empty-pie {
  grid-column: 1 / -1;
  min-height: 210px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
}

@media (max-width: 720px) {
  .pie-shell {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .pie-legend {
    width: 100%;
  }
}
</style>
