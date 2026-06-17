# API 接口参考文档

**api-convert** AI API 网关对外暴露了一组兼容 OpenAI 和 Anthropic 协议的 API 端点，以及一组管理端 API 用于配置管理。

---

## 目录

1. [通用说明](#通用说明)
2. [公共 API 端点](#公共-api-端点)
3. [管理端 API](#管理端-api)
4. [数据模型](#数据模型)
5. [附录](#附录)

---

## 通用说明

### Base URL

- 开发环境: `http://localhost:8080`
- 生产环境: 由部署配置决定

### 认证方式

| 类型 | 说明 |
|---|---|
| **Gateway API Key** | 公共 API 端点使用，通过 `Authorization: Bearer <gateway-key>` 传递 |
| **Admin Token** | 管理端 API 使用，通过 `Authorization: Bearer <admin-token>` 传递，从登录接口获取 |
| **无认证** | `/health` 端点公开访问，无需认证 |

### 统一响应格式

所有 API 响应遵循统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

- `code`: `200` 表示成功，其他值表示错误
- `message`: 响应消息
- `data`: 响应数据，可能为 `null`

### 分页响应格式

管理端分页接口返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

### 通用错误码

| code | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或 token 无效 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 公共 API 端点

所有公共 API 端点均需通过 Gateway API Key 认证（`/health` 除外）。

### 1. 健康检查

```
GET /health
```

无需认证。返回服务状态和核心配置数量。

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "database": "UP",
    "installed": true,
    "schemaVersion": 15,
    "providerCount": 5,
    "enabledModelCount": 20
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | String | 服务状态 |
| `database` | String | 数据库状态 |
| `installed` | Boolean | 是否已安装 |
| `schemaVersion` | Integer | 数据库 schema 版本 |
| `providerCount` | Integer | 已配置的渠道数量 |
| `enabledModelCount` | Integer | 已启用的模型数量 |

---

### 2. 模型列表

```
GET /v1/models
```

OpenAI 兼容的模型列表。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |

**响应示例：**

```json
{
  "object": "list",
  "data": [
    {
      "id": "gpt-4o",
      "object": "model",
      "created": 1700000000,
      "owned_by": "gateway"
    }
  ]
}
```

---

### 3. Chat Completions

```
POST /v1/chat/completions
```

OpenAI 兼容的聊天补全接口，支持 SSE 流式透传。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "stream": false,
  "temperature": 0.7,
  "max_tokens": 1024,
  "response_format": {
    "type": "json_object"
  }
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称（支持 `channel/model` 格式指定渠道） |
| `messages` | Array | 是 | 消息列表 |
| `stream` | Boolean | 否 | 是否启用 SSE 流式输出，默认 `false` |
| `temperature` | Number | 否 | 采样温度 |
| `max_tokens` | Integer | 否 | 最大输出 token 数 |
| `response_format` | Object | 否 | 响应格式（JSON 模式 / JSON Schema） |
| `tools` | Array | 否 | 工具/函数调用定义 |
| `tool_choice` | String/Object | 否 | 工具选择策略 |

**流式响应（`stream: true`）：**

标准 OpenAI SSE 格式，每个 `data:` 行包含一个 `chat.completion.chunk` 对象。

**非流式响应：**

标准 OpenAI Chat Completions 响应格式。

---

### 4. Anthropic Messages

```
POST /v1/messages
```

Anthropic Messages 兼容对话接口，支持 SSE 流式透传。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `anthropic-version` | 是 | `2023-06-01` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "claude-3-opus",
  "messages": [
    {"role": "user", "content": "Hello!"}
  ],
  "system": "You are a helpful assistant.",
  "max_tokens": 1024,
  "stream": false
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称（支持 `channel/model` 格式指定渠道） |
| `messages` | Array | 是 | 消息列表 |
| `system` | String | 否 | 系统提示词 |
| `max_tokens` | Integer | 是 | 最大输出 token 数 |
| `stream` | Boolean | 否 | 是否启用 SSE 流式输出 |
| `temperature` | Number | 否 | 采样温度 |
| `thinking` | Object | 否 | Thinking 扩展配置 |

---

### 5. Responses API

```
POST /v1/responses
```

OpenAI Responses API 新协议，支持 SSE 流式透传。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "gpt-4o",
  "input": "Hello!",
  "stream": false
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称 |
| `input` | String/Array | 是 | 输入消息 |
| `stream` | Boolean | 否 | 是否启用 SSE 流式输出 |
| `instructions` | String | 否 | 系统指令 |
| `tools` | Array | 否 | 工具列表 |

---

### 6. Videos API

```
POST /v1/videos
```

OpenAI Videos API 视频生成，按模型路由到支持视频的 OpenAI 兼容提供商。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "sora-2",
  "prompt": "A cat walking on a sunny beach",
  "n": 1,
  "size": "1920x1080"
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称 |
| `prompt` | String | 是 | 视频生成提示词 |
| `n` | Integer | 否 | 生成数量 |
| `size` | String | 否 | 视频尺寸 |

---

### 7. Images API

```
POST /v1/images/generations
```

OpenAI Images API 图片生成，按模型路由到支持图片的 OpenAI 兼容提供商。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "dall-e-3",
  "prompt": "A beautiful sunset over mountains",
  "n": 1,
  "size": "1024x1024",
  "quality": "standard"
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称 |
| `prompt` | String | 是 | 图片生成提示词 |
| `n` | Integer | 否 | 生成数量 |
| `size` | String | 否 | 图片尺寸 |
| `quality` | String | 否 | 图片质量 |

### 8. Embeddings API

```
POST /v1/embeddings
```

OpenAI 兼容嵌入接口，按模型路由到支持嵌入的 OpenAI 兼容提供商；按 `prompt_tokens` 计费，不计 output / cache。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "text-embedding-3-small",
  "input": "The quick brown fox jumps over the lazy dog",
  "encoding_format": "float",
  "dimensions": 1536
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | 模型名称 |
| `input` | String \| String[] | 是 | 单字符串或字符串数组，批量嵌入时传数组 |
| `encoding_format` | String | 否 | `float` 或 `base64`，默认 `float` |
| `dimensions` | Integer | 否 | 输出向量维度（部分模型支持） |
| `user` | String | 否 | 透传给上游的端用户标识 |

**响应（成功）：**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0123, -0.0456, ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 9,
    "total_tokens": 9
  }
}
```

### 9. Audio Speech API（TTS）

```
POST /v1/audio/speech
```

OpenAI 兼容文本转语音，按 `response_format` 决定输出格式（mp3 / opus / aac / flac / wav / pcm），返回二进制音频字节。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `application/json` |

**请求体：**

```json
{
  "model": "tts-1",
  "input": "The quick brown fox jumps over the lazy dog.",
  "voice": "alloy",
  "response_format": "mp3",
  "speed": 1.0
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | String | 是 | TTS 模型 ID，如 `tts-1` / `tts-1-hd` |
| `input` | String | 是 | 待合成文本，最长 4096 字符 |
| `voice` | String | 是 | 预置音色：`alloy` / `echo` / `fable` / `onyx` / `nova` / `shimmer` |
| `response_format` | String | 否 | 输出格式，默认 `mp3` |
| `speed` | Float | 否 | 语速倍率，可选 0.25 ~ 4.0，默认 1.0 |

**响应**：二进制音频字节 + 对应 Content-Type（`audio/mpeg` / `audio/ogg` / `audio/aac` / `audio/flac` / `audio/wav` / `audio/pcm`）；`Content-Disposition` 含 `attachment; filename="speech.<ext>"`。

### 10. Audio Transcriptions API（STT / Whisper）

```
POST /v1/audio/transcriptions
```

OpenAI 兼容语音转写（Whisper），以 multipart/form-data 上传音频文件，上游按 `verbose_json` 返回带词级时间戳的结果。

**Headers：**

| Header | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <gateway-api-key>` |
| `Content-Type` | 是 | `multipart/form-data; boundary=...` |

**表单字段：**

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `file` | File | 是 | 音频文件（mp3 / mp4 / mpeg / mpga / m4a / wav / webm），建议 ≤ 25 MB |
| `model` | String | 是 | Whisper 模型 ID，如 `whisper-1` |
| `language` | String | 否 | ISO-639-1 语言代码 |
| `prompt` | String | 否 | 上下文提示词，用于引导风格和专有名词 |
| `response_format` | String | 否 | 响应格式，默认 `json`；网关固定透传 `verbose_json` |
| `temperature` | Float | 否 | 0.0 ~ 1.0，默认 0 |
| `timestamp_granularities[]` | String[] | 否 | `word` / `segment` 时间戳粒度 |

**响应（verbose_json）：**

```json
{
  "task": "transcribe",
  "language": "en",
  "duration": 1.2,
  "text": "Hello, world!",
  "words": [
    { "word": "Hello", "start": 0.0, "end": 0.4 },
    { "word": "world", "start": 0.5, "end": 0.9 }
  ],
  "segments": [
    { "id": 0, "start": 0.0, "end": 1.2, "text": "Hello, world!", "tokens": [1, 2], "temperature": 0.0 }
  ]
}
```

---

## 管理端 API

管理端 API 需先登录获取 Admin Token，通过 `Authorization: Bearer <admin-token>` 传递。

### 11. 管理员认证

#### 登录

```
POST /api/admin/login
```

**请求体：**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应数据：**

```json
{
  "token": "xxxxx",
  "username": "admin"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `token` | String | Sa-Token Bearer token，后续请求作为 `Authorization: Bearer <token>` |
| `username` | String | 用户名 |

#### 登出

```
POST /api/admin/logout
```

**Headers：** `Authorization: Bearer <admin-token>`

#### 当前用户

```
GET /api/admin/me
```

**Headers：** `Authorization: Bearer <admin-token>`

**响应数据：**

```json
{
  "username": "admin"
}
```

---

### 12. 渠道管理

#### 渠道列表

```
GET /api/admin/channels
```

查询所有已配置渠道，凭据信息只返回脱敏结果。

**响应数据：** `List<ChannelVO>`

#### 获取单个渠道

```
GET /api/admin/channels/{id}
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 渠道 ID |

#### 创建渠道

```
POST /api/admin/channels
```

**请求体：**

```json
{
  "code": "my-channel",
  "name": "我的渠道",
  "type": "OPENAI_COMPATIBLE",
  "baseUrl": "https://api.example.com",
  "chatPath": "/v1/chat/completions",
  "videoPath": "/v1/videos",
  "imagePath": "/v1/images/generations",
  "embeddingPath": "/v1/embeddings",
  "audioSpeechPath": "/v1/audio/speech",
  "audioTranscriptionPath": "/v1/audio/transcriptions",
  "modelsPath": "/v1/models",
  "apiKey": "sk-xxxxx",
  "authMode": null,
  "priority": 10,
  "status": "ACTIVE",
  "publicModel": null,
  "providerModel": null,
  "modelPrefix": null,
  "models": [
    {
      "publicName": "gpt-4o",
      "providerModel": "gpt-4o-2024-08-06",
      "vision": false,
      "toolsSupport": true,
      "jsonModeSupport": true,
      "contextLength": 128000,
      "enabled": true
    }
  ],
  "enabled": true
}
```

**字段说明：**

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `code` | String | 是 | 稳定的渠道编码，持久化时也作为 provider_code |
| `name` | String | 是 | 管理后台显示名称 |
| `type` | String | 是 | ProviderType，如 `OPENAI_COMPATIBLE`, `ANTHROPIC`, `OPENAI_RESPONSES`, `GEMINI` 等 |
| `baseUrl` | String | 是 | 上游 Base URL |
| `chatPath` | String | 否 | 提供商特定的对话/消息请求路径 |
| `videoPath` | String | 否 | 视频生成 API 路径（V15+） |
| `imagePath` | String | 否 | 图片生成 API 路径（V15+） |
| `modelsPath` | String | 否 | 提供商特定的模型列表路径 |
| `apiKey` | String | 否 | 提供商凭据；更新时为空表示保留现有密钥 |
| `authMode` | String | 否 | AUTH 渠道的认证模式 |
| `priority` | Integer | 否 | 路由权重，加权模式下数值越高分配流量越大 |
| `status` | String | 否 | 凭证状态，如 `ACTIVE` 或 `DISABLED` |
| `publicModel` | String | 否 | 网关对外模型别名 |
| `providerModel` | String | 否 | 上游模型 ID，与 publicModel 配对保存 |
| `modelPrefix` | String | 否 | 模型前缀，保存时会拼接到对外模型名前方 |
| `models` | Array | 否 | 批量保存的模型映射列表，优先于兼容用的 publicModel/providerModel |
| `enabled` | Boolean | 否 | 渠道和端点是否可用于路由 |

#### 获取上游模型

```
POST /api/admin/channels/models
```

使用尚未保存的渠道表单值获取提供商模型选项。

**请求体：**

```json
{
  "type": "OPENAI_COMPATIBLE",
  "baseUrl": "https://api.example.com",
  "apiKey": "sk-xxxxx",
  "chatPath": null,
  "modelsPath": "/v1/models"
}
```

**响应数据：** `List<UpstreamModelVO>`（`{ id, ownedBy }`）

#### 实时获取渠道额度

```
GET /api/admin/channels/{id}/quota
```

实时获取当前渠道在上游提供商的额度；不保存查询结果。

**响应数据：**

```json
{
  "channelId": 1,
  "channelCode": "my-channel",
  "supported": true,
  "summary": "...",
  "balance": 100.00,
  "used": 50.00,
  "available": 50.00,
  "currency": "USD",
  "rawSummary": "..."
}
```

#### 更新渠道

```
PUT /api/admin/channels/{id}
```

请求体同创建渠道，渠道编码不可变。

#### 删除渠道

```
DELETE /api/admin/channels/{id}
```

删除渠道及其依赖的端点、凭证和模型映射记录。

---

### 13. 渠道 OAuth 授权

#### 上传 auth.json

```
POST /api/admin/channels/{id}/auth/upload
```

Content-Type: `multipart/form-data`

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 渠道 ID |
| `file` | File | auth.json 文件 |

#### 开始 OAuth 授权

```
POST /api/admin/channels/{id}/auth/start
```

生成授权 URL，前端打开提供商授权页面。

**响应数据：** `{ channelId, providerType, authorizationUrl, state }`

#### OAuth 回调

```
GET /api/admin/channels/auth/callback?code=xxx&state=xxx
```

处理 OAuth 回调 code。

#### 手动输入回调 URL

```
POST /api/admin/channels/{id}/auth/callback-url
```

**请求体：**

```json
{
  "callbackUrl": "https://..."
}
```

#### 获取授权状态

```
GET /api/admin/channels/{id}/auth/status
```

**响应数据：** `{ channelId, providerType, authMode, authStatus, authSubject, authExpiresAt, hasAuthFile }`

#### 删除授权

```
DELETE /api/admin/channels/{id}/auth
```

---

### 14. API Key 管理

#### API Key 列表

```
GET /api/admin/api-keys
```

#### 获取单个 Key

```
GET /api/admin/api-keys/{id}
```

#### 创建 API Key

```
POST /api/admin/api-keys
```

**请求体：**

```json
{
  "name": "My Key",
  "failoverEnabled": false,
  "channelCodes": ["channel-1", "channel-2"],
  "modelNames": ["gpt-4o", "claude-3-opus"],
  "quotaBalance": 100.00,
  "quotaLimit": 10.00,
  "quotaWindowValue": 1,
  "quotaWindowUnit": "HOUR",
  "limits": [
    {
      "type": "QUOTA",
      "value": 10.00,
      "windowValue": 1,
      "windowUnit": "HOUR"
    },
    {
      "type": "REQUEST_COUNT",
      "value": 100,
      "windowValue": 1,
      "windowUnit": "DAY"
    }
  ]
}
```

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `name` | String | 是 | 密钥显示名称 |
| `failoverEnabled` | Boolean | 否 | 是否启用请求上游未写出即失败后的同模型多频道切换 |
| `channelCodes` | Array | 否 | 允许使用的渠道编码列表，空列表表示允许所有渠道 |
| `modelNames` | Array | 否 | 允许使用的对外模型名列表，空列表表示允许所有模型 |
| `quotaBalance` | BigDecimal | 否 | 初始剩余额度；为空表示不限总额度 |
| `quotaLimit` | BigDecimal | 否 | 滑动窗口内最多可消耗的额度；为空表示不限制 |
| `quotaWindowValue` | Integer | 否 | 滑动窗口长度数值 |
| `quotaWindowUnit` | String | 否 | 滑动窗口单位（HOUR / DAY） |
| `limits` | Array | 否 | 可并存的限制项列表；空列表表示不启用任何窗口限制 |

**响应数据：** `ApiKeyCreationVO`（包含 `rawKey` 明文密钥）

#### 更新 API Key

```
PUT /api/admin/api-keys/{id}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 密钥名称 |
| `status` | String | 状态：`ACTIVE` / `DISABLED` |
| `failoverEnabled` | Boolean | 是否启用故障切换 |
| `channelCodes` | Array | 允许的渠道编码 |
| `modelNames` | Array | 允许的模型名称 |

#### 追加额度

```
POST /api/admin/api-keys/{id}/quota
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `amount` | BigDecimal | 追加的额度数量 |

#### 删除 API Key

```
DELETE /api/admin/api-keys/{id}
```

---

### 15. 模型映射管理

#### 模型列表

```
GET /api/admin/models
```

查询按对外模型名去重后的模型列表。

**响应数据：** `List<ModelVO>`

#### 获取单个模型

```
GET /api/admin/models/{id}
```

#### 更新模型额度单价

```
PUT /api/admin/models/{id}/quota
```

```json
{
  "inputQuotaPerMillion": 2.50,
  "outputQuotaPerMillion": 10.00,
  "cacheReadQuotaPerMillion": 1.25
}
```

#### 启用/禁用模型

```
PUT /api/admin/models/{id}/enabled
```

```json
{
  "enabled": true
}
```

#### 更新模型能力配置

```
PUT /api/admin/models/{id}/capabilities
```

```json
{
  "vision": true,
  "toolsSupport": true,
  "jsonModeSupport": true,
  "contextLength": 128000
}
```

---

### 16. 请求日志

#### 日志分页查询

```
GET /api/admin/request-logs
```

**查询参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| `requestId` | String | 请求编号 |
| `gatewayApiKeyId` | Long | 调用方网关密钥 ID |
| `sourceProtocol` | String | 外部协议，如 `openai` 或 `anthropic` |
| `requestType` | String | 对话接口类型，如 `chat_completions` 或 `messages` |
| `providerCode` | String | 实际渠道编码 |
| `providerType` | String | 实际提供商协议类型 |
| `publicModel` | String | 请求对外模型名 |
| `success` | Boolean | 是否成功 |
| `startTime` | String | 日志开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `endTime` | String | 日志结束时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `page` | Integer | 页码 |
| `pageSize` | Integer | 每页数量 |

**响应数据：** `PageResult<RequestLogVO>`

#### 获取日志详情

```
GET /api/admin/request-logs/{id}
```

**RequestLogVO 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 日志 ID |
| `requestId` | String | 请求编号 |
| `gatewayApiKeyId` | Long | 网关密钥 ID |
| `gatewayApiKeyName` | String | 网关密钥名称 |
| `gatewayApiKeyPreview` | String | 网关密钥脱敏预览 |
| `sourceProtocol` | String | 外部协议 |
| `requestType` | String | 请求类型 |
| `providerCode` | String | 渠道编码 |
| `providerType` | String | 提供商类型 |
| `publicModel` | String | 对外模型名 |
| `providerModel` | String | 上游模型名 |
| `stream` | Boolean | 是否流式 |
| `success` | Boolean | 是否成功 |
| `httpStatus` | Integer | HTTP 状态码 |
| `latencyMs` | Long | 延迟（毫秒） |
| `inputTokens` | Integer | 输入 token 数 |
| `cacheReadInputTokens` | Integer | 缓存读取 token 数 |
| `outputTokens` | Integer | 输出 token 数 |
| `totalTokens` | Integer | 总 token 数 |
| `errorCode` | String | 错误码 |
| `errorMessage` | String | 错误消息 |
| `createdAt` | String | 创建时间（`yyyy-MM-dd HH:mm:ss`） |

---

### 17. 仪表盘统计

```
GET /api/admin/dashboard/stats
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `days` | Integer | 否 | 按天统计的最远天数 |
| `hours` | Integer | 否 | 按小时统计的最远小时数 |
| `topN` | Integer | 否 | 模型、渠道、密钥维度返回的 Top N 数量 |

**响应数据：**

```json
{
  "summary": {
    "requestCount": 1000,
    "successCount": 950,
    "failureCount": 50,
    "inputTokens": 500000,
    "cacheReadInputTokens": 100000,
    "outputTokens": 200000,
    "totalTokens": 700000
  },
  "dailyTokenUsage": [...],
  "hourlyTokenUsage": [...],
  "modelDistribution": [...],
  "channelDistribution": [...],
  "apiKeyDistribution": [...],
  "modelSeries": [...],
  "channelSeries": [...],
  "apiKeySeries": [...]
}
```

---

### 18. 网关元信息

```
GET /api/admin/gateway-info
```

返回当前请求推导出的后端 Base URL 和已支持的公开网关接口。

**响应数据：**

```json
{
  "baseUrl": "http://localhost:8080",
  "endpoints": [
    {
      "method": "GET",
      "path": "/health",
      "protocol": "通用",
      "auth": "无需鉴权",
      "description": "健康检查..."
    },
    {
      "method": "POST",
      "path": "/v1/chat/completions",
      "protocol": "OpenAI",
      "auth": "Gateway API Key",
      "description": "OpenAI 兼容聊天补全..."
    }
  ]
}
```

---

### 19. 系统配置

#### 获取路由配置

```
GET /api/admin/system-config/routing
```

**响应数据：**

```json
{
  "mode": "RANDOM",
  "failureThreshold": 3,
  "failureCooldownMinutes": 5,
  "stickyTtlMinutes": 60
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `mode` | String | 路由模式：`RANDOM`, `ROUND_ROBIN`, `WEIGHTED`, `SESSION_STICKY` |
| `failureThreshold` | Integer | 故障阈值 |
| `failureCooldownMinutes` | Integer | 故障冷却时间（分钟） |
| `stickyTtlMinutes` | Integer | 会话粘性 TTL（分钟） |

#### 更新路由配置

```
PUT /api/admin/system-config/routing
```

**请求体：**

```json
{
  "mode": "WEIGHTED",
  "failureThreshold": 5,
  "failureCooldownMinutes": 10,
  "stickyTtlMinutes": 30
}
```

---

## 可观测性接口

Spring Boot Actuator 默认暴露健康检查与 Prometheus 抓取端点，路径前缀 `/actuator`；**不需要鉴权**（运维内网访问），部署时请通过反向代理限制访问。

### 1. 健康检查

```
GET /actuator/health
```

**响应：**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

### 2. Prometheus 抓取

```
GET /actuator/prometheus
```

网关业务指标（详见 [AI_GATEWAY_PROGRESS.md](AI_GATEWAY_PROGRESS.md)）：

| 指标 | 类型 | Tag 维度 | 说明 |
|---|---|---|---|
| `gateway_requests_total` | Counter | endpoint, status | 网关入口请求总数 |
| `gateway_request_duration_seconds` | Timer | endpoint, status | 网关入口请求耗时分布 |
| `gateway_upstream_calls_total` | Counter | endpoint, provider, status | 上游调用总数 |
| `gateway_upstream_duration_seconds` | Timer | endpoint, provider, status | 上游调用耗时分布 |
| `gateway_failover_attempts_total` | Counter | endpoint, provider | 失败切换次数 |
| `gateway_errors_total` | Counter | endpoint, error_code | 错误码分布 |
| `gateway_active_requests` | Gauge | endpoint | 在飞请求数 |

### 3. 请求 ID 关联

所有公开端点的响应头会回传 `X-Request-Id`，同时写入 SLF4J MDC，贯穿 `app-info.log` / `app-error.log` 等所有日志文件，便于跨服务追踪同一请求。

```bash
curl -i http://localhost:8080/v1/models \
  -H 'Authorization: Bearer sk-local-dev'
# HTTP/1.1 200
# X-Request-Id: 4f8b9c2e1a0d4e7f...
```

### 20. 熔断器管理

按 `(providerCode, providerModel)` 维度查看 / 重置上游熔断器状态；OPEN 状态由真实失败率自动驱动，不提供手动 OPEN 接口避免误操作把已知不可用渠道加回路由。

#### 列出所有熔断器

```
GET /api/admin/circuit-breakers
```

**响应数据：**

```json
{
  "code": 200,
  "data": [
    {
      "providerCode": "openai-main",
      "providerModel": "gpt-4o",
      "state": "CLOSED",
      "totalCalls": 20,
      "failureCount": 0,
      "openedAtEpochMs": 0
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `state` | `CLOSED` / `OPEN` / `HALF_OPEN` |
| `totalCalls` | 当前滑动窗口内的总调用数 |
| `failureCount` | 当前滑动窗口内的失败数 |

#### 读取熔断器配置

```
GET /api/admin/circuit-breakers/properties
```

**响应数据：**

```json
{
  "windowSize": 20,
  "failureRateThreshold": 0.5,
  "minimumCalls": 10,
  "openDurationSeconds": 60,
  "halfOpenMaxTrials": 3
}
```

#### 重置熔断器

```
POST /api/admin/circuit-breakers/{providerCode}/{providerModel}/reset
```

**路径参数：**

| 名称 | 说明 |
|---|---|
| `providerCode` | 渠道编码 |
| `providerModel` | 上游真实模型 ID |

强制把指定维度的熔断器重置为 `CLOSED`，并清空滑动窗口；用于运维主动恢复或验证场景。

---

## 附录

### 支持的 Provider 类型

| 类型 | 鉴权 | 协议 | 流式 | 说明 |
|---|---|---|---|---|
| `OPENAI_COMPATIBLE` | Bearer | Chat Completions + Videos + Images | 是 | 通用兼容 |
| `ANTHROPIC` | Bearer + version | Messages | 是 | Claude 官方 |
| `OPENAI_RESPONSES` | Bearer | Responses API | 是 | 原生 Responses |
| `GPT_AUTH` | Bearer (auth.json) | Chat Completions + Videos + Images | 是 | OAuth 授权（V12） |
| `CLAUDE_AUTH` | Bearer (auth.json) | Messages | 是 | OAuth 授权（V12） |
| `DEEPSEEK_CHAT` | Bearer | Chat + reasoning | 是 | DeepSeek Chat 风格 |
| `DEEPSEEK_ANTHROPIC` | Bearer + version | Messages + thinking | 是 | DeepSeek Claude 风格 |
| `GEMINI` | `x-goog-api-key` | `generateContent` | 否 | Google Gemini |
| `LOCAL` | 本地 | - | - | 本地模型 |

### 渠道编码（Channel Code）说明

- 每个渠道有稳定的编码（`code`），创建后不可变更
- 路由时支持 `channel/model` 格式指定渠道
- 删除渠道后，关联的 API Key 授权记录和模型映射一并清理

### 模型路由说明

网关支持多种路由模式：

| 模式 | 说明 |
|---|---|
| `RANDOM` | 随机选择已授权的渠道 |
| `ROUND_ROBIN` | 轮询选择 |
| `WEIGHTED` | 按权重分配流量（priority 字段） |
| `SESSION_STICKY` | 会话粘性，同一 API Key 的请求尽量路由到同一渠道 |

### 故障切换

当 API Key 启用 `failoverEnabled` 且请求在上游未写出任何响应字节就失败时，网关会自动重试已授权的其他渠道（同一模型）。涵盖的错误类型：

- 上游余额不足
- 频率限制
- 认证失败
- 上游参数/响应异常
- 不支持的路径能力
- 意外路由异常

网关本地认证、额度检查、模型授权失败直接返回，不触发切换。

### 配额与限制

API Key 支持多种限制类型：

| 类型 | 说明 |
|---|---|
| `QUOTA` | 额度消耗限制（按金额） |
| `REQUEST_COUNT` | 请求次数限制 |

每种限制可独立配置窗口大小（数值 + 单位 HOUR/DAY）。

### 数据脱敏

- 渠道 API Key：返回时仅显示前4位和后4位中间加 `****`
- API Key 明文：仅在创建时返回一次 `rawKey`，后续返回脱敏预览
- 请求日志中密钥信息自动脱敏
- 上游响应摘要字段脱敏

### 渠道授权模式（Auth Mode）

| 模式 | 说明 |
|---|---|
| `NONE` | 无授权，使用 `apiKey` 明文 |
| `AUTH_FILE` | 使用上传的 `auth.json` 文件 |
| `OAUTH` | OAuth 授权流程 |
