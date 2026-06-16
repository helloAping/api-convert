<script setup lang="ts">
import { h, computed, onMounted, ref } from 'vue'
import { useMessage, NButton, NTag } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { channelTypes, supplierDefaultEndpoints, capabilityDefaultPaths, endpointLabels } from '@/types'
import type { ChannelForm, ChannelModelForm, ChannelQuotaVO, ChannelVO } from '@/types'
import { endpointTypeOptions } from '@/types'
import { createChannel, deleteChannel, fetchChannelModels, fetchChannelQuota, getChannels, startChannelAuth, submitChannelAuthCallbackUrl, updateChannel, uploadChannelAuth } from '@/api/channels'

const message = useMessage()
const loading = ref(false)
const data = ref<ChannelVO[]>([])
const showModal = ref(false)
const modalMode = ref<'create' | 'edit' | 'copy'>('create')
const editingId = ref<number | null>(null)
const fetchingModels = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
const selectedProviderModels = ref<string[]>([])
const quotaMap = ref<Record<number, ChannelQuotaVO>>({})
const quotaLoading = ref<Record<number, boolean>>({})
const form = ref<ChannelForm>(emptyForm())
const authUploading = ref(false)
const authFileInput = ref<HTMLInputElement | null>(null)
const oauthAuthorizationUrl = ref('')
const oauthCallbackUrl = ref('')

const configuredCapabilityOptions = computed(() => {
  const caps = form.value.capabilities || []
  if (caps.length === 0) return endpointTypeOptions
  const types = new Set(caps.map(c => c.type))
  return endpointTypeOptions.filter(opt => types.has(opt.value))
})

const columns: DataTableColumn<ChannelVO>[] = [
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
      h('span', { class: quotaMap.value[row.id]?.supported === false ? 'quota-muted' : '' }, quotaText(row)),
      h(NButton, {
        size: 'tiny',
        loading: !!quotaLoading.value[row.id],
        onClick: () => refreshQuota(row),
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
      h(NButton, { size: 'small', onClick: () => edit(row) }, { default: () => '编辑' }),
      h(NButton, { size: 'small', onClick: () => copyChannel(row) }, { default: () => '复制' }),
      h(NButton, { size: 'small', type: 'error', onClick: () => remove(row.id) }, { default: () => '删除' }),
    ]),
  },
]

function emptyForm(): ChannelForm {
  return {
    code: '',
    name: '',
    type: 'OPENAI',
    baseUrl: '',
    chatPath: '/v1/chat/completions',
    videoPath: '/v1/videos',
    imagePath: '/v1/images/generations',
    modelsPath: '/v1/models',
    apiKey: '',
    authMode: 'API_KEY',
    priority: 100,
    status: 'ACTIVE',
    publicModel: '',
    providerModel: '',
    modelPrefix: '',
    models: [],
    enabled: true,
    capabilities: [],
  }
}

function quotaText(row: ChannelVO) {
  const quota = quotaMap.value[row.id]
  if (!quota) {
    return '未获取'
  }
  return quota.summary || (quota.supported ? '已获取额度' : '不支持获取')
}

function channelTypeLabel(type: string) {
  return {
    OPENAI: 'OpenAI',
    ANTHROPIC: 'Anthropic',
    CUSTOM: '自定义',
    MIMO_TOKEN_PLAN: 'MiMo Token Plan',
    DEEPSEEK: 'DeepSeek',
    VOLC_CODINGPLAN: '火山 CodingPlan',
    OPENCODE: 'OpenCode',
    GEMINI: 'Google Gemini',
    GPT_AUTH: 'GPT-AUTH',
    CLAUDE_AUTH: 'CLAUDE-AUTH',
  }[type] || type
}

