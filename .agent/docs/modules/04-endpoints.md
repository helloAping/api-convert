# 模块 04：端点与协议适配

> 对应代码：`endpoint/`、`adapter/endpoint/`、`controller/GatewayController`
> 依赖模块：[03-路由](03-routing.md)（`RoutingService`、`ChatGatewayService`）、[05-Provider](05-providers.md)（`AiProviderClient`）
> 被依赖模块：[06-流式](06-streaming.md)（流式转换器）、[08-测试](08-testing.md)

---

## 1. 端点策略模式 (`endpoint` 包)

### 1.1 架构组件

| 组件 | 说明 |
|---|---|
| `EndpointType` 枚举 | 携带 HTTP 方法、路径、协议标签、鉴权方式、中文说明；提供 `toEndpointVO()` 和 `allEndpointVOs()` 供管理端展示 |
| `ProtocolFormat` | 协议格式标识符：`openai`、`claude`、`gemini`、`openai-responses` |
| `EndpointHandler` 接口 | `endpointType()` 返回所属类型；`handle(request, response)` 处理完整请求生命周期 |
| `EndpointRegistry` | Spring 组件，注入 `List<EndpointHandler>` 构建 `EnumMap<EndpointType, EndpointHandler>` |
| `GatewayController` | 统一调度控制器，只负责路径映射和委托注册表执行，不含业务逻辑 |

### 1.2 当前端点处理器

| 端点 | 处理器 | 说明 |
|---|---|---|
| `CHAT_COMPLETIONS` | `ChatCompletionsEndpointHandler` | OpenAI Chat 协议：`OpenAiRequestAdapter` → 网关 → `OpenAiResponseAdapter` |
| `ANTHROPIC_MESSAGES` | `AnthropicMessagesEndpointHandler` | Claude 协议：`AnthropicRequestAdapter` → 网关 → `AnthropicResponseAdapter` |
| `OPENAI_RESPONSES` | `OpenAiResponsesEndpointHandler` | Responses API：`OpenAiResponsesRequestAdapter` → 网关 → `OpenAiResponsesResponseAdapter`；流式使用 `RealTimeResponsesTransformer` |
| `OPENAI_MODELS` | `OpenAiModelsEndpointHandler` | 模型列表（不走 ChatGatewayService，直接查 DB） |

**管理端端点元信息**：`AdminGatewayInfoController` 通过 `EndpointType.allEndpointVOs()` 自动推导端点清单，新增端点时无需修改控制器。

## 2. 两层策略模式协作

1. **端点层（EndpointHandler）** — 负责"入口协议适配"：外部请求格式 → `UnifiedChatRequest` → 调用网关 → `UnifiedChatResponse` → 转回外部响应格式
2. **Provider 层（AiProviderClient）** — 负责"上游厂商适配"：`UnifiedChatRequest` → 厂商 API 请求 → 调用上游 → 转回 `UnifiedChatResponse`

端点通过 `ChatGatewayService` 调用 Provider 层，后者按 `ModelRoute.providerType` 从 `ProviderClientRegistry` 获取对应客户端。

## 3. 端点-供应商适配器 (`adapter/endpoint/` 包)

当客户端请求的端点协议与路由解析出的供应商类型不一致时，由适配器自动完成请求预处理和响应后处理。

### 3.1 架构组件

| 组件 | 说明 |
|---|---|
| `EndpointProviderAdapter` 接口 | `sourceEndpoint()` 源端点、`targetProvider()` 目标供应商、`adaptRequest()` 请求预处理（默认透传）、`adaptResponse()` 响应适配 |
| `EndpointProviderAdapterRegistry` | 构建 `(EndpointType, ProviderType)` → `EndpointProviderAdapter` 映射 |
| 12 个具体适配器 | 覆盖主流跨协议组合 |
| `ChatToolSequenceNormalizer` | DeepSeek Chat 等严格 Chat 上游要求 assistant `tool_calls` 与对应 `tool` 结果相邻；该辅助类会重排匹配结果并裁剪无结果调用 |

