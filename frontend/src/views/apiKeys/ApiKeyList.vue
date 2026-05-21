<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useMessage, NButton, NTag, NInput } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { addApiKeyQuota, getApiKeys, createApiKey, updateApiKey, deleteApiKey } from '@/api/apiKeys'
import { getChannels } from '@/api/channels'
import { getModels } from '@/api/models'
import type { ApiKeyLimitForm, ApiKeyVO, ApiKeyForm, ApiKeyUpdateForm, ChannelVO, ModelVO } from '@/types'
import { activeStatuses, apiKeyLimitTypes, quotaWindowUnits } from '@/types'

const message = useMessage()
// 跟踪密钥表格加载状态。
const loading = ref(false)
// 管理端返回的网关密钥列表，包含明文密钥，展示时必须脱敏。
const data = ref<ApiKeyVO[]>([])
// 可授权给密钥使用的渠道选项。
const channelOptions = ref<{ label: string; value: string }[]>([])
// 可授权给密钥使用的对外模型选项。
const modelOptions = ref<{ label: string; value: string }[]>([])
// 控制修改密钥状态和渠道范围弹窗。
const showModal = ref(false)
// 控制创建密钥弹窗。
const showCreateModal = ref(false)
// 控制追加密钥额度弹窗。
const showQuotaModal = ref(false)
// 当前正在编辑的密钥 ID。
const editingId = ref<number | null>(null)
// 当前正在追加额度的密钥 ID。
const quotaEditingId = ref<number | null>(null)
// 修改表单；channelCodes 为空表示允许所有渠道。
const updateForm = ref<ApiKeyUpdateForm>({ status: 'ACTIVE', channelCodes: [], modelNames: [], quotaLimit: null, quotaWindowValue: null, quotaWindowUnit: null, limits: [] })
// 创建表单；channelCodes 为空表示允许所有渠道。
const createForm = ref<ApiKeyForm>({ name: '', channelCodes: [], modelNames: [], quotaBalance: null, quotaLimit: null, quotaWindowValue: null, quotaWindowUnit: null, limits: [] })
// 追加额度表单，amount 必须大于 0。
const quotaAmount = ref<number | null>(null)
// 新生成的明文密钥，用于创建弹窗内展示和复制。
const newKey = ref('')

const columns: DataTableColumn<ApiKeyVO>[] = [
  { title: '编号', key: 'id', width: 70 },
  { title: '密钥名称', key: 'name', minWidth: 150 },
  {
    title: '密钥',
    key: 'rawKey',
    width: 220,
    render: (row) => h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
      h('span', { style: 'font-family:monospace' }, maskRawKey(row.rawKey || row.keyPreview)),
      h(NTag, {
        size: 'small',
        type: row.rawKey ? 'success' : 'default',
        style: row.rawKey ? 'cursor:pointer' : 'cursor:not-allowed',
        onClick: () => copyKey(row),
      }, { default: () => '复制' }),
    ]),
  },
  {
    title: '可用渠道',
    key: 'channelCodes',
    minWidth: 180,
    render: (row) => row.channelCodes.length ? row.channelCodes.join('、') : '所有渠道',
  },
  {
    title: '可用模型',
    key: 'modelNames',
    minWidth: 180,
    render: (row) => row.modelNames?.length ? row.modelNames.join('、') : '所有模型',
  },
  {
    title: '剩余额度',
    key: 'quotaBalance',
    width: 120,
    render: (row) => quotaBalanceText(row.quotaBalance),
  },
  {
    title: '窗口限制',
    key: 'limits',
    minWidth: 260,
    render: (row) => limitSummary(row.limits),
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(NTag, { type: row.status === 'ACTIVE' ? 'success' : 'default' }, { default: () => statusLabel(row.status) }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 240,
    render: (row) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        h(NButton, { size: 'small', onClick: () => editStatus(row) }, { default: () => '修改' }),
        h(NButton, { size: 'small', onClick: () => openAddQuota(row) }, { default: () => '加额度' }),
        h(NButton, { size: 'small', type: 'error', onClick: () => remove(row.id) }, { default: () => '删除' }),
      ]),
  },
]

function statusLabel(status: string) {
  return { ACTIVE: '启用', DISABLED: '禁用', EXPIRED: '已过期' }[status] || status
}

function quotaBalanceText(value: number | null) {
  return value === null || value === undefined ? '不限' : String(value)
}

