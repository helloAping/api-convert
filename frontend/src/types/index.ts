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

/** 宸蹭繚瀛樿嚜瀹氫箟涓婃父娓犻亾鐨勭鐞嗙瑙嗗浘锛宎piKey 濮嬬粓鐢卞悗绔劚鏁忋€?*/
export interface ChannelVO {
  /** 娓犻亾璁板綍 ID銆?*/
  id: number
  /** 绋冲畾鐨勬笭閬撶紪鐮侊紝鍒涘缓鍚庝笉鍏佽淇敼銆?*/
  code: string
  /** 绠＄悊椤甸潰灞曠ず鍚嶇О銆?*/
  name: string
  /** 渚涘簲鍟嗙被鍨嬶紙ProviderType锛夛紝渚嬪 OPENAI_COMPATIBLE銆丄NTHROPIC銆丱PENAI_RESPONSES銆丟EMINI銆?*/
  type: string
  /** 娓犻亾鏄惁鍙敤浜庤矾鐢便€?*/
  enabled: boolean
  /** 涓婃父鏈嶅姟 Base URL銆?*/
  baseUrl: string
  /** 渚涘簲鍟嗙壒瀹氱殑瀵硅瘽鎴栨秷鎭姹傝矾寰勩€?*/
  chatPath: string
  /** 渚涘簲鍟嗙壒瀹氱殑瑙嗛鐢熸垚璇锋眰璺緞銆?*/
  videoPath: string
  /** 渚涘簲鍟嗙壒瀹氱殑鍥剧墖鐢熸垚璇锋眰璺緞銆?*/
  imagePath: string
  /** 渚涘簲鍟嗙壒瀹氱殑妯″瀷鍒楄〃璇锋眰璺緞銆?*/
  modelsPath: string
  /** 娓犻亾瀵嗛挜鍏煎瀛楁锛屽綋鍓嶇瓑鍚屼簬娓犻亾 ID銆?*/
  credentialId: number | null
  /** 娓犻亾瀵嗛挜灞曠ず鍚嶏紝涓嶈兘浣滀负鐪熷疄鍑瘉浣跨敤銆?*/
  credentialName: string
  /** 宸茶劚鏁忕殑 API Key锛屼笉瑕佹妸璇ュ€煎綋浣滅湡瀹炲瘑閽ユ彁浜ゅ洖鍚庣銆?*/
  apiKey: string
  /** 娓犻亾閴存潈妯″紡锛欰PI_KEY銆丄UTH_FILE 鎴?OAUTH銆?*/
  authMode: string
  /** auth.json/OAuth 鎺堟潈鐘舵€併€?*/
  authStatus: string
  /** 鑴辨晱鍚庣殑鎺堟潈韬唤鎽樿銆?*/
  authSubject: string | null
  /** access token 杩囨湡鏃堕棿銆?*/
  authExpiresAt: string | null
  /** 鏄惁宸茬粡缁戝畾鎺堟潈鏂囦欢銆?*/
  hasAuthFile: boolean
  /** 娓犻亾璺敱鏉冮噸锛屽姞鏉冩ā寮忎笅鏁板€艰秺楂樺垎閰嶆祦閲忚秺澶氥€?*/
  priority: number
  /** 娓犻亾鐘舵€侊紝渚嬪 ACTIVE 鎴?DISABLED銆?*/
  status: string
  /** 缁戝畾鍒拌娓犻亾鐨勬ā鍨嬫槧灏勬暟閲忋€?*/
  modelCount: number
  /** 渠道已保存的模型映射列表。 */
  models: ChannelModelMappingVO[]
  /** 渠道能力配置列表，每项对应一种上游端点类型及其独立请求路径。 */
  capabilities: ChannelCapability[]
}

/** 鐢ㄦ埛鐐瑰嚮鍒锋柊鏃跺疄鏃惰幏鍙栫殑娓犻亾棰濆害锛屼笉鎸佷箙鍖栥€?*/
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

/** 妯″瀷绠＄悊椤佃仛鍚堝悗鐨勬ā鍨嬭鍥俱€?*/
export interface ModelVO {
  id: number
  publicName: string
  providerCode: string
  providerModel: string
  /** 鏄惁鏀寔鍥剧墖/瑙嗚杈撳叆銆?*/
  vision: boolean | null
  /** 鏄惁鏀寔宸ュ叿/鍑芥暟璋冪敤銆?*/
  toolsSupport: boolean | null
  /** 鏄惁鏀寔 JSON 杈撳嚭妯″紡銆?*/
  jsonModeSupport: boolean | null
  /** 鏈€澶т笂涓嬫枃绐楀彛锛坱oken 鏁帮級銆?*/
  contextLength: number | null
  enabled: boolean
  channelCount: number
  providerCodes: string[]
  providerModels: string[]
  inputQuotaPerMillion: number | null
  outputQuotaPerMillion: number | null
  cacheReadQuotaPerMillion: number | null
}

