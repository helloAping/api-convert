# OAuth Auth Provider Import Plan

## 背景与边界

目标是在现有渠道管理中新增 GPT、Claude、Gemini 的 OAuth/授权文件导入型供应商，让前端在供应商类型中选择 `GPT_AUTH`、`CLAUDE_AUTH`、`GEMINI_AUTH` 后，可以上传 `auth.json`，或触发对应渠道的 OAuth 登录流程。授权后的身份校验文件保存到数据库文件所在目录同级的 `auth-dir` 下，并按供应商类型隔离。

当前仓库主链路使用 `ai_channel.api_key` 承载上游凭据，`ModelRoute.apiKey()` 传入 Provider Client。新增能力需要把“静态 API Key”扩展为“文件凭据/可刷新 OAuth token”，但不改动网关入口协议和路由策略。

官方文档边界：

- OpenAI API 官方 REST 鉴权仍是 API Key Bearer；`GPT_AUTH` 如使用 ChatGPT/Codex 账号 OAuth，属于需要独立适配的授权文件来源，不应混入 `OPENAI_RESPONSES` 的 API Key 语义。
- Claude API 官方支持 API Key 和 Workload Identity Federation；`CLAUDE_AUTH` 的用户网页登录/Claude Code 类凭据需要作为单独 auth-provider 处理，并保留不支持或需配置的状态提示。
- Gemini API 官方支持 OAuth/ADC Bearer token；`GEMINI_AUTH` 可以优先实现完整 OAuth token 刷新链路。

## 成功标准

1. 前端渠道表单可选择 `GPT_AUTH`、`CLAUDE_AUTH`、`GEMINI_AUTH`。
2. 选择 auth 类型后，表单展示“上传 auth.json”和“OAuth 登录”入口，不再要求直接输入 API Key。
3. 上传或登录成功后，后端将标准化后的 `auth.json` 保存到 `{db-parent}/auth-dir/{provider-type}/{channel-code}.json`，并在数据库中保存相对引用与状态，不在 API 响应和日志中暴露 token。
4. 路由到 auth 类型渠道时，Provider Client 自动从文件加载有效 access token；过期时按 refresh token 刷新并原子写回文件。
5. 现有 API Key 类型渠道行为不变。
6. 完成后更新 `.agent/docs/AI_GATEWAY_PROGRESS.md`。

## 数据库与存储设计

### 1. 扩展渠道表

文件：

- `src/main/resources/db/schema-sqlite.sql`
- `src/main/resources/db/schema-mysql.sql`
- `src/main/resources/db/migration/sqlite/V12.sql`
- `src/main/resources/db/migration/mysql/V12.sql`
- `src/main/java/cn/ms08/apiconvert/service/DatabaseInstaller.java`
- `src/main/java/cn/ms08/apiconvert/entity/AiChannelEntity.java`

新增字段建议：

- `auth_mode`: `API_KEY` / `AUTH_FILE` / `OAUTH`
- `auth_file_path`: 相对 `auth-dir` 的路径，例如 `GEMINI_AUTH/google-main.json`
- `auth_status`: `NOT_CONFIGURED` / `AUTHORIZED` / `EXPIRED` / `ERROR`
- `auth_subject`: 授权身份展示值，例如邮箱、账号 ID、项目 ID，必须脱敏
- `auth_expires_at`: access token 过期时间

迁移：

- `CURRENT_SCHEMA_VERSION` 从 11 升到 12。
- 旧渠道默认 `auth_mode='API_KEY'`，`auth_status` 根据 `api_key` 是否为空设置为 `AUTHORIZED` 或 `NOT_CONFIGURED`。
- 首次安装 schema 同步包含 V12 字段。

### 2. 新增 auth-dir 解析服务

文件：

- `src/main/java/cn/ms08/apiconvert/config/GatewayProperties.java`
- `src/main/java/cn/ms08/apiconvert/service/auth/AuthStorageService.java`
- `src/main/java/cn/ms08/apiconvert/service/auth/AuthFileService.java`

规则：

- SQLite 默认：取 `api-convert.database.sqlite-path` 的父目录，写入 `auth-dir`。
- MySQL 默认：采用服务默认数据目录 `/opt/data/auth-dir`；同时提供 `api-convert.auth.storage-dir` 允许显式覆盖。
- 按 `{providerType}/{safeChannelCode}.json` 保存，禁止路径穿越。
- 写入使用临时文件 + 原子替换；权限尽量限制为当前用户可读写。

