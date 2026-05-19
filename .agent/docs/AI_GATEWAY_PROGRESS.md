# AI Gateway 功能实现进度

## 项目概述

**api-convert** 是一个 AI API 网关，聚合不同 AI 厂商 API 端点，通过统一入口适配 OpenAI / Claude 等客户端协议，并路由到指定厂商的指定模型。

技术栈：Spring Boot 4.0.6 + Java 25 + Maven + MyBatis-Plus 3.5.16。

---

## 已实现功能清单

### 1. 基础框架

| 模块 | 说明 |
|---|---|
| Spring Boot 4.0.6 | Web 层使用 Spring MVC（`spring-boot-starter-webmvc`） |
| MyBatis-Plus 3.5.16 | ORM 层，7 个 Mapper 接口均继承 `BaseMapper` |
| 双数据库支持 | SQLite（默认开发）和 MySQL，通过 `api-convert.database.type` 切换 |
| Spring JDBC | `DatabaseInstaller` 使用 `JdbcTemplate` 执行安装脚本和检查 |
| Spring RestClient | `WebConfig` 提供 `RestClient.Builder` bean，各 Provider Client 使用它调用上游 |
| Log4j2 | 使用 `log4j2-spring.xml` 输出控制台、应用日志和 SQL 日志 |
| Lombok | Entity 类使用 `@Getter/@Setter`，避免手写 |

### 2. 数据库自动安装与增量升级 (`DatabaseInstaller`)

启动时自动执行：

- 检查 `api-convert.database.install-enabled`（默认 `true`）
- 根据 `api-convert.database.type`（`sqlite` / `mysql`）选择脚本
- 首次安装：`gateway_schema_version` 不存在时，只执行 `schema-sqlite.sql` 或 `schema-mysql.sql`
- 增量升级：`gateway_schema_version` 已存在时，从当前版本逐个执行 `src/main/resources/db/migration/{sqlite,mysql}/V{version}.sql`
- 当前结构版本：`11`
- 首次安装脚本不得删除用户表；如版本 SQL 需要替换表或删除字段，必须先在脚本内完成备份或数据同步

**当前核心表：**

| 表名 | 用途 |
|---|---|
| `gateway_schema_version` | 安装版本追踪 |
| `ai_channel` | 自定义渠道，整合供应商类型、baseUrl、请求路径、模型列表路径和上游密钥 |
| `ai_channel_model` | 渠道模型映射，支持模型前缀、唯一别名、1M 输入/输出/缓存读取额度单价以及结构化能力字段（vision、tools_support、json_mode_support、context_length） |
| `gateway_api_key` | 网关 API Key，保存明文用于管理端复制，保存 SHA-256 哈希用于鉴权，并支持余额和滑动窗口额度限制 |
| `gateway_api_key_channel` | 网关密钥可用渠道范围；没有授权记录表示允许全部渠道 |
| `gateway_system_config` | 网关运行期系统配置，当前用于路由模式、会话粘性和错误避让参数 |
| `request_log` | 每次请求的审计日志（含 token 用量、延迟、错误信息） |

### 3. 启动引导数据 (`GatewayBootstrapService`)

`@CommandLineRunner`，在 `DatabaseInstaller` 之后运行，只处理网关调用密钥：

- 写入 `gateway_api_key`：`sk-local-dev`（保存明文、脱敏预览和 SHA-256 哈希），默认 `ACTIVE`
- 升级后的历史 bootstrap 记录如果缺少明文，会用配置中的 bootstrap key 补齐 `raw_key` 和 `key_preview`
- 渠道、端点、上游密钥和模型不再从配置文件引导，必须通过管理端或数据库写入 `ai_channel`、`ai_channel_model` 等表

**策略**：网关密钥不存在时插入；历史 bootstrap 密钥缺少明文时只补齐 `raw_key` 和 `key_preview`，不覆盖其他已有配置。

### 4. 鉴权 (`GatewayApiKeyFilter`)

- 过滤器类型：`OncePerRequestFilter`
- 可通过 `api-convert.security.enabled=false` 关闭
- 公开路径白名单：`/health`（不鉴权）
- 支持两种 key 传递方式：
  - `Authorization: Bearer <key>`
  - `x-api-key: <key>`
- `ApiKeyHasher` 对 key 做 SHA-256 哈希后与 `gateway_api_key` 表比对
- 只匹配 `status='ACTIVE'` 的 key
- 认证成功后设置 `GatewayPrincipal` 为 request attribute（含 `apiKeyId`、`name`）
- 失败返回 `UNAUTHORIZED`（401）

### 5. 健康检查 (`GET /health`) — `HealthController`

公开接口，无需鉴权。返回内容：

```json
{
  "service": "api-convert",
  "status": "UP",
  "database": "UP",
  "installed": true,
  "schemaVersion": 1,
  "providerCount": 1,
  "enabledModelCount": 1
}
```

- 数据库检查：执行 `SELECT 1`
- `installed`/`schemaVersion` 由 `InstallStatusService` 查询 `gateway_schema_version` 得出

### 6. 模型列表 (`GET /v1/models`) — `OpenAiModelsEndpointHandler`

- 查询所有 `enabled=true` 的 `ai_channel_model` 行，并按对外模型名聚合去重
- 过滤 `isRoutable()`：渠道必须启用、状态为 `ACTIVE`、且有 API Key
- 返回 OpenAI 兼容格式：

```json
{
  "object": "list",
  "data": [
    { "id": "example-chat", "object": "model", "owned_by": "api-convert" }
  ]
}
```

### 7. 聊天转发 (`POST /v1/chat/completions`) — `ChatCompletionsEndpointHandler` → `ChatGatewayService`

完整链路（EndpointType 感知 + 跨协议适配，1.0.2 增强）：

```
客户端请求 (OpenAI / Anthropic / Responses API 格式)
  → GatewayController (统一调度，委托给对应 EndpointHandler)
    → ChatCompletionsEndpointHandler / AnthropicMessagesEndpointHandler / OpenAiResponsesEndpointHandler
      → 端点请求适配器 (OpenAiRequestAdapter / AnthropicRequestAdapter / OpenAiResponsesRequestAdapter)
        → toUnified()  # 转为 UnifiedChatRequest
          → ChatGatewayService.chat(unifiedRequest, endpointType) / stream(unifiedRequest, endpointType)
            1. 生成 UUID requestId
            2. RoutingService.resolve(model)           # 解析路由
            3. EndpointProviderAdapter.adaptRequest()  # 跨协议请求适配（1.0.2 新增：清理字段、转换格式）
            4. ProviderClientRegistry.get(type)        # 获取厂商客户端
            5a. 非流式 → client.chat(route, adaptedRequest)
                        → EndpointProviderAdapter.adaptResponse()  # 跨协议响应适配
            5b. 流式 → streamTransformerRegistry.get(endpoint, provider)  # 查找流式转换器（1.0.2 新增）
                      → transformer.wrap(outputStream) → sendInitialEvents()
                      → client.streamChat(route, adaptedRequest, wrappedStream)  # SSE 实时转换
                      → wrappedStream.complete()
            6. ApiKeyQuotaService.deduct()             # 扣减额度
            7. UsageRecorder.recordSuccess()            # 记录日志
          → 端点响应适配器 (OpenAiResponseAdapter / AnthropicResponseAdapter / OpenAiResponsesResponseAdapter)
              or rawResponse 直接序列化（适配器已处理时）
      → 返回给客户端
```