/** 绠＄悊绔綉鍏冲瘑閽ヨ鍥撅紝rawKey 浠呯敤浜庡巻鍙插吋瀹癸紝鐣岄潰搴斾紭鍏堝睍绀鸿劚鏁忛瑙堛€?*/
export interface ApiKeyVO {
  id: number
  name: string
  rawKey: string
  keyPrefix: string
  keyPreview: string
  status: string
  failoverEnabled: boolean
  quotaBalance: number | null
  quotaLimit: number | null
  quotaWindowValue: number | null
  quotaWindowUnit: string | null
  channelCodes: string[]
  modelNames: string[]
  limits: ApiKeyLimitVO[]
}

/** 鏂板缓缃戝叧瀵嗛挜鍚庤繑鍥炵殑缁撴灉锛宺awKey 鍙湪鍒涘缓鍝嶅簲涓敤浜庣鐞嗗憳澶嶅埗銆?*/
export interface ApiKeyCreationVO {
  id: number
  name: string
  rawKey: string
  keyPreview: string
  status: string
  failoverEnabled: boolean
  quotaBalance: number | null
  quotaLimit: number | null
  quotaWindowValue: number | null
  quotaWindowUnit: string | null
  channelCodes: string[]
  modelNames: string[]
  limits: ApiKeyLimitVO[]
}

/** 缃戝叧瀵嗛挜闄愬埗椤硅鍥撅紝鏀寔棰濆害鍜岃姹傛暟绛夋粦鍔ㄧ獥鍙ｉ檺鍒躲€?*/
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

/** 鎺у埗鍙板睍绀虹殑缃戝叧澶栭儴璋冪敤淇℃伅锛屼笉鍖呭惈浠讳綍瀵嗛挜銆?*/
export interface GatewayInfoVO {
  /** 褰撳墠鍚庣鎺ュ彛 Base URL锛屽墠绔睍绀烘椂浼氫笌绔偣璺緞缁勫悎鎴愬畬鏁磋皟鐢ㄥ湴鍧€銆?*/
  baseUrl: string
  /** 褰撳墠鍚庣宸叉敮鎸佺殑鍏紑璋冪敤绔偣銆?*/
  endpoints: GatewayEndpointVO[]
}

/** 鍗曚釜缃戝叧绔偣鐨勫睍绀轰俊鎭€?*/
export interface GatewayEndpointVO {
  /** HTTP 鏂规硶锛屼緥濡?GET 鎴?POST銆?*/
  method: string
  /** 绔偣璺緞锛屼緥濡?/v1/chat/completions銆?*/
  path: string
  /** 鍏煎鍗忚鎴栭€氱敤鍒嗙被銆?*/
  protocol: string
  /** 璋冪敤璇ョ鐐规墍闇€鐨勯壌鏉冩柟寮忋€?*/
  auth: string
  /** 闈㈠悜绠＄悊鍛樼殑绠€鐭敤閫旇鏄庛€?*/
  description: string
}

export interface DashboardStatsVO {
  summary: DashboardSummaryVO
  tokenUsage: DashboardTokenPointVO[]
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

/** 鍒涘缓鎴栨洿鏂版笭閬撹仛鍚堥厤缃殑琛ㄥ崟杞借嵎銆?*/

/** 渠道能力配置项，每项对应一种上游端点类型及其独立请求路径。 */
export interface ChannelCapability {
  /** 端点类型名称，对应 EndpointType 枚举值 */
  type: string
  /** 该能力对应的上游请求路径 */
  path: string
}
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
  /** 供应商特定的视频生成请求路径。 */
  videoPath: string
  /** 供应商特定的图片生成请求路径。 */
  imagePath: string
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
  /** 渠道能力配置列表，每项对应一种上游端点类型及其独立请求路径。 */
  capabilities?: ChannelCapability[]
}