function handleTypeChange(type: string) {
  if (form.value.models.length > 0) {
    const oldDefaults = supplierDefaultEndpoints[form.value.type] || []
    form.value.models = form.value.models.map((model) => {
      const currentEndpoints = model.allowedEndpointTypes || ''
      if (!currentEndpoints || currentEndpoints === oldDefaults.join(',')) {
        const newDefaults = supplierDefaultEndpoints[type] || []
        return { ...model, allowedEndpointTypes: newDefaults.join(',') }
      }
      return model
    })
  }
  const defaults: Record<string, { baseUrl: string; chatPath: string; videoPath: string; imagePath: string; modelsPath: string }> = {
    OPENAI: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    ANTHROPIC: { baseUrl: 'https://api.anthropic.com', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    CUSTOM: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    MIMO_TOKEN_PLAN: { baseUrl: 'https://token-plan-cn.xiaomimimo.com', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    DEEPSEEK: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    VOLC_CODINGPLAN: { baseUrl: 'https://ark.cn-beijing.volces.com/api/coding', chatPath: '/v3/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v3/models' },
    OPENCODE: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    GEMINI: { baseUrl: '', chatPath: '/v1beta/models', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1beta/models' },
    GPT_AUTH: { baseUrl: 'https://api.openai.com', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    CLAUDE_AUTH: { baseUrl: 'https://api.anthropic.com', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
  }
  const defaultPaths = new Set(Object.values(defaults).flatMap(d => [d.chatPath, d.videoPath, d.imagePath, d.modelsPath]))
  const newDefault = defaults[type]
  if (!newDefault) return
  // 官方供应商预填 baseUrl：GPT_AUTH / CLAUDE_AUTH / ANTHROPIC / MIMO_TOKEN_PLAN
  if (newDefault.baseUrl) {
    form.value.baseUrl = newDefault.baseUrl
  }
  if (defaultPaths.has(form.value.chatPath)) {
    form.value.chatPath = newDefault.chatPath
  }
  if (defaultPaths.has(form.value.videoPath)) {
    form.value.videoPath = newDefault.videoPath
  }
  if (defaultPaths.has(form.value.imagePath)) {
    form.value.imagePath = newDefault.imagePath
  }
  if (defaultPaths.has(form.value.modelsPath)) {
    form.value.modelsPath = newDefault.modelsPath
  }
  form.value.authMode = isAuthType(type) ? 'AUTH_FILE' : 'API_KEY'
  form.value.capabilities = []
}

function isAuthType(type: string) {
  return type === 'GPT_AUTH' || type === 'CLAUDE_AUTH'
}

function syncSelectedModels(values: string[]) {
  const uniqueValues = uniqueProviderModels(values)
  selectedProviderModels.value = uniqueValues
  const current = new Map(form.value.models.map((model) => [model.providerModel.trim(), model]))
  form.value.models = uniqueValues.map((providerModel) => {
    const existing = current.get(providerModel)
    if (existing) return existing
    return {
      publicName: '',
      providerModel,
      modelAlias: '',
      allowedEndpointTypes: '',
      allowedCapabilities: '',
    }
  })
}

function uniqueProviderModels(providerModels: string[]) {
  return Array.from(new Set(providerModels
    .map((providerModel) => providerModel?.trim())
    .filter((providerModel): providerModel is string => !!providerModel)))
}

function mergeModelOptions(options: { label: string; value: string }[]) {
  const merged = new Map<string, { label: string; value: string }>()
  for (const option of options) {
    const value = option.value?.trim()
    if (!value || merged.has(value)) {
      continue
    }
    merged.set(value, { label: option.label || value, value })
  }
  modelOptions.value = Array.from(merged.values())
}

function buildPublicModelName(providerModel: string, prefix: string) {
  const normalizedPrefix = prefix.trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return normalizedPrefix ? `${normalizedPrefix}/${providerModel}` : providerModel
}

function ensureModelOptions(providerModels: string[]) {
  mergeModelOptions([
    ...modelOptions.value,
    ...uniqueProviderModels(providerModels).map((providerModel) => ({ label: providerModel, value: providerModel })),
  ])
}

function normalizeModelAlias(model: ChannelModelForm) {
  model.modelAlias = model.modelAlias?.trim() || ''
  model.publicName = model.modelAlias
}

function parseEndpointTypes(value: string | null | undefined): string[] {
  if (!value) return []
  return value.split(',').map(s => s.trim()).filter(Boolean)
}

function errorMessage(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message || fallback
}

async function load() {
  loading.value = true
  try {
    const res = await getChannels()
    data.value = res.data.data
  } catch (error) {
    message.error(errorMessage(error, '加载渠道失败'))
  } finally {
    loading.value = false
  }
}

async function refreshQuota(row: ChannelVO) {
  quotaLoading.value = { ...quotaLoading.value, [row.id]: true }
  try {
    const res = await fetchChannelQuota(row.id)
    quotaMap.value = { ...quotaMap.value, [row.id]: res.data.data }
    if (res.data.data.supported) {
      message.success('额度已刷新')
    } else {
      message.warning(res.data.data.summary || '当前供应商不支持额度获取')
    }
  } catch (error) {
    message.error(errorMessage(error, '获取额度失败'))
  } finally {
    quotaLoading.value = { ...quotaLoading.value, [row.id]: false }
  }
}

function showCreate() {
  editingId.value = null
  modalMode.value = 'create'
  form.value = emptyForm()
  selectedProviderModels.value = []
  modelOptions.value = []
  oauthAuthorizationUrl.value = ''
  oauthCallbackUrl.value = ''
  showModal.value = true
}

function edit(item: ChannelVO) {
  editingId.value = item.id
  modalMode.value = 'edit'
  oauthAuthorizationUrl.value = ''
  oauthCallbackUrl.value = ''
  form.value = {
    code: item.code,
    name: item.name,
    type: item.type,
    baseUrl: item.baseUrl,
    chatPath: item.chatPath,
    videoPath: item.videoPath || '/v1/videos',
    imagePath: item.imagePath || '/v1/images/generations',
    modelsPath: item.modelsPath,
    apiKey: '',
    authMode: item.authMode || (isAuthType(item.type) ? 'AUTH_FILE' : 'API_KEY'),
    priority: item.priority,
    status: item.status,
    publicModel: '',
    providerModel: '',
    modelPrefix: '',
    models: item.models.map((model) => ({
      publicName: model.modelAlias || '',
      providerModel: model.providerModel,
      modelAlias: model.modelAlias || '',
      inputQuotaPerMillion: model.inputQuotaPerMillion,
      outputQuotaPerMillion: model.outputQuotaPerMillion,
      cacheReadQuotaPerMillion: model.cacheReadQuotaPerMillion,
      allowedEndpointTypes: model.allowedEndpointTypes || '',
      allowedCapabilities: model.allowedCapabilities || '',
    })),
    enabled: item.enabled,
    capabilities: item.capabilities || [],
  }
  selectedProviderModels.value = uniqueProviderModels(form.value.models.map((model) => model.providerModel))
  modelOptions.value = []
  ensureModelOptions(selectedProviderModels.value)
  showModal.value = true
}

function copyChannel(source: ChannelVO) {
  editingId.value = null
  modalMode.value = 'copy'
  oauthAuthorizationUrl.value = ''
  oauthCallbackUrl.value = ''
  form.value = {
    code: '',
    name: '',
    type: source.type,
    baseUrl: source.baseUrl,
    chatPath: source.chatPath,
    videoPath: source.videoPath || '/v1/videos',
    imagePath: source.imagePath || '/v1/images/generations',
    modelsPath: source.modelsPath,
    apiKey: '',
    authMode: source.authMode || (isAuthType(source.type) ? 'AUTH_FILE' : 'API_KEY'),
    priority: source.priority,
    status: source.status,
    publicModel: '',
    providerModel: '',
    modelPrefix: '',
    models: source.models.map((model) => ({
      publicName: model.modelAlias || '',
      providerModel: model.providerModel,
      modelAlias: model.modelAlias || '',
      inputQuotaPerMillion: model.inputQuotaPerMillion,
      outputQuotaPerMillion: model.outputQuotaPerMillion,
      cacheReadQuotaPerMillion: model.cacheReadQuotaPerMillion,
      allowedEndpointTypes: model.allowedEndpointTypes || '',
      allowedCapabilities: model.allowedCapabilities || '',
    })),
    enabled: source.enabled,
    capabilities: source.capabilities || [],
  }
  selectedProviderModels.value = uniqueProviderModels(form.value.models.map((model) => model.providerModel))
  modelOptions.value = []
  ensureModelOptions(selectedProviderModels.value)
  showModal.value = true
}

function triggerAuthUpload() {
  if (!editingId.value) {
    message.warning('请先保存渠道后再上传 auth.json')
    return
  }
  authFileInput.value?.click()
}

async function handleAuthFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !editingId.value) return
  authUploading.value = true
  try {
    await uploadChannelAuth(editingId.value, file)
    await load()
    message.success('授权文件已上传')
  } catch (error) {
    message.error(errorMessage(error, '上传授权文件失败'))
  } finally {
    authUploading.value = false
  }
}

async function startOauthLogin() {
  if (!editingId.value) {
    message.warning('请先保存渠道后再触发 OAuth 登录')
    return
  }
  try {
    const res = await startChannelAuth(editingId.value)
    oauthAuthorizationUrl.value = res.data.data.authorizationUrl
    oauthCallbackUrl.value = ''
    message.success('授权链接已生成')
  } catch (error) {
    message.error(errorMessage(error, '触发 OAuth 登录失败'))
  }
}

function openOauthLink() {
  if (!oauthAuthorizationUrl.value) return
  window.open(oauthAuthorizationUrl.value, '_blank', 'noopener,noreferrer')
}

async function copyOauthLink() {
  if (!oauthAuthorizationUrl.value) return
  try {
    await navigator.clipboard.writeText(oauthAuthorizationUrl.value)
    message.success('授权链接已复制')
  } catch {
    message.warning('复制失败，请手动复制授权链接')
  }
}

async function submitOauthCallbackUrl() {
  if (!editingId.value) {
    message.warning('请先保存渠道后再提交回调 URL')
    return
  }
  if (!oauthCallbackUrl.value.trim()) {
    message.warning('请粘贴浏览器跳转后的完整回调 URL')
    return
  }
  try {
    await submitChannelAuthCallbackUrl(editingId.value, oauthCallbackUrl.value.trim())
    oauthAuthorizationUrl.value = ''
    oauthCallbackUrl.value = ''
    await load()
    message.success('OAuth 授权已保存')
  } catch (error) {
    message.error(errorMessage(error, '提交 OAuth 回调失败'))
  }
}

async function loadUpstreamModels() {
  fetchingModels.value = true
  try {
    const res = await fetchChannelModels({
      type: form.value.type,
      channelId: editingId.value,
      baseUrl: form.value.baseUrl,
      modelsPath: form.value.modelsPath,
      apiKey: form.value.apiKey,
    })
    const selected = new Set(uniqueProviderModels(selectedProviderModels.value))
    const fetchedOptions = res.data.data.map((model) => ({
      label: model.ownedBy ? `${model.id}（${model.ownedBy}）` : model.id,
      value: model.id,
    }))
    mergeModelOptions([
      ...fetchedOptions,
      ...modelOptions.value.filter((option) => selected.has(option.value) && !fetchedOptions.some((item) => item.value === option.value)),
    ])
    if (modelOptions.value.length === 0) {
      message.warning('上游未返回可用模型')
    } else {
      message.success('模型列表已更新')
    }
  } catch (error) {
    message.error(errorMessage(error, '获取上游模型失败'))
  } finally {
    fetchingModels.value = false
  }
}

async function save() {
  try {
    form.value.models.forEach(normalizeModelAlias)
    if (editingId.value) {
      await updateChannel(editingId.value, form.value)
    } else {
      await createChannel(form.value)
    }
    showModal.value = false
    await load()
    message.success('保存成功')
  } catch (error) {
    message.error(errorMessage(error, '保存失败'))
  }
}

async function remove(id: number) {
  try {
    await deleteChannel(id)
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
      <n-space justify="space-between" align="center">
        <n-h2>渠道管理</n-h2>
        <n-button type="primary" @click="showCreate">新增渠道</n-button>
      </n-space>

      <n-alert type="info" title="渠道用于把网关请求转发到指定上游">
        选择供应商后勾选需要的端点能力，为每种能力填写独立的上游请求路径。模型支持多选和手动输入；别名非必填，填写后模型管理中会按别名单独展示，未填写时使用"模型前缀/上游模型名"。
      </n-alert>

      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" :scroll-x="2120" />
    </n-space>

    <n-modal v-model:show="showModal" :title="modalMode === 'copy' ? '复制渠道' : editingId ? '编辑渠道' : '新增渠道'">
      <n-card style="width: 760px">
        <n-form :model="form" label-placement="left" label-width="120">
          <n-form-item label="渠道编码">
            <n-input v-model:value="form.code" :disabled="!!editingId" placeholder="例如：deepseek" />
          </n-form-item>
          <n-form-item label="渠道名称">
            <n-input v-model:value="form.name" placeholder="例如：DeepSeek" />
          </n-form-item>
          <n-form-item label="供应商">
            <n-select
              v-model:value="form.type"
              :options="channelTypes.map(t => ({ label: channelTypeLabel(t), value: t }))"
              @update:value="handleTypeChange"
            />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="Base URL">
            <n-input v-model:value="form.baseUrl" placeholder="例如：https://api.deepseek.com" />
          </n-form-item>

          <n-form-item v-if="!isAuthType(form.type)" label="端点能力">
            <n-space vertical style="width: 100%">
              <n-select
                :value="(form.capabilities || []).map(c => c.type)"
                :options="endpointTypeOptions"
                multiple
                clearable
                :max-tag-count="1"
                placeholder="请选择需要支持的端点能力"
                @update:value="(vals: string[]) => {
                  const existing = form.capabilities || []
                  const currentTypes = new Set(existing.map(c => c.type))
                  const newSet = new Set(vals)
                  const added = vals.filter(v => !currentTypes.has(v))
                  const removed = new Set([...currentTypes].filter(t => !newSet.has(t)))
                  const kept = existing.filter(c => !removed.has(c.type))
                  for (const t of added) {
                    kept.push({ type: t, path: capabilityDefaultPaths[t]?.[form.type] || '' })
                  }
                  form.capabilities = kept
                  // 清理模型能力限制中已被移除的能力
                  if (removed.size > 0) {
                    form.models = form.models.map((m: ChannelModelForm) => {
                      const caps = parseEndpointTypes(m.allowedCapabilities)
                      const filtered = caps.filter(c => !removed.has(c))
                      return { ...m, allowedCapabilities: filtered.join(',') }
                    })
                  }
                }"
              />
              <div v-if="(form.capabilities || []).length > 0" class="capability-table">
                <div class="capability-row capability-head">
                  <div>端点类型</div>
                  <div>请求路径</div>
                </div>
                <div
                  v-for="cap in (form.capabilities || [])"
                  :key="cap.type"
                  class="capability-row"
                >
                  <n-text>{{ endpointLabels[cap.type] || cap.type }}</n-text>
                  <n-input
                    :value="cap.path"
                    :placeholder="capabilityDefaultPaths[cap.type]?.[form.type] || ''"
                    size="small"
                    @update:value="(val: string) => { cap.path = val }"
                  />
                </div>
              </div>
            </n-space>
          </n-form-item>

          <n-form-item v-if="!isAuthType(form.type)" label="模型列表路径">
            <n-input v-model:value="form.modelsPath" placeholder="例如：/v1/models" />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="API Key">
            <n-input
              v-model:value="form.apiKey"
              type="password"
              show-password-on="click"
              :placeholder="editingId ? '留空表示不修改密钥' : modalMode === 'copy' ? '复制的渠道需重新输入密钥' : '请输入上游 API Key'"
            />
          </n-form-item>
          <n-form-item v-else label="授权文件">
            <n-space vertical style="width: 100%">
              <n-space>
                <n-button :loading="authUploading" @click="triggerAuthUpload">上传 auth.json</n-button>
                <n-button @click="startOauthLogin">生成 OAuth 授权链接</n-button>
                <input ref="authFileInput" type="file" accept=".json,application/json" style="display: none" @change="handleAuthFileChange" />
              </n-space>
              <n-input-group v-if="oauthAuthorizationUrl">
                <n-input :value="oauthAuthorizationUrl" readonly />
                <n-button @click="openOauthLink">打开</n-button>
                <n-button @click="copyOauthLink">复制</n-button>
              </n-input-group>
              <n-input-group v-if="oauthAuthorizationUrl">
                <n-input
                  v-model:value="oauthCallbackUrl"
                  placeholder="粘贴完整回调 URL，例如 http://localhost:1455/auth/callback?code=...&state=..."
                />
                <n-button type="primary" @click="submitOauthCallbackUrl">提交回调</n-button>
              </n-input-group>
              <n-text depth="3">
                先保存渠道，再上传 auth.json 或生成 OAuth 授权链接；打开授权链接后，把浏览器跳转到 localhost 的完整 URL 粘贴回来。
              </n-text>
            </n-space>
          </n-form-item>
          <n-form-item label="模型前缀">
            <n-input
              v-model:value="form.modelPrefix"
              placeholder="非必填，例如：baidu"
            />
          </n-form-item>
          <n-form-item label="上游模型">
            <n-space vertical style="width: 100%">
              <n-space>
                <n-select
                  v-model:value="selectedProviderModels"
                  :options="modelOptions"
                  multiple
                  filterable
                  tag
                  clearable
                  :max-tag-count="1"
                  placeholder="请选择或输入多个上游模型名"
                  style="width: 480px"
                  @update:value="syncSelectedModels"
                />
                <n-button :loading="fetchingModels" @click="loadUpstreamModels">获取模型</n-button>
              </n-space>
              <n-text depth="3">获取失败或列表为空时，可以直接输入自定义上游模型名。</n-text>
            </n-space>
          </n-form-item>
          <n-form-item v-if="form.models.length > 0" label="模型别名">
            <div class="model-alias-table">
              <div class="model-alias-row model-alias-head">
                <div>模型名称</div>
                <div>模型别名</div>
                <div>允许端点</div>
                <div>能力限制</div>
              </div>
              <div
                v-for="model in form.models"
                :key="model.providerModel"
                class="model-alias-row"
              >
                <n-text>{{ model.providerModel }}</n-text>
                <n-input
                  v-model:value="model.modelAlias"
                  :placeholder="`默认：${buildPublicModelName(model.providerModel, form.modelPrefix)}`"
                  @blur="normalizeModelAlias(model)"
                />
                <n-select
                  :value="parseEndpointTypes(model.allowedEndpointTypes)"
                  :options="endpointTypeOptions"
                  multiple
                  clearable
                  :max-tag-count="1"
                  placeholder="不限"
                  @update:value="(val: string[]) => { model.allowedEndpointTypes = val.join(',') }"
                />
                <n-select
                  :value="parseEndpointTypes(model.allowedCapabilities)"
                  :options="configuredCapabilityOptions"
                  multiple
                  clearable
                  :max-tag-count="1"
                  placeholder="不限"
                  @update:value="(val: string[]) => { model.allowedCapabilities = val.join(',') }"
                />
              </div>
            </div>
          </n-form-item>
          <n-form-item label="路由权重">
            <n-input-number v-model:value="form.priority" :min="1" :precision="0" />
          </n-form-item>
          <n-form-item label="启用渠道">
            <n-switch v-model:value="form.enabled" />
          </n-form-item>
        </n-form>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </n-space>
      </n-card>
    </n-modal>
  </div>
</template>

<style scoped>
.model-alias-table {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.model-alias-row {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 0.8fr) minmax(0, 1.2fr) minmax(0, 1.2fr);
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-top: 1px solid #edf0f5;
}

.model-alias-row:first-child {
  border-top: 0;
}

.model-alias-head {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

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

.capability-table {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.capability-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid #edf0f5;
}

.capability-row:first-child {
  border-top: 0;
}

.capability-head {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
</style>