**支持流式透传**：
- `stream=true` 时直接将上游 SSE 字节流透传客户端
- 流结束时从最后一个 SSE 块提取 token 用量（`usage` 字段）
- 路由阶段失败仍返回普通错误；已开始流式写出后的错误写成 SSE error 事件
- OpenAI 风格错误事件通过 `writeOpenAiStreamError()` 写出

**支持 response_format（JSON 模式）**：
- `response_format` 已显式建模为 `ResponseFormat` record，支持 `text` / `json_object` / `json_schema` 三种 type
- `isJson()` 方法判断是否启用了 JSON 输出模式
- OpenAI 入口（`/v1/chat/completions`）的 `response_format` 由 Jackson 直接反序列化为声明的 `ResponseFormat` 字段，不经过 aditionalProperties
- `OpenAiRequestAdapter` 显式提取并传递该字段，经 `UnifiedChatRequest.responseFormat` 路由到上游
- 跨协议路由（OpenAI→Anthropic）时 `response_format` 被过滤，不会传给 Anthropic 上游

**`RoutingService` 路由解析逻辑**：

- 支持两种模型写法：
  - `example-chat` — 按 `ai_channel_model.public_name` 匹配
  - `channel-a/example-chat` — 按 `channel_code + provider_model` 直接指定渠道模型
- 依次查找：模型映射 → 渠道主配置
- 同一个模型存在多个可用渠道时，按系统配置选择 `RANDOM`（随机）、`ROUND_ROBIN`（轮询）、`WEIGHTED`（按渠道 `priority` 权重平滑加权轮询）或 `SESSION_STICKY`（会话粘性）模式
- 会话粘性模式会从请求头/参数提取 `session_id`、`thread_id`、`x-client-request-id`、`prompt_cache_key` 等稳定标识，同一网关密钥 + 模型 + 会话在 TTL 内优先命中首次渠道，提高上游缓存命中率
- 当请求携带 `tools` 时，如果同名模型存在 `tools_support=true` 的候选渠道，会优先从这些渠道中选择，避免 Codex 等工具调用请求随机命中不支持工具的渠道；未配置任何 `tools_support=true` 时保持原有随机策略
- 上游 `ProviderException` 失败会按“网关密钥 + 渠道 + 模型”累计；达到配置阈值后，该密钥在冷却时间内会避让该渠道模型并切换其他可用渠道，成功调用会清除对应失败状态
- 失败情况：
  - 模型不存在 → `MODEL_NOT_FOUND` (404)
  - 厂商不存在 → `PROVIDER_NOT_FOUND` (404)
  - 厂商未启用 → `PROVIDER_UNAVAILABLE` (503)
  - 无有效凭证 → `PROVIDER_AUTH_FAILED` (502)

### 7.1 密钥额度与模型计费

- 模型映射支持配置 `input_quota_per_million`、`output_quota_per_million`、`cache_read_quota_per_million`，表示每 100 万 token 消耗多少密钥额度。
- 网关密钥支持 `quota_balance` 总余额；为空表示兼容历史密钥，不限制总额度。
- 网关密钥支持 `quota_limit` + `quota_window_value` + `quota_window_unit`（`HOUR`/`DAY`/`MONTH`）滑动窗口限制。
- 请求上游之前按请求内容估算输入 token、按 `maxTokens` 估算输出 token 做额度预检；明显不足时返回 `QUOTA_INSUFFICIENT`。
- 上游成功后优先按实际 usage 计算费用并扣减密钥余额，同时写入密钥级滑动窗口缓存；上游未返回 usage 时退回使用预估值。

### 7.2 模型能力结构化字段（V10 新增）

将 `capabilities_json` 自由文本字段替换为 4 个类型化字段：

- `vision`（Boolean）— 是否支持图片/视觉输入
- `tools_support`（Boolean）— 是否支持工具/函数调用
- `json_mode_support`（Boolean）— 是否支持 JSON 输出模式
- `context_length`（Long）— 最大上下文窗口（token 数）

管理端模型列表页新增"能力"列展示上述字段，并支持通过 PUT `/admin/models/{id}/capabilities` 批量更新同一对外模型名下所有渠道映射的能力配置。

### 7.3 路由策略与错误避让（V11 新增）

- 新增 `gateway_system_config` 表，管理端通过 `GET/PUT /api/admin/system-config/routing` 读取和保存路由配置。
- 支持路由模式：`RANDOM`、`ROUND_ROBIN`、`WEIGHTED`、`SESSION_STICKY`。
- `WEIGHTED` 使用渠道管理里的 `priority` 字段作为路由权重，数值越高分配流量越多；默认值为 100。
- `SESSION_STICKY` 使用稳定会话标识绑定首次命中的渠道，绑定 TTL 由 `routing.sticky_ttl_minutes` 控制；上游失败时会解除该会话绑定。
- 失败避让参数：`routing.failure_threshold`、`routing.failure_cooldown_minutes`。任一值为 0 时关闭避让；启用后达到阈值的“密钥 + 渠道 + 模型”组合会在冷却期内被过滤。
- 前端新增“系统配置”页面，可直接调整上述路由参数。

### 8. Provider 抽象层

| 组件 | 说明 |
|---|---|
| `ProviderType` 枚举 | `OPENAI_COMPATIBLE`, `ANTHROPIC`, `OPENAI_RESPONSES`, `GEMINI`, `DEEPSEEK_CHAT`, `DEEPSEEK_ANTHROPIC`, `LOCAL` |
| `AiProviderClient` 接口 | `type()`, `chat()`, `streamChat()`, `supportsStreaming()`, `models()`, `quota()` |
| `ProviderClientRegistry` | Spring 组件，`EnumMap` 注册所有 `AiProviderClient` bean |
| `OpenAiCompatibleProviderClient` | OpenAI 兼容 Client，`RestClient` 转发，支持流式 SSE 透传 |
| `AnthropicProviderClient` | Anthropic 原生 Client，`RestClient` 转发，支持流式 SSE 透传 |
| `DeepSeekChatProviderClient` | DeepSeek Chat Completions 风格 Client，复用 OpenAI Chat 传输实现，作为独立供应商类型路由 |
| `DeepSeekAnthropicProviderClient` | DeepSeek Anthropic API 风格 Client，复用 Anthropic 传输实现，并隔离 thinking 块回传规则 |
| `OpenAiResponsesProviderClient` | OpenAI Responses API Client，使用 `OpenAiResponsesRequestAdapter` 构建请求，Bearer 鉴权 |
| `GeminiProviderClient` | Google Gemini Client，`x-goog-api-key` 鉴权，URL 格式 `{model}:generateContent`，支持模型列表查询 |

**DeepSeek 独立供应商处理**：
- DeepSeek 不再通过 `OPENAI_COMPATIBLE` 或 `ANTHROPIC` 渠道的 URL 兼容逻辑处理，而是拆分为 `DEEPSEEK_CHAT` 与 `DEEPSEEK_ANTHROPIC` 两个独立供应商类型。
- `DEEPSEEK_CHAT` 支持 Chat Completions 风格上游，并新增 `/v1/responses` 端点适配；该适配会把 Responses 历史中的 `reasoning` item 恢复为 DeepSeek Chat 续轮需要的 `reasoning_content`，且在客户端未回传 reasoning 时为历史 assistant 消息兜底携带空 `reasoning_content` 字段。
- `DEEPSEEK_ANTHROPIC` 支持 Anthropic API 风格上游，并仅在该供应商 Client 内补齐 `type="thinking"` 内容块的 `thinking` 字段。

