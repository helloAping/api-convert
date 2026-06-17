<script setup lang="ts">
import { ref, onMounted, h, watch, computed } from 'vue'
import { useMessage, NButton, NTag, dateZhCN } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { searchRequestLogs } from '@/api/requestLogs'
import type { RequestLogVO, RequestLogSearchParam } from '@/types'
import { useDebouncedFn } from '@/composables/useDebounce'
import PageHeader from '@/components/PageHeader.vue'
import { DocumentTextOutline } from '@vicons/ionicons5'

const message = useMessage()
const loading = ref(false)
const data = ref<RequestLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = ref<RequestLogSearchParam>({ success: undefined })
const dateRange = ref<[string, string] | null>(null)
const showAdvanced = ref(false)

/**
 * 主搜索区字段（请求编号 / 密钥 / 结果）变化后 300ms 自动重新查询；高级字段保持按钮触发避免误触。
 */
const autoSearchKey = computed(() => `${search.value.requestId || ''}|${search.value.gatewayApiKeyKeyword || ''}|${search.value.success ?? ''}`)
const debouncedAutoSearch = useDebouncedFn(() => {
  page.value = 1
  load()
}, 300)
watch(autoSearchKey, () => debouncedAutoSearch())

const dateLocale = dateZhCN

const protocolOptions = [
  { label: '全部', value: undefined },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
]

const requestTypeOptions = [
  { label: '全部', value: undefined },
  { label: '对话补全', value: 'chat_completions' },
  { label: '消息接口', value: 'messages' },
]

const successOptions = [
  { label: '全部', value: undefined },
  { label: '成功', value: true },
  { label: '失败', value: false },
]

const streamOptions = [
  { label: '全部', value: undefined },
  { label: '是', value: true },
  { label: '否', value: false },
]

function protocolLabel(value: string | null) {
  return ({ openai: 'OpenAI', anthropic: 'Anthropic' } as Record<string, string>)[value || ''] || value || '-'
}

function requestTypeLabel(value: string | null) {
  return ({ chat_completions: '对话补全', messages: '消息接口' } as Record<string, string>)[value || ''] || value || '-'
}

function textOrDash(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function endpointLabel(value: string | null) {
  return ({ CHAT_COMPLETIONS: 'Chat', ANTHROPIC_MESSAGES: 'Anthropic', OPENAI_RESPONSES: 'Responses', OPENAI_VIDEOS: '视频', OPENAI_IMAGES: '图片' } as Record<string, string>)[value || ''] || value || '-'
}

function inputTokenView(row: RequestLogVO) {
  return h('div', [
    h('div', textOrDash(row.inputTokens)),
    row.cacheReadInputTokens === null || row.cacheReadInputTokens === undefined
      ? null
      : h('div', { style: 'font-size:12px;color:#666;line-height:1.3;' }, `缓存读取:${row.cacheReadInputTokens}`),
  ])
}

function apiKeyView(row: RequestLogVO) {
  if (!row.gatewayApiKeyId && !row.gatewayApiKeyName) return '-'
  const name = row.gatewayApiKeyName || `Key #${row.gatewayApiKeyId}`
  const detail = [
    row.gatewayApiKeyId ? `ID:${row.gatewayApiKeyId}` : '',
    row.gatewayApiKeyPreview || '',
  ].filter(Boolean).join(' · ')
  return h('div', [
    h('div', { style: 'font-weight:500;' }, name),
    detail
      ? h('div', { style: 'font-size:12px;color:#666;line-height:1.3;' }, detail)
      : null,
  ])
}

const columns: DataTableColumn<RequestLogVO>[] = [
  { title: '编号', key: 'id', width: 70 },
  { title: '时间', key: 'createdAt', width: 170, render: (row) => textOrDash(row.createdAt) },
  { title: '请求编号', key: 'requestId', width: 150, ellipsis: { tooltip: true } },
  { title: '密钥', key: 'gatewayApiKeyName', width: 170, ellipsis: { tooltip: true }, render: apiKeyView },
  { title: '协议', key: 'sourceProtocol', width: 90, render: (row) => protocolLabel(row.sourceProtocol) },
  { title: '接口类型', key: 'requestType', width: 110, render: (row) => requestTypeLabel(row.requestType) },
  { title: '上游端点', key: 'upstreamEndpointType', width: 110, render: (row) => endpointLabel(row.upstreamEndpointType) },
  { title: '渠道', key: 'providerCode', width: 120, render: (row) => textOrDash(row.providerCode) },
  { title: '供应商类型', key: 'providerType', width: 140, render: (row) => textOrDash(row.providerType) },
  { title: '对外模型', key: 'publicModel', width: 150, ellipsis: { tooltip: true }, render: (row) => textOrDash(row.publicModel) },
  { title: '上游模型', key: 'providerModel', width: 150, ellipsis: { tooltip: true }, render: (row) => textOrDash(row.providerModel) },
  { title: '流式', key: 'stream', width: 60, render: (row) => row.stream ? '是' : '否' },
  { title: '结果', key: 'success', width: 70, render: (row) => h(NTag, { type: row.success ? 'success' : 'error' }, { default: () => row.success ? '成功' : '失败' }) },
  { title: '状态码', key: 'httpStatus', width: 70 },
  { title: '耗时', key: 'latencyMs', width: 70, render: (row) => row.latencyMs ? row.latencyMs + 'ms' : '-' },
  { title: '输入 Token', key: 'inputTokens', width: 130, render: inputTokenView },
  { title: '输出 Token', key: 'outputTokens', width: 100, render: (row) => textOrDash(row.outputTokens) },
  { title: '总 Token', key: 'totalTokens', width: 90, render: (row) => textOrDash(row.totalTokens) },
  { title: '错误码', key: 'errorCode', width: 130, render: (row) => textOrDash(row.errorCode) },
  { title: '错误信息', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true }, render: (row) => textOrDash(row.errorMessage) },
]

