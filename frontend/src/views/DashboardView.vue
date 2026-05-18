<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import request from '@/api/request'
import { getDashboardStats } from '@/api/dashboard'
import { getGatewayInfo } from '@/api/gatewayInfo'
import LineChart from '@/components/charts/LineChart.vue'
import PieChart from '@/components/charts/PieChart.vue'
import type {
  DashboardDimensionUsageVO,
  DashboardSeriesVO,
  DashboardStatsVO,
  DashboardTokenPointVO,
  GatewayEndpointVO,
  GatewayInfoVO,
} from '@/types'

const message = useMessage()
const palette = ['#2563eb', '#16a34a', '#f97316', '#9333ea', '#dc2626', '#0891b2', '#64748b', '#ca8a04']

interface HealthStats {
  status?: string
  database?: string
  providerCount?: number
  enabledModelCount?: number
}

const stats = ref<HealthStats>({})
const gatewayInfo = ref<GatewayInfoVO>({
  baseUrl: '',
  endpoints: [],
})
const dashboard = ref<DashboardStatsVO>(emptyDashboard())
const loading = ref(false)
const days = ref(7)
const hours = ref(24)
const topN = ref(6)

const dayOptions = [
  { label: '最近 7 天', value: 7 },
  { label: '最近 14 天', value: 14 },
  { label: '最近 30 天', value: 30 },
]
const hourOptions = [
  { label: '最近 24 小时', value: 24 },
  { label: '最近 48 小时', value: 48 },
  { label: '最近 72 小时', value: 72 },
]
const topOptions = [
  { label: 'Top 5', value: 5 },
  { label: 'Top 6', value: 6 },
  { label: 'Top 10', value: 10 },
]

const endpointColumns: DataTableColumn<GatewayEndpointVO>[] = [
  {
    title: '方法',
    key: 'method',
    width: 90,
    render: (row) => h(NTag, { type: row.method === 'GET' ? 'success' : 'info', size: 'small' }, { default: () => row.method }),
  },
  { title: '协议', key: 'protocol', width: 120 },
  { title: '端点', key: 'path', minWidth: 220 },
  {
    title: '调用地址',
    key: 'url',
    minWidth: 320,
    render: (row) => `${gatewayInfo.value.baseUrl}${row.path}`,
  },
  { title: '鉴权', key: 'auth', width: 140 },
  { title: '说明', key: 'description', minWidth: 200 },
]

const dailyTokenSeries = computed(() => tokenSeries(dashboard.value.dailyTokenUsage))
const hourlyTokenSeries = computed(() => tokenSeries(dashboard.value.hourlyTokenUsage))
const modelLineSeries = computed(() => dimensionLineSeries(dashboard.value.modelSeries))
const channelLineSeries = computed(() => dimensionLineSeries(dashboard.value.channelSeries))
const apiKeyLineSeries = computed(() => dimensionLineSeries(dashboard.value.apiKeySeries))
const dailyModelTooltipDetails = computed(() => dimensionTooltipDetails(dashboard.value.modelSeries))
const modelPieItems = computed(() => pieItems(dashboard.value.modelDistribution))
const channelPieItems = computed(() => pieItems(dashboard.value.channelDistribution))
const apiKeyPieItems = computed(() => pieItems(dashboard.value.apiKeyDistribution))
const successRate = computed(() => {
  const summary = dashboard.value.summary
  if (!summary.requestCount) return '0%'
  return `${((summary.successCount / summary.requestCount) * 100).toFixed(1)}%`
})

function emptyDashboard(): DashboardStatsVO {
  return {
    summary: {
      requestCount: 0,
      successCount: 0,
      failureCount: 0,
      inputTokens: 0,
      cacheReadInputTokens: 0,
      outputTokens: 0,
      totalTokens: 0,
    },
    dailyTokenUsage: [],
    hourlyTokenUsage: [],
    modelDistribution: [],
    channelDistribution: [],
    apiKeyDistribution: [],
    modelSeries: [],
    channelSeries: [],
    apiKeySeries: [],
  }
}

function tokenSeries(points: DashboardTokenPointVO[]) {
  return [
    {
      name: '总 Token',
      color: palette[0],
      points: points.map((point) => ({ label: point.label, value: point.totalTokens })),
    },
    {
      name: '输入',
      color: palette[1],
      points: points.map((point) => ({ label: point.label, value: point.inputTokens })),
    },
    {
      name: '输出',
      color: palette[2],
      points: points.map((point) => ({ label: point.label, value: point.outputTokens })),
    },
    {
      name: '缓存读取',
      color: palette[3],
      points: points.map((point) => ({ label: point.label, value: point.cacheReadInputTokens })),
    },
  ]
}