**Provider 错误码细化**（P0 已实现）：
- 401/403 → `PROVIDER_AUTH_FAILED`
- 429 → `PROVIDER_RATE_LIMITED`
- 5xx → `PROVIDER_UNAVAILABLE`
- 400 → `PROVIDER_BAD_RESPONSE`
- 网络/连接异常 → `PROVIDER_UNAVAILABLE`

### 9. 协议适配层 (`adapter` 包)

| 类 | 职责 |
|---|---|
| `OpenAiRequestAdapter` | `toUnified()` — OpenAI Request → UnifiedChatRequest；`toProviderRequest()` — UnifiedChatRequest → OpenAI Request（含 `stream` 参数） |
| `OpenAiResponseAdapter` | `toUnified()` — OpenAI Response → UnifiedChatResponse；`toOpenAi()` — UnifiedChatResponse → OpenAI Response |
| `AnthropicRequestAdapter` | `toUnified()` — Anthropic Request → UnifiedChatRequest；`toProviderRequest()` — UnifiedChatRequest → Anthropic Request（含 `stream` 参数） |
| `AnthropicResponseAdapter` | `toUnified()` — Anthropic Response → UnifiedChatResponse；`toAnthropic()` — UnifiedChatResponse → Anthropic Response |
| `OpenAiChatCompletionRequest` | OpenAI 请求体 DTO（model, messages, stream, temperature, maxTokens），含 `@JsonAnyGetter/Setter` 透传未知字段 |
| `OpenAiMessage` | 消息体（role, content 支持 String/Object 多模态, name） |
| `OpenAiChatCompletionResponse` | OpenAI 响应体 VO（含 Choice、Usage 嵌套类） |
| `OpenAiModelListResponse` | 模型列表响应 VO |
| `AnthropicMessage` | Anthropic 消息体 DTO |
| `AnthropicMessageRequest` | Anthropic 请求体 DTO |
| `RealTimeResponsesTransformer` | Chat Completions SSE → Responses API SSE 实时转换器，作为 OutputStream 拦截器工作 |
| `ResponsesSseTransformer` | 备用 Responses API SSE 转换器（旧版批处理方式） |

统一内部模型（Record 类型）：

- `UnifiedChatRequest` — model, messages, stream, temperature, maxTokens, responseFormat, rawOptions
- `UnifiedChatResponse` — id, model, messages, usage, rawResponse
- `UnifiedMessage` — role, content, name
- `UnifiedContentPart` — type, value（多模态预留）
- `UnifiedUsage` — inputTokens, outputTokens, totalTokens, cacheReadInputTokens
- `ModelRoute` — 路由解析结果（publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, apiKey）
- `ResponseFormat` — response_format（type, jsonSchema），支持 text/json_object/json_schema

### 10. 请求日志与运行日志

### 10.1 请求日志 (`UsageRecorder`)

写入 `request_log` 表，字段包括：

- `request_id`, `gateway_api_key_id`
- `source_protocol`, `request_type`
- `provider_code`, `provider_type`, `public_model`, `provider_model`
- `stream`, `http_status`, `latency_ms`
- `input_tokens`, `output_tokens`, `total_tokens`
- `error_code`, `error_message`（失败时记录）, `created_at`

- OpenAI 兼容入口记录为 `source_protocol=openai`、`request_type=chat_completions`
- Anthropic 兼容入口记录为 `source_protocol=anthropic`、`request_type=messages`
- 成功请求记录实际路由渠道、供应商类型、上游模型、耗时和上游返回的 token 用量
- 失败请求也会落库，包括流式未支持、路由失败、上游异常和网关内部异常
- 管理端请求日志页面支持按协议、接口类型、时间范围、渠道、模型、密钥 ID 和结果筛选，并展示密钥名称、token 用量与耗时；密钥明文不会进入请求日志响应

### 10.2 Log4j2 日志配置

- 日志配置文件：`src/main/resources/log4j2-spring.xml`
- 应用日志：控制台 + 按级别拆分的 `logs/app-trace.log`、`logs/app-debug.log`、`logs/app-info.log`、`logs/app-warn.log`、`logs/app-error.log`
- SQL 日志：控制台 + 按级别拆分的 `logs/sql-trace.log`、`logs/sql-debug.log`、`logs/sql-info.log`、`logs/sql-warn.log`、`logs/sql-error.log`
- 日志按天和 100 MB 大小滚动为 `*.log.gz`，滚动时自动清理超过 5 天的压缩归档
- 日志目录可通过 `LOG_PATH` 覆盖；Docker 部署文档示例显式将 `LOG_PATH=/app/data/logs` 指向容器数据映射目录下的日志子目录
- MyBatis-Plus 使用 `org.apache.ibatis.logging.slf4j.Slf4jImpl`，通过 Log4j2 打印 Mapper SQL 和参数；设置 `SQL_PARAM_LOG_LEVEL=TRACE` 时会额外打印结果行
- Spring JDBC 安装/升级 SQL 通过 `org.springframework.jdbc.core.JdbcTemplate` 相关 logger 打印
- 可通过环境变量调整级别：`LOG_LEVEL`、`APP_LOG_LEVEL`、`SQL_LOG_LEVEL`、`SQL_PARAM_LOG_LEVEL`
- SQL 参数日志会输出数据库参数值，生产环境开启前必须确认不会泄露上游密钥、网关密钥明文等敏感字段

### 10.3 渠道额度实时获取

- 管理端接口：`GET /admin/channels/{id}/quota`
- 后端读取当前渠道已保存的 `base_url`、`type`、`api_key`，调用对应 `AiProviderClient.quota()` 实时获取
- 查询结果不写入数据库，仅返回前端展示
- OpenAI 兼容实现会尝试常见余额接口 `/user/balance`，失败后尝试 OpenAI 风格 `/dashboard/billing/credit_grants`
- Anthropic 普通 API Key 当前没有通用额度接口，返回"不支持获取额度"
- 上游错误响应会脱敏后返回前端，便于管理员排查

### 10.4 管理端统计仪表盘

- 管理端接口：`GET /api/admin/dashboard/stats`
- 统计数据来自 `request_log` 表，后端按项目时区聚合，避免数据库方言差异影响 SQLite/MySQL 兼容性
- 支持查询参数：`days`（按天趋势，默认 7，最大 90）、`hours`（按小时趋势，默认 24，最大 168）、`topN`（维度排行数量，默认 6，最大 20）
- 返回总请求数、成功/失败请求数、成功率、输入/输出/总 token、缓存读取 token
- 返回按天和按小时的 token 折线图数据
- 返回按模型、渠道、网关密钥划分的 token 分布饼图数据，以及对应按天趋势折线图数据；密钥维度使用密钥 ID 做稳定 key，展示名使用 `gateway_api_key.name`
- 前端控制台首页已接入该接口，展示 token 趋势、模型/渠道/密钥维度折线图和饼图，并保留健康检查、网关基础信息和端点列表
- “按天 Token 消耗”折线图支持鼠标悬停查看当天总量、输入/输出/缓存读取 token，以及当天各模型的 token 消耗明细

