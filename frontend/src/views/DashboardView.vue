<script setup lang="ts">
import { h, ref, onMounted } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import request from '@/api/request'
import { getGatewayInfo } from '@/api/gatewayInfo'
import type { GatewayEndpointVO, GatewayInfoVO } from '@/types'

const message = useMessage()

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
const endpointLoading = ref(false)

// 控制台展示的是外部客户端实际调用地址，后端负责按当前请求推导 baseUrl。
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

onMounted(async () => {
  endpointLoading.value = true
  try {
    const [healthRes, gatewayInfoRes] = await Promise.all([
      request.get<HealthStats>('/health'),
      getGatewayInfo(),
    ])
    stats.value = healthRes.data
    gatewayInfo.value = gatewayInfoRes.data.data
  } catch {
    message.error('加载控制台数据失败')
  } finally {
    endpointLoading.value = false
  }
})
</script>

<template>
  <div>
    <n-space vertical :size="16">
      <n-h2>控制台</n-h2>
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
                <n-tag type="success" size="small">正常</n-tag>
              </template>
            </n-statistic>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card>
            <n-statistic label="数据库状态" :value="stats.database === 'UP' ? '正常' : stats.database">
              <template #suffix>
                <n-tag type="success" size="small">正常</n-tag>
              </template>
            </n-statistic>
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
            :loading="endpointLoading"
            :pagination="false"
          />
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>