function unitLabel(unit: string | null | undefined) {
  return { MINUTE: '分钟', HOUR: '小时', DAY: '天' }[unit || ''] || unit || '-'
}

function limitTypeLabel(type: string | null | undefined) {
  return { QUOTA: '额度', REQUEST: '请求数' }[type || ''] || type || '-'
}

function limitUnitOptions(type: string | null | undefined) {
  const units = type === 'REQUEST' ? quotaWindowUnits : quotaWindowUnits.filter(unit => unit !== 'MINUTE')
  return units.map(unit => ({ label: unitLabel(unit), value: unit }))
}

function limitUnitValues(type: string | null | undefined) {
  return limitUnitOptions(type).map(option => option.value)
}

function limitSummary(limits: ApiKeyVO['limits']) {
  if (!limits || !limits.length) {
    return '不限制'
  }
  return limits.map(limit => {
    const suffix = limit.limitType === 'REQUEST' ? '次' : ''
    return `${limitTypeLabel(limit.limitType)}：${limit.windowValue || '-'}${unitLabel(limit.windowUnit)}内 ${limit.limitValue ?? '-'}${suffix}`
  }).join('；')
}

function createLimit(type = 'QUOTA', windowUnit?: string): ApiKeyLimitForm {
  return {
    limitType: type,
    windowValue: 1,
    windowUnit: windowUnit || (type === 'REQUEST' ? 'MINUTE' : 'HOUR'),
    limitValue: null,
    configJson: null,
  }
}

function nextAvailableLimitUnit(limits: ApiKeyLimitForm[], type: string) {
  const used = new Set(limits
    .filter(limit => limit.limitType === type)
    .map(limit => limit.windowUnit)
    .filter(Boolean))
  return limitUnitValues(type).find(unit => !used.has(unit))
}

function addLimit(limits: ApiKeyLimitForm[], type = 'QUOTA') {
  const unit = nextAvailableLimitUnit(limits, type)
  if (!unit) {
    message.warning(`${limitTypeLabel(type)}限制已配置全部窗口单位`)
    return
  }
  limits.push(createLimit(type, unit))
}

function addCreateLimit(type = 'QUOTA') {
  addLimit(createForm.value.limits, type)
}

function addUpdateLimit(type = 'QUOTA') {
  addLimit(updateForm.value.limits, type)
}

function removeLimit(limits: ApiKeyLimitForm[], index: number) {
  limits.splice(index, 1)
}

function onLimitTypeChange(limit: ApiKeyLimitForm) {
  const validUnits = limitUnitValues(limit.limitType)
  if (!limit.windowUnit || !validUnits.includes(limit.windowUnit)) {
    limit.windowUnit = limit.limitType === 'REQUEST' ? 'MINUTE' : 'HOUR'
  }
}

// 同一限制类型下同一窗口单位只能保留一条，后端也会做相同校验。
function validateLimitUniqueness(limits: ApiKeyLimitForm[]) {
  const seen = new Set<string>()
  for (const limit of limits) {
    if (!limit.limitType || !limit.windowUnit) {
      continue
    }
    const key = `${limit.limitType}|${limit.windowUnit}`
    if (seen.has(key)) {
      message.warning(`${limitTypeLabel(limit.limitType)}限制中 ${unitLabel(limit.windowUnit)} 窗口只能配置一条`)
      return false
    }
    seen.add(key)
  }
  return true
}

// 前端展示密钥时只显示脱敏结果，复制时才使用原文。
function maskRawKey(rawKey: string) {
  if (!rawKey) return 'sk-****'
  if (rawKey.startsWith('sha256:') || rawKey.includes('****')) return rawKey
  const suffix = rawKey.length > 4 ? rawKey.slice(-4) : rawKey
  return `sk-****${suffix}`
}

// 后端校验错误会直接展示，便于管理员看到渠道授权或状态错误。
function errorMessage(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message || fallback
}

// 加载密钥和渠道列表，渠道列表用于创建/编辑时选择授权范围。
async function load() {
  loading.value = true
  try {
    const [keysRes, channelsRes, modelsRes] = await Promise.all([getApiKeys(), getChannels(), getModels()])
    data.value = keysRes.data.data
    channelOptions.value = channelsRes.data.data.map((channel: ChannelVO) => ({
      label: `${channel.name}（${channel.code}）`,
      value: channel.code,
    }))
    modelOptions.value = Array.from(new Set(modelsRes.data.data.map((model: ModelVO) => model.publicName)))
      .sort()
      .map((name) => ({ label: name, value: name }))
  } catch (error) {
    message.error(errorMessage(error, '加载失败'))
  } finally {
    loading.value = false
  }
}