### 11. 异常处理 (`GlobalExceptionHandler`)

返回 OpenAI 兼容格式：

```json
{
  "error": {
    "message": "具体错误信息",
    "type": "authentication_error|invalid_request_error|server_error",
    "code": "UNAUTHORIZED|MODEL_NOT_FOUND|..."
  }
}
```

HTTP 状态码映射：

- 401/403 → `authentication_error`
- 其他 4xx → `invalid_request_error`
- 5xx → `server_error`

**14 个错误码**（`ErrorCode` 枚举）：

`INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `MODEL_NOT_FOUND`, `PROVIDER_NOT_FOUND`, `PROVIDER_UNAVAILABLE`, `PROVIDER_AUTH_FAILED`, `PROVIDER_RATE_LIMITED`, `PROVIDER_TIMEOUT`, `PROVIDER_BAD_RESPONSE`, `ROUTE_NOT_FOUND`, `UNSUPPORTED_FEATURE`, `QUOTA_INSUFFICIENT`, `INTERNAL_ERROR`

### 12. Anthropic Messages 协议入口 (`POST /v1/messages`) — `AnthropicMessagesEndpointHandler`

兼容 Anthropic SDK/client 的 `/v1/messages` 入口，通过 `EndpointRegistry` 调度：

- `AnthropicMessagesEndpointHandler.handle()` — 接收 Anthropic 格式请求，适配后转发
- 非流式：`requestAdapter.toUnified()` → `chatGatewayService.chat()` → `responseAdapter.toAnthropic()`
- 流式：`requestAdapter.toUnified()` → `chatGatewayService.stream()` → SSE 字节流透传
- 设置 `x-accel-buffering: no` 头防止反向代理缓冲流式响应
- 响应包含 `anthropic-version` 请求头（当前固定 `2023-06-01`）
- 请求日志按 `source_protocol=anthropic`、`request_type=messages` 记录

### 12.1 OpenAI Responses API 协议入口 (`POST /v1/responses`) — `OpenAiResponsesEndpointHandler`

兼容 OpenAI Responses API 新协议的 `/v1/responses` 入口，通过 `EndpointRegistry` 调度：

- `OpenAiResponsesEndpointHandler.handle()` — 接收 Responses API 格式请求，适配后转发
- Request Adapter 将 `input`（String、message 数组、`function_call`、`function_call_output`）转为 `UnifiedMessage` 列表，`instructions`、`tools`、`reasoning`、`previous_response_id` 等 Responses 原生参数保留在 rawOptions；供应商特定清洗下沉到端点-供应商适配器，避免原生 Responses 上游收到 Chat 化参数
- Response Adapter 将统一响应转为 Responses API 格式（`output` 数组），同协议时直接透传 rawResponse；跨协议工具调用会输出标准 `function_call` item，工具结果输入会映射为 `function_call_output`
- 非流式：`requestAdapter.toUnified()` → `chatGatewayService.chat()` → `responseAdapter.toOpenAiResponses()`
- 流式：`requestAdapter.toUnified()` → `chatGatewayService.stream()` → `RealTimeResponsesTransformer` 实时转换 SSE 事件

**流式转换实现（`RealTimeResponsesTransformer`）：**

作为 `OutputStream` 拦截器，将上游 Chat Completions SSE 字节流实时转为 Responses API SSE 事件：

1. 首个字节到达时写出初始 4 个事件：`response.created`、`response.in_progress`、`response.output_item.added`、`response.content_part.added`
2. 每个 Chat Completions data chunk 的 `delta.content` 即时转为 `response.output_text.delta` 事件并 flush
3. 检测到 `finish_reason` 或 `[DONE]` 时依次写出 `response.output_text.done`、`response.content_part.done`、`response.output_item.done`
4. 用量到达后写出 `response.completed`（含完整 output 和 usage）
5. 上游失败时写出 `error` + `response.completed`（status=failed）

**Codex Responses SSE 兼容处理：**
- 兼容部分 OpenAI-compatible 上游在 Chat Completions SSE 中返回累计 `delta.content` 的情况，转换为 Responses `response.output_text.delta` 时只下发新增后缀，避免 Codex 客户端重复显示同一段文本。
- 兼容上游实际返回原生 Responses SSE 事件但渠道类型配置为 `OPENAI_COMPATIBLE` 的情况，网关会解析 `response.output_text.delta` / `response.completed` 并重新输出规范 Responses SSE。
- 兼容 OpenAI Chat Completions 流式 `delta.tool_calls`，实时转换为 Responses `response.output_item.added`、`response.function_call_arguments.delta/done` 和最终 `function_call` output item，确保 Codex 能收到并执行工具调用。
- 流结束时确保输出合法 JSON 的 `response.completed`，避免 Codex 因无法解析完成事件而报错 `stream closed before response.completed` 并重试。

**SSE 事件字段规范对照（OpenAI Responses API）：**

| 事件 | 字段要求 |
|---|---|
| `response.output_item.added` | `item`（非 `output_item`），含 `object:"item"`、`status:"in_progress"` |
| `response.content_part.added` | `part` 中必须含 `annotations:[]` |
| `response.output_text.delta` | `delta` 字段 |
| `response.output_text.done` | `text` + `annotations:[]` |
| `response.function_call_arguments.delta` | `output_index`、`item_id`、`delta` |
| `response.function_call_arguments.done` | `output_index`、`item_id`、完整 `arguments` |
| `response.content_part.done` | `part` 含完整 `output_text` + `annotations:[]` |
| `response.output_item.done` | `item`（非 `output_item`），含 `object:"item"`、`status:"completed"` |
| `response.completed` | output 中 item 含 `object:"item"`、`status:"completed"` |

注意：控制器流式路径返回 `StreamingResponseBody`（不包装在 `ResponseEntity` 中），由 `StreamingResponseBodyReturnValueHandler` 在异步线程中写出 SSE 事件。直接在 `HttpServletResponse` 上设置头部。不能使用 `ResponseEntity<StreamingResponseBody>`，否则 Spring 会尝试用消息转换器序列化导致 `HttpMediaTypeNotAcceptableException`。

- 请求日志按 `source_protocol=openai`、`request_type=responses` 记录

### 12.2 端点策略模式 (`endpoint` 包)

所有公开 API 端点统一抽取为策略模式，每个端点独立负责请求接收、协议适配和响应写出。新增端点只需添加 `EndpointType` 枚举常量并创建对应的 `EndpointHandler` 实现。

**架构组件：**

| 组件 | 说明 |
|---|---|
| `EndpointType` 枚举 | 携带 HTTP 方法、路径、协议标签、鉴权方式、中文说明；提供 `toEndpointVO()` 和 `allEndpointVOs()` 供管理端展示 |
| `EndpointHandler` 接口 | `endpointType()` 返回所属类型；`handle(request, response)` 处理完整请求生命周期 |
| `EndpointRegistry` | Spring 组件，注入 `List<EndpointHandler>` 构建 `EnumMap<EndpointType, EndpointHandler>`，通过 `get(type)` 按类型查找处理器 |
| `GatewayController` | 统一调度控制器，`@PostMapping/@GetMapping` 映射路径后委托注册表执行，不含业务逻辑 |

**当前端点处理器：**

| 端点 | 处理器 | 说明 |
|---|---|---|
| `CHAT_COMPLETIONS` | `ChatCompletionsEndpointHandler` | 读取 `OpenAiChatCompletionRequest` → `OpenAiRequestAdapter.toUnified()` → 网关 → `OpenAiResponseAdapter.toOpenAi()` |
| `ANTHROPIC_MESSAGES` | `AnthropicMessagesEndpointHandler` | 读取 `AnthropicMessageRequest` → `AnthropicRequestAdapter.toUnified()` → 网关 → `AnthropicResponseAdapter.toAnthropic()` |
| `OPENAI_RESPONSES` | `OpenAiResponsesEndpointHandler` | 读取 `OpenAiResponsesRequest` → `OpenAiResponsesRequestAdapter.toUnified()` → 网关 → `OpenAiResponsesResponseAdapter.toOpenAiResponses()`；流式使用 `RealTimeResponsesTransformer` |
| `OPENAI_MODELS` | `OpenAiModelsEndpointHandler` | 直接查询 DB 模型表（不走 ChatGatewayService），按 `isRoutable()` 过滤可转发渠道 |

**管理端端点元信息** — `AdminGatewayInfoController` 通过 `EndpointType.allEndpointVOs()` 自动推导端点清单，新增端点时无需修改控制器。

### 12.3 统一调度入口 (`GatewayController`)

负责 4 个公开 API 端点的路径映射和调度委托，不含任何业务逻辑：

```java
@RestController
public class GatewayController {
    private final EndpointRegistry endpointRegistry;