## 后端接口设计

### 3. 扩展渠道表单与响应

文件：

- `src/main/java/cn/ms08/apiconvert/dto/admin/ChannelForm.java`
- `src/main/java/cn/ms08/apiconvert/vo/admin/ChannelVO.java`
- `src/main/java/cn/ms08/apiconvert/service/admin/AdminChannelService.java`

新增字段：

- Request: `authMode`, `authFilePath` 不由前端直接提交原始路径，上传接口写入后绑定。
- VO: `authMode`, `authStatus`, `authSubject`, `authExpiresAt`, `hasAuthFile`。

校验：

- `OPENAI_COMPATIBLE`、`ANTHROPIC`、`OPENAI_RESPONSES`、`GEMINI` 等现有类型仍要求 API Key 才可路由。
- `GPT_AUTH`、`CLAUDE_AUTH`、`GEMINI_AUTH` 不要求 `apiKey`，但路由前必须存在可用 `auth_file_path` 且 `auth_status='AUTHORIZED'`。

### 4. 新增授权管理接口

文件：

- `src/main/java/cn/ms08/apiconvert/controller/admin/AdminChannelAuthController.java`
- `src/main/java/cn/ms08/apiconvert/service/admin/AdminChannelAuthService.java`
- `src/main/java/cn/ms08/apiconvert/dto/admin/ChannelAuthStartRequest.java`
- `src/main/java/cn/ms08/apiconvert/vo/admin/ChannelAuthStartVO.java`
- `src/main/java/cn/ms08/apiconvert/vo/admin/ChannelAuthStatusVO.java`

接口建议：

- `POST /api/admin/channels/{id}/auth/upload`：multipart 上传 `auth.json`，解析、标准化、保存文件、更新渠道 auth 状态。
- `POST /api/admin/channels/{id}/auth/start`：生成 OAuth 授权 URL、state、回调地址。
- `GET /api/admin/channels/auth/callback`：接收 OAuth code/state，换取 token，保存 auth 文件。
- `GET /api/admin/channels/{id}/auth/status`：查询授权状态。
- `DELETE /api/admin/channels/{id}/auth`：删除 auth 文件引用并将状态置为 `NOT_CONFIGURED`。

安全要求：

- 上传大小限制在小范围内，例如 256 KB。
- 只接受 JSON，解析后按 provider schema 白名单字段保存。
- state 存短期内存缓存，绑定管理员会话和 channelId。
- 所有日志走 `LogSanitizer`，不得打印 access token、refresh token、client secret。

## Provider 与 token 刷新

### 5. 新增 ProviderType 与凭据抽象

文件：

- `src/main/java/cn/ms08/apiconvert/provider/ProviderType.java`
- `src/main/java/cn/ms08/apiconvert/dto/ModelRoute.java`
- `src/main/java/cn/ms08/apiconvert/service/RoutingService.java`
- `src/main/java/cn/ms08/apiconvert/provider/auth/AuthCredential.java`
- `src/main/java/cn/ms08/apiconvert/provider/auth/AuthTokenProvider.java`
- `src/main/java/cn/ms08/apiconvert/provider/auth/AuthTokenProviderRegistry.java`

新增类型：

- `GPT_AUTH`
- `CLAUDE_AUTH`
- `GEMINI_AUTH`

`ModelRoute` 增加 `authFilePath`、`authMode`，现有 `apiKey` 保持兼容。

`AuthTokenProvider` 职责：

- 读取 auth 文件。
- 校验 access token 是否过期。
- 使用 refresh token 刷新。
- 返回用于上游 HTTP 的鉴权头。

### 6. 新增 auth 类型 Provider Client

文件：

- `src/main/java/cn/ms08/apiconvert/provider/GptAuthProviderClient.java`
- `src/main/java/cn/ms08/apiconvert/provider/ClaudeAuthProviderClient.java`
- `src/main/java/cn/ms08/apiconvert/provider/GeminiAuthProviderClient.java`

实现策略：

