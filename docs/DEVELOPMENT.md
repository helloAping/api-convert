# api-convert 开发文档

本文面向维护者和二次开发者，说明项目结构、端点、Provider、协议适配器、数据库迁移和前端开发约定。英文版见 [DEVELOPMENT_EN.md](DEVELOPMENT_EN.md)。

## 架构概览

`api-convert` 的核心链路是：

1. 客户端调用网关公开端点。
2. `GatewayApiKeyFilter` 校验网关 API Key，并加载渠道/模型授权范围。
3. `EndpointHandler` 将不同入口协议转换为统一请求模型。
4. `RoutingService` 根据模型、密钥授权、路由策略和失败避让选择 `ModelRoute`。
5. `EndpointProviderAdapter` 按端点类型和 Provider 类型转换请求/响应。
6. `AiProviderClient` 调用上游模型服务。
7. `ApiKeyQuotaService` 估算/扣减额度，记录请求数限制窗口。
8. `UsageRecorder` 写入请求日志，Dashboard 基于日志聚合统计。

## 目录结构

| 路径 | 说明 |
|---|---|
| `src/main/java/cn/ms08/apiconvert/controller` | HTTP 控制器，公开网关入口和管理端接口 |
| `src/main/java/cn/ms08/apiconvert/endpoint` | 端点策略，按公开协议处理请求 |
| `src/main/java/cn/ms08/apiconvert/provider` | 上游 Provider 策略实现 |
| `src/main/java/cn/ms08/apiconvert/adapter` | 端点协议、上游协议和流式响应适配 |
| `src/main/java/cn/ms08/apiconvert/service` | 路由、计费、安装、日志等业务服务 |
| `src/main/java/cn/ms08/apiconvert/security` | 网关 API Key 鉴权 |
| `src/main/java/cn/ms08/apiconvert/entity` | MyBatis-Plus 表实体 |
| `src/main/java/cn/ms08/apiconvert/dao` | MyBatis-Plus Mapper |
| `src/main/resources/db` | SQLite/MySQL 首次安装脚本和版本迁移脚本 |
| `frontend` | Vue 3.5 + Naive UI 管理端 |

## 公开端点

| 端点 | Handler | 说明 |
|---|---|---|
| `GET /health` | `HealthController` | 健康检查，不需要网关 API Key |
| `GET /v1/models` | `OpenAiModelsEndpointHandler` | OpenAI 兼容模型列表 |
| `POST /v1/chat/completions` | `ChatCompletionsEndpointHandler` | OpenAI Chat Completions 入口，支持流式 |
| `POST /v1/messages` | `AnthropicMessagesEndpointHandler` | Anthropic Messages 入口，支持流式 |
| `POST /v1/responses` | `OpenAiResponsesEndpointHandler` | OpenAI Responses API 入口，支持流式 |
| `POST /v1/videos` | `OpenAiVideosEndpointHandler` | OpenAI Videos API 入口，非流式视频生成 |
| `POST /v1/images/generations` | `OpenAiImagesEndpointHandler` | OpenAI Images API 入口，非流式图片生成 |

新增公开端点时：

1. 在 `EndpointType` 中添加枚举。
2. 实现 `EndpointHandler`。
3. 确认端点能转换为统一请求/响应模型，或明确不走统一对话链路。
4. 为端点补充测试和管理端网关信息展示。

## Provider 开发规范

当前可用 Provider 类型：

| 类型 | Client | 上游协议 |
|---|---|---|
| `OPENAI_COMPATIBLE` | `OpenAiCompatibleProviderClient` | Chat Completions + Videos + Images |
| `ANTHROPIC` | `AnthropicProviderClient` | Messages |
| `OPENAI_RESPONSES` | `OpenAiResponsesProviderClient` | Responses API |
| `GPT_AUTH` | `GptAuthProviderClient` | Chat Completions + Videos + Images + auth.json |
| `CLAUDE_AUTH` | `ClaudeAuthProviderClient` | Messages + auth.json |
| `DEEPSEEK_CHAT` | `DeepSeekChatProviderClient` | Chat Completions + reasoning |
| `DEEPSEEK_ANTHROPIC` | `DeepSeekAnthropicProviderClient` | Messages + thinking |
| `GEMINI` | `GeminiProviderClient` | Gemini `generateContent` |

`LOCAL` 只是预留枚举，当前没有可用 Client。

新增 Provider 时：

1. 在 `ProviderType` 中添加类型。
2. 实现 `AiProviderClient`，并在 Spring 中注册为 Bean。
3. 在 `ProviderClientRegistry` 能按类型取到该 Client。
4. 按需新增 `EndpointProviderAdapter`，覆盖每个需要支持的端点。
5. 如支持流式输出，实现或复用 `StreamResponseTransformer`。
6. 在管理端类型列表和文档中补充该 Provider。
7. 增加单元测试，至少覆盖 URL 构造、鉴权头、请求体转换、错误处理和用量解析。

安全要求：

- 上游 API Key、auth token、refresh token 不得出现在接口响应或业务日志中。
- Provider 异常需要转为 `ProviderException` 或 `GatewayException`，不要向用户暴露未脱敏的上游原始响应。
- AUTH 文件只保存到配置的数据目录，接口返回授权状态、主体、过期时间等脱敏信息。

## 适配器开发规范

端点与 Provider 不一定使用同一协议，因此通过 `EndpointProviderAdapter` 适配。

命名建议：

```text
{EndpointProtocol}To{ProviderProtocol}Adapter
```

示例：