    @PostMapping("/v1/chat/completions")
    public void chatCompletions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        endpointRegistry.get(EndpointType.CHAT_COMPLETIONS).handle(request, response);
    }

    @PostMapping("/v1/messages")
    public void messages(...) { endpointRegistry.get(EndpointType.ANTHROPIC_MESSAGES).handle(request, response); }

    @PostMapping("/v1/responses")
    public void responses(...) { endpointRegistry.get(EndpointType.OPENAI_RESPONSES).handle(request, response); }

    @GetMapping("/v1/models")
    public void models(...) { endpointRegistry.get(EndpointType.OPENAI_MODELS).handle(request, response); }
}
```

已删除的旧控制器：`OpenAiChatController`、`AnthropicMessageController`、`OpenAiResponsesController`、`OpenAiModelController`。

### 12.4 Provider 策略与端点策略的协作

两层策略模式互不耦合：

1. **端点层（EndpointHandler）** — 负责"入口协议适配"：接收外部请求格式 → 转为 UnifiedChatRequest → 调用网关 → 将 UnifiedChatResponse 转回外部响应格式
2. **Provider 层（AiProviderClient）** — 负责"上游厂商适配"：接收 UnifiedChatRequest → 转为厂商 API 请求 → 调用上游 → 将上游响应转回 UnifiedChatResponse

端点通过 `ChatGatewayService` 调用 Provider 层，后者按 `ModelRoute.providerType` 从 `ProviderClientRegistry` 获取对应客户端。

渠道创建时选择 `ProviderType`（供应商策略），自动推导 `chatPath`（请求路径后缀）。供应商策略决定了哪个 `AiProviderClient` 处理该渠道的上游请求。

### 12.5 Gemini Provider 实现 (`GeminiProviderClient`)

Google Gemini API 的 Provider 实现：

- **鉴权**：使用 `x-goog-api-key` 请求头（非 Bearer token）
- **请求 URL**：`{baseUrl}/v1beta/models/{providerModel}:generateContent`
- **请求体格式**：`contents` 数组（user/assistant 消息轮次）、`system_instruction`（首条 system 消息）、`generationConfig`（temperature, maxOutputTokens）
- **模型列表**：`GET /v1beta/models`，按 `supportedGenerationMethods` 过滤 `generateContent` 方法
- **额度查询**：不支持，返回 `ProviderQuota(supported=false)`
- **流式**：暂不支持流式（`supportsStreaming()` 返回 `false`）

### 12.6 端点-供应商接口适配器 (`adapter/endpoint/` 包)

在路由层增加接口适配器层，自动处理端点协议与上游供应商协议之间的差异。当客户端请求的端点协议与路由解析出的供应商类型不一致时，由适配器完成请求预处理和响应后处理。

**架构组件：**

| 组件 | 说明 |
|---|---|
| `EndpointProviderAdapter` 接口 | 四个方法：`sourceEndpoint()` 源端点类型、`targetProvider()` 目标供应商类型、`adaptRequest()` 请求预处理（默认透传）、`adaptResponse()` 响应适配（将供应商返回的统一响应转为端点协议可序列化 VO） |
| `EndpointProviderAdapterRegistry` | Spring 组件，注入 `List<EndpointProviderAdapter>` 构建 `(EndpointType, ProviderType)` → `EndpointProviderAdapter` 映射 |
| 6 个具体适配器 | 覆盖主流跨协议组合（详见下方适配器状态表），请求适配清理供应商不兼容字段，响应适配将统一响应转为端点协议 VO |

**适配器在 `ChatGatewayService` 中的集成：**

```
请求适配（由端点处理器的 RequestAdapter 完成）
  → ChatGatewayService.chat(unifiedRequest, EndpointType)
    → RoutingService.resolve() → ModelRoute(providerType)
    → ProviderClientRegistry.get(providerType).chat(route, request)
    → EndpointProviderAdapterRegistry.get(endpointType, providerType)
      → 匹配时：adapter.adaptResponse(response, publicModel)
    → 返回适配后的 UnifiedChatResponse(rawResponse=端点协议 VO)
  → 端点处理器直接序列化 rawResponse
```

**EndpointType 感知的方法签名：**

```java
// ChatGatewayService 新增的 EndpointType 重载
public UnifiedChatResponse chat(UnifiedChatRequest request, HttpServletRequest servletRequest, EndpointType endpointType);
public StreamingResponseBody stream(UnifiedChatRequest request, HttpServletRequest servletRequest, EndpointType endpointType);
```

**端点处理器简化效果（以 `OpenAiResponsesEndpointHandler` 为例）：**

简化前 — 端点处理器显式调用响应适配器：
```java
UnifiedChatResponse unifiedResponse = chatGatewayService.chat(
    unifiedRequest, request, "openai", "responses");
OpenAiResponsesResponse apiResponse = responseAdapter.toOpenAiResponses(
    unifiedResponse, unifiedRequest.model());
objectMapper().writeValue(response.getOutputStream(), apiResponse);
```

简化后 — 路由层适配器自动处理，端点处理器只需写 rawResponse；适配器未覆盖的组合仍 fallback 到端点响应适配器：
```java
UnifiedChatResponse unifiedResponse = chatGatewayService.chat(
    unifiedRequest, request, EndpointType.OPENAI_RESPONSES);
