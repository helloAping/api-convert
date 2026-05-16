<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useMessage, NButton, NInputNumber, NSwitch, NTag } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { getModels, updateModelCapabilities, updateModelEnabled, updateModelQuota } from '@/api/models'
import type { ModelCapabilitiesForm, ModelQuotaForm, ModelVO } from '@/types'

const message = useMessage()
// 跟踪模型聚合列表加载状态。
const loading = ref(false)
// 后端按对外模型名去重后返回的模型列表。
const data = ref<ModelVO[]>([])
// 控制模型额度配置弹窗。
const showQuotaModal = ref(false)
// 当前正在编辑额度的模型聚合记录。
const editingModel = ref<ModelVO | null>(null)
// 模型额度单价表单，字段均表示每 100 万 token 消耗多少额度。
const quotaForm = ref<ModelQuotaForm>({ inputQuotaPerMillion: null, outputQuotaPerMillion: null, cacheReadQuotaPerMillion: null })
// 控制模型能力配置弹窗。
const showCapabilitiesModal = ref(false)
// 当前正在编辑能力的模型聚合记录。
const editingCapabilitiesModel = ref<ModelVO | null>(null)
// 模型能力表单。
const capabilitiesForm = ref<ModelCapabilitiesForm>({ vision: null, toolsSupport: null, jsonModeSupport: null, contextLength: null })

// 模型管理只负责聚合展示渠道保存的模型，不再在该页面维护渠道映射。
const columns: DataTableColumn<ModelVO>[] = [
  { title: '编号', key: 'id', width: 70 },
  { title: '模型名称', key: 'publicName', minWidth: 180 },
  {
    title: '渠道数量',
    key: 'channelCount',
    width: 100,
  },
  {
    title: '渠道',
    key: 'providerCodes',
    minWidth: 180,
    render: (row) => row.providerCodes.join('、') || row.providerCode || '-',
  },
  {
    title: '上游模型',
    key: 'providerModels',
    minWidth: 220,
    render: (row) => row.providerModels.join('、') || row.providerModel || '-',
  },
  {
    title: '能力',
    key: 'capabilities',
    width: 220,
    render: (row) => {
      const tags: ReturnType<typeof h>[] = []
      if (row.vision) tags.push(h(NTag, { size: 'small', type: 'info' }, { default: () => '视觉' }))
      if (row.toolsSupport) tags.push(h(NTag, { size: 'small', type: 'warning' }, { default: () => '工具调用' }))
      if (row.jsonModeSupport) tags.push(h(NTag, { size: 'small', type: 'success' }, { default: () => 'JSON 模式' }))
      if (row.contextLength) {
        const len = row.contextLength
        tags.push(h(NTag, { size: 'small', type: 'default' }, { default: () => `${(len / 1000).toFixed(0)}K` }))
      }
      if (tags.length === 0) return h('span', '-')
      return h('div', { style: 'display:flex;gap:4px;flex-wrap:wrap' }, tags)
    },
  },
  {
    title: '输入/输出/缓存额度',
    key: 'quota',
    width: 190,
    render: (row) => `${quotaText(row.inputQuotaPerMillion)} / ${quotaText(row.outputQuotaPerMillion)} / ${quotaText(row.cacheReadQuotaPerMillion)}`,
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
    width: 190,
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', onClick: () => editQuota(row) }, { default: () => '额度配置' }),
      h(NButton, { size: 'small', onClick: () => editCapabilities(row) }, { default: () => '能力配置' }),
      h(NButton, {
        size: 'small',
        type: row.enabled ? 'warning' : 'primary',
        onClick: () => toggleEnabled(row),
      }, { default: () => row.enabled ? '关闭' : '启用' }),
    ]),
  },
]

function quotaText(value: number | null) {
  return value === null || value === undefined ? '未设' : String(value)
}

function editQuota(row: ModelVO) {
  editingModel.value = row
  quotaForm.value = {
    inputQuotaPerMillion: row.inputQuotaPerMillion,
    outputQuotaPerMillion: row.outputQuotaPerMillion,
    cacheReadQuotaPerMillion: row.cacheReadQuotaPerMillion,
  }
  showQuotaModal.value = true
}

