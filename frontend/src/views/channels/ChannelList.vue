<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useMessage, NButton, NTag } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { channelTypes } from '@/types'
import type { ChannelForm, ChannelModelForm, ChannelQuotaVO, ChannelVO } from '@/types'
import { createChannel, deleteChannel, fetchChannelModels, fetchChannelQuota, getChannels, startChannelAuth, submitChannelAuthCallbackUrl, updateChannel, uploadChannelAuth } from '@/api/channels'

const message = useMessage()
// 跟踪渠道表格加载状态，刷新期间保留当前数据。
const loading = ref(false)
// 后端聚合接口返回的渠道行数据。
const data = ref<ChannelVO[]>([])
// 控制创建/编辑弹窗是否展示。
const showModal = ref(false)
// 编辑模式下的当前渠道 ID；null 表示创建模式。
const editingId = ref<number | null>(null)
// 跟踪上游模型发现请求，让获取按钮展示加载状态。
const fetchingModels = ref(false)
// 从供应商模型接口获取并合并本地已选模型后的可搜索下拉选项。
const modelOptions = ref<{ label: string; value: string }[]>([])
// 多选框当前选中的上游模型 ID，实际保存时会同步到 form.models。
const selectedProviderModels = ref<string[]>([])
// 按渠道 ID 暂存用户点击刷新后获得的额度结果，不写入数据库。
const quotaMap = ref<Record<number, ChannelQuotaVO>>({})
// 按渠道 ID 跟踪额度刷新按钮加载状态。
const quotaLoading = ref<Record<number, boolean>>({})
// 弹窗内创建/编辑表单共用的可变状态。
const form = ref<ChannelForm>(emptyForm())
const authUploading = ref(false)
const authFileInput = ref<HTMLInputElement | null>(null)
const oauthAuthorizationUrl = ref('')
const oauthCallbackUrl = ref('')

// 表格列通过渲染函数展示协议标签、状态标签和行操作。
const columns: DataTableColumn<ChannelVO>[] = [
  { title: '编号', key: 'id', width: 70 },
  { title: '渠道编码', key: 'code', width: 150 },
  { title: '渠道名称', key: 'name', width: 160 },
  { title: '供应商', key: 'type', width: 150, render: (row) => channelTypeLabel(row.type) },
  { title: 'Base URL', key: 'baseUrl', ellipsis: { tooltip: true } },
  { title: '请求路径', key: 'chatPath', width: 180 },
  { title: '视频接口路径', key: 'videoPath', width: 170 },
  { title: '图片接口路径', key: 'imagePath', width: 190 },
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
    width: 180,
    render: (row) => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'small', onClick: () => edit(row) }, { default: () => '编辑' }),
      h(NButton, { size: 'small', type: 'error', onClick: () => remove(row.id) }, { default: () => '删除' }),
    ]),
  },
]

// 创建带协议默认值的新渠道表单。
function emptyForm(): ChannelForm {
  return {
    code: '',
    name: '',
    type: 'OPENAI_COMPATIBLE',
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
  }
}

// 将当前行的额度结果压缩成列表可读文本；刷新前不主动请求上游。
function quotaText(row: ChannelVO) {
  const quota = quotaMap.value[row.id]
  if (!quota) {
    return '未获取'
  }
  return quota.summary || (quota.supported ? '已获取额度' : '不支持获取')
}

// 将供应商策略常量转换为管理界面的中文标签。
function channelTypeLabel(type: string) {
  return {
    OPENAI_COMPATIBLE: 'OpenAI 兼容',
    ANTHROPIC: 'Anthropic',
    OPENAI_RESPONSES: 'OpenAI Responses',
    GPT_AUTH: 'GPT-AUTH',
    CLAUDE_AUTH: 'CLAUDE-AUTH',
    DEEPSEEK_CHAT: 'DeepSeek Chat',
    DEEPSEEK_ANTHROPIC: 'DeepSeek Anthropic',
    GEMINI: 'Google Gemini',
  }[type] || type
}