Object apiResponse = unifiedResponse.rawResponse();
if (!(apiResponse instanceof OpenAiResponsesResponse)) {
    apiResponse = responseAdapter.toOpenAiResponses(unifiedResponse, unifiedRequest.model());
}
objectMapper().writeValue(response.getOutputStream(), apiResponse);
```

**扩展方式：** 新增 (端点, 供应商) 适配只需实现 `EndpointProviderAdapter` 接口并注册为 Spring `@Component`，无需修改 `ChatGatewayService` 或端点处理器。例如后续添加 `HEALTH` → `GEMINI` 或新端点适配器，新建 `XxxAdapter implements EndpointProviderAdapter` 即可自动注册。

**适配器接口扩展：**
- `adaptRequest(UnifiedChatRequest)` — 新增请求适配方法（1.0.2），在路由阶段预处理请求格式，如清理供应商不支持的字段、转换 role/tools 格式、为必填参数提供默认值
- 未找到适配器时直接返回原始请求（`default` 方法）

**当前实现状态（共 6 个适配器）：**

| (端点, 供应商) 组合 | 适配器 | 说明 |
|---|---|---|
| CHAT_COMPLETIONS → ANTHROPIC | `ChatCompletionsToAnthropicAdapter` | 清理 OpenAI 特有字段（logprobs、stop 等），转换 tools/tool_choice 格式为 Anthropic 格式，为 max_tokens 提供默认值；响应转为 Chat Completions 格式 |
| CHAT_COMPLETIONS → GEMINI | `ChatCompletionsToGeminiAdapter` | 清理 OpenAI 特有字段（含 tools、response_format、tool_choice），转换 developer 角色为 user |
| ANTHROPIC_MESSAGES → OPENAI_COMPATIBLE | `AnthropicToOpenAiCompatibleAdapter` | 将 `system` 转为 Chat system 消息，`tools/input_schema` 转为 OpenAI function tools，`tool_choice` 转为 OpenAI 取值，`tool_use/tool_result` 转为 `tool_calls/tool` 消息；响应中的 `tool_calls` 转回 Anthropic `tool_use` 内容块 |
| ANTHROPIC_MESSAGES → GEMINI | `AnthropicMessagesToGeminiAdapter` | 过滤 Anthropic 特有字段和内容块类型（thinking、tool_use、tool_result），清理计费头，自动简化单块 text 内容 |
| OPENAI_RESPONSES → OPENAI_COMPATIBLE | `ResponsesToOpenAiCompatibleAdapter` | 请求适配：`instructions`→system 消息、Responses function tools→Chat function tools、`tool_choice`→Chat 格式、`reasoning.effort`→`reasoning_effort`、过滤 Responses-only 字段；响应适配：Chat Completions 响应 → Responses API 格式 |
| OPENAI_RESPONSES → ANTHROPIC | `ResponsesToAnthropicAdapter` | 请求适配：`instructions`→Anthropic `system`、Responses function tools→`input_schema`、`tool_choice`→Anthropic 格式、`function_call/function_call_output`→`tool_use/tool_result`、过滤 OpenAI 专有字段；响应适配：Anthropic Messages 响应 → Responses API 格式 |
| 其他组合 | — | 同协议直接透传（仅修正 model 为公共模型名）；跨协议无适配器时抛 `UNSUPPORTED_FEATURE` |

### 12.7 流式响应转换器抽象层 (`adapter/stream/` 包)

当流式请求的端点协议与上游供应商协议不一致时（如 `/v1/responses` 端点路由到 ANTHROPIC 上游），需要将上游 SSE 字节流实时转换为目标协议格式。1.0.2 版本新增流式转换器抽象层，将 `RealTimeResponsesTransformer` 接入统一接口。

**架构组件：**

| 组件 | 说明 |
|---|---|
| `StreamResponseTransformer` 接口 | 三个核心方法：`supports(endpoint, provider)` 判断是否匹配、`wrap(outputStream, ...)` 包装输出流返回 `WrappedStream` 访问器 |
| `WrappedStream` 内部接口 | `outputStream()` 获取转换后的输出流、`sendInitialEvents()` 发送初始事件、`complete()` 发送完成事件、`writeErrorEvent(msg)` 写出错误事件 |
| `StreamTransformerRegistry` | Spring 组件，自动注入 `List<StreamResponseTransformer>`，通过 `get(endpointType, providerType)` 按需查找匹配的转换器 |
| `ResponsesStreamTransformerFactory` | 将 `RealTimeResponsesTransformer` 包装为 `StreamResponseTransformer`，支持 `OPENAI_RESPONSES → OPENAI_COMPATIBLE` 和 `OPENAI_RESPONSES → ANTHROPIC` 两种上游 SSE 输入到 Responses API SSE 格式的实时转换 |

**流式路径集成（`ChatGatewayService.streamToClient()`）：**

```
1. routingService.resolve() → ModelRoute
2. apiKeyQuotaService.assertEnough()     # 额度预检
3. applyRequestAdapter()                 # 端点-供应商请求适配
4. streamTransformerRegistry.get(endpointType, route.providerType())
   → 匹配时创建 wrappedStream, 调用 sendInitialEvents(), 替换目标输出流
5. providerClient.streamChat()           # 上游 SSE 写入转换后的输出流
6. wrappedStream.complete()              # 发送完成事件
7. apiKeyQuotaService.deduct()           # 扣减额度
8. usageRecorder.recordSuccess()         # 记录请求日志
```

**错误处理：**
- 路由阶段失败 → 通过 `writeOpenAiStreamError()` 写出 OpenAI 风格 SSE error 事件
- 已启动 wrappedStream 后失败 → 调用 `wrappedStream.writeErrorEvent()` 写出目标协议兼容错误
- 流式完成或异常后最终都记录请求日志（含失败码和错误信息）

### 13. 配置属性 (`GatewayProperties`)

`@ConfigurationProperties("api-convert")`，支持环境变量覆盖：

| 配置项 | 环境变量 | 默认值 |
|---|---|---|
| `time-zone` | `API_CONVERT_TIME_ZONE` | `Asia/Shanghai` |
| `database.type` | `API_CONVERT_DB_TYPE` | `sqlite` |
| `database.sqlite-path` | `API_CONVERT_SQLITE_PATH` | `${user.dir}/api-convert.db` |
| `database.install-enabled` | `API_CONVERT_DB_INSTALL_ENABLED` | `true` |
| `security.enabled` | `API_CONVERT_SECURITY_ENABLED` | `true` |
| `security.bootstrap-api-key` | `API_CONVERT_BOOTSTRAP_API_KEY` | `sk-local-dev` |
| datasource URL | `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${api-convert.database.sqlite-path}` |

SQLite 默认文件路径为应用启动目录下的 `api-convert.db`；部署时如果只需要调整 SQLite 文件位置，传入 `API_CONVERT_SQLITE_PATH` 即可，例如 `D:\data\api-convert\api-convert.db`。如果要切换到 MySQL 或完全自定义 JDBC URL，继续使用 `SPRING_DATASOURCE_URL` 覆盖完整连接串。

### 14. 日期时间格式

- Java 日期时间字段统一使用 `LocalDateTime` 映射，不使用 `Date`、`Instant` 等类型承载业务日期时间。
- 接口输入、输出统一格式为 `yyyy-MM-dd HH:mm:ss`，例如 `2025-05-15 00:00:00`。
- 项目默认时区固定为 `Asia/Shanghai`，启动时会同步设置 JVM 默认时区；如确需覆盖，可传入 `API_CONVERT_TIME_ZONE`。
- 新增和更新记录的 `createdAt`、`updatedAt` 由 MyBatis-Plus 自动使用项目时区填充，避免 SQLite `CURRENT_TIMESTAMP` 按 UTC 写入导致时间偏移。
- `DateTimeConfig` 负责统一 JSON 和 MVC 查询参数的日期时间格式；对已经暴露的 `LocalDateTime` 字段同时保留字段级格式注解，避免回退为 ISO `T` 分隔格式。
- 前端日期时间选择器同样使用 `yyyy-MM-dd HH:mm:ss` 作为提交格式。

### 15. 管理端前端路由

- 管理端使用 Vue Router hash history，生产访问路径形如 `/#/login`、`/#/channels`。
- Axios 401 拦截器会清理本地 token 并整页跳转到 `/#/login`，避免直接访问 `/login` 命中后端路由导致 404。