function dimensionLineSeries(series: DashboardSeriesVO[]) {
  return series.map((item, index) => ({
    name: item.name,
    color: palette[index % palette.length],
    points: item.points.map((point) => ({ label: point.label, value: point.totalTokens })),
  }))
}

function dimensionTooltipDetails(series: DashboardSeriesVO[]) {
  const details: Record<string, { name: string; value: number; color: string }[]> = {}
  series.forEach((item, index) => {
    const color = palette[index % palette.length]
    item.points.forEach((point) => {
      if (!point.totalTokens) return
      const bucket = details[point.label] || []
      bucket.push({ name: item.name, value: point.totalTokens, color })
      details[point.label] = bucket
    })
  })
  Object.values(details).forEach((items) => items.sort((a, b) => b.value - a.value))
  return details
}

function pieItems(items: DashboardDimensionUsageVO[]) {
  return items.map((item) => ({
    name: item.name,
    totalTokens: item.totalTokens,
    requestCount: item.requestCount,
  }))
}

function formatNumber(value: number | null | undefined) {
  const normalized = value || 0
  return normalized.toLocaleString()
}

async function load() {
  loading.value = true
  try {
    const [healthRes, gatewayInfoRes, dashboardRes] = await Promise.all([
      request.get<HealthStats>('/health'),
      getGatewayInfo(),
      getDashboardStats({ days: days.value, hours: hours.value, topN: topN.value }),
    ])
    stats.value = healthRes.data
    gatewayInfo.value = gatewayInfoRes.data.data
    dashboard.value = dashboardRes.data.data
  } catch {
    message.error('加载控制台数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <n-space vertical :size="16">
      <n-space justify="space-between" align="center">
        <n-h2>控制台</n-h2>
        <n-space align="center">
          <n-select v-model:value="days" :options="dayOptions" size="small" style="width: 130px" />
          <n-select v-model:value="hours" :options="hourOptions" size="small" style="width: 150px" />
          <n-select v-model:value="topN" :options="topOptions" size="small" style="width: 100px" />
          <n-button size="small" :loading="loading" @click="load">刷新</n-button>
        </n-space>
      </n-space>

      <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
        <n-grid-item>
          <n-card>
            <n-statistic label="总 Token" :value="formatNumber(dashboard.summary.totalTokens)" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="输入 / 输出" :value="`${formatNumber(dashboard.summary.inputTokens)} / ${formatNumber(dashboard.summary.outputTokens)}`" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="请求数" :value="formatNumber(dashboard.summary.requestCount)" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="成功率" :value="successRate">
              <template #suffix>
                <n-tag :type="dashboard.summary.failureCount ? 'warning' : 'success'" size="small">
                  失败 {{ formatNumber(dashboard.summary.failureCount) }}
                </n-tag>
              </template>
            </n-statistic>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
        <n-grid-item>
          <n-card>
            <n-statistic label="厂商数量" :value="stats.providerCount || 0" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="已启用模型" :value="stats.enabledModelCount || 0" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="服务状态" :value="stats.status === 'UP' ? '正常' : stats.status">
              <template #suffix>
                <n-tag :type="stats.status === 'UP' ? 'success' : 'error'" size="small">{{ stats.status || '-' }}</n-tag>
              </template>
            </n-statistic>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="数据库状态" :value="stats.database === 'UP' ? '正常' : stats.database">
              <template #suffix>
                <n-tag :type="stats.database === 'UP' ? 'success' : 'error'" size="small">{{ stats.database || '-' }}</n-tag>
              </template>
            </n-statistic>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
        <n-grid-item>
          <n-card title="按天 Token 消耗">
            <LineChart :series="dailyTokenSeries" :tooltip-details="dailyModelTooltipDetails" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="按小时 Token 消耗">
            <LineChart :series="hourlyTokenSeries" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="模型 Token 趋势">
            <LineChart :series="modelLineSeries" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="渠道 Token 趋势">
            <LineChart :series="channelLineSeries" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="密钥 Token 趋势">
            <LineChart :series="apiKeyLineSeries" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="模型 Token 占比">
            <PieChart :items="modelPieItems" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="渠道 Token 占比">
            <PieChart :items="channelPieItems" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card title="密钥 Token 占比">
            <PieChart :items="apiKeyPieItems" />
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-card title="接口调用信息">
        <n-space vertical :size="12">
          <n-descriptions bordered :column="1" label-placement="left">
            <n-descriptions-item label="后端 Base URL">
              <n-text code>{{ gatewayInfo.baseUrl || '-' }}</n-text>
            </n-descriptions-item>
          </n-descriptions>
          <n-data-table
            :columns="endpointColumns"
            :data="gatewayInfo.endpoints"
            :loading="loading"
            :pagination="false"
          />
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>
