# AI Gateway 项目总览

**api-convert** 是一个 AI API 网关，聚合不同 AI 厂商 API 端点，适配 OpenAI / Claude 等客户端协议，并路由到指定厂商的指定模型。
技术栈：Spring Boot 4.0.6 + Java 25 + Maven + MyBatis-Plus 3.5.16。数据库 schema 版本：**V20**。管理前端：Vue 3.5 + Naive UI + Vite。

---

## 模块文档索引

按业务模块拆分，每个模块独立维护，互不影响。AI 在修改某一模块时只需读取对应文档，同时通过本索引了解其他模块的存在和功能边界。

| 编号 | 模块 | 文件 | 说明 |
|---|---|---|---|
| 01 | **基础设施与数据层** | `modules/01-infrastructure.md` | Spring Boot、MyBatis-Plus、数据库安装/升级、核心数据表、启动引导 |
| 02 | **安全鉴权与限流** | `modules/02-security.md` | API Key 鉴权（SHA-256）、额度计费、滑动窗口限流 |
| 03 | **路由与调度** | `modules/03-routing.md` | 模型路由解析（RANDOM/ROUND_ROBIN/WEIGHTED/SESSION_STICKY）、工具优先、错误避让、请求日志 |
| 04 | **端点与协议适配** | `modules/04-endpoints.md` | 6 个公开端点（CHAT_COMPLETIONS/ANTHROPIC_MESSAGES/OPENAI_RESPONSES/OPENAI_VIDEOS/OPENAI_IMAGES/OPENAI_MODELS）、9 个跨协议适配器（按 `(源端点, 目标端点)` 维度索引 + 供应商 hook 串联）、两层策略模式 |
| 05 | **Provider 厂商实现** | `modules/05-providers.md` | 10 个 Provider 类型：OPENAI / ANTHROPIC / CUSTOM / MIMO_TOKEN_PLAN / GPT_AUTH / CLAUDE_AUTH / DEEPSEEK / GEMINI / VOLC_CODINGPLAN / OPENCODE，每个供应商通过 `EndpointCapability` 声明自己原生支持的端点能力 |
| 06 | **流式传输与 SSE 转换** | `modules/06-streaming.md` | SSE 字节级透传、`RealTimeResponsesTransformer` Codex 兼容转换 |
| 07 | **管理端与前端** | `modules/07-admin.md` | 9 个管理端控制器、Sa-Token 鉴权、Dashboard 统计、Vue 3.5 前端 |
| 08 | **测试体系** | `modules/08-testing.md` | 单元测试覆盖 adapter registry / hook / 流式转换器 / provider 客户端等 |
| 09 | **部署与运维** | `modules/09-deployment.md` | Docker、Nginx、环境变量、API 测试命令、本地运行 |
| 10 | **代码目录结构** | `modules/10-code-structure.md` | 完整的 Java 源码目录树 |
| 11 | **Agent 工作流 Hook** | `modules/11-hooks.md` | `.agent/hooks/*.yml` 多 agent 串行工作流：plan → review → code → review 类流水线；`workflow-runner` skill 调度，`agents/*.md` 提供 sub-agent 角色契约；与代码层 `ProviderHook` 同名但完全正交；v1.1 加入 token 优化（公共约定外置 / 项目地图 / 摘要契约 / 大小硬上限 / 状态外置） |

---

## 快速功能概览

### 公开 API 端点

| 端点 | 方法 | 鉴权 | 说明 |
|---|---|---|---|
| `/health` | GET | ❌ 公开 | 健康检查（含数据库状态） |
| `/v1/models` | GET | ✅ Bearer | OpenAI 兼容模型列表 |
| `/v1/chat/completions` | POST | ✅ Bearer | OpenAI Chat Completions（流式/非流式） |
| `/v1/messages` | POST | ✅ Bearer | Anthropic Messages（流式/非流式） |
| `/v1/responses` | POST | ✅ Bearer | OpenAI Responses API（SSE 流式） |
| `/v1/videos` | POST | ✅ Bearer | OpenAI Videos API（非流式视频生成） |
| `/v1/images/generations` | POST | ✅ Bearer | OpenAI Images API（非流式图片生成） |
| `/v1/embeddings` | POST | ✅ Bearer | OpenAI 兼容嵌入（按 prompt_tokens 计费） |
| `/v1/audio/speech` | POST | ✅ Bearer | OpenAI 兼容 TTS（mp3/opus/aac/flac/wav/pcm 二进制响应） |
| `/v1/audio/transcriptions` | POST | ✅ Bearer | OpenAI 兼容 STT（multipart 上传，Whisper） |
| `/actuator/health` | GET | ❌ 公开 | Spring Boot 健康检查（含 db / diskSpace / liveness / readiness） |
| `/actuator/prometheus` | GET | ❌ 公开 | Prometheus 抓取端点（`gateway_*` 业务指标 + JVM 指标） |
| `/api/admin/circuit-breakers` | GET | ✅ Admin | 列出所有 (provider, model) 维度熔断器状态 |
| `/api/admin/circuit-breakers/{code}/{model}/reset` | POST | ✅ Admin | 强制重置熔断器为 CLOSED |