渠道、端点、上游密钥和模型不再支持 `providers`、`models` 等配置项；新增或调整请使用管理端或直接写入数据库。

---

## 当前包结构（共 141 个 Java 源文件 + 8 个测试文件）

```
cn.ms08.apiconvert
├── ApiConvertApplication.java          # 入口，@SpringBootApplication
├── adapter/                            # 协议适配 + SSE 转换 + 路由层接口适配
│   ├── protocol/                        # 端点协议适配器（外部 DTO ↔ Unified）
│   │   ├── AnthropicRequestAdapter.java
│   │   ├── AnthropicResponseAdapter.java
│   │   ├── OpenAiRequestAdapter.java
│   │   ├── OpenAiResponseAdapter.java
│   │   ├── OpenAiResponsesRequestAdapter.java
│   │   └── OpenAiResponsesResponseAdapter.java
│   ├── stream/                          # SSE 流式转换器抽象层（1.0.2 新增）
│   │   ├── StreamResponseTransformer.java               # 接口：supports/wrap/WrappedStream
│   │   ├── StreamTransformerRegistry.java                # 自动发现注册表
│   │   ├── ResponsesStreamTransformerFactory.java        # RealTimeResponsesTransformer 工厂包装
│   │   ├── RealTimeResponsesTransformer.java             # Responses SSE 实时转换器
│   │   └── ResponsesSseTransformer.java                  # 旧版备用转换器
│   └── endpoint/                        # 端点-供应商接口适配器（路由层）
│       ├── EndpointProviderAdapter.java                   # 接口：按 (端点, 供应商) 组合适配
│       ├── EndpointProviderAdapterRegistry.java           # 注册表
│       ├── ChatCompletionsToAnthropicAdapter.java         # CHAT_COMPLETIONS → ANTHROPIC（1.0.2 新增）
│       ├── ChatCompletionsToGeminiAdapter.java            # CHAT_COMPLETIONS → GEMINI（1.0.2 新增）
│       ├── AnthropicToOpenAiCompatibleAdapter.java        # ANTHROPIC → OPENAI_COMPATIBLE（1.0.2 新增）
│       ├── AnthropicMessagesToGeminiAdapter.java          # ANTHROPIC → GEMINI（1.0.2 新增）
│       ├── ResponsesToOpenAiCompatibleAdapter.java        # OPENAI_RESPONSES → OPENAI_COMPATIBLE
│       └── ResponsesToAnthropicAdapter.java               # OPENAI_RESPONSES → ANTHROPIC（1.0.2 新增）
├── config/                             # 配置
│   ├── DateTimeConfig.java
│   ├── GatewayProperties.java
│   ├── MyBatisPlusConfig.java
│   ├── MyBatisTimeFillConfig.java
│   ├── SaTokenWebConfig.java
│   ├── SpaRouteConfig.java                # SPA 路由兜底
│   └── WebConfig.java
├── controller/                         # HTTP 接口
│   ├── GatewayController.java             # 统一调度入口（委托给 EndpointHandler）
│   ├── HealthController.java              # 健康检查（不纳入策略）
│   └── admin/
│       ├── AdminApiKeyController.java
│       ├── AdminAuthController.java
│       ├── AdminChannelController.java
│       ├── AdminDashboardController.java
│       ├── AdminGatewayInfoController.java
│       ├── AdminModelController.java
│       ├── AdminRequestLogController.java
│       └── AdminSystemConfigController.java
├── dao/                                # MyBatis-Plus Mapper
│   ├── AiChannelMapper.java
│   ├── AiChannelModelMapper.java
│   ├── GatewayApiKeyChannelMapper.java
│   ├── GatewayApiKeyMapper.java
│   ├── GatewaySystemConfigMapper.java
│   └── RequestLogMapper.java
├── dto/                                # DTO / 内部传输对象
│   ├── AnthropicMessage.java
│   ├── AnthropicMessageRequest.java
│   ├── ModelRoute.java
│   ├── OpenAiChatCompletionRequest.java
│   ├── OpenAiMessage.java
│   ├── OpenAiResponsesRequest.java
│   ├── ProviderModel.java
│   ├── ProviderModelFetchRequest.java
│   ├── ProviderQuota.java
│   ├── ProviderQuotaFetchRequest.java
│   ├── ResponseFormat.java                # response_format（text/json_object/json_schema）
│   ├── RoutingConfig.java
│   ├── RoutingMode.java
│   ├── UnifiedChatRequest.java
│   ├── UnifiedChatResponse.java
│   ├── UnifiedContentPart.java
│   ├── UnifiedMessage.java
│   ├── UnifiedUsage.java
│   └── admin/
│       ├── AdminLoginRequest.java
│       ├── ApiKeyForm.java
│       ├── ApiKeyQuotaAddRequest.java
│       ├── ApiKeyUpdateForm.java
│       ├── ChannelForm.java
│       ├── ChannelModelFetchRequest.java
│       ├── ChannelModelForm.java
│       ├── DashboardStatsParam.java
│       ├── ModelCapabilitiesForm.java
│       ├── ModelEnabledForm.java
│       ├── ModelQuotaForm.java
│       ├── RequestLogSearchParam.java
│       └── RoutingConfigRequest.java
├── endpoint/                           # 端点策略模式 + 协议格式标识符
│   ├── EndpointType.java                   # 端点枚举（method, path, protocol, auth, description, defaultProvider）
│   ├── ProtocolFormat.java                 # 协议格式标识符常量（1.0.2 新增）：openai/claude/gemini/openai-response
│   ├── EndpointHandler.java                # 策略接口
│   ├── EndpointRegistry.java               # EnumMap 注册表
│   ├── ChatCompletionsEndpointHandler.java
│   ├── AnthropicMessagesEndpointHandler.java
│   ├── OpenAiResponsesEndpointHandler.java
│   └── OpenAiModelsEndpointHandler.java
├── entity/                             # 数据库实体
│   ├── AiChannelEntity.java
│   ├── AiChannelModelEntity.java
│   ├── GatewayApiKeyChannelEntity.java
│   ├── GatewayApiKeyEntity.java
│   ├── GatewaySystemConfigEntity.java
│   └── RequestLogEntity.java
├── exception/                          # 异常处理
│   ├── ErrorCode.java
│   ├── GatewayException.java
│   ├── ProviderException.java
│   └── GlobalExceptionHandler.java
├── logging/                            # HTTP 日志脱敏
│   ├── HttpTrafficLoggingFilter.java
│   ├── LogSanitizer.java
│   └── RestClientLoggingInterceptor.java
├── provider/                           # Provider 策略层
│   ├── ProviderType.java                  # 供应商枚举
│   ├── AiProviderClient.java              # 策略接口
│   ├── ProviderClientRegistry.java        # EnumMap 注册表
│   ├── OpenAiCompatibleProviderClient.java
│   ├── AnthropicProviderClient.java
│   ├── OpenAiResponsesProviderClient.java
│   └── GeminiProviderClient.java
├── security/                           # 鉴权
│   ├── ApiKeyHasher.java
│   ├── GatewayPrincipal.java
│   └── GatewayApiKeyFilter.java
├── service/                            # 业务服务
│   ├── ChatGatewayService.java
│   ├── ApiKeyQuotaService.java           # 密钥额度预检与扣减（1.0.2 新增）
│   ├── DatabaseInstaller.java
│   ├── GatewayBootstrapService.java
│   ├── InstallStatusService.java
│   ├── RoutingService.java
│   ├── SystemConfigService.java
│   ├── UsageRecorder.java
│   └── admin/
│       ├── AdminApiKeyService.java
│       ├── AdminAuthService.java
│       ├── AdminChannelService.java
│       ├── AdminDashboardService.java
│       ├── AdminModelService.java
│       └── AdminRequestLogService.java
└── vo/                                 # API 响应 VO
    ├── AnthropicMessageResponse.java
    ├── OpenAiChatCompletionResponse.java
    ├── OpenAiModelListResponse.java
    ├── OpenAiResponsesResponse.java
    ├── ApiResponse.java
    ├── PageResult.java
    └── admin/
        ├── AdminLoginVO.java
        ├── ApiKeyCreationVO.java
        ├── ApiKeyVO.java
        ├── ChannelModelMappingVO.java
        ├── ChannelQuotaVO.java
        ├── ChannelVO.java
        ├── DashboardDimensionUsageVO.java
        ├── DashboardSeriesPointVO.java
        ├── DashboardSeriesVO.java
        ├── DashboardStatsVO.java
        ├── DashboardSummaryVO.java
        ├── DashboardTokenPointVO.java
        ├── GatewayInfoVO.java
        ├── ModelVO.java
        ├── RequestLogVO.java
        ├── RoutingConfigVO.java
        └── UpstreamModelVO.java
```

