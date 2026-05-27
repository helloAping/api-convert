# AI Gateway 项目总览

**api-convert** 是一个 AI API 网关，聚合不同 AI 厂商 API 端点，适配 OpenAI / Claude 等客户端协议，并路由到指定厂商的指定模型。

技术栈：Spring Boot 4.0.6 + Java 25 + Maven + MyBatis-Plus 3.5.16。
数据库 schema 版本：**V15**。管理前端：Vue 3.5 + Naive UI + Vite。

---

## 模块文档索引

按业务模块拆分，每个模块独立维护，互不影响。AI 在修改某一模块时只需读取对应文档，同时通过本索引了解其他模块的存在和功能边界。

| 编号 | 模块 | 文件 | 说明 |
|---|---|---|---|
| 01 | **基础设施与数据层** | `modules/01-infrastructure.md` | Spring Boot、MyBatis-Plus、数据库安装/升级、核心数据表、启动引导 |
| 02 | **安全鉴权与限流** | `modules/02-security.md` | API Key 鉴权（SHA-256）、额度计费、滑动窗口限流 |
| 03 | **路由与调度** | `modules/03-routing.md` | 模型路由解析（RANDOM/ROUND_ROBIN/WEIGHTED/SESSION_STICKY）、工具优先、错误避让、请求日志 |
| 04 | **端点与协议适配** | `modules/04-endpoints.md` | 6 个公开端点（CHAT_COMPLETIONS/ANTHROPIC_MESSAGES/OPENAI_RESPONSES/OPENAI_VIDEOS/OPENAI_IMAGES/OPENAI_MODELS）、12 个跨协议适配器、两层策略模式 |
| 05 | **Provider 厂商实现** | `modules/05-providers.md` | 8 个 Provider 类型（OPENAI_COMPATIBLE/ANTHROPIC/OPENAI_RESPONSES/GPT_AUTH/CLAUDE_AUTH/DEEPSEEK_CHAT/DEEPSEEK_ANTHROPIC/GEMINI）|
| 06 | **流式传输与 SSE 转换** | `modules/06-streaming.md` | SSE 字节级透传、`RealTimeResponsesTransformer` Codex 兼容转换 |
| 07 | **管理端与前端** | `modules/07-admin.md` | 9 个管理端控制器、Sa-Token 鉴权、Dashboard 统计、Vue 3.5 前端 |
| 08 | **测试体系** | `modules/08-testing.md` | 12 个测试类、52 个用例、运行命令 |
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

### 管理端端点

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
| `OPENAI_COMPATIBLE` | Bearer | Chat Completions + Videos + Images | ✅ | 通用兼容 |
| `ANTHROPIC` | Bearer + version | Messages | ✅ | Claude 官方 |
| `OPENAI_RESPONSES` | Bearer | Responses API | ✅ | 原生 Responses |
| `GPT_AUTH` | Bearer (auth.json) | Chat Completions + Videos + Images | ✅ | OAuth 授权（V12）|
| `CLAUDE_AUTH` | Bearer (auth.json) | Messages | ✅ | OAuth 授权（V12）|
| `DEEPSEEK_CHAT` | Bearer | Chat + reasoning | ✅ | DeepSeek Chat 风格 |
| `DEEPSEEK_ANTHROPIC` | Bearer + version | Messages + thinking | ✅ | DeepSeek Claude 风格 |
| `GEMINI` | `x-goog-api-key` | `generateContent` | ❌ | Google Gemini |

---

## 待实现功能（按优先级排序）

| 优先级 | 功能 | 说明 | 相关模块 |
|---|---|---|---|
| P2 | 凭证加密 | `ai_channel.api_key` 加密存储或外部密钥管理 | 01-基础设施、02-安全 |
| P2 | 集成测试 | SQLite 安装、健康检查、鉴权失败、流式转发等场景 | 08-测试 |
| P3 | 其他 Provider | 本地模型 client 实现 | 05-Provider |

---

## 近期更新

### 文档

- README 补充简化版启动说明：创建工作目录并克隆仓库、按系统下载并解压 JDK 25、通过 `scripts/start.*` 指定 JDK 路径和管理员账号密码启动；同时保留清华 TUNA 国内镜像目录说明，开发文档跳转到 README。

### 前端

- Channel management exposes `GPT_AUTH`/`CLAUDE_AUTH`, hides API Key inputs for AUTH channels, provides `auth.json` upload + OAuth link generation.
- Dashboard pie charts with hoverable SVG segments (name, tokens, count, share).
- Vite manual chunks (Vue/Naive UI/Axios), explicit component registration.

### 后端

- OpenAI 兼容视频生成端点：新增 `POST /v1/videos`，通过 `OpenAiVideosEndpointHandler` 和 `VideoGatewayService` 复用 API Key 鉴权、模型/渠道授权、请求数限制、路由避让与请求日志；`AiProviderClient.generateVideo()` 默认不支持，`OPENAI_COMPATIBLE` 与 `GPT_AUTH` 透传到上游 `/v1/videos`。
- **V15 多模态端点路径**：渠道表新增 `video_path`、`image_path`，前端渠道管理支持保存视频生成和图片生成 API 路径；新增 `POST /v1/images/generations` 图片生成端点，`AiProviderClient.generateImage()` 默认不支持，`OPENAI_COMPATIBLE` 与 `GPT_AUTH` 按渠道保存路径透传图片生成请求。
- JSON 解析兼容：全局 `ObjectMapper` 的 Jackson 单个字符串最大长度默认提升到 `100000000`，并通过 `API_CONVERT_JACKSON_MAX_STRING_LENGTH` 可配置；公开端点和 `RestClient` JSON 转换器统一使用该 mapper，支持 base64 图片/视频请求和响应透传，并兼容上游 OpenAI 兼容响应中的供应商扩展字段与 MiMo `audio_tokens`/`video_tokens` 用量明细。
- **V12 auth-file provider**: `GPT_AUTH`/`CLAUDE_AUTH` with `auth.json` upload, `auth-dir` storage, desensitized API responses.
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
- 失败重试切换渠道时，同步和流式路径均写入失败请求日志；Dashboard 查询增加 `success=true` 过滤，失败不计入请求数。
