<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useMessage, NButton, NTag } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { searchRequestLogs } from '@/api/requestLogs'
import type { RequestLogVO, RequestLogSearchParam } from '@/types'

const message = useMessage()
const loading = ref(false)
const data = ref<RequestLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = ref<RequestLogSearchParam>({ success: undefined })
// 时间范围只用于查询条件，不会写入日志数据。
const dateRange = ref<[string, string] | null>(null)

// 将后端协议常量翻译为中文，便于运营排查调用来源。
function protocolLabel(value: string | null) {
  return ({ openai: 'OpenAI', anthropic: 'Anthropic' } as Record<string, string>)[value || ''] || value || '-'
}

// 将接口类型翻译为中文。
function requestTypeLabel(value: string | null) {
  return ({ chat_completions: '对话补全', messages: '消息接口' } as Record<string, string>)[value || ''] || value || '-'
}

// 空值统一展示为短横线，避免数字列出现 undefined。
function textOrDash(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

// 输入 token 列合并展示缓存读取量，方便对比总输入和缓存命中。
function inputTokenView(row: RequestLogVO) {
  return h('div', [
    h('div', textOrDash(row.inputTokens)),
    row.cacheReadInputTokens === null || row.cacheReadInputTokens === undefined
      ? null
      : h('div', { style: 'font-size:12px;color:#666;line-height:1.3;' }, `缓存读取:${row.cacheReadInputTokens}`),
  ])
}

const columns: DataTableColumn<RequestLogVO>[] = [
  { title: '编号', key: 'id', width: 70 },
  { title: '时间', key: 'createdAt', width: 170, render: (row) => textOrDash(row.createdAt) },
  { title: '请求编号', key: 'requestId', width: 150, ellipsis: { tooltip: true } },
  { title: '协议', key: 'sourceProtocol', width: 90, render: (row) => protocolLabel(row.sourceProtocol) },
  { title: '接口类型', key: 'requestType', width: 110, render: (row) => requestTypeLabel(row.requestType) },
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

// 查询时回到第一页，避免当前页超过过滤后的总页数。
function handleSearch() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <n-space vertical>
      <n-h2>请求日志</n-h2>
      <n-space>
        <n-input v-model:value="search.requestId" placeholder="请求编号" style="width:160px" />
        <n-select v-model:value="search.sourceProtocol" clearable :options="[{ label: 'OpenAI', value: 'openai' }, { label: 'Anthropic', value: 'anthropic' }]" placeholder="协议" style="width:130px" />
        <n-select v-model:value="search.requestType" clearable :options="[{ label: '对话补全', value: 'chat_completions' }, { label: '消息接口', value: 'messages' }]" placeholder="接口类型" style="width:140px" />
        <n-input v-model:value="search.providerCode" placeholder="渠道" style="width:120px" />
        <n-input v-model:value="search.publicModel" placeholder="模型" style="width:120px" />
        <n-select v-model:value="search.success" :options="[{ label: '全部', value: undefined }, { label: '成功', value: true }, { label: '失败', value: false }]" style="width:120px" />
        <n-date-picker v-model:formatted-value="dateRange" type="datetimerange" value-format="yyyy-MM-dd HH:mm:ss" clearable style="width:360px" />
        <n-button type="primary" @click="handleSearch">查询</n-button>
      </n-space>
      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" :scroll-x="1730" />
      <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="handlePageChange" />
    </n-space>
  </div>
</template>