### 管理端点

| 端点 | 说明 |
|---|---|
| `POST /api/admin/login` | Sa-Token 登录 |
| `/api/admin/api-keys` | API Key CRUD、额度追加、渠道/模型授权、滑动窗口限制 |
| `/api/admin/channels` | 渠道 CRUD、模型抓取、OAuth 授权 |
| `/api/admin/channels/{id}/auth/*` | AUTH 渠道授权文件上传、授权链接生成、回调 URL 导入、状态查询 |
| `/api/admin/models` | 模型映射 CRUD |
| `/api/admin/request-logs` | 请求日志分页 |
| `/api/admin/dashboard` | 统计仪表盘 |
| `/api/admin/gateway-info` | 端点元信息 |
| `/api/admin/system-config` | 路由模式配置 |

### Provider 类型

| 类型 | 鉴权 | 协议 | 流式 | 说明 |
|---|---|---|---|---|
| `OPENAI` | Bearer | Chat Completions + Responses + Videos + Images | ✅ | 通用 OpenAI 兼容上游，同时声明 Chat、Responses、Videos、Images 四个能力（V17 合并 OPENAI_COMPATIBLE / OPENAI_RESPONSES） |
| `ANTHROPIC` | x-api-key | Messages | ✅ | 官方 Anthropic 供应商（V19 新增），默认 baseUrl `https://api.anthropic.com`，仅声明 ANTHROPIC_MESSAGES 能力 |
| `CUSTOM` | Bearer / x-api-key | Chat Completions + Messages | ✅ | 自定义供应商（V19 新增），用户自填 baseUrl，同时声明 CHAT_COMPLETIONS + ANTHROPIC_MESSAGES 两个能力 |
| `MIMO_TOKEN_PLAN` | Bearer / x-api-key | Chat Completions + Messages | ✅ | Xiaomi MiMo Token Plan 供应商（V19 新增），默认 baseUrl `https://token-plan-cn.xiaomimimo.com`，参考 https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call |
| `GPT_AUTH` | Bearer (auth.json) | Chat Completions + Videos + Images | ✅ | OAuth 授权（V12） |
| `CLAUDE_AUTH` | Bearer (auth.json) | Messages | ✅ | OAuth 授权（V12） |
| `DEEPSEEK` | Bearer | Chat + reasoning + Messages + thinking | ✅ | DeepSeek，同时声明 OpenAI Chat（含 `reasoning_content`）与 Anthropic Messages（含 thinking 块）两个能力 |
| `GEMINI` | `x-goog-api-key` | `generateContent` | ❌ | Google Gemini |
| `VOLC_CODINGPLAN` | Bearer | Chat Completions + Messages | ✅ | 火山 CodingPlan，同时声明 OpenAI Chat 与 Anthropic Messages 两个能力，默认 baseUrl `https://ark.cn-beijing.volces.com/api/coding`，Chat 走 `/v3/chat/completions` |
| `OPENCODE` | Bearer | Chat Completions + Messages | ✅ | OpenCode，同时声明 OpenAI Chat 与 Anthropic Messages 两个能力，baseUrl 由用户填写 |

---

## 待实现功能（按优先级排序）

| 优先级 | 功能 | 说明 | 相关模块 |
|---|---|---|---|
| P2 | 凭证加密 | `ai_channel.api_key` 加密存储或外部密钥管理 | 01-基础设施、02-安全 |
| P2 | 集成测试 | SQLite 安装、健康检查、鉴权失败、流转发等场景 | 08-测试 |
| P3 | 其他 Provider | 本地模型 client 实现 | 05-Provider |

## 已修复 Bug