---

## 测试

| 测试类 | 用例数 | 说明 |
|---|---|---|
| `ApiConvertApplicationTests.java` | 18 | `@SpringBootTest` 验证 Spring 上下文加载、渠道 CRUD、模型管理、请求日志分页、统计仪表盘聚合、路由解析、工具请求优先路由到支持工具的渠道，以及系统路由配置、轮询、加权、会话粘性和失败避让 |
| `OpenAiCompatibleProviderClientTests.java` | 1 | Provider Client URL 构建逻辑 |
| `DeepSeekChatProviderClientTests.java` | 2 | DeepSeek Chat thinking 模式下 assistant 历史消息的 `reasoning_content` 字段兜底与保留 |
| `AnthropicProviderClientTests.java` | 1 | DeepSeek Anthropic 独立供应商 thinking 内容块兼容处理 |
| `AnthropicToOpenAiCompatibleAdapterTests.java` | 2 | Anthropic Messages 映射到 OpenAI Chat 上游时的工具参数、工具消息与响应工具块转换 |
| `OpenAiResponsesRequestAdapterTests.java` | 2 | Responses 原生参数保留、`function_call/function_call_output` 与统一工具消息互转 |
| `ResponsesToOpenAiCompatibleAdapterTests.java` | 6 | `/v1/responses` 映射到 OpenAI Chat/DeepSeek Chat 上游时的请求参数、工具调用响应与 reasoning 续轮转换 |
| `ResponsesToAnthropicAdapterTests.java` | 1 | `/v1/responses` 映射到 Anthropic Messages 上游时的 system/tools/tool_use/tool_result 转换 |
| `RealTimeResponsesTransformerTests.java` | 4 | Codex Responses SSE 兼容：累计文本去重、原生 Responses SSE 解析、Chat `reasoning_content/tool_calls` 转 Responses `reasoning/function_call` 并保证 `response.completed` |

验证命令：

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
cd frontend && npm run build
```

---

## 本地运行

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn spring-boot:run
```

## Docker 部署

`README.md` 的 Docker 运行示例已覆盖 JDK 25 紧凑对象头参数和日志目录映射：

- 通过 `JAVA_OPTS='-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders'` 开启紧凑对象头
- 通过 `-v api-convert-data:/app/data` 挂载统一数据目录，并设置 `LOG_PATH=/app/data/logs`
- README Docker 段落已补充阿里云发布镜像地址和直接部署命令：`crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:{版本号}`
- README Docker 段落已补充 Nginx 反向代理说明：必须配置 `proxy_set_header Authorization $http_authorization;` 显式透传鉴权头，否则反代后端无法读取 Bearer token，网关接口和管理端接口会鉴权失败

```bash
# 健康检查（无需鉴权）
curl http://localhost:8080/health

# 启动后先在管理端新增渠道和模型，再调用模型列表或聊天转发。

# 模型列表
curl -H 'Authorization: Bearer sk-local-dev' http://localhost:8080/v1/models

# OpenAI 聊天转发（非流式）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "example-chat", "messages": [{"role": "user", "content": "hello"}], "stream": false}'

# OpenAI 聊天转发（流式）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "example-chat", "messages": [{"role": "user", "content": "hello"}], "stream": true}'

# Anthropic 消息转发（非流式）
curl -X POST http://localhost:8080/v1/messages \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -H 'anthropic-version: 2023-06-01' \
  -d '{"model": "claude-3-opus", "messages": [{"role": "user", "content": "hello"}]}'

# Anthropic 消息转发（流式）
curl -X POST http://localhost:8080/v1/messages \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -H 'anthropic-version: 2023-06-01' \
  -d '{"model": "claude-3-opus", "messages": [{"role": "user", "content": "hello"}], "stream": true}'
```

---

## 待实现功能（按优先级排序）

| 优先级 | 功能 | 说明 |
|---|---|---|
| P2 | 凭证加密 | ai_channel.api_key 加密存储或外部密钥管理 |
| P2 | 集成测试 | SQLite 安装、健康检查、鉴权失败、流式转发等场景 |
| P3 | 其他 Provider | 本地模型 client 实现 |

---

## Recent frontend updates

- Dashboard pie charts now render hoverable SVG segments. Hovering a pie slice or legend row shows the matched model/channel/API key name, total tokens, request count, and share percentage.
- Frontend production build now uses Vite manual chunks for Vue, Naive UI/icon, and Axios dependencies, registers only the Naive UI components used by templates with explicit global names, and raises the chunk warning threshold to match the remaining cached vendor bundle size.

## Recent backend updates

- DeepSeek is modeled as independent provider types: `DEEPSEEK_CHAT` for Chat Completions-style upstreams and `DEEPSEEK_ANTHROPIC` for Anthropic API-style upstreams. The generic `ANTHROPIC` provider no longer contains DeepSeek URL compatibility logic.
- Added endpoint adapters for the new DeepSeek providers, including `/v1/responses` -> `DEEPSEEK_CHAT`, which restores Responses `reasoning` items into DeepSeek Chat `reasoning_content` only on that dedicated provider path.
- `DEEPSEEK_CHAT` now also ensures historical assistant messages include a `reasoning_content` field even when the downstream client did not return a reasoning item.
- Fixed request-log pagination on MyBatis 3.5.19 by copying immutable `BoundSql` parameter mappings before adding `LIMIT/OFFSET` parameters; `/api/admin/request-logs` now has an integration test for page and pageSize behavior.
