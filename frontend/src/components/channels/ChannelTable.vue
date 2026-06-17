<script setup lang="ts">
import { computed, h } from 'vue'
import { NButton, NTag } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { ChannelQuotaVO, ChannelVO } from '@/types'
import { endpointLabels } from '@/types'
import { channelTypeLabel } from '@/composables/useChannelForm'

const props = defineProps<{
  data: ChannelVO[]
  loading: boolean
  quotaMap: Record<number, ChannelQuotaVO>
  quotaLoading: Record<number, boolean>
}>()

const emit = defineEmits<{
  (e: 'edit', row: ChannelVO): void
  (e: 'copy', row: ChannelVO): void
  (e: 'remove', id: number): void
  (e: 'refresh-quota', row: ChannelVO): void
}>()

function quotaText(row: ChannelVO): string {
  const quota = props.quotaMap[row.id]
  if (!quota) return '未获取'
  return quota.summary || (quota.supported ? '已获取额度' : '不支持获取')
}

const columns = computed<DataTableColumn<ChannelVO>[]>(() => [
  { title: '编号', key: 'id', width: 70 },
  { title: '渠道编码', key: 'code', width: 150 },
  { title: '渠道名称', key: 'name', width: 160 },
  { title: '供应商', key: 'type', width: 150, render: (row) => channelTypeLabel(row.type) },
  { title: 'Base URL', key: 'baseUrl', ellipsis: { tooltip: true } },
  {
    title: '能力',
    key: 'capabilities',
    minWidth: 280,
    render: (row) => {
      const caps = row.capabilities || []
      if (caps.length === 0) return h('span', { style: 'color:#94a3b8' }, '未配置')
      return h('div', { class: 'capability-tags' }, caps.map((cap) =>
        h(NTag, { type: 'info', size: 'small', round: true, bordered: true, title: cap.path || '' }, {
          default: () => endpointLabels[cap.type] || cap.type,
        })
      ))
    },
  },
  { title: '模型数', key: 'modelCount', width: 90 },
  { title: '密钥', key: 'apiKey', width: 140 },
  {
    title: '额度',
    key: 'quota',
    width: 280,
    render: (row) => h('div', { class: 'quota-cell' }, [
      h('span', { class: props.quotaMap[row.id]?.supported === false ? 'quota-muted' : '' }, quotaText(row)),
      h(NButton, {
        size: 'tiny',
        loading: !!props.quotaLoading[row.id],
        onClick: () => emit('refresh-quota', row),
      }, { default: () => '刷新' }),
    ]),
  },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) => h(NTag, { type: row.enabled ? 'success' : 'default' }, { default: () => row.enabled ? '已启用' : '未启用' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 240,
    fixed: 'right',
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', onClick: () => emit('edit', row) }, { default: () => '编辑' }),
      h(NButton, { size: 'small', onClick: () => emit('copy', row) }, { default: () => '复制' }),
      h(NButton, { size: 'small', type: 'error', onClick: () => emit('remove', row.id) }, { default: () => '删除' }),
    ]),
  },
])
</script>

<template>
  <n-data-table
    :columns="columns"
    :data="props.data"
    :loading="props.loading"
    :pagination="false"
    :scroll-x="2120"
  />
</template>

<style scoped>
.quota-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.quota-cell span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quota-muted {
  color: #6b7280;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
</style>