async function load() {
  loading.value = true
  try {
    const [startTime, endTime] = dateRange.value || []
    const res = await searchRequestLogs({ ...search.value, startTime, endTime, page: page.value, pageSize: pageSize.value })
    data.value = res.data.data.records
    total.value = res.data.data.total
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

function handlePageChange(p: number) { page.value = p; load() }

function handleSearch() {
  page.value = 1
  load()
}

function handleReset() {
  search.value = { success: undefined }
  dateRange.value = null
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="请求日志"
      subtitle="按请求编号、密钥、协议、模型等维度检索历史请求；高级过滤项可展开。"
      :icon="DocumentTextOutline"
    />

    <n-space vertical>

      <n-space align="center">
        <n-input v-model:value="search.requestId" placeholder="请求编号" style="width:160px" />
        <n-input v-model:value="search.gatewayApiKeyKeyword" placeholder="密钥名称或ID" style="width:140px" />
        <n-select v-model:value="search.success" :options="successOptions" placeholder="结果" style="width:100px" />
        <n-date-picker v-model:formatted-value="dateRange" type="datetimerange" value-format="yyyy-MM-dd HH:mm:ss" clearable :locale="dateLocale" style="width:360px" />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
        <n-button quaternary @click="showAdvanced = !showAdvanced">
          {{ showAdvanced ? '收起' : '展开' }}
        </n-button>
      </n-space>

      <n-space v-if="showAdvanced" align="center">
        <n-select v-model:value="search.sourceProtocol" clearable :options="protocolOptions" placeholder="协议" style="width:130px" />
        <n-select v-model:value="search.requestType" clearable :options="requestTypeOptions" placeholder="接口类型" style="width:130px" />
        <n-input v-model:value="search.providerCode" placeholder="渠道" clearable style="width:120px" />
        <n-input v-model:value="search.publicModel" placeholder="对外模型" clearable style="width:120px" />
        <n-input v-model:value="search.providerModel" placeholder="上游模型" clearable style="width:120px" />
        <n-select v-model:value="search.providerType" clearable placeholder="供应商类型" style="width:150px"
          :options="[
            { label: '全部', value: undefined },
            { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
            { label: 'Anthropic', value: 'ANTHROPIC' },
            { label: 'DeepSeek Chat', value: 'DEEPSEEK_CHAT' },
            { label: 'DeepSeek Anthropic', value: 'DEEPSEEK_ANTHROPIC' },
            { label: 'GPT-AUTH', value: 'GPT_AUTH' },
            { label: 'CLAUDE-AUTH', value: 'CLAUDE_AUTH' },
            { label: 'Gemini', value: 'GEMINI' },
          ]"
        />
        <n-select v-model:value="search.stream" clearable :options="streamOptions" placeholder="流式" style="width:90px" />
      </n-space>

      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" :scroll-x="2120" />
      <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="handlePageChange" />
    </n-space>
  </div>
</template>
