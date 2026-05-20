# 模块 05：Provider 厂商实现

> 对应代码：`provider/` 包
> 依赖模块：[03-路由](03-routing.md)（`ModelRoute`）、[04-端点](04-endpoints.md)（`EndpointType`）
> 被依赖模块：[06-流式](06-streaming.md)（流式转换器依赖 Provider 返回格式）

---

## 1. Provider 策略架构

| 组件 | 说明 |
|---|---|
| `ProviderType` 枚举 | 供应商类型标识，渠道创建时选择，自动推导 `chatPath` |
| `AiProviderClient` 接口 | 策略接口：`chat()`、`streamChat()`、`supportsStreaming()`、`fetchModels()`、`fetchQuota()` |
| `ProviderClientRegistry` | Spring 组件，构建 `EnumMap<ProviderType, AiProviderClient>` 注册表 |
| `ProviderException` | 上游调用失败时抛出，含状态码和上游错误体 |

## 2. 各 Provider 实现对比

| Provider | 鉴权方式 | 请求格式 | 流式 | 模型列表 | 额度查询 | 特殊说明 |
|---|---|---|---|---|---|---|
| `OPENAI_COMPATIBLE` | Bearer `Authorization` | OpenAI Chat Completions | ✅ | ✅ | ❌ | 通用兼容，如兼容 API |
| `ANTHROPIC` | Bearer `Authorization` + `anthropic-version` | Anthropic Messages | ✅ | ❌ | ❌ | Claude 官方 |
| `OPENAI_RESPONSES` | Bearer `Authorization` | OpenAI Responses API | ✅ | ❌ | ❌ | 原生 Responses 上游 |
| `GPT_AUTH` | Bearer `Authorization`（从 auth.json 读取） | OpenAI Chat Completions | ✅ | ❌ | ❌ | V12 新增，OAuth 授权 |
| `CLAUDE_AUTH` | Bearer `Authorization`（从 auth.json 读取） | Anthropic Messages | ✅ | ❌ | ❌ | V12 新增，OAuth 授权 |
| `DEEPSEEK_CHAT` | Bearer `Authorization` | OpenAI Chat Completions（含 `reasoning_content`） | ✅ | ❌ | ❌ | DeepSeek Chat 风格 |
| `DEEPSEEK_ANTHROPIC` | Bearer `Authorization` + `anthropic-version` | Anthropic Messages（含 thinking 块） | ✅ | ❌ | ❌ | DeepSeek Claude 风格 |
| `GEMINI` | `x-goog-api-key` | Gemini `generateContent` | ❌ | ✅（过滤 `generateContent`） | ❌ | `contents` + `system_instruction` |

## 3. DeepSeek 特殊处理

### 3.1 `DEEPSEEK_CHAT` — `reasoning_content`

- `/v1/responses` → `DEEPSEEK_CHAT` 时，将 Responses `reasoning` items 恢复为 DeepSeek Chat 的 `reasoning_content` 字段
- 历史 assistant 消息包含 `reasoning_content` 字段兜底，即使下游未返回 reasoning item

### 3.2 `DEEPSEEK_ANTHROPIC` — thinking 内容块

- 独立供应商类型，不再混在 `ANTHROPIC` 中
- 适配 DeepSeek 在 Anthropic API 风格中返回的 thinking 内容块

## 4. AUTH 文件 Provider（V12 新增）

### 4.1 `GPT_AUTH` / `CLAUDE_AUTH`

- 渠道保存时自动填充官方默认上游地址和路径：
  - `GPT_AUTH`: `https://api.openai.com` + `/v1/chat/completions`
  - `CLAUDE_AUTH`: `https://api.anthropic.com` + `/v1/messages`
- `ChatGatewayService` 将 `GPT_AUTH` 映射到 OpenAI-compatible adapters，`CLAUDE_AUTH` 映射到 Anthropic adapters
- OAuth 授权链路：内置 Codex/OpenAI 和 Claude OAuth metadata，支持配置覆盖
- 管理员可通过 `POST /api/admin/channels/{id}/auth/callback-url` 粘贴 localhost 回调 URL 完成授权
- 授权文件存储见 [01-基础设施](01-infrastructure.md)