/** 娓犻亾琛ㄥ崟涓殑鍗曚釜妯″瀷鏄犲皠椤广€?*/
export interface ChannelModelForm {
  /** 缃戝叧瀵瑰鏆撮湶鐨勬ā鍨嬪悕锛涗负绌烘椂鐢卞墠缂€鍜屼笂娓告ā鍨嬪悕鐢熸垚銆?*/
  publicName: string
  /** 涓婃父渚涘簲鍟嗙湡瀹炴ā鍨?ID銆?*/
  providerModel: string
  /** 鐢ㄦ埛鎵嬪姩璁剧疆鐨勬ā鍨嬪埆鍚嶏紱涓虹┖鏃朵娇鐢ㄩ粯璁ゅ澶栨ā鍨嬪悕銆?*/
  modelAlias: string
  /** 姣?100 涓囨櫘閫氳緭鍏?token 娑堣€楃殑棰濆害銆?*/
  inputQuotaPerMillion?: number | null
  /** 姣?100 涓囪緭鍑?token 娑堣€楃殑棰濆害銆?*/
  outputQuotaPerMillion?: number | null
  /** 姣?100 涓囩紦瀛樿鍙栬緭鍏?token 娑堣€楃殑棰濆害銆?*/
  cacheReadQuotaPerMillion?: number | null
  /** 鏄惁鏀寔鍥剧墖/瑙嗚杈撳叆銆?*/
  vision?: boolean | null
  /** 鏄惁鏀寔宸ュ叿/鍑芥暟璋冪敤銆?*/
  toolsSupport?: boolean | null
  /** 鏄惁鏀寔 JSON 杈撳嚭妯″紡銆?*/
  jsonModeSupport?: boolean | null
  /** 鏈€澶т笂涓嬫枃绐楀彛锛坱oken 鏁帮級銆?*/
  contextLength?: number | null
  /** 閫楀彿鍒嗛殧鐨?EndpointType 鍚嶇О锛涗负绌烘垨绌哄瓧绗︿覆琛ㄧず涓嶉檺鍒剁鐐圭被鍨嬨€?*/
  allowedEndpointTypes?: string | null
}

/** 娓犻亾璇︽儏涓繑鍥炵殑妯″瀷鏄犲皠椤广€?*/
export interface ChannelModelMappingVO {
  /** 妯″瀷鏄犲皠璁板綍 ID銆?*/
  id: number
  /** 缃戝叧瀵瑰鏆撮湶鐨勬ā鍨嬪悕銆?*/
  publicName: string
  /** 涓婃父渚涘簲鍟嗙湡瀹炴ā鍨?ID銆?*/
  providerModel: string
  /** 鐢ㄦ埛鎵嬪姩璁剧疆鐨勬ā鍨嬪埆鍚嶏紱涓虹┖鏃朵娇鐢ㄩ粯璁ゅ澶栨ā鍨嬪悕銆?*/
  modelAlias: string | null
  /** 璇ユā鍨嬫槧灏勬槸鍚﹀惎鐢ㄣ€?*/
  enabled: boolean
  /** 姣?100 涓囨櫘閫氳緭鍏?token 娑堣€楃殑棰濆害銆?*/
  inputQuotaPerMillion: number | null
  /** 姣?100 涓囪緭鍑?token 娑堣€楃殑棰濆害銆?*/
  outputQuotaPerMillion: number | null
  /** 姣?100 涓囩紦瀛樿鍙栬緭鍏?token 娑堣€楃殑棰濆害銆?*/
  cacheReadQuotaPerMillion: number | null
  /** 鏄惁鏀寔鍥剧墖/瑙嗚杈撳叆銆?*/
  vision: boolean | null
  /** 鏄惁鏀寔宸ュ叿/鍑芥暟璋冪敤銆?*/
  toolsSupport: boolean | null
  /** 鏄惁鏀寔 JSON 杈撳嚭妯″紡銆?*/
  jsonModeSupport: boolean | null
  /** 鏈€澶т笂涓嬫枃绐楀彛锛坱oken 鏁帮級銆?*/
  contextLength: number | null
  /** 閫楀彿鍒嗛殧鐨?EndpointType 鍚嶇О锛涗负绌鸿〃绀轰笉闄愬埗绔偣绫诲瀷銆?*/
  allowedEndpointTypes: string | null
}

