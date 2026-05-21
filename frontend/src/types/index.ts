export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 已保存自定义上游渠道的管理端视图，apiKey 始终由后端脱敏。 */
export interface ChannelVO {
  /** 渠道记录 ID。 */
  id: number
  /** 稳定的渠道编码，创建后不允许修改。 */
  code: string
  /** 管理页面展示名称。 */
  name: string
  /** 供应商类型（ProviderType），例如 OPENAI_COMPATIBLE、ANTHROPIC、OPENAI_RESPONSES、GEMINI。 */
  type: string
  /** 渠道是否可用于路由。 */
  enabled: boolean
  /** 上游服务 Base URL。 */
  baseUrl: string
  /** 供应商特定的对话或消息请求路径。 */
  chatPath: string
  /** 供应商特定的模型列表请求路径。 */
  modelsPath: string
  /** 渠道密钥兼容字段，当前等同于渠道 ID。 */
  credentialId: number | null
  /** 渠道密钥展示名，不能作为真实凭证使用。 */
  credentialName: string
  /** 已脱敏的 API Key，不要把该值当作真实密钥提交回后端。 */
  apiKey: string
  /** 渠道鉴权模式：API_KEY、AUTH_FILE 或 OAUTH。 */
  authMode: string
  /** auth.json/OAuth 授权状态。 */
  authStatus: string
  /** 脱敏后的授权身份摘要。 */
  authSubject: string | null
  /** access token 过期时间。 */
  authExpiresAt: string | null
  /** 是否已经绑定授权文件。 */
  hasAuthFile: boolean
  /** 渠道路由权重，加权模式下数值越高分配流量越多。 */
  priority: number
  /** 渠道状态，例如 ACTIVE 或 DISABLED。 */
  status: string
  /** 绑定到该渠道的模型映射数量。 */
  modelCount: number
  /** 渠道已保存的模型映射列表。 */
  models: ChannelModelMappingVO[]
}

/** 用户点击刷新时实时获取的渠道额度，不持久化。 */
export interface ChannelQuotaVO {
  channelId: number
  channelCode: string
  supported: boolean
  summary: string
  balance: number | null
  used: number | null
  available: number | null
  currency: string
  rawSummary: string
}

/** 模型管理页聚合后的模型视图。 */
export interface ModelVO {
  id: number
  publicName: string
  providerCode: string
  providerModel: string
  /** 是否支持图片/视觉输入。 */
  vision: boolean | null
  /** 是否支持工具/函数调用。 */
  toolsSupport: boolean | null
  /** 是否支持 JSON 输出模式。 */
  jsonModeSupport: boolean | null
  /** 最大上下文窗口（token 数）。 */
  contextLength: number | null
  enabled: boolean
  channelCount: number
  providerCodes: string[]
  providerModels: string[]
  inputQuotaPerMillion: number | null
  outputQuotaPerMillion: number | null
  cacheReadQuotaPerMillion: number | null
}

/** 管理端网关密钥视图，rawKey 仅用于历史兼容，界面应优先展示脱敏预览。 */
export interface ApiKeyVO {
  id: number
  name: string
  rawKey: string
  keyPrefix: string
  keyPreview: string
  status: string
  quotaBalance: number | null
  quotaLimit: number | null
  quotaWindowValue: number | null
  quotaWindowUnit: string | null
  channelCodes: string[]
  modelNames: string[]
  limits: ApiKeyLimitVO[]
}

/** 新建网关密钥后返回的结果，rawKey 只在创建响应中用于管理员复制。 */
export interface ApiKeyCreationVO {
  id: number
  name: string
  rawKey: string
  keyPreview: string
  status: string
  quotaBalance: number | null
  quotaLimit: number | null
  quotaWindowValue: number | null
  quotaWindowUnit: string | null
  channelCodes: string[]
  modelNames: string[]
  limits: ApiKeyLimitVO[]
}

/** 网关密钥限制项视图，支持额度和请求数等滑动窗口限制。 */
export interface ApiKeyLimitVO {
  id: number | null
  limitType: string
  windowValue: number | null
  windowUnit: string | null
  limitValue: number | null
  configJson?: string | null
}

export interface RequestLogVO {
  id: number
  requestId: string
  gatewayApiKeyId: number | null
  gatewayApiKeyName: string | null
  gatewayApiKeyPreview: string | null
  sourceProtocol: string
  requestType: string
  providerCode: string | null
  providerType: string | null
  publicModel: string | null
  providerModel: string | null
  stream: boolean
  success: boolean
  httpStatus: number | null
  latencyMs: number | null
  inputTokens: number | null
  cacheReadInputTokens: number | null
  outputTokens: number | null
  totalTokens: number | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string | null
}

export interface AdminLoginVO {
  token: string
  username: string
}

export interface RoutingConfigVO {
  mode: string
  failureThreshold: number
  failureCooldownMinutes: number
  stickyTtlMinutes: number
}