// 保存模型额度配置后刷新聚合列表，确保同名模型的所有渠道映射同步展示。
async function saveQuota() {
  if (!editingModel.value) {
    return
  }
  try {
    await updateModelQuota(editingModel.value.id, quotaForm.value)
    showQuotaModal.value = false
    await load()
    message.success('额度配置已保存')
  } catch {
    message.error('保存额度配置失败')
  }
}

// 在模型管理页关闭模型会同步关闭同名模型下的全部渠道映射。
async function toggleEnabled(row: ModelVO) {
  try {
    await updateModelEnabled(row.id, { enabled: !row.enabled })
    await load()
    message.success(row.enabled ? '模型已关闭' : '模型已启用')
  } catch {
    message.error(row.enabled ? '关闭模型失败' : '启用模型失败')
  }
}

function editCapabilities(row: ModelVO) {
  editingCapabilitiesModel.value = row
  capabilitiesForm.value = {
    vision: row.vision,
    toolsSupport: row.toolsSupport,
    jsonModeSupport: row.jsonModeSupport,
    contextLength: row.contextLength,
  }
  showCapabilitiesModal.value = true
}

async function saveCapabilities() {
  if (!editingCapabilitiesModel.value) return
  try {
    await updateModelCapabilities(editingCapabilitiesModel.value.id, capabilitiesForm.value)
    showCapabilitiesModal.value = false
    await load()
    message.success('能力配置已保存')
  } catch {
    message.error('保存能力配置失败')
  }
}

// 从管理端聚合接口加载所有渠道已保存模型，重复模型由后端去重。
async function load() {
  loading.value = true
  try {
    const res = await getModels()
    data.value = res.data.data
  } catch {
    message.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <n-space vertical>
      <n-space justify="space-between" align="center">
        <n-h2>模型管理</n-h2>
      </n-space>
      <n-alert type="info" title="模型来源于渠道保存结果">
        这里聚合展示所有渠道中已保存的模型，重复模型只展示一次。模型的新增、删除和对外名称调整请在渠道管理中完成。
      </n-alert>
      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" />
    </n-space>

    <n-modal v-model:show="showQuotaModal" title="模型额度配置">
      <n-card style="width: 520px">
        <n-form :model="quotaForm" label-placement="left" label-width="150">
          <n-form-item label="1M 输入消耗额度">
            <n-input-number v-model:value="quotaForm.inputQuotaPerMillion" :min="0" clearable placeholder="未设则不按输入计费" style="width: 100%" />
          </n-form-item>
          <n-form-item label="1M 输出消耗额度">
            <n-input-number v-model:value="quotaForm.outputQuotaPerMillion" :min="0" clearable placeholder="未设则不按输出计费" style="width: 100%" />
          </n-form-item>
          <n-form-item label="1M 缓存读取额度">
            <n-input-number v-model:value="quotaForm.cacheReadQuotaPerMillion" :min="0" clearable placeholder="未设则按输入单价计费" style="width: 100%" />
          </n-form-item>
        </n-form>
        <n-space justify="end">
          <n-button @click="showQuotaModal = false">取消</n-button>
          <n-button type="primary" @click="saveQuota">保存</n-button>
        </n-space>
      </n-card>
    </n-modal>

    <n-modal v-model:show="showCapabilitiesModal" title="模型能力配置">
      <n-card style="width: 520px">
        <n-form :model="capabilitiesForm" label-placement="left" label-width="150">
          <n-form-item label="视觉/图片输入">
            <n-switch :value="capabilitiesForm.vision ?? false" @update:value="capabilitiesForm.vision = $event" />
          </n-form-item>
          <n-form-item label="工具/函数调用">
            <n-switch :value="capabilitiesForm.toolsSupport ?? false" @update:value="capabilitiesForm.toolsSupport = $event" />
          </n-form-item>
          <n-form-item label="JSON 输出模式">
            <n-switch :value="capabilitiesForm.jsonModeSupport ?? false" @update:value="capabilitiesForm.jsonModeSupport = $event" />
          </n-form-item>
          <n-form-item label="上下文窗口(token)">
            <n-input-number v-model:value="capabilitiesForm.contextLength" :min="0" clearable placeholder="例如 128000" style="width: 100%" />
          </n-form-item>
        </n-form>
        <n-space justify="end">
          <n-button @click="showCapabilitiesModal = false">取消</n-button>
          <n-button type="primary" @click="saveCapabilities">保存</n-button>
        </n-space>
      </n-card>
    </n-modal>
  </div>
</template>