function showCreateKey() {
  createForm.value = { name: '', channelCodes: [], modelNames: [], quotaBalance: null, quotaLimit: null, quotaWindowValue: null, quotaWindowUnit: null, limits: [] }
  newKey.value = ''
  showCreateModal.value = true
}

function editStatus(item: ApiKeyVO) {
  editingId.value = item.id
  updateForm.value = {
    status: item.status,
    channelCodes: [...item.channelCodes],
    modelNames: [...(item.modelNames || [])],
    quotaLimit: null,
    quotaWindowValue: null,
    quotaWindowUnit: null,
    limits: (item.limits || []).map(limit => ({
      limitType: limit.limitType,
      windowValue: limit.windowValue,
      windowUnit: limit.windowUnit,
      limitValue: limit.limitValue,
      configJson: limit.configJson,
    })),
  }
  showModal.value = true
}

function openAddQuota(item: ApiKeyVO) {
  quotaEditingId.value = item.id
  quotaAmount.value = null
  showQuotaModal.value = true
}

async function saveCreate() {
  if (!validateLimitUniqueness(createForm.value.limits)) {
    return
  }
  try {
    const res = await createApiKey(createForm.value)
    const created = res.data.data
    newKey.value = created.rawKey
    await load()
    message.success('密钥已生成')
  } catch (error) {
    message.error(errorMessage(error, '生成失败'))
  }
}

async function copyText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

async function copyRawKey(rawKey: string) {
  if (!rawKey || rawKey.includes('****') || rawKey.startsWith('sha256:')) {
    message.warning('该密钥缺少可复制的原文')
    return
  }
  try {
    await copyText(rawKey)
    message.success('密钥原文已复制')
  } catch {
    message.error('复制失败')
  }
}

async function copyKey(row: ApiKeyVO) {
  await copyRawKey(row.rawKey)
}

async function saveUpdate() {
  if (!validateLimitUniqueness(updateForm.value.limits)) {
    return
  }
  try {
    if (editingId.value) {
      await updateApiKey(editingId.value, updateForm.value)
      showModal.value = false
      await load()
      message.success('更新成功')
    }
  } catch (error) {
    message.error(errorMessage(error, '更新失败'))
  }
}

async function saveAddQuota() {
  try {
    if (!quotaEditingId.value || !quotaAmount.value || quotaAmount.value <= 0) {
      message.warning('追加额度必须大于 0')
      return
    }
    await addApiKeyQuota(quotaEditingId.value, { amount: quotaAmount.value })
    showQuotaModal.value = false
    await load()
    message.success('额度已追加')
  } catch (error) {
    message.error(errorMessage(error, '追加额度失败'))
  }
}