| 日期 | 问题 | 修复 | 相关文件 |
|---|---|---|---|
| 2026-05-27 | OpenAiChatCompletionRequest 将 null 的 frequency_penalty/presence_penalty 序列化发送至上游，导致图片识别上游返回 400 错误 | 添加 @JsonInclude(JsonInclude.Include.NON_NULL) 注解，避免序列化 null 字段 | dto/OpenAiChatCompletionRequest.java |
| 2026-06-16 | `ModelRoute.effectiveEndpoint()` 在客户端端点不在 `allowedCapabilities` 时直接采用用户填写的 `allowedCapabilities` 第一个能力作为上游端点，导致请求日志 `sourceEndpointType` 为「对话补全」但实际却走「Anthropic Messages」地址 | 回退顺序改为按 `capabilities`（已用 `allowedCapabilities` 过滤）保留渠道侧配置顺序的第一个能力，保证渠道主能力（Chat Completions）优先于模型侧补登的次能力（Anthropic Messages） | dto/ModelRoute.java |
| 2026-06-16 | 用户在「能力配置」中限制模型只允许 Anthropic Messages，外部以 chat 端点请求时仍被 `effectiveEndpoint` fallback 到 `clientEndpoint`（CHAT_COMPLETIONS），触发渠道 400 错误 | 当渠道 `capabilities` 为空或与 `allowedCapabilities` 无交集时，fallback 改为 `allowedCapabilities` 中用户填写的第一个能力，确保用户对模型的能力限制被尊重，从而触发 Chat↔Anthropic 跨协议适配器 | dto/ModelRoute.java |
| 2026-06-16 | Codex 请求 mimo-v2.5 报 "stream closed before response.completed"；流式上游请求日志使用 `route.chatPath()`（旧单字段）而非 `route.resolvedChatPath(upstreamEndpoint)`，导致日志路径与实际请求 URL 不一致 | `streamToClient` 流式日志改用 `resolvedChatPath(upstreamEndpoint)`；`ResponsesStreamTransformer.flush()` 强制设置 `finishReasonSeen=true` 后再 `trySendCompleted`，避免上游不发 usage chunk 时 Codex 客户端提前断流；`ChannelList.vue` 列表去掉 `请求路径` / `视频接口路径` / `图片接口路径` 三列，换成 `能力` 列以 tag 形式展示 `capabilities` 列表 | service/ChatGatewayService.java, adapter/stream/ResponsesStreamTransformer.java, frontend/src/views/channels/ChannelList.vue |
| 2026-06-16 | Codex `/v1/responses` 报 `No adapter found for endpoint CHAT_COMPLETIONS with provider MIMO_TOKEN_PLAN`：V18 把适配器按 `(sourceEndpoint, targetProvider)` 索引，把供应商身份绑死在 key 上，但适配器本质是「上游端点协议 → 下游端点协议」的能力转换，与具体供应商类型解耦。OPENAI / DEEPSEEK / MIMO_TOKEN_PLAN / CUSTOM 任何支持目标端点协议的供应商都应该复用同一份适配器 | `EndpointProviderAdapter` 接口新增 `targetEndpoint()` 取代（并存）`targetProvider()`；`EndpointProviderAdapterRegistry` 改用 `(sourceEndpoint, targetEndpoint)` 作主 key，旧 `(source, provider)` 仍保留为回退路径；9 个具体适配器全部补 `targetEndpoint()`（AnthropicToOpenAiCompatible→CHAT_COMPLETIONS / ChatCompletionsToAnthropic→ANTHROPIC_MESSAGES / ResponsesToOpenAiCompatible→CHAT_COMPLETIONS / ResponsesToAnthropic→ANTHROPIC_MESSAGES / DeepSeek & Gemini 系列 target 与 source 同端点）；`ChatGatewayService.applyAdapter` / `applyRequestAdapter` 签名加 `targetEndpoint` 参数，先按能力维度查，miss 时回退到旧按供应商查 | adapter/endpoint/EndpointProviderAdapter.java, adapter/endpoint/EndpointProviderAdapterRegistry.java, adapter/endpoint/*Adapter.java, service/ChatGatewayService.java, test/.../EndpointProviderAdapterRegistryTest.java |
| 2026-06-17 | 实现「跨协议 adapter × 供应商 hook」双层组合：能力适配器按 `(source, target)` 解耦供应商身份，但 DeepSeek 这种「Chat Completions 必须带 reasoning_content=""、Anthropic thinking 块必须带 thinking 字段」的渠道特化逻辑又必须绑定到具体供应商。新增 `ProviderHook` 接口（按 `@HooksForProvider(ProviderType)` 注解注册），`ProviderHookRegistry` 按 `ProviderType` 索引；`DeepSeekHook` 用单一类同时覆盖 Chat Completions 和 Anthropic Messages 两种源端点（`preProcess` 收到 `sourceEndpoint` 参数后按形态分支处理）；`ChatGatewayService.applyRequestAdapter` / `applyAdapter` 串联 hook 与 adapter，请求方向 `hook.preProcess → adapter.adaptRequest`、响应方向 `adapter.adaptResponse → hook.postProcess`；`DeepSeekProviderClient.beforeChatRequest` / `beforeAnthropicRequest` 删除，逻辑完全迁出 `BaseAiProviderClient` 钩子机制 | adapter/endpoint/ProviderHook.java, adapter/endpoint/HooksForProvider.java, adapter/endpoint/ProviderHookRegistry.java, adapter/endpoint/DeepSeekHook.java, provider/DeepSeekProviderClient.java, service/ChatGatewayService.java, test/.../DeepSeekHookTest.java |
| 2026-06-17 | 修复 #1：同协议短路导致 DeepSeek/Gemini 自适配器被绕过；同 `(源, 目标)` key 上多个 provider 特化适配器（如 `ResponsesToOpenAiCompatibleAdapter` 与 `ResponsesToDeepSeekChatAdapter` 都声明 `(OPENAI_RESPONSES, CHAT_COMPLETIONS)`）会触发重复注册 | `ChatGatewayService.applyAdapter` / `applyRequestAdapter` 改为按 (源, 供应商) → (源, 目标) → (源, adapterProvider(供应商)) 顺序查，让 provider 维度覆盖优先；`EndpointProviderAdapterRegistry` 重复键改为 `putIfAbsent` 静默跳过而非抛 `IllegalStateException`，保证 Spring 上下文能正常启动；同步避免回归 `ChatCompletionsToDeepSeekChatAdapter` 的 `ChatToolSequenceNormalizer` 工具序列归一化、`ChatCompletionsToGeminiAdapter` 的 `mapDeveloperRole` / `cleanRawOptions` / 响应重建、DeepSeek/Gemini Anthropic 端同协议适配器 | service/ChatGatewayService.java, adapter/endpoint/EndpointProviderAdapterRegistry.java |
| 2026-06-17 | 修复 #2：旧测试仍通过反射调用已删除的 `BaseAiProviderClient.beforeChatRequest` / `beforeAnthropicRequest`，与新 hook 架构脱节 | `AnthropicProviderClientTests` / `DeepSeekChatProviderClientTests` 改写为端到端链路测试：provider DTO → 协议 adapter.toUnified → `DeepSeekHook.preProcess` → 协议 adapter.toProviderRequest，覆盖 hook 在真实请求体上的效果 | test/.../AnthropicProviderClientTests.java, test/.../DeepSeekChatProviderClientTests.java |
| 2026-06-17 | 修复 #3：`OpenAiRequestAdapter.toProviderRequest` 的 `reasoning_effort` 分支空操作，导致 `ResponsesToOpenAiCompatibleAdapterTests` 期望落空 | 改为 `providerRequest.setReasoningEffort(s)` 写入显式字段，避免落入 `additionalProperties` 被重复序列化 | adapter/protocol/OpenAiRequestAdapter.java |
| 2026-06-17 | 修复 #4：`StreamResponseTransformer.supportsUpstream` 默认返回 false，`getForUpstream` 永远为 null，新流式分流路径成为死代码 | `ResponsesStreamTransformer` / `AnthropicToOpenAiStreamTransformer` / `OpenAiToAnthropicStreamTransformer` 各自按 (client, upstream) 维度重写 `supportsUpstream`，与供应商身份解耦 | adapter/stream/ResponsesStreamTransformer.java, adapter/stream/AnthropicToOpenAiStreamTransformer.java, adapter/stream/OpenAiToAnthropicStreamTransformer.java |
| 2026-06-16 | 新增 3 个 ProviderType：`ANTHROPIC`（官方 Anthropic，默认 `https://api.anthropic.com`，x-api-key 鉴权，仅 Messages 能力）、`CUSTOM`（自定义，同时声明 Chat + Messages 两个能力，用户自填 baseUrl）、`MIMO_TOKEN_PLAN`（Xiaomi MiMo Token Plan，默认 `https://token-plan-cn.xiaomimimo.com`，Anthropic 路径 `/anthropic/v1/messages`，参考 https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call） | 新增 `AnthropicProviderClient` / `CustomProviderClient` / `MimoTokenPlanProviderClient`；`AdminChannelService.defaultBaseUrl` / `defaultPath` 写入官方默认地址；前端 `channelTypes` / `supplierDefaultEndpoints` / `capabilityDefaultPaths` / `handleTypeChange` 同步预填；`AnthropicToOpenAiStreamTransformer` / `OpenAiToAnthropicStreamTransformer` / `ResponsesStreamTransformer.supports` 加入新类型；`ProtocolFormat.fromProvider` 补全 switch 分支 | provider/ProviderType.java, provider/AnthropicProviderClient.java, provider/CustomProviderClient.java, provider/MimoTokenPlanProviderClient.java, service/admin/AdminChannelService.java, endpoint/ProtocolFormat.java, adapter/stream/*.java, frontend/src/types/index.ts, frontend/src/views/channels/ChannelList.vue |

---

## 近期更新

### 音频端点 + 上游熔断器（2026-06-17）

- **`/v1/audio/speech` 端点**（TTS）：OpenAI 兼容文本转语音，按 `response_format` 映射 Content-Type（mp3 → audio/mpeg / opus → audio/ogg / aac / flac / wav / pcm）；上游二进制响应通过 `RestClient.body(ParameterizedTypeReference<Resource>)` 接收，按需写入响应；新增 `EndpointType.AUDIO_SPEECH` + `AudioSpeechGatewayService` + `AudioSpeechEndpointHandler`；`AiProviderClient.speech()` 默认 `BaseAiProviderClient` 实现自动覆盖 OPENAI / CUSTOM / MIMO / DEEPSEEK / VOLC / OPENCODE / GPT_AUTH；V20 迁移新增 `ai_channel.audio_speech_path` 字段
- **`/v1/audio/transcriptions` 端点**（STT / Whisper）：OpenAI 兼容 multipart/form-data 语音转写，上游按 `verbose_json` 返回；新增 `EndpointType.AUDIO_TRANSCRIPTIONS` + `AudioTranscriptionGatewayService` + `AudioTranscriptionEndpointHandler`；`AiProviderClient.transcribe()` 默认 `BaseAiProviderClient` 实现用 `MultipartBodyBuilder` 构造透传请求；V20 迁移新增 `ai_channel.audio_transcription_path` 字段；`application.yaml` 增加 `spring.servlet.multipart.max-file-size: 25MB` 配置
- **上游熔断器**（in-house，不引依赖）：`CircuitBreaker` 三态机（CLOSED / OPEN / HALF_OPEN），按 `(providerCode, providerModel)` 维度持有滑动窗口（默认 20 / 最小样本 10 / 失败率 50% / 冷却 60s / 试探 3 次），HALF_OPEN 试探失败立即 OPEN，成功达到阈值关闭；`CircuitBreakerRegistry` 单例 + Micrometer Gauge `gateway_circuit_state{provider, model}`（0/1/2 映射 CLOSED/HALF_OPEN/OPEN）+ `gateway_circuit_failure_rate` 暴露给 Prometheus；`RoutingService.resolveModel()` 在 `activeCandidates` 中过滤掉 OPEN 渠道，`recordSuccess/recordFailure` 同步驱动 CB；`AdminCircuitBreakerController` 提供 list / properties / reset 三个管理端点
- **网关配置新增**：`api-convert.circuit-breaker.{window-size,failure-rate-threshold,minimum-calls,open-duration-seconds,half-open-max-trials}`，全部支持 `API_CONVERT_CB_*` 环境变量覆盖

### 可观测性与嵌入端点（2026-06-17）

- **可观测性基础设施**：`spring-boot-starter-actuator` + `micrometer-registry-prometheus`，暴露 `/actuator/health`（含 `db` 健康）与 `/actuator/prometheus`；`EndpointMetricsFilter` 在 servlet 层为 `/v1/*` 请求打 Timer / Counter，按 endpoint + status 拆分；`ChatGatewayService` / `ImageGatewayService` / `VideoGatewayService` / `EmbeddingGatewayService` 在服务层埋点上游调用耗时、失败切换、错误码分布；tag 维度收敛到 endpoint / provider / status / error_code，避免高基数
- **请求 ID 关联**：`RequestContextFilter` 在 `HIGHEST_PRECEDENCE` 读取 `X-Request-Id` 请求头（缺失时生成 UUID），写入 SLF4J MDC 的 `requestId` 与响应头；log4j2 `%X{requestId}` 自动贯穿所有日志行，便于跨服务追踪同一请求
- **`/v1/embeddings` 端点**：OpenAI 兼容嵌入接口，按 `prompt_tokens` 计费；新增 `EndpointType.OPENAI_EMBEDDINGS` + `EmbeddingGatewayService` + `OpenAiEmbeddingsEndpointHandler` + `EmbeddingGatewayServiceTest`；`AiProviderClient.embed()` 默认 `BaseAiProviderClient` 实现自动覆盖 OPENAI / CUSTOM / MIMO_TOKEN_PLAN / DEEPSEEK / VOLC_CODINGPLAN / OPENCODE / GPT_AUTH；V19 迁移新增 `ai_channel.embedding_path` 字段
- **`ApiKeyQuotaService.deductEmbeddings`**：嵌入按 prompt_tokens 复用 input 单价单独扣减，未配置 input 单价时不扣费只记请求数和审计日志
- **bug 修复 #5**：`DatabaseInstaller.run()` 在 fresh install 路径下 `return` 得太早，导致 V17-V19 永远不跑；改为 fall-through 到迁移循环；同时把 V18/V19 的 version 行从 `gateway_schema_version` 中按需移除，避免与 schema.sqlite 中已包含的列产生重复建表

### 工程清理（2026-06-17）

- 删除根目录 `node_modules/`（之前临时 `npm install --no-save js-yaml` 验证 YAML 语法留下的残留）
- `.gitignore` 加固 4 条规则：
  - `.agent/hooks/.state/` — v1.1 引入的 workflow-runner 状态文件
  - `auth-dir/` — `GPT_AUTH` / `CLAUDE_AUTH` 渠道的 OAuth 授权凭证
  - `/node_modules/` / `package.json` / `package-lock.json` — 防止以后 npm 误装在根目录

### Agent 工作流 Token 优化（v1.1）

- 新增 `.agent/agents/_CONVENTIONS.md`：把 8 个 sub-agent 共享的 status 字段约定 / 摘要契约 / 大小硬上限 / 不要做 5 项通用条款集中外置；角色文件开头一行 `> 遵循 .agent/agents/_CONVENTIONS.md` 即可，每个角色文件从平均 ~2000 字符降至 ~1200 字符
- 新增 `.agent/CONTEXT_MAP.md`（≤ 1k token）：项目地图指针；sub-agent 默认**只**加载这份地图，需要时自己用 `Read` 工具按需加载 `AGENTS.md` / `rules.md` / `AI_GATEWAY_PROGRESS.md`，不再全量塞进任务包
- schema.json 新增 token 优化字段：`summaryRequired` / `summaryMaxChars`（默认 600）/ `maxArtifactSize`（默认 6000）/ `contextMap` / `stateFile`
- 两个 pipeline YAML 显式声明：`contextMap: .agent/CONTEXT_MAP.md` / `summaryMaxChars: 600` / `maxArtifactSize: 6000` / `stateFile: .agent/hooks/.state/<pipeline>.json`；每个 stage 加 `summaryRequired: true`
- workflow-runner SKILL.md 重写"执行循环"：构造 sub-agent 任务包**只**包含 `_CONVENTIONS.md` + `contextMap` + 上一 stage 摘要 + inputs 路径 + sub-agent 角色；**不**传上一 stage 全文、不传长文档全文；每次 stage 切换把 `{currentStage, history, retryCounters}` 持久化到 `stateFile`
- `.agent/rules/hooks.md` 新增"§13 Token 优化（强制）"：13.1 公共约定外置 / 13.2 项目地图 / 13.3 摘要契约 / 13.4 大小硬上限 / 13.5 状态外置 / 13.6 主 agent 内存自检清单
- 11-hooks.md 新增"§10 Token 优化策略"5 项手段对比表 + 主 agent 内存边界 + sub-agent 任务包结构 + `stateFile` schema + 优化前后估算（**单次完整 pipeline 从 ~10375 token 降至 ~3600 token，节省 65%**）
- 8 个 sub-agent 角色文件全部重写为紧凑版：开头声明遵循 `_CONVENTIONS.md`、保留差异化"身份 / 输入 / 输出 / status 判定"四段

### Agent 工作流 Hook 机制（v1.0）

- 新增 `.agent/hooks/` 目录与 `README.md` + `schema.json`：声明式 YAML 流水线 + stage transition + sub-agent 串行调度；schema 用 JSON Schema 约束字段
- 新增 `.agent/hooks/feature-development.yml` 与 `pr-review.yml` 两个可运行 pipeline：
  - `feature-development`：`plan → review-plan → code → review-code` 四段，被打回可回退到上游 stage，每 stage 都有 `maxRetries` 上限
  - `pr-review`：`diff-reader → security → spec → summary` 四段，安全/规范双线审查
- 新增 `.agent/skills/workflow-runner/SKILL.md`：唯一的流水线执行 skill，在同一会话内串行调用 sub-agent、维护 stage 重试计数、解析产物 `status:` 字段
- 新增 9 个 sub-agent 角色契约：`.agent/agents/{planner,plan-reviewer,coder,code-reviewer,diff-reader,security-auditor,spec-reviewer,reviewer-summary}.md`；每个都按"身份 / 输入 / 输出 / 协作契约 / 不要做"五小节规范
- 新增 `.agent/rules/hooks.md` 开发规范：12 条约束覆盖命名、字段、stage 设计、status 约定、重试上限、文件契约、提交前自检清单
- 新增 `.agent/docs/modules/11-hooks.md` 设计文档：背景、核心概念、YAML 字段详解、执行时序、与 skills/agents/commands/rules 边界、v1 限制与 v2 扩展
- 明确命名边界：`.agent/hooks/` 下的工作流 hook 与代码层 `cn.ms08.apiconvert.adapter.endpoint.ProviderHook`（供应商特化 hook）**同名但完全正交**——前者是 AI agent 接力编排，后者是 Java 接口处理 HTTP 请求体；04-endpoints.md 已加入这条说明

### 文档

- 新增 `.agent/docs/API_REFERENCE.md` API 参考文档：覆盖 7 个公开 API 端点 + 9 个管理 API + 鉴权方式 + 各 Provider 类型说明，支持 OpenAPI 格式输出
- 新增 `src/main/resources/static/docs/api-reference.html` 静态 HTML 文档页面
- README 补充简化版启动说明：创建工作目录并克隆仓库、按系统下载并解压 JDK 25、通过 `scripts/start.*` 指定 JDK 路径和管理员账号密码启动；同时保留清华 TUNA 国内镜像目录说明，开发文档跳转到 README

### 前端

- 控制台侧边栏移除独立“API 文档”菜单；API 文档入口收敛到控制台“接口调用信息”卡片右上角按钮和端点表“文档”操作列；Vite 本地开发代理 `/docs` 到后端静态文档，前后端分离调试不会再落到 5173 404。
- Channel management exposes `GPT_AUTH`/`CLAUDE_AUTH`, hides API Key inputs for AUTH channels, provides `auth.json` upload + OAuth link generation.
- Dashboard pie charts with hoverable SVG segments (name, tokens, count, share).
- Vite manual chunks (Vue/Naive UI/Axios), explicit component registration.

### 后端

- **OpenCode 供应商**：新增 `OPENCODE_CHAT` 和 `OPENCODE_ANTHROPIC` 两种 Provider 类型，支持 OpenCode 的 OpenAI 兼容和 Anthropic 兼容上游接口，使用标准 API Key 鉴权。
- **火山 CodingPlan 供应商**：新增 `VOLC_CODINGPLAN_CHAT` 和 `VOLC_CODINGPLAN_ANTHROPIC` 两种 Provider 类型，分别对接火山引擎 CodingPlan 的 OpenAI 兼容接口（`https://ark.cn-beijing.volces.com/api/coding/v3`）和 Anthropic 兼容接口（`https://ark.cn-beijing.volces.com/api/coding`），使用标准 API Key 鉴权。
- **Anthropic ↔ OpenAI Chat 流式 SSE 实时转换**：新增 `AnthropicToOpenAiStreamTransformer`（上游 Anthropic SSE → OpenAI Chat SSE）和 `OpenAiToAnthropicStreamTransformer`（上游 OpenAI Chat SSE → Anthropic SSE），补全 `CHAT_COMPLETIONS → ANTHROPIC` 和 `ANTHROPIC_MESSAGES → OPENAI_COMPATIBLE` 两条跨协议流式路径的实时格式转换能力；支持文本、工具调用、推理内容（thinking/reasoning_content）的逐 chunk 转换，自动映射 stop_reason/finish_reason，错误事件格式转换。
- **V15 多模态端点路由**：渠道表新增 `video_path`、`image_path`，前端渠道管理支持保存视频生成和图片生成 API 路径；新增 `POST /v1/videos` 视频生成端点和 `POST /v1/images/generations` 图片生成端点，`AiProviderClient.generateVideo()`/`generateImage()` 默认不支持，`OPENAI_COMPATIBLE` 与 `GPT_AUTH` 按渠道保存路径透传。
- JSON 解析兼容：全局 `ObjectMapper` 的 Jackson 单个字符串最大长度默认提升到 `100000000`，并通过 `API_CONVERT_JACKSON_MAX_STRING_LENGTH` 可配置；公开端点和 `RestClient` JSON 转换器统一使用该 mapper，支持 base64 图片/视频请求和响应透传，并兼容上游 OpenAI 兼容响应中的供应商扩展字段与 MiMo `audio_tokens`/`video_tokens` 用量明细。
- **V12 auth-file provider**: `GPT_AUTH`/`CLAUDE_AUTH` with `auth.json` upload, `auth-dir` storage, desensitized API responses.
- HTTP 体日志内存保护：出站 RestClient 日志不再读取并缓存完整上游响应体；超大请求/响应正文在脱敏阶段直接输出摘要，流式上游请求日志遇到大文本/base64 内容时不再额外序列化完整 JSON。
- Auto-fill official upstream addresses for AUTH channels on save.
- OAuth start/callback endpoints with built-in Codex/OpenAI and Claude metadata.
- `ChatGatewayService` maps `GPT_AUTH` → OpenAI adapters, `CLAUDE_AUTH` → Anthropic adapters.
- DeepSeek split into `DEEPSEEK_CHAT` and `DEEPSEEK_ANTHROPIC` independent providers.
- `/v1/responses` → `DEEPSEEK_CHAT` adapter restores `reasoning` items to `reasoning_content`.
- `DEEPSEEK_CHAT` ensures historical assistant messages include `reasoning_content` fallback.
- `ChatToolSequenceNormalizer` repairs strict Chat tool-call sequences for DeepSeek Chat by moving matching tool results next to assistant tool calls and trimming unanswered calls.
- Fixed MyBatis 3.5.19 `BoundSql` pagination by copying immutable parameter mappings.
- **V13 gateway key limits**: API Key limits moved to extensible rows, supporting simultaneous quota limits by hour/day and request-count limits by minute/hour/day; request-count limits are recorded after routing so failed upstream requests are counted, and each limit type allows only one row per window unit.
- API Key model allowlist added alongside channel allowlist; routing applies both scopes, including direct `channel/model` requests.
- Channel model selection now deduplicates custom typed and fetched upstream model IDs; backend rejects repeated provider models in the same channel before insert.
- Deleting a channel now removes matching `gateway_api_key_channel` allowlist rows and disables keys that lose their last explicit channel scope, preventing an empty allowlist from expanding back to all channels.
- **V14 API key failover switch**: gateway keys can enable multi-channel failover; when a route attempt fails before any response bytes are written, the gateway retries the unchanged request against remaining authorized routes for the same model and only returns failure after all candidates fail. It covers upstream provider errors such as no balance, rate limiting, auth failures, bad upstream parameters/responses, unsupported route capabilities, and unexpected route-attempt exceptions; gateway-local auth/quota/model failures are returned directly. For streaming requests, failover only happens before the SSE response has written to the client.
- **V16 渠道模型端点类型限制**：`ai_channel_model` 新增 `allowed_endpoint_types` 字段（逗号分隔的 EndpointType 名称），允许按模型标记只兼容特定端点类型；路由时自动过滤不匹配的候选渠道，解决同一模型多渠道（如 OpenAI Chat + Anthropic Messages）轮询到不兼容渠道导致工具调用 ID 不匹配的 400 错误。前端渠道管理新增"允许端点"多选列，留空表示不限制。
- **Chat → Anthropic 工具调用适配修复**：`ChatCompletionsToAnthropicAdapter` 新增消息格式转换，将 OpenAI Chat 格式的 `tool_calls`（assistant 消息 options）和 `tool` 消息（role=tool + tool_call_id）正确转为 Anthropic 的 `tool_use`/`tool_result` content block 格式，修复 Chat 端点请求路由到 Anthropic 渠道时因 tool result ID 不匹配导致的 400 错误。
- **Anthropic → OpenAI Chat 流式 usage JSON 修复**：`AnthropicToOpenAiStreamTransformer.writeChunk()` 修复 usage 字段被拼接到 JSON 对象 `}` 外部导致客户端 JSON 解析失败的 bug。
- **V17 供应商能力维度合并**：将 12 个 ProviderType 合并为 7 个（`OPENAI / DEEPSEEK / VOLC_CODINGPLAN / OPENCODE / GEMINI / GPT_AUTH / CLAUDE_AUTH`），每个供应商通过 `EndpointCapability` 接口声明自身原生支持的端点能力（`OpenAiChatCapability` / `AnthropicMessagesCapability` / `OpenAiResponsesCapability`），子类只组合并按需覆盖。`ProviderClientRegistry.getCapability(type, endpointType)` 在路由时按能力查找对应实现，未声明的能力直接返回 `UNSUPPORTED_FEATURE`。前端渠道管理：渠道编辑页加 `supplierDefaultEndpoints` 默认能力预选与中文 `endpointLabels` 标签，新建模型自动填默认允许端点，切换供应商时同步迁移旧默认值；`handleTypeChange` 默认请求路径表统一到 V17 新枚举，`VOLC_CODINGPLAN` 默认 baseUrl 写入 `https://ark.cn-beijing.volces.com/api/coding` + `/v3/chat/completions`。SQL 迁移 `V17__provider_type_merge.sql` 把历史渠道的旧枚举值刷新到新值。
- 失败重试切换渠道时，同步和流式路径均写入失败请求日志；Dashboard 查询增加 `success=true` 过滤，失败不计入请求数。
- **V17 前端能力配置 UI**：渠道编辑表单用能力勾选表格替代旧的独立请求路径字段（chatPath/videoPath/imagePath），每种端点能力（Chat Completions / Anthropic Messages / Responses API / 视频生成 / 图片生成）通过 checkbox 勾选，勾选后显示独立的上游请求路径输入框，`capabilityDefaultPaths` 按供应商类型提供默认路径。切换供应商时自动重置能力列表。`ChannelForm` 和 `ChannelVO` 新增 `capabilities` 字段，`syncLegacyPaths` 同步 `ANTHROPIC_MESSAGES` 到 `chatPath`。
- **Gemini 供应商能力迁移**：`GeminiProviderClient` 从直接实现 `AiProviderClient.chat()` 迁移到能力模式，内嵌 `GeminiChatCapability` 实现 `EndpointCapability`，同时声明 `CHAT_COMPLETIONS` 和 `ANTHROPIC_MESSAGES` 两个端点能力，均路由到同一 `generateContent` 调用逻辑。