/** 鍚庣鑾峰彇涓婃父妯″瀷閫夐」鎵€闇€鐨勬湭淇濆瓨琛ㄥ崟鍊笺€?*/
export interface ChannelModelFetchRequest {
  /** 渚涘簲鍟嗙被鍨嬶紙ProviderType锛夛紝鐢ㄤ簬閫夋嫨鍚庣渚涘簲鍟嗗鎴风銆?*/
  type: string
  /** 缂栬緫宸叉湁娓犻亾鏃朵紶閫掓笭閬?ID锛屽厑璁稿悗绔湪瀵嗛挜鐣欑┖鏃惰鍙栧凡淇濆瓨瀵嗛挜銆?*/
  channelId?: number | null
  /** 涓婃父鏈嶅姟 Base URL銆?*/
  baseUrl: string
  /** 渚涘簲鍟嗙壒瀹氱殑妯″瀷鍒楄〃璇锋眰璺緞銆?*/
  modelsPath: string
  /** 浠呯敤浜庢湰娆¤幏鍙栬姹傜殑鍘熷渚涘簲鍟嗗瘑閽ャ€?*/
  apiKey: string
}

/** 渚涘簲鍟嗙壒瀹氭ā鍨嬪彂鐜拌繑鍥炵殑妯″瀷閫夐」銆?*/
export interface UpstreamModelVO {
  /** 鍙啓鍏?providerModel 鐨勪笂娓告ā鍨?ID銆?*/
  id: string
  /** 涓嬫媺妗嗕腑灞曠ず鐨勫彲閫夊綊灞炴枃鏈€?*/
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

/** 鍒涘缓缃戝叧瀵嗛挜鐨勮〃鍗曡浇鑽凤紝绌烘笭閬?妯″瀷鍒楄〃琛ㄧず涓嶉檺鍒跺搴旇寖鍥淬€?*/
export interface ApiKeyForm {
  name: string
  failoverEnabled: boolean
  channelCodes: string[]
  modelNames: string[]
  quotaBalance?: number | null
  quotaLimit?: number | null
  quotaWindowValue?: number | null
  quotaWindowUnit?: string | null
  limits: ApiKeyLimitForm[]
}

/** 鏇存柊缃戝叧瀵嗛挜鐨勮〃鍗曡浇鑽凤紝limits 涓虹┖鏁扮粍琛ㄧず娓呯┖鎵€鏈夌獥鍙ｉ檺鍒躲€?*/
export interface ApiKeyUpdateForm {
  status: string
  failoverEnabled: boolean
  channelCodes: string[]
  modelNames: string[]
  quotaLimit?: number | null
  quotaWindowValue?: number | null
  quotaWindowUnit?: string | null
  limits: ApiKeyLimitForm[]
}

/** 鍗曟潯缃戝叧瀵嗛挜闄愬埗椤硅〃鍗曪紝涓嶈兘鍖呭惈浠讳綍瀵嗛挜鎴?token 閰嶇疆銆?*/
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

/** 绠＄悊绔洿鏂版ā鍨嬭兘鍔涢厤缃殑璇锋眰浣撱€?*/
export interface ModelCapabilitiesForm {
  /** 鏄惁鏀寔鍥剧墖/瑙嗚杈撳叆銆?*/
  vision: boolean | null
  /** 鏄惁鏀寔宸ュ叿/鍑芥暟璋冪敤銆?*/
  toolsSupport: boolean | null
  /** 鏄惁鏀寔 JSON 杈撳嚭妯″紡銆?*/
  jsonModeSupport: boolean | null
  /** 鏈€澶т笂涓嬫枃绐楀彛锛坱oken 鏁帮級銆?*/
  contextLength: number | null
}

export interface RequestLogSearchParam {
  requestId?: string
  gatewayApiKeyId?: number
  gatewayApiKeyKeyword?: string
  sourceProtocol?: string
  requestType?: string
  providerCode?: string
  providerType?: string
  publicModel?: string
  providerModel?: string
  stream?: boolean
  success?: boolean
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

/** 渠道聚合表单当前支持的供应商策略类型。 */
export const channelTypes = ['OPENAI', 'DEEPSEEK', 'VOLC_CODINGPLAN', 'OPENCODE', 'GEMINI', 'GPT_AUTH', 'CLAUDE_AUTH']
export const activeStatuses = ['ACTIVE', 'DISABLED', 'EXPIRED']
/** 绠＄悊绔彲閫夌殑婊戝姩绐楀彛鍗曚綅锛岃姹傛暟鍙敤鍒嗛挓锛岄搴︾晫闈細闅愯棌鍒嗛挓銆?*/
export const quotaWindowUnits = ['MINUTE', 'HOUR', 'DAY']
/** 绠＄悊绔綋鍓嶅紑鏀剧殑瀵嗛挜闄愬埗绫诲瀷锛岃〃缁撴瀯淇濈暀鏈潵鎵╁睍鑳藉姏銆?*/
export const apiKeyLimitTypes = ['QUOTA', 'REQUEST']
export const routeModes = ['RANDOM', 'ROUND_ROBIN', 'WEIGHTED', 'SESSION_STICKY']
/** 绠＄悊绔彲閫夌殑绔偣绫诲瀷锛岀敤浜庢寜妯″瀷闄愬埗鍏佽璋冪敤鐨勭鐐广€?*/
/** 每个供应商默认支持的端点列表（能力维度），新建渠道时自动预选。 */
export const supplierDefaultEndpoints: Record<string, string[]> = {
  OPENAI: ['CHAT_COMPLETIONS', 'OPENAI_RESPONSES', 'OPENAI_VIDEOS', 'OPENAI_IMAGES'],
  DEEPSEEK: ['CHAT_COMPLETIONS', 'ANTHROPIC_MESSAGES'],
  VOLC_CODINGPLAN: ['CHAT_COMPLETIONS', 'ANTHROPIC_MESSAGES'],
  OPENCODE: ['CHAT_COMPLETIONS', 'ANTHROPIC_MESSAGES'],
  GEMINI: ['CHAT_COMPLETIONS', 'ANTHROPIC_MESSAGES'],
  GPT_AUTH: ['CHAT_COMPLETIONS', 'OPENAI_VIDEOS', 'OPENAI_IMAGES'],
  CLAUDE_AUTH: ['ANTHROPIC_MESSAGES'],
}

/** 端点类型中文标签，用于管理界面展示。 */
export const endpointLabels: Record<string, string> = {
  CHAT_COMPLETIONS: 'Chat Completions',
  ANTHROPIC_MESSAGES: 'Anthropic Messages',
  OPENAI_RESPONSES: 'Responses API',
  OPENAI_VIDEOS: '视频生成',
  OPENAI_IMAGES: '图像生成',
}

/** 各端点类型在各供应商下的默认请求路径，用于新建渠道时自动填充。 */
export const capabilityDefaultPaths: Record<string, Record<string, string>> = {
  CHAT_COMPLETIONS: {
    OPENAI: '/v1/chat/completions',
    DEEPSEEK: '/v1/chat/completions',
    VOLC_CODINGPLAN: '/v3/chat/completions',
    OPENCODE: '/v1/chat/completions',
    GEMINI: '/v1beta/models',
    GPT_AUTH: '/v1/chat/completions',
    CLAUDE_AUTH: '/v1/chat/completions',
  },
  ANTHROPIC_MESSAGES: {
    OPENAI: '/v1/messages',
    DEEPSEEK: '/v1/messages',
    VOLC_CODINGPLAN: '/v1/messages',
    OPENCODE: '/v1/messages',
    GEMINI: '/v1beta/models',
    GPT_AUTH: '/v1/messages',
    CLAUDE_AUTH: '/v1/messages',
  },
  OPENAI_RESPONSES: {
    OPENAI: '/v1/responses',
    DEEPSEEK: '/v1/responses',
    VOLC_CODINGPLAN: '/v1/responses',
    OPENCODE: '/v1/responses',
    GEMINI: '/v1/responses',
    GPT_AUTH: '/v1/responses',
    CLAUDE_AUTH: '/v1/responses',
  },
  OPENAI_VIDEOS: {
    OPENAI: '/v1/videos',
    DEEPSEEK: '/v1/videos',
    VOLC_CODINGPLAN: '/v1/videos',
    OPENCODE: '/v1/videos',
    GEMINI: '/v1/videos',
    GPT_AUTH: '/v1/videos',
    CLAUDE_AUTH: '/v1/videos',
  },
  OPENAI_IMAGES: {
    OPENAI: '/v1/images/generations',
    DEEPSEEK: '/v1/images/generations',
    VOLC_CODINGPLAN: '/v1/images/generations',
    OPENCODE: '/v1/images/generations',
    GEMINI: '/v1/images/generations',
    GPT_AUTH: '/v1/images/generations',
    CLAUDE_AUTH: '/v1/images/generations',
  },
}

export const endpointTypeOptions = [
  { label: 'Chat Completions', value: 'CHAT_COMPLETIONS' },
  { label: 'Anthropic Messages', value: 'ANTHROPIC_MESSAGES' },
  { label: 'Responses API', value: 'OPENAI_RESPONSES' },
  { label: '视频生成', value: 'OPENAI_VIDEOS' },
  { label: '图像生成', value: 'OPENAI_IMAGES' },
]