// 供应商类型变化时切换默认请求路径和模型列表路径。
function handleTypeChange(type: string) {
  const defaults: Record<string, { baseUrl: string; chatPath: string; videoPath: string; imagePath: string; modelsPath: string }> = {
    OPENAI_COMPATIBLE: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    ANTHROPIC: { baseUrl: '', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    OPENAI_RESPONSES: { baseUrl: '', chatPath: '/v1/responses', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    GPT_AUTH: { baseUrl: 'https://api.openai.com', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    CLAUDE_AUTH: { baseUrl: 'https://api.anthropic.com', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    DEEPSEEK_CHAT: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    DEEPSEEK_ANTHROPIC: { baseUrl: '', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1/models' },
    GEMINI: { baseUrl: '', chatPath: '/v1beta/models', videoPath: '/v1/videos', imagePath: '/v1/images/generations', modelsPath: '/v1beta/models' },
  }
  // 收集所有默认路径，用于判断用户是否手动修改过
  const defaultPaths = new Set(Object.values(defaults).flatMap(d => [d.chatPath, d.videoPath, d.imagePath, d.modelsPath]))
  const newDefault = defaults[type]
  if (!newDefault) return
  if (isAuthType(type)) {
    form.value.baseUrl = newDefault.baseUrl
  }
  // 仅当当前路径仍然匹配任意默认值时自动切换，用户手动修改过的路径不会被覆盖
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
}

function isAuthType(type: string) {
  return type === 'GPT_AUTH' || type === 'CLAUDE_AUTH'
}

// 多选模型变化时保留已有别名，新选中的模型默认不填别名，交给前缀生成默认展示名。
function syncSelectedModels(values: string[]) {
  const uniqueValues = uniqueProviderModels(values)
  selectedProviderModels.value = uniqueValues
  const current = new Map(form.value.models.map((model) => [model.providerModel.trim(), model]))
  form.value.models = uniqueValues.map((providerModel) => current.get(providerModel) || {
    publicName: '',
    providerModel,
    modelAlias: '',
  })
}

// 手动输入和上游返回都可能出现重复项，统一按去空格后的模型名保留一份。
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

// 根据可选前缀生成模型管理中展示的默认对外模型名。
function buildPublicModelName(providerModel: string, prefix: string) {
  const normalizedPrefix = prefix.trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return normalizedPrefix ? `${normalizedPrefix}/${providerModel}` : providerModel
}

// 手动输入模型名或编辑已有渠道时，将未出现在接口返回中的模型补进下拉框。
function ensureModelOptions(providerModels: string[]) {
  mergeModelOptions([
    ...modelOptions.value,
    ...uniqueProviderModels(providerModels).map((providerModel) => ({ label: providerModel, value: providerModel })),
  ])
}

// 只清理用户填写的别名；留空表示后端按前缀自动生成对外模型名。
function normalizeModelAlias(model: ChannelModelForm) {
  model.modelAlias = model.modelAlias?.trim() || ''
  model.publicName = model.modelAlias
}

// 优先展示后端校验或上游错误详情，便于管理员排查供应商失败。
function errorMessage(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message || fallback
}

// 从管理端聚合接口加载已保存渠道。
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

// 实时请求当前渠道上游额度，结果只保存在页面状态中。
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

// 以创建模式打开弹窗，并清空之前的模型选项。
function showCreate() {
  editingId.value = null
  form.value = emptyForm()
  selectedProviderModels.value = []
  modelOptions.value = []
  oauthAuthorizationUrl.value = ''
  oauthCallbackUrl.value = ''
  showModal.value = true
}

// 以编辑模式打开弹窗；apiKey 留空表示不替换现有密钥。
function edit(item: ChannelVO) {
  editingId.value = item.id
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
    })),
    enabled: item.enabled,
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

// 通过后端供应商特定实现获取上游模型选项。
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

// 根据弹窗模式创建或更新表单数据。
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

// 删除渠道，并由后端移除其依赖记录。
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

      <n-alert type=”info” title=”渠道用于把网关请求转发到指定上游”>
        选择供应商后填写上游 Base URL、实际请求路径和 API Key。模型支持多选和手动输入；别名非必填，填写后模型管理中会按别名单独展示，未填写时使用”模型前缀/上游模型名”。
      </n-alert>

      <n-data-table :columns="columns" :data="data" :loading="loading" :pagination="false" />
    </n-space>

    <n-modal v-model:show="showModal" :title="editingId ? '编辑渠道' : '新增渠道'">
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
          <n-form-item v-if="!isAuthType(form.type)" label="请求路径">
            <n-input v-model:value="form.chatPath" placeholder="例如：/v1/chat/completions 或 /v1/messages" />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="视频接口路径">
            <n-input v-model:value="form.videoPath" placeholder="例如：/v1/videos" />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="图片接口路径">
            <n-input v-model:value="form.imagePath" placeholder="例如：/v1/images/generations" />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="模型列表路径">
            <n-input v-model:value="form.modelsPath" placeholder="例如：/v1/models" />
          </n-form-item>
          <n-form-item v-if="!isAuthType(form.type)" label="API Key">
            <n-input
              v-model:value="form.apiKey"
              type="password"
              show-password-on="click"
              :placeholder="editingId ? '留空表示不修改密钥' : '请输入上游 API Key'"
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
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
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
</style>