### 3.2 适配器列表

| 端点 → 供应商 | 适配器 | 说明 |
|---|---|---|
| `CHAT_COMPLETIONS` → `ANTHROPIC` | `ChatCompletionsToAnthropicAdapter` | Chat → Claude |
| `CHAT_COMPLETIONS` → `DEEPSEEK_CHAT` | `ChatCompletionsToDeepSeekChatAdapter` | Chat → DeepSeek Chat |
| `CHAT_COMPLETIONS` → `DEEPSEEK_ANTHROPIC` | `ChatCompletionsToDeepSeekAnthropicAdapter` | Chat → DeepSeek Claude |
| `CHAT_COMPLETIONS` → `GEMINI` | `ChatCompletionsToGeminiAdapter` | Chat → Gemini |
| `ANTHROPIC_MESSAGES` → `OPENAI_COMPATIBLE` | `AnthropicToOpenAiCompatibleAdapter` | Claude → OpenAI |
| `ANTHROPIC_MESSAGES` → `DEEPSEEK_CHAT` | `AnthropicToDeepSeekChatAdapter` | Claude → DeepSeek Chat |
| `ANTHROPIC_MESSAGES` → `GEMINI` | `AnthropicMessagesToGeminiAdapter` | Claude → Gemini |
| `ANTHROPIC_MESSAGES` → `DEEPSEEK_ANTHROPIC` | `AnthropicMessagesToDeepSeekAnthropicAdapter` | Claude → DeepSeek Claude |
| `OPENAI_RESPONSES` → `OPENAI_COMPATIBLE` | `ResponsesToOpenAiCompatibleAdapter` | Responses → OpenAI |
| `OPENAI_RESPONSES` → `ANTHROPIC` | `ResponsesToAnthropicAdapter` | Responses → Claude |
| `OPENAI_RESPONSES` → `DEEPSEEK_CHAT` | `ResponsesToDeepSeekChatAdapter` | Responses → DeepSeek Chat |
| `OPENAI_RESPONSES` → `DEEPSEEK_ANTHROPIC` | `ResponsesToDeepSeekAnthropicAdapter` | Responses → DeepSeek Claude |

### 3.3 `response_format` 支持

- 显式建模为 `ResponseFormat` record，支持 `text`/`json_object`/`json_schema`
- `isJson()` 方法判断是否启用 JSON 输出模式
- 跨协议路由（OpenAI→Anthropic）时 `response_format` 被过滤，不会传给 Anthropic 上游

### 3.4 DeepSeek Chat 工具消息归一化

- `ChatCompletionsToDeepSeekChatAdapter` 和 `ResponsesToDeepSeekChatAdapter` 会调用 `ChatToolSequenceNormalizer.normalizeForStrictChat()`
- assistant `tool_calls` 后面的匹配 `tool` 结果会被提前到相邻位置，满足 DeepSeek Chat 对 Chat Completions 工具序列的严格校验
- 没有对应工具结果的 tool call 会被裁剪；如 assistant 同时有文本内容，则保留文本消息，避免上游因孤立 tool call 拒绝请求

## 4. 聊天转发完整链路

```
客户端请求
  → GatewayController (统一调度)
    → EndpointHandler (按 EndpointType 分发)
      → RequestAdapter.toUnified()  # 转为 UnifiedChatRequest
        → ChatGatewayService.chat() / .stream()
          1. 生成 UUID requestId
          2. RoutingService.resolve(model)
          3. EndpointProviderAdapter.adaptRequest()  # 跨协议请求预处理
          4. ProviderClientRegistry.get(type)  # 获取厂商客户端
          5. 非流式: client.chat(route, adaptedRequest) → adaptResponse()
          5. 流式: streamTransformerRegistry.get(endpoint, provider) → transformer.wrap() → client.streamChat()
          6. ApiKeyQuotaService.deduct()  # 扣减额度
          7. UsageRecorder.recordSuccess()  # 记录日志
        → ResponseAdapter.toXXX()  # 转回外部响应格式
  → 返回客户端
```
