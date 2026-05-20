# 模块 06：流式传输与 SSE 转换

> 对应代码：`stream/` 包、`RealTimeResponsesTransformer`
> 依赖模块：[04-端点](04-endpoints.md)（端点类型决定转换目标格式）、[05-Provider](05-providers.md)（上游 SSE 格式）
> 被依赖模块：无（最下游链路）

---

## 1. StreamResponseTransformer 抽象

| 组件 | 说明 |
|---|---|
| `StreamResponseTransformer` 接口 | `wrap(OutputStream)` 返回包装后的输出流；将上游 SSE 字节流实时转换为目标协议 SSE |
| `StreamTransformerRegistry` | 按 `(EndpointType, ProviderType)` 查找对应的 `StreamResponseTransformer` |
| `RealTimeResponsesTransformer` | 核心实现：将上游 Chat Completions SSE 实时转为 Responses API SSE |

## 2. SSE 字节级透传

- `stream=true` 时直接将上游 SSE 字节流透传客户端
- 流结束时从最后一个 SSE 块提取 `usage` 字段
- 路由阶段失败返回普通错误；已开始流式写出后的错误写成 SSE error 事件
- OpenAI 风格错误通过 `writeOpenAiStreamError()` 写出

## 3. RealTimeResponsesTransformer 转换逻辑

作为 `OutputStream` 拦截器，将上游 Chat Completions SSE 转为 Responses API SSE：

### 3.1 事件序列

1. 首个字节到达时写出初始 4 个事件：`response.created`、`response.in_progress`、`response.output_item.added`、`response.content_part.added`
2. 每个 Chat Completions `delta.content` 即时转为 `response.output_text.delta` 事件并 flush
3. 检测到 `finish_reason` 或 `[DONE]` 时写出 `response.output_text.done`、`response.content_part.done`、`response.output_item.done`
4. 用量到达后写出 `response.completed`（含完整 output 和 usage）
5. 上游失败时写出 `error` + `response.completed`（status=failed）

### 3.2 Codex 兼容处理

| 场景 | 处理方式 |
|---|---|
| 累计 `delta.content` | 转换为 `response.output_text.delta` 时只下发新增后缀，避免重复显示 |
| 上游返回原生 Responses SSE | 解析 `response.output_text.delta`/`response.completed` 并重新输出规范 SSE |
| Chat `delta.tool_calls` | 实时转为 `response.output_item.added`、`response.function_call_arguments.delta/done` + 最终 `function_call` output item |
| 流结束 | 确保输出合法 JSON 的 `response.completed`，避免 Codex 报错重试 |

### 3.3 SSE 字段规范对照

| 事件 | 字段要求 |
|---|---|
| `response.output_item.added` | `item`（非 `output_item`），含 `object:"item"`、`status:"in_progress"` |
| `response.content_part.added` | `part` 必须含 `annotations:[]` |
| `response.output_text.delta` | `delta` 字段 |
| `response.output_text.done` | `text` + `annotations:[]` |
| `response.function_call_arguments.delta` | `output_index`、`item_id`、`delta` |
| `response.function_call_arguments.done` | `output_index`、`item_id`、完整 `arguments` |
| `response.content_part.done` | `part` 含完整 `output_text` + `annotations:[]` |
| `response.output_item.done` | `item`（非 `output_item`），含 `object:"item"`、`status:"completed"` |
| `response.completed` | output 中 item 含 `object:"item"`、`status:"completed"` |

### 3.4 控制器注意事项

- 流式路径返回 `StreamingResponseBody`（**不包装在 `ResponseEntity`** 中）
- 由 `StreamingResponseBodyReturnValueHandler` 在异步线程中写出 SSE
- 直接在 `HttpServletResponse` 上设置头部
- 不能用 `ResponseEntity<StreamingResponseBody>`，否则 Spring 会尝试序列化导致 `HttpMediaTypeNotAcceptableException`
