import { computed, ref, type Ref } from 'vue'
import { channelTypes, supplierDefaultEndpoints, capabilityDefaultPaths, endpointLabels, endpointTypeOptions } from '@/types'
import type { ChannelCapability, ChannelForm, ChannelModelForm, ChannelVO } from '@/types'

/**
 * 供应商类型 label 映射：与 AdminChannelService.DEFAULT_* 解耦，纯前端展示用。
 */
export function channelTypeLabel(type: string): string {
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

export function isAuthType(type: string | undefined): boolean {
  return type === 'GPT_AUTH' || type === 'CLAUDE_AUTH'
}

/**
 * 渠道供应商预设默认值：baseUrl / 各端点路径，类型切换时按需自动填充。
 */
const SUPPLIER_PRESETS: Record<string, {
  baseUrl: string
  chatPath: string
  videoPath: string
  imagePath: string
  embeddingPath: string
  audioSpeechPath: string
  audioTranscriptionPath: string
  modelsPath: string
}> = {
  OPENAI: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  ANTHROPIC: { baseUrl: 'https://api.anthropic.com', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  CUSTOM: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  MIMO_TOKEN_PLAN: { baseUrl: 'https://token-plan-cn.xiaomimimo.com', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  DEEPSEEK: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  VOLC_CODINGPLAN: { baseUrl: 'https://ark.cn-beijing.volces.com/api/coding', chatPath: '/v3/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v3/models' },
  OPENCODE: { baseUrl: '', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  GEMINI: { baseUrl: '', chatPath: '/v1beta/models', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1beta/models' },
  GPT_AUTH: { baseUrl: 'https://api.openai.com', chatPath: '/v1/chat/completions', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
  CLAUDE_AUTH: { baseUrl: 'https://api.anthropic.com', chatPath: '/v1/messages', videoPath: '/v1/videos', imagePath: '/v1/images/generations', embeddingPath: '/v1/embeddings', audioSpeechPath: '/v1/audio/speech', audioTranscriptionPath: '/v1/audio/transcriptions', modelsPath: '/v1/models' },
}

const DEFAULT_PATHS = new Set(
  Object.values(SUPPLIER_PRESETS).flatMap(p => [p.chatPath, p.videoPath, p.imagePath, p.embeddingPath, p.audioSpeechPath, p.audioTranscriptionPath, p.modelsPath])
)

function emptyForm(): ChannelForm {
  return {
    code: '',
    name: '',
    type: 'OPENAI',
    baseUrl: '',
    chatPath: '/v1/chat/completions',
    videoPath: '/v1/videos',
    imagePath: '/v1/images/generations',
    embeddingPath: '/v1/embeddings',
    audioSpeechPath: '/v1/audio/speech',
    audioTranscriptionPath: '/v1/audio/transcriptions',
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

function fromChannel(item: ChannelVO): ChannelForm {
  return {
    code: item.code,
    name: item.name,
    type: item.type,
    baseUrl: item.baseUrl,
    chatPath: item.chatPath,
    videoPath: item.videoPath || '/v1/videos',
    imagePath: item.imagePath || '/v1/images/generations',
    embeddingPath: item.embeddingPath || '/v1/embeddings',
    audioSpeechPath: item.audioSpeechPath || '/v1/audio/speech',
    audioTranscriptionPath: item.audioTranscriptionPath || '/v1/audio/transcriptions',
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
}

function uniqueProviderModels(providerModels: Array<string | undefined>): string[] {
  return Array.from(new Set(
    providerModels.map(p => p?.trim()).filter((p): p is string => !!p)
  ))
}

function buildPublicModelName(providerModel: string, prefix: string): string {
  const normalizedPrefix = prefix.trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return normalizedPrefix ? `${normalizedPrefix}/${providerModel}` : providerModel
}

function parseEndpointTypes(value: string | null | undefined): string[] {
  if (!value) return []
  return value.split(',').map(s => s.trim()).filter(Boolean)
}

function normalizeModelAlias(model: ChannelModelForm): void {
  model.modelAlias = model.modelAlias?.trim() || ''
  model.publicName = model.modelAlias
}

/**
 * 渠道表单状态机：维护 form / modelOptions / selectedProviderModels / showModal / modalMode / editingId，
 * 并暴露 handleTypeChange / syncSelectedModels / mergeModelOptions / buildPublicModelName / parseEndpointTypes / normalizeModelAlias
 * 等业务方法，让 ChannelList.vue 不再关心 80% 的字段搬运逻辑。
 */
export function useChannelForm() {
  const form = ref<ChannelForm>(emptyForm())
  const modelOptions = ref<{ label: string; value: string }[]>([])
  const selectedProviderModels = ref<string[]>([])
  const showModal = ref(false)
  const modalMode = ref<'create' | 'edit' | 'copy'>('create')
  const editingId = ref<number | null>(null)

  const configuredCapabilityOptions = computed(() => {
    const caps = form.value.capabilities || []
    if (caps.length === 0) return endpointTypeOptions
    const types = new Set(caps.map(c => c.type))
    return endpointTypeOptions.filter(opt => types.has(opt.value))
  })

  function showCreate() {
    editingId.value = null
    modalMode.value = 'create'
    form.value = emptyForm()
    selectedProviderModels.value = []
    modelOptions.value = []
    showModal.value = true
  }

  function showEdit(item: ChannelVO) {
    editingId.value = item.id
    modalMode.value = 'edit'
    form.value = fromChannel(item)
    selectedProviderModels.value = uniqueProviderModels(form.value.models.map(m => m.providerModel))
    modelOptions.value = []
    ensureModelOptions(selectedProviderModels.value)
    showModal.value = true
  }

  function showCopy(source: ChannelVO) {
    editingId.value = null
    modalMode.value = 'copy'
    form.value = fromChannel(source)
    form.value.code = ''
    form.value.name = ''
    form.value.apiKey = ''
    selectedProviderModels.value = uniqueProviderModels(form.value.models.map(m => m.providerModel))
    modelOptions.value = []
    ensureModelOptions(selectedProviderModels.value)
    showModal.value = true
  }

  function closeModal() {
    showModal.value = false
  }

  function handleTypeChange(type: string) {
    // 已存在模型时同步刷新 allowedEndpointTypes 默认值
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
    const preset = SUPPLIER_PRESETS[type]
    if (!preset) return
    if (preset.baseUrl) form.value.baseUrl = preset.baseUrl
    if (DEFAULT_PATHS.has(form.value.chatPath)) form.value.chatPath = preset.chatPath
    if (DEFAULT_PATHS.has(form.value.videoPath)) form.value.videoPath = preset.videoPath
    if (DEFAULT_PATHS.has(form.value.imagePath)) form.value.imagePath = preset.imagePath
    if (DEFAULT_PATHS.has(form.value.embeddingPath)) form.value.embeddingPath = preset.embeddingPath
    if (DEFAULT_PATHS.has(form.value.audioSpeechPath)) form.value.audioSpeechPath = preset.audioSpeechPath
    if (DEFAULT_PATHS.has(form.value.audioTranscriptionPath)) form.value.audioTranscriptionPath = preset.audioTranscriptionPath
    if (DEFAULT_PATHS.has(form.value.modelsPath)) form.value.modelsPath = preset.modelsPath
    form.value.authMode = isAuthType(type) ? 'AUTH_FILE' : 'API_KEY'
    form.value.capabilities = []
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

  function ensureModelOptions(providerModels: string[]) {
    const merged = new Map<string, { label: string; value: string }>()
    for (const option of modelOptions.value) {
      const value = option.value?.trim()
      if (!value || merged.has(value)) continue
      merged.set(value, { label: option.label || value, value })
    }
    for (const providerModel of uniqueProviderModels(providerModels)) {
      if (merged.has(providerModel)) continue
      merged.set(providerModel, { label: providerModel, value: providerModel })
    }
    modelOptions.value = Array.from(merged.values())
  }

  function mergeFetchedModels(fetched: { id: string; ownedBy?: string }[]) {
    const selected = new Set(uniqueProviderModels(selectedProviderModels.value))
    const fetchedOptions = fetched.map((model) => ({
      label: model.ownedBy ? `${model.id}（${model.ownedBy}）` : model.id,
      value: model.id,
    }))
    ensureModelOptions(
      [
        ...fetchedOptions.map(o => o.value),
        ...modelOptions.value.filter((option) => selected.has(option.value) && !fetchedOptions.some((item) => item.value === option.value))
          .map(o => o.value),
      ]
    )
  }

  function updateCapabilities(types: string[]) {
    const existing = form.value.capabilities || []
    const currentTypes = new Set(existing.map(c => c.type))
    const newSet = new Set(types)
    const added = types.filter(t => !currentTypes.has(t))
    const removed = new Set(Array.from(currentTypes).filter(t => !newSet.has(t)))
    const kept = existing.filter(c => !removed.has(c.type))
    for (const t of added) {
      kept.push({ type: t, path: capabilityDefaultPaths[t]?.[form.value.type] || '' })
    }
    form.value.capabilities = kept
    if (removed.size > 0) {
      form.value.models = form.value.models.map((m: ChannelModelForm) => {
        const caps = parseEndpointTypes(m.allowedCapabilities)
        const filtered = caps.filter(c => !removed.has(c))
        return { ...m, allowedCapabilities: filtered.join(',') }
      })
    }
  }

  function normalizeAllAliases() {
    form.value.models.forEach(normalizeModelAlias)
  }

  return {
    form, modelOptions, selectedProviderModels, showModal, modalMode, editingId,
    configuredCapabilityOptions,
    showCreate, showEdit, showCopy, closeModal,
    handleTypeChange, syncSelectedModels, ensureModelOptions, mergeFetchedModels,
    updateCapabilities, normalizeAllAliases,
    buildPublicModelName, parseEndpointTypes, channelTypeLabel, isAuthType,
  }
}

export type ChannelFormApi = ReturnType<typeof useChannelForm>

/**
 * 用于 reset 场景的 hook：保留引用相等性，便于父组件用 v-model 引用 form。
 */
export function useFormRef() {
  return ref<ChannelForm>(emptyForm())
}