export interface RoutingConfigForm {
  mode: string
  failureThreshold: number
  failureCooldownMinutes: number
  stickyTtlMinutes: number
}

/** 控制台展示的网关外部调用信息，不包含任何密钥。 */
export interface GatewayInfoVO {
  /** 当前后端接口 Base URL，前端展示时会与端点路径组合成完整调用地址。 */
  baseUrl: string
  /** 当前后端已支持的公开调用端点。 */
  endpoints: GatewayEndpointVO[]
}

/** 单个网关端点的展示信息。 */
export interface GatewayEndpointVO {
  /** HTTP 方法，例如 GET 或 POST。 */
  method: string
  /** 端点路径，例如 /v1/chat/completions。 */
  path: string
  /** 兼容协议或通用分类。 */
  protocol: string
  /** 调用该端点所需的鉴权方式。 */
  auth: string
  /** 面向管理员的简短用途说明。 */
  description: string
}

export interface DashboardStatsVO {
  summary: DashboardSummaryVO
  dailyTokenUsage: DashboardTokenPointVO[]
  hourlyTokenUsage: DashboardTokenPointVO[]
  modelDistribution: DashboardDimensionUsageVO[]
  channelDistribution: DashboardDimensionUsageVO[]
  apiKeyDistribution: DashboardDimensionUsageVO[]
  modelSeries: DashboardSeriesVO[]
  channelSeries: DashboardSeriesVO[]
  apiKeySeries: DashboardSeriesVO[]
}

export interface DashboardSummaryVO {
  requestCount: number
  successCount: number
  failureCount: number
  inputTokens: number
  cacheReadInputTokens: number
  outputTokens: number
  totalTokens: number
}

export interface DashboardTokenPointVO {
  label: string
  requestCount: number
  inputTokens: number
  cacheReadInputTokens: number
  outputTokens: number
  totalTokens: number
}

export interface DashboardDimensionUsageVO {
  key: string
  name: string
  requestCount: number
  successCount: number
  failureCount: number
  inputTokens: number
  cacheReadInputTokens: number
  outputTokens: number
  totalTokens: number
}

export interface DashboardSeriesVO {
  key: string
  name: string
  points: DashboardSeriesPointVO[]
}

export interface DashboardSeriesPointVO {
  label: string
  totalTokens: number
}

/** 创建或更新渠道聚合配置的表单载荷。 */
export interface ChannelForm {
  /** 稳定的渠道编码，创建后不允许修改。 */
  code: string
  /** 管理页面展示名称。 */
  name: string
  /** 供应商类型（ProviderType），用于选择后端供应商实现。 */
  type: string
  /** 上游服务 Base URL。 */
  baseUrl: string
  /** 供应商特定的对话或消息请求路径。 */
  chatPath: string
  /** 供应商特定的模型列表请求路径。 */
  modelsPath: string
  /** 原始供应商密钥；更新时为空表示保留现有密钥。 */
  apiKey: string
  /** 渠道鉴权模式，AUTH 类型默认为 AUTH_FILE。 */
  authMode?: string
  /** 渠道路由权重，加权模式下数值越高分配流量越多。 */
  priority: number
  /** 渠道状态，例如 ACTIVE 或 DISABLED。 */
  status: string
  /** 单模型兼容字段，批量模型列表为空时使用。 */
  publicModel: string
  /** 单模型兼容字段，批量模型列表为空时使用。 */
  providerModel: string
  /** 保存模型时拼到对外模型名前方的可选前缀。 */
  modelPrefix: string
  /** 批量保存的模型映射列表，优先于单模型兼容字段。 */
  models: ChannelModelForm[]
  /** 渠道是否启用。 */
  enabled: boolean
}

/** 渠道表单中的单个模型映射项。 */
export interface ChannelModelForm {
  /** 网关对外暴露的模型名；为空时由前缀和上游模型名生成。 */
  publicName: string
  /** 上游供应商真实模型 ID。 */
  providerModel: string
  /** 用户手动设置的模型别名；为空时使用默认对外模型名。 */
  modelAlias: string
  /** 每 100 万普通输入 token 消耗的额度。 */
  inputQuotaPerMillion?: number | null
  /** 每 100 万输出 token 消耗的额度。 */
  outputQuotaPerMillion?: number | null
  /** 每 100 万缓存读取输入 token 消耗的额度。 */
  cacheReadQuotaPerMillion?: number | null
  /** 是否支持图片/视觉输入。 */
  vision?: boolean | null
  /** 是否支持工具/函数调用。 */
  toolsSupport?: boolean | null
  /** 是否支持 JSON 输出模式。 */
  jsonModeSupport?: boolean | null
  /** 最大上下文窗口（token 数）。 */
  contextLength?: number | null
}

