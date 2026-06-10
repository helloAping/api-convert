<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import request from '@/api/request'
import { getDashboardStats } from '@/api/dashboard'
import { getGatewayInfo } from '@/api/gatewayInfo'
import LineChart from '@/components/charts/LineChart.vue'
import { DocumentOutline } from '@vicons/ionicons5'
import type {
  DashboardSeriesVO,
  DashboardStatsVO,
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
const gatewayInfo = ref<GatewayInfoVO>({ baseUrl: '', endpoints: [] })
const dashboard = ref<DashboardStatsVO>(emptyDashboard())
const loading = ref(false)
const range = ref('7d')
const topN = ref(6)
const trendDimension = ref<'model' | 'channel' | 'apiKey'>('model')

const rangeOptions = [
  { label: '最近 24 小时', value: '24h' },
  { label: '最近 48 小时', value: '48h' },
  { label: '最近 7 天', value: '7d' },
  { label: '最近 14 天', value: '14d' },
  { label: '最近 30 天', value: '30d' },
]
const topOptions = [
  { label: 'Top 5', value: 5 },
  { label: 'Top 6', value: 6 },
  { label: 'Top 10', value: 10 },
]
const dimensionOptions = [
  { label: '模型', value: 'model' },
  { label: '渠道', value: 'channel' },
  { label: '密钥', value: 'apiKey' },
]

const apiDocsHref = computed(() => docsHref())

function endpointDocHref(path: string) {
  const anchorMap: Record<string, string> = {
    '/health': 'health',
    '/v1/models': 'models',
    '/v1/chat/completions': 'chat-completions',
    '/v1/messages': 'messages',
    '/v1/responses': 'responses',
    '/v1/videos': 'videos',
    '/v1/images/generations': 'images',
  }
  return docsHref(anchorMap[path])
}

function docsHref(anchor?: string) {
  const baseUrl = gatewayInfo.value.baseUrl?.replace(/\/$/, '') || ''
  const hash = anchor ? `#${anchor}` : ''
  return `${baseUrl}/docs/api-reference.html${hash}`
}

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
    render: () => `${gatewayInfo.value.baseUrl}${'path'}`,
  },
  { title: '鉴权', key: 'auth', width: 140 },
  { title: '说明', key: 'description', minWidth: 200 },
  {
    title: '文档',
    key: 'docs',
    width: 100,
    render: (row) => h(
      'a',
      { class: 'endpoint-doc-link', href: endpointDocHref(row.path), target: '_blank', rel: 'noopener noreferrer' },
      '查看',
    ),
  },
]

const tokenSeries = computed(() => {
  const points = dashboard.value.tokenUsage
  return [
    { name: '总 Token', color: palette[0], points: points.map(p => ({ label: p.label, value: p.totalTokens })) },
    { name: '输入', color: palette[1], points: points.map(p => ({ label: p.label, value: p.inputTokens })) },
    { name: '输出', color: palette[2], points: points.map(p => ({ label: p.label, value: p.outputTokens })) },
    { name: '缓存读取', color: palette[3], points: points.map(p => ({ label: p.label, value: p.cacheReadInputTokens })) },
  ]
})

const trendSeries = computed(() => {
  const seriesMap = { model: dashboard.value.modelSeries, channel: dashboard.value.channelSeries, apiKey: dashboard.value.apiKeySeries }
  return dimensionLineSeries(seriesMap[trendDimension.value])
})

const successRate = computed(() => {
  const s = dashboard.value.summary
  if (!s.requestCount) return '0%'
  return `${((s.successCount / s.requestCount) * 100).toFixed(1)}%`
})

function emptyDashboard(): DashboardStatsVO {
  return {
    summary: { requestCount: 0, successCount: 0, failureCount: 0, inputTokens: 0, cacheReadInputTokens: 0, outputTokens: 0, totalTokens: 0 },
    tokenUsage: [],
    modelDistribution: [],
    channelDistribution: [],
    apiKeyDistribution: [],
    modelSeries: [],
    channelSeries: [],
    apiKeySeries: [],
  }
}

function dimensionLineSeries(series: DashboardSeriesVO[]) {
  return series.map((item, index) => ({
    name: item.name,
    color: palette[index % palette.length],
    points: item.points.map(p => ({ label: p.label, value: p.totalTokens })),
  }))
}

function formatNumber(value: number | null | undefined) {
  return (value || 0).toLocaleString()
}

async function load() {
  loading.value = true
  try {
    const [healthRes, gatewayInfoRes, dashboardRes] = await Promise.all([
      request.get<HealthStats>('/health'),
      getGatewayInfo(),
      getDashboardStats({ range: range.value, topN: topN.value }),
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
          <n-select v-model:value="range" :options="rangeOptions" size="small" style="width: 150px" />
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
          <n-card title="Token 消耗趋势">
            <LineChart :series="tokenSeries" />
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <template #header>
              <n-space align="center" :size="8">
                <span>维度趋势</span>
                <n-select v-model:value="trendDimension" :options="dimensionOptions" size="tiny" style="width: 90px" />
              </n-space>
            </template>
            <LineChart :series="trendSeries" />
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-card title="接口调用信息">
        <template #header-extra>
          <a class="api-doc-button" :href="apiDocsHref" target="_blank" rel="noopener noreferrer">
            <n-icon size="16"><DocumentOutline /></n-icon>
            完整文档
          </a>
        </template>
        <n-space vertical :size="12">
          <n-descriptions bordered :column="1" label-placement="left">
            <n-descriptions-item label="后端 Base URL">
              <n-text code>{{ gatewayInfo.baseUrl || '-' }}</n-text>
            </n-descriptions-item>
          </n-descriptions>
          <n-data-table :columns="endpointColumns" :data="gatewayInfo.endpoints" :loading="loading" :pagination="false" />
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>

<style scoped>
.api-doc-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #2563eb;
  border-radius: 4px;
  color: #2563eb;
  font-size: 13px;
  line-height: 1;
  text-decoration: none;
}

.api-doc-button:hover,
.endpoint-doc-link:hover {
  color: #1d4ed8;
  border-color: #1d4ed8;
}

.endpoint-doc-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 26px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  color: #2563eb;
  font-size: 12px;
  text-decoration: none;
}
</style>
