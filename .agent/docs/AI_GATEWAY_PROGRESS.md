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
| MyBatis-Plus 3.5.16 | ORM 层，6 个 Mapper 接口均继承 `BaseMapper` |
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
- 当前结构版本：`10`
- 首次安装脚本不得删除用户表；如版本 SQL 需要替换表或删除字段，必须先在脚本内完成备份或数据同步

**当前核心表：**

| 表名 | 用途 |
|---|---|
| `gateway_schema_version` | 安装版本追踪 |
| `ai_channel` | 自定义渠道，整合供应商类型、baseUrl、请求路径、模型列表路径和上游密钥 |
| `ai_channel_model` | 渠道模型映射，支持模型前缀、唯一别名、1M 输入/输出/缓存读取额度单价以及结构化能力字段（vision、tools_support、json_mode_support、context_length） |
| `gateway_api_key` | 网关 API Key，保存明文用于管理端复制，保存 SHA-256 哈希用于鉴权，并支持余额和滑动窗口额度限制 |
| `gateway_api_key_channel` | 网关密钥可用渠道范围；没有授权记录表示允许全部渠道 |
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

### 6. 模型列表 (`GET /v1/models`) — `OpenAiModelController`

- 查询所有 `enabled=true` 的 `ai_channel_model` 行，并按对外模型名聚合去重
- 返回 OpenAI 兼容格式：

```json
{
  "object": "list",
  "data": [
    { "id": "example-chat", "object": "model", "owned_by": "api-convert" }
  ]
}
```

### 7. 聊天转发 (`POST /v1/chat/completions`) — `OpenAiChatController` → `ChatGatewayService`

完整链路：