- `ResponsesToOpenAiCompatibleAdapter`
- `ResponsesToAnthropicAdapter`
- `ChatCompletionsToDeepSeekChatAdapter`
- `AnthropicMessagesToGeminiAdapter`

开发要求：

- 请求适配要保留模型、系统提示、消息、工具、流式标记和供应商特定参数。
- 响应适配要输出统一响应模型，确保用量字段可被计费和日志使用。
- 工具调用序列必须满足目标上游约束；DeepSeek Chat 使用 `ChatToolSequenceNormalizer` 修复严格顺序。
- 不支持的能力要显式抛出 `UNSUPPORTED_FEATURE`，不要静默丢字段。
- 新适配器需要覆盖非流式和流式行为；流式格式不同步时需要补充 `StreamResponseTransformer`。

## 路由与模型约定

模型路由支持两种写法：

| 写法 | 说明 |
|---|---|
| `public-model` | 按 `ai_channel_model.public_name` 匹配，路由策略选择渠道 |
| `channel/provider-model` | 直连指定渠道和上游模型 |

约束：

- 同一对外模型名可以由多个渠道承载，用于随机、轮询、加权和会话粘性路由。
- 同一渠道内不能重复保存相同 `provider_model`。
- 手动模型别名 `model_alias` 非空时必须全局唯一。
- 网关密钥可同时限制渠道和模型；直连写法也必须通过授权校验。

## 网关密钥与限制项

相关表：

| 表 | 说明 |
|---|---|
| `gateway_api_key` | 网关密钥主体，明文仅供管理端复制，哈希用于鉴权 |
| `gateway_api_key_channel` | 密钥允许使用的渠道，空列表表示全部渠道 |
| `gateway_api_key_model` | 密钥允许使用的对外模型，空列表表示全部模型 |
| `gateway_api_key_limit` | 可扩展限制项，当前支持额度和请求数 |

限制项：

- `QUOTA`：额度滑动窗口，支持 `HOUR`、`DAY`。
- `REQUEST`：请求数滑动窗口，支持 `MINUTE`、`HOUR`、`DAY`。
- 请求数在路由成功后记录，因此上游失败也会计入限制。
- 同一密钥、同一限制类型、同一窗口单位只能配置一条。

## 数据库迁移规范

- 首次安装脚本：`src/main/resources/db/schema-sqlite.sql`、`schema-mysql.sql`。
- 增量迁移脚本：`src/main/resources/db/migration/{sqlite,mysql}/V{version}.sql`。
- `DatabaseInstaller.CURRENT_SCHEMA_VERSION` 必须和最新迁移版本一致。
- 新增表/字段必须同时更新 SQLite、MySQL schema 和迁移脚本。
- SQLite 不支持原生表注释时，在 SQL 中使用中文注释说明表和字段含义。
- 删除或替换表前必须先备份或同步数据，并在 SQL 注释中说明数据保护方式。

## 前端开发规范

- 技术栈：Vue 3.5、`<script setup lang="ts">`、Naive UI、Vite。
- 类型统一放在 `frontend/src/types/index.ts`。
- 接口封装放在 `frontend/src/api/`。
- CRUD 页面遵循：表格、弹窗表单、API 调用、toast 错误提示。
- 网关密钥和上游密钥只显示脱敏值；创建密钥时允许复制原文，其他场景不要暴露明文。
- 渠道模型选择要按上游模型 ID 去重，避免同一渠道重复保存模型。

## JDK 25 安装

项目必须使用 JDK 25 编译和运行。创建目录、克隆仓库、下载 JDK 和启动脚本示例见根目录 [README.md](../README.md#下载-jdk-并启动)。

## 测试与验证

后端：

```bash
JAVA_HOME=/path/to/jdk-25 PATH=/path/to/jdk-25/bin:$PATH mvn -q test
```

前端：

```bash
cd frontend
npm run build
```

新增能力建议覆盖：

- 管理端接口保存和查询。
- 路由授权和失败路径。
- Provider 请求体、响应体、错误处理。
- 协议适配器的工具调用、流式输出和 usage 解析。
- 数据库迁移后的兼容行为。

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端监听端口 |
| `API_CONVERT_TIME_ZONE` | `Asia/Shanghai` | 应用时区 |
| `API_CONVERT_JACKSON_MAX_STRING_LENGTH` | `100000000` | JSON 单个字符串最大长度，支持 base64 图片/视频透传 |
| `API_CONVERT_DB_TYPE` | `sqlite` | 数据库类型：`sqlite` 或 `mysql` |
| `API_CONVERT_SQLITE_PATH` | `${user.dir}/api-convert.db` | SQLite 文件路径 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${api-convert.database.sqlite-path}` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | 空 | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `API_CONVERT_DB_INSTALL_ENABLED` | `true` | 是否启动时自动安装/升级表结构 |
| `API_CONVERT_SECURITY_ENABLED` | `true` | 是否启用网关 API Key 鉴权 |
| `API_CONVERT_ADMIN_USERNAME` | `admin` | 管理端账号 |
| `API_CONVERT_ADMIN_PASSWORD` | `admin123` | 管理端密码 |
| `API_CONVERT_AUTH_STORAGE_DIR` | 空 | AUTH 渠道授权文件存储目录 |
| `LOG_PATH` | `logs` | 日志输出目录 |

## 发布检查

1. 更新数据库 schema 和进度文档。
2. 执行后端测试和前端构建。
3. 确认 README 与开发文档描述和实际能力一致。
4. 创建版本 tag，例如 `v1.0.5`。
5. 构建并推送 Docker 镜像。
