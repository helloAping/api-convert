# AI Gateway 项目总览

**api-convert** 是一个 AI API 网关，聚合不同 AI 厂商 API 端点，适配 OpenAI / Claude 等客户端协议，并路由到指定厂商的指定模型。
技术栈：Spring Boot 4.0.6 + Java 25 + Maven + MyBatis-Plus 3.5.16。数据库 schema 版本：**V16**。管理前端：Vue 3.5 + Naive UI + Vite。

---

## 模块文档索引

按业务模块拆分，每个模块独立维护，互不影响。AI 在修改某一模块时只需读取对应文档，同时通过本索引了解其他模块的存在和功能边界。

| 编号 | 模块 | 文件 | 说明 |
|---|---|---|---|
| 01 | **基础设施与数据层** | `modules/01-infrastructure.md` | Spring Boot、MyBatis-Plus、数据库安装/升级、核心数据表、启动引导 |
| 02 | **安全鉴权与限流** | `modules/02-security.md` | API Key 鉴权（SHA-256）、额度计费、滑动窗口限流 |
| 03 | **路由与调度** | `modules/03-routing.md` | 模型路由解析（RANDOM/ROUND_ROBIN/WEIGHTED/SESSION_STICKY）、工具优先、错误避让、请求日志 |
| 04 | **端点与协议适配** | `modules/04-endpoints.md` | 6 个公开端点（CHAT_COMPLETIONS/ANTHROPIC_MESSAGES/OPENAI_RESPONSES/OPENAI_VIDEOS/OPENAI_IMAGES/OPENAI_MODELS）、12 个跨协议适配器、两层策略模式 |
| 05 | **Provider 厂商实现** | `modules/05-providers.md` | 8 个 Provider 类型（OPENAI_COMPATIBLE/ANTHROPIC/OPENAI_RESPONSES/GPT_AUTH/CLAUDE_AUTH/DEEPSEEK_CHAT/DEEPSEEK_ANTHROPIC/GEMINI） |
| 05 | **Provider 厂商实现** | `modules/05-providers.md` | 7 个 Provider 类型（V17 能力维度合并）：OPENAI / DEEPSEEK / VOLC_CODINGPLAN / OPENCODE / GEMINI / GPT_AUTH / CLAUDE_AUTH，每个供应商通过 `EndpointCapability` 声明自己原生支持的端点能力 |
| 06 | **流式传输与 SSE 转换** | `modules/06-streaming.md` | SSE 字节级透传、`RealTimeResponsesTransformer` Codex 兼容转换 |
| 07 | **管理端与前端** | `modules/07-admin.md` | 9 个管理端控制器、Sa-Token 鉴权、Dashboard 统计、Vue 3.5 前端 |
| 08 | **测试体系** | `modules/08-testing.md` | 15 个测试类、65 个用例、运行命令 |
| 09 | **部署与运维** | `modules/09-deployment.md` | Docker、Nginx、环境变量、API 测试命令、本地运行 |
| 10 | **代码目录结构** | `modules/10-code-structure.md` | 完整的 Java 源码目录树 |

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
| 2026-06-16 | 新增 3 个 ProviderType：`ANTHROPIC`（官方 Anthropic，默认 `https://api.anthropic.com`，x-api-key 鉴权，仅 Messages 能力）、`CUSTOM`（自定义，同时声明 Chat + Messages 两个能力，用户自填 baseUrl）、`MIMO_TOKEN_PLAN`（Xiaomi MiMo Token Plan，默认 `https://token-plan-cn.xiaomimimo.com`，Anthropic 路径 `/anthropic/v1/messages`，参考 https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call） | 新增 `AnthropicProviderClient` / `CustomProviderClient` / `MimoTokenPlanProviderClient`；`AdminChannelService.defaultBaseUrl` / `defaultPath` 写入官方默认地址；前端 `channelTypes` / `supplierDefaultEndpoints` / `capabilityDefaultPaths` / `handleTypeChange` 同步预填；`AnthropicToOpenAiStreamTransformer` / `OpenAiToAnthropicStreamTransformer` / `ResponsesStreamTransformer.supports` 加入新类型；`ProtocolFormat.fromProvider` 补全 switch 分支 | provider/ProviderType.java, provider/AnthropicProviderClient.java, provider/CustomProviderClient.java, provider/MimoTokenPlanProviderClient.java, service/admin/AdminChannelService.java, endpoint/ProtocolFormat.java, adapter/stream/*.java, frontend/src/types/index.ts, frontend/src/views/channels/ChannelList.vue |

---

## 近期更新

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