```
客户端请求 (OpenAI 格式)
  → OpenAiChatController (参数校验 @Valid)
    → OpenAiRequestAdapter.toUnified()      # 转为 UnifiedChatRequest
      → ChatGatewayService.chat() / stream()
        1. 生成 UUID requestId
        2. RoutingService.resolve(model)    # 解析路由
        3. ProviderClientRegistry.get(type) # 获取厂商客户端
        4a. 非流式 → client.chat(route, request)  # 转发到上游
        4b. 流式   → client.streamChat(route, request, outputStream)  # SSE 透传
        5. UsageRecorder.recordSuccess()    # 记录日志
      → OpenAiResponseAdapter.toOpenAi()    # 转回 OpenAI 格式
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

**`RoutingService` 路由解析逻辑**（87 行）：

- 支持两种模型写法：
  - `example-chat` — 按 `ai_channel_model.public_name` 匹配
  - `channel-a/example-chat` — 按 `channel_code + provider_model` 直接指定渠道模型
- 依次查找：模型映射 → 渠道主配置
- 同一个模型存在多个可用渠道时随机选择一个启用且 `status='ACTIVE'` 的渠道
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

### 8. Provider 抽象层

| 组件 | 说明 |
|---|---|
| `ProviderType` 枚举 | `OPENAI_COMPATIBLE`, `ANTHROPIC`, `OPENAI`, `GEMINI`, `LOCAL` |
| `AiProviderClient` 接口 | `type()`, `chat()`, `streamChat()`, `supportsStreaming()`, `models()`, `quota()` |
| `ProviderClientRegistry` | Spring 组件，`EnumMap` 注册所有 `AiProviderClient` bean |
| `OpenAiCompatibleProviderClient` | OpenAI 兼容 Client，`RestClient` 转发，支持流式 SSE 透传 |
| `AnthropicProviderClient` | Anthropic 原生 Client，`RestClient` 转发，支持流式 SSE 透传 |

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
- 管理端请求日志页面支持按协议、接口类型、时间范围、渠道、模型和结果筛选，并展示 token 用量与耗时

### 10.2 Log4j2 日志配置

- 日志配置文件：`src/main/resources/log4j2-spring.xml`
- 应用日志：控制台 + `logs/api-convert.log`
- SQL 日志：控制台 + `logs/sql.log`
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

### 12. Anthropic Messages 协议入口 (`POST /v1/messages`)

兼容 Anthropic SDK/client 的 `/v1/messages` 入口：

- `AnthropicMessageController.messages()` — 接收 Anthropic 格式请求，适配后转发
- 非流式：`requestAdapter.toUnified()` → `chatGatewayService.chat()` → `responseAdapter.toAnthropic()`
- 流式：`requestAdapter.toUnified()` → `chatGatewayService.stream()` → SSE 字节流透传
- 响应包含 `anthropic-version` 请求头（当前固定 `2023-06-01`）
- 请求日志按 `source_protocol=anthropic`、`request_type=messages` 记录

### 12.1 OpenAI Responses API 协议入口 (`POST /v1/responses`)

兼容 OpenAI Responses API 新协议的 `/v1/responses` 入口：

- `OpenAiResponsesController.createResponse()` — 接收 Responses API 格式请求，适配后转发
- Request Adapter 将 `input`（String 或 content 数组）转为 `UnifiedMessage` 列表，`instructions` → rawOptions，`max_output_tokens` → maxTokens
- Response Adapter 将统一响应转为 Responses API 格式（`output` 数组），同协议时直接透传 rawResponse
- 非流式：`requestAdapter.toUnified()` → `chatGatewayService.chat()` → `responseAdapter.toOpenAiResponses()`
- 流式：`requestAdapter.toUnified()` → `chatGatewayService.stream()` → `RealTimeResponsesTransformer` 实时转换 SSE 事件

**流式转换实现（`RealTimeResponsesTransformer`）：**

作为 `OutputStream` 拦截器，将上游 Chat Completions SSE 字节流实时转为 Responses API SSE 事件：

1. 首个字节到达时写出初始 4 个事件：`response.created`、`response.in_progress`、`response.output_item.added`、`response.content_part.added`
2. 每个 Chat Completions data chunk 的 `delta.content` 即时转为 `response.output_text.delta` 事件并 flush
3. 检测到 `finish_reason` 或 `[DONE]` 时依次写出 `response.output_text.done`、`response.content_part.done`、`response.output_item.done`
4. 用量到达后写出 `response.completed`（含完整 output 和 usage）
5. 上游失败时写出 `error` + `response.completed`（status=failed）

**SSE 事件字段规范对照（OpenAI Responses API）：**

| 事件 | 字段要求 |
|---|---|
| `response.output_item.added` | `item`（非 `output_item`），含 `object:"item"`、`status:"in_progress"` |
| `response.content_part.added` | `part` 中必须含 `annotations:[]` |
| `response.output_text.delta` | `delta` 字段 |
| `response.output_text.done` | `text` + `annotations:[]` |
| `response.content_part.done` | `part` 含完整 `output_text` + `annotations:[]` |
| `response.output_item.done` | `item`（非 `output_item`），含 `object:"item"`、`status:"completed"` |
| `response.completed` | output 中 item 含 `object:"item"`、`status:"completed"` |

注意：控制器流式路径返回 `StreamingResponseBody`（不包装在 `ResponseEntity` 中），由 `StreamingResponseBodyReturnValueHandler` 在异步线程中写出 SSE 事件。直接在 `HttpServletResponse` 上设置头部。不能使用 `ResponseEntity<StreamingResponseBody>`，否则 Spring 会尝试用消息转换器序列化导致 `HttpMediaTypeNotAcceptableException`。

- 请求日志按 `source_protocol=openai`、`request_type=responses` 记录

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

渠道、端点、上游密钥和模型不再支持 `providers`、`models` 等配置项；新增或调整请使用管理端或直接写入数据库。

---

## 当前包结构（共 ~70 个 Java 文件）

```
cn.ms08.apiconvert
├── ApiConvertApplication.java          # 入口，@SpringBootApplication
├── adapter/                            # 协议适配
│   ├── AnthropicRequestAdapter.java
│   ├── AnthropicResponseAdapter.java
│   ├── OpenAiRequestAdapter.java
│   ├── OpenAiResponseAdapter.java
│   ├── OpenAiResponsesRequestAdapter.java
│   └── OpenAiResponsesResponseAdapter.java
├── config/                             # 配置
│   ├── DateTimeConfig.java
│   ├── GatewayProperties.java
│   ├── MyBatisPlusConfig.java
│   ├── MyBatisTimeFillConfig.java
│   ├── SaTokenWebConfig.java
│   └── WebConfig.java
├── controller/                         # HTTP 接口
│   ├── AnthropicMessageController.java
│   ├── HealthController.java
│   ├── OpenAiChatController.java
│   ├── OpenAiModelController.java
│   ├── OpenAiResponsesController.java
│   └── SpaForwardController.java
│   └── admin/
│       ├── AdminApiKeyController.java
│       ├── AdminAuthController.java
│       ├── AdminChannelController.java
│       ├── AdminGatewayInfoController.java
│       ├── AdminModelController.java
│       └── AdminRequestLogController.java
├── dao/                                # MyBatis-Plus Mapper
│   ├── AiChannelMapper.java
│   ├── AiChannelModelMapper.java
│   ├── GatewayApiKeyChannelMapper.java
│   ├── GatewayApiKeyMapper.java
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
│   ├── UnifiedChatRequest.java
│   ├── UnifiedChatResponse.java
│   ├── UnifiedContentPart.java
│   ├── UnifiedMessage.java
│   ├── UnifiedUsage.java
│   └── admin/
│       ├── ApiKeyForm.java
│       ├── ApiKeyQuotaAddRequest.java
│       ├── ApiKeyUpdateForm.java
│       ├── ChannelForm.java
│       ├── ChannelModelFetchRequest.java
│       ├── ChannelModelForm.java
│       ├── ModelCapabilitiesForm.java
│       ├── ModelEnabledForm.java
│       ├── ModelQuotaForm.java
│       └── RequestLogSearchParam.java
├── entity/                             # 数据库实体
│   ├── AiChannelEntity.java
│   ├── AiChannelModelEntity.java
│   ├── GatewayApiKeyChannelEntity.java
│   ├── GatewayApiKeyEntity.java
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
├── provider/                           # Provider 抽象
│   ├── ProviderType.java
│   ├── AiProviderClient.java
│   ├── ProviderClientRegistry.java
│   ├── OpenAiCompatibleProviderClient.java
│   └── AnthropicProviderClient.java
├── security/                           # 鉴权
│   ├── ApiKeyHasher.java
│   ├── GatewayPrincipal.java
│   └── GatewayApiKeyFilter.java
├── service/                            # 业务服务
│   ├── ChatGatewayService.java
│   ├── DatabaseInstaller.java
│   ├── GatewayBootstrapService.java
│   ├── InstallStatusService.java
│   ├── RoutingService.java
│   ├── UsageRecorder.java
│   └── admin/
│       ├── AdminApiKeyService.java
│       ├── AdminChannelService.java
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
        ├── GatewayInfoVO.java
        ├── ModelVO.java
        ├── RequestLogVO.java
        └── UpstreamModelVO.java
```

---

## 测试

`ApiConvertApplicationTests.java`：单一 `@SpringBootTest` 验证 Spring 上下文加载。

验证命令：

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
```

---

## 本地运行

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn spring-boot:run
```

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
| P3 | 其他 Provider | Gemini、本地模型 client 实现 |