- `GEMINI_AUTH` 复用 Gemini 请求/响应转换，但鉴权从 `x-goog-api-key` 改为 `Authorization: Bearer <access_token>`，并按需要附加 `x-goog-user-project`。
- `GPT_AUTH` 优先复用 OpenAI Responses 或 OpenAI-compatible 请求路径，鉴权使用 auth 文件提供的 Bearer token；若授权文件字段不足，模型获取和请求返回明确错误。
- `CLAUDE_AUTH` 优先复用 Anthropic 请求/响应转换，鉴权按官方 WIF Bearer token 或兼容 auth 文件提供的头部生成；若无法合法刷新，状态置为 `ERROR` 并提示管理员改用 API Key 或配置 WIF。

为降低第一阶段风险，可以先实现 auth 文件导入 + token header 使用，OAuth start/callback 只对 `GEMINI_AUTH` 完整打通；`GPT_AUTH`、`CLAUDE_AUTH` 的 OAuth 登录入口可在 provider 未配置 client metadata 时返回“不支持在线登录，请上传 auth.json”。

## 前端变更

### 7. API 与类型

文件：

- `frontend/src/types/index.ts`
- `frontend/src/api/channels.ts`

新增：

- `ChannelAuthStartVO`
- `ChannelAuthStatusVO`
- `uploadChannelAuth(id, file)`
- `startChannelAuth(id)`
- `getChannelAuthStatus(id)`
- `deleteChannelAuth(id)`

### 8. 渠道管理 UI

文件：

- `frontend/src/views/channels/ChannelList.vue`

变更：

- `channelTypes` 增加 auth 类型，标签显示为 `GPT-AUTH`、`CLAUDE-AUTH`、`GEMINI-AUTH`。
- auth 类型下隐藏或弱化 API Key 输入，展示授权状态、上传 `auth.json`、OAuth 登录按钮。
- 上传成功后刷新渠道详情并展示脱敏身份。
- OAuth 登录按钮打开后端返回的授权 URL，回调成功后前端刷新状态。
- “获取模型”对 auth 类型调用后端时不再传 API Key，而是使用已保存 auth 文件。

## 测试计划

### 9. 后端测试

文件：

- `src/test/java/cn/ms08/apiconvert/ApiConvertApplicationTests.java`
- `src/test/java/cn/ms08/apiconvert/provider/GeminiAuthProviderClientTests.java`
- `src/test/java/cn/ms08/apiconvert/service/auth/AuthFileServiceTests.java`

覆盖：

- V12 schema 安装和迁移字段存在。
- 上传 auth.json 后文件保存到 `auth-dir/{type}`，数据库状态更新，响应脱敏。
- 路径穿越、非 JSON、大文件被拒绝。
- `RoutingService` 允许 auth 文件渠道参与路由，不再要求 `api_key`。
- `GEMINI_AUTH` 使用 Bearer token 请求模型列表。
- token 过期时触发 refresh 并写回。

### 10. 前端验证

命令：

- `cd frontend && npm run build`

手工验证：

- 新建 `GEMINI_AUTH` 渠道，上传 auth 文件，保存模型映射。
- 编辑已有 API Key 渠道时行为不变。
- 删除授权后渠道状态变为未配置。

## 文档同步

文件：

- `.agent/docs/AI_GATEWAY_PROGRESS.md`

更新内容：

- 当前 schema 版本从 11 到 12。
- Provider 类型增加 `GPT_AUTH`、`CLAUDE_AUTH`、`GEMINI_AUTH`。
- 新增 auth-dir 存储规则、管理端授权接口、前端渠道授权入口。
- 待实现/已实现列表同步调整。

## 风险与待确认

1. `GPT_AUTH` 的 auth.json 来源和字段格式需要确认。OpenAI 官方 API 仍是 API Key；如果目标是 Codex/ChatGPT 登录凭据，需要明确目标文件格式。
2. `CLAUDE_AUTH` 的用户网页登录凭据不是 Claude API 官方主推方式；建议确认是否只支持 Claude WIF，还是要兼容 Claude Code auth 文件。
3. “数据库目录同级”在 MySQL 部署下没有本地数据库文件，计划默认用 `/opt/data/auth-dir`，并提供配置覆盖。
4. OAuth 在线登录需要每个 provider 的 client_id/client_secret/redirect_uri 配置；没有配置时只开放 auth.json 上传。