/** 渠道详情中返回的模型映射项。 */
export interface ChannelModelMappingVO {
  /** 模型映射记录 ID。 */
  id: number
  /** 网关对外暴露的模型名。 */
  publicName: string
  /** 上游供应商真实模型 ID。 */
  providerModel: string
  /** 用户手动设置的模型别名；为空时使用默认对外模型名。 */
  modelAlias: string | null
  /** 该模型映射是否启用。 */
  enabled: boolean
  /** 每 100 万普通输入 token 消耗的额度。 */
  inputQuotaPerMillion: number | null
  /** 每 100 万输出 token 消耗的额度。 */
  outputQuotaPerMillion: number | null
  /** 每 100 万缓存读取输入 token 消耗的额度。 */
  cacheReadQuotaPerMillion: number | null
  /** 是否支持图片/视觉输入。 */
  vision: boolean | null
  /** 是否支持工具/函数调用。 */
  toolsSupport: boolean | null
  /** 是否支持 JSON 输出模式。 */
  jsonModeSupport: boolean | null
  /** 最大上下文窗口（token 数）。 */
  contextLength: number | null
}

/** 后端获取上游模型选项所需的未保存表单值。 */
export interface ChannelModelFetchRequest {
  /** 供应商类型（ProviderType），用于选择后端供应商客户端。 */
  type: string
  /** 编辑已有渠道时传递渠道 ID，允许后端在密钥留空时读取已保存密钥。 */
  channelId?: number | null
  /** 上游服务 Base URL。 */
  baseUrl: string
  /** 供应商特定的模型列表请求路径。 */
  modelsPath: string
  /** 仅用于本次获取请求的原始供应商密钥。 */
  apiKey: string
}

/** 供应商特定模型发现返回的模型选项。 */
export interface UpstreamModelVO {
  /** 可写入 providerModel 的上游模型 ID。 */
  id: string
  /** 下拉框中展示的可选归属文本。 */
  ownedBy: string
}

export interface ChannelAuthStartVO {
  channelId: number
  providerType: string
  authorizationUrl: string
  state: string
}

export interface ChannelAuthStatusVO {
  channelId: number
  providerType: string
  authMode: string
  authStatus: string
  authSubject: string | null
  authExpiresAt: string | null
  hasAuthFile: boolean
}

/** 创建网关密钥的表单载荷，空渠道/模型列表表示不限制对应范围。 */
export interface ApiKeyForm {
  name: string
  channelCodes: string[]
  modelNames: string[]
  quotaBalance?: number | null
  quotaLimit?: number | null
  quotaWindowValue?: number | null
  quotaWindowUnit?: string | null
  limits: ApiKeyLimitForm[]
}

/** 更新网关密钥的表单载荷，limits 为空数组表示清空所有窗口限制。 */
export interface ApiKeyUpdateForm {
  status: string
  channelCodes: string[]
  modelNames: string[]
  quotaLimit?: number | null
  quotaWindowValue?: number | null
  quotaWindowUnit?: string | null
  limits: ApiKeyLimitForm[]
}

/** 单条网关密钥限制项表单，不能包含任何密钥或 token 配置。 */
export interface ApiKeyLimitForm {
  limitType: string
  windowValue: number | null
  windowUnit: string | null
  limitValue: number | null
  configJson?: string | null
}

export interface ApiKeyQuotaAddRequest {
  amount: number
}

export interface ModelQuotaForm {
  inputQuotaPerMillion: number | null
  outputQuotaPerMillion: number | null
  cacheReadQuotaPerMillion: number | null
}

export interface ModelEnabledForm {
  enabled: boolean
}

/** 管理端更新模型能力配置的请求体。 */
export interface ModelCapabilitiesForm {
  /** 是否支持图片/视觉输入。 */
  vision: boolean | null
  /** 是否支持工具/函数调用。 */
  toolsSupport: boolean | null
  /** 是否支持 JSON 输出模式。 */
  jsonModeSupport: boolean | null
  /** 最大上下文窗口（token 数）。 */
  contextLength: number | null
}

export interface RequestLogSearchParam {
  requestId?: string
  gatewayApiKeyId?: number
  sourceProtocol?: string
  requestType?: string
  providerCode?: string
  providerType?: string
  publicModel?: string
  success?: boolean
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

/** 渠道聚合表单当前支持的供应商策略类型。 */
export const channelTypes = ['OPENAI_COMPATIBLE', 'ANTHROPIC', 'OPENAI_RESPONSES', 'GPT_AUTH', 'CLAUDE_AUTH', 'DEEPSEEK_CHAT', 'DEEPSEEK_ANTHROPIC', 'GEMINI']
export const activeStatuses = ['ACTIVE', 'DISABLED', 'EXPIRED']
/** 管理端可选的滑动窗口单位，请求数可用分钟，额度界面会隐藏分钟。 */
export const quotaWindowUnits = ['MINUTE', 'HOUR', 'DAY']
/** 管理端当前开放的密钥限制类型，表结构保留未来扩展能力。 */
export const apiKeyLimitTypes = ['QUOTA', 'REQUEST']
export const routeModes = ['RANDOM', 'ROUND_ROBIN', 'WEIGHTED', 'SESSION_STICKY']