async function remove(id: number) {
  try {
    await deleteApiKey(id)
    await load()
    message.success('删除成功')
  } catch (error) {
    message.error(errorMessage(error, '删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <n-space vertical>
      <n-space justify="space-between">
        <n-h2>网关密钥</n-h2>
        <n-button type="primary" @click="showCreateKey">生成密钥</n-button>
      </n-space>
      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" />
    </n-space>

    <n-modal v-model:show="showCreateModal" title="生成网关密钥">
      <n-card style="width: 560px">
        <n-form :model="createForm" label-placement="left" label-width="110">
          <n-form-item label="密钥名称">
            <n-input v-model:value="createForm.name" placeholder="例如：外部工具密钥" />
          </n-form-item>
          <n-form-item label="可用渠道">
            <n-select
              v-model:value="createForm.channelCodes"
              :options="channelOptions"
              multiple
              filterable
              clearable
              placeholder="不选择表示允许所有渠道"
            />
          </n-form-item>
          <n-form-item label="可用模型">
            <n-select
              v-model:value="createForm.modelNames"
              :options="modelOptions"
              multiple
              filterable
              clearable
              placeholder="不选择表示允许所有模型"
            />
          </n-form-item>
          <n-form-item label="初始额度">
            <n-input-number v-model:value="createForm.quotaBalance" :min="0" clearable placeholder="留空表示不限总额度" style="width: 100%" />
          </n-form-item>
          <n-form-item label="限制项">
            <n-space vertical style="width: 100%">
              <n-space v-for="(limit, index) in createForm.limits" :key="index" align="center">
                <n-select
                  v-model:value="limit.limitType"
                  :options="apiKeyLimitTypes.map(type => ({ label: limitTypeLabel(type), value: type }))"
                  style="width: 110px"
                  @update:value="onLimitTypeChange(limit)"
                />
                <n-input-number v-model:value="limit.windowValue" :min="1" placeholder="窗口" style="width: 100px" />
                <n-select
                  v-model:value="limit.windowUnit"
                  :options="limitUnitOptions(limit.limitType)"
                  placeholder="单位"
                  style="width: 110px"
                />
                <n-input-number v-model:value="limit.limitValue" :min="0" placeholder="上限" style="width: 130px" />
                <n-button size="small" @click="removeLimit(createForm.limits, index)">删除</n-button>
              </n-space>
              <n-space>
                <n-button size="small" @click="addCreateLimit('QUOTA')">加额度限制</n-button>
                <n-button size="small" @click="addCreateLimit('REQUEST')">加请求数限制</n-button>
              </n-space>
            </n-space>
          </n-form-item>
        </n-form>
        <n-button type="primary" @click="saveCreate" :disabled="!createForm.name">生成</n-button>
        <n-alert v-if="newKey" type="warning" title="密钥已生成，列表中会脱敏展示" style="margin-top: 12px">
          <template #default>
            <n-space vertical>
              <n-input :value="maskRawKey(newKey)" readonly style="font-family: monospace" />
              <n-button size="small" @click="copyRawKey(newKey)">复制密钥原文</n-button>
            </n-space>
          </template>
        </n-alert>
      </n-card>
    </n-modal>

    <n-modal v-model:show="showModal" title="修改网关密钥">
      <n-card style="width: 560px">
        <n-form :model="updateForm" label-placement="left" label-width="110">
          <n-form-item label="状态">
            <n-select v-model:value="updateForm.status" :options="activeStatuses.map(s => ({ label: statusLabel(s), value: s }))" />
          </n-form-item>
          <n-form-item label="可用渠道">
            <n-select
              v-model:value="updateForm.channelCodes"
              :options="channelOptions"
              multiple
              filterable
              clearable
              placeholder="不选择表示允许所有渠道"
            />
          </n-form-item>
          <n-form-item label="可用模型">
            <n-select
              v-model:value="updateForm.modelNames"
              :options="modelOptions"
              multiple
              filterable
              clearable
              placeholder="不选择表示允许所有模型"
            />
          </n-form-item>
          <n-form-item label="限制项">
            <n-space vertical style="width: 100%">
              <n-space v-for="(limit, index) in updateForm.limits" :key="index" align="center">
                <n-select
                  v-model:value="limit.limitType"
                  :options="apiKeyLimitTypes.map(type => ({ label: limitTypeLabel(type), value: type }))"
                  style="width: 110px"
                  @update:value="onLimitTypeChange(limit)"
                />
                <n-input-number v-model:value="limit.windowValue" :min="1" placeholder="窗口" style="width: 100px" />
                <n-select
                  v-model:value="limit.windowUnit"
                  :options="limitUnitOptions(limit.limitType)"
                  placeholder="单位"
                  style="width: 110px"
                />
                <n-input-number v-model:value="limit.limitValue" :min="0" placeholder="上限" style="width: 130px" />
                <n-button size="small" @click="removeLimit(updateForm.limits, index)">删除</n-button>
              </n-space>
              <n-space>
                <n-button size="small" @click="addUpdateLimit('QUOTA')">加额度限制</n-button>
                <n-button size="small" @click="addUpdateLimit('REQUEST')">加请求数限制</n-button>
              </n-space>
            </n-space>
          </n-form-item>
        </n-form>
        <n-button type="primary" @click="saveUpdate">保存</n-button>
      </n-card>
    </n-modal>

    <n-modal v-model:show="showQuotaModal" title="追加密钥额度">
      <n-card style="width: 420px">
        <n-form label-placement="left" label-width="90">
          <n-form-item label="追加额度">
            <n-input-number v-model:value="quotaAmount" :min="0" clearable placeholder="请输入大于 0 的额度" style="width: 100%" />
          </n-form-item>
        </n-form>
        <n-space justify="end">
          <n-button @click="showQuotaModal = false">取消</n-button>
          <n-button type="primary" @click="saveAddQuota">确认追加</n-button>
        </n-space>
      </n-card>
    </n-modal>
  </div>
</template>
