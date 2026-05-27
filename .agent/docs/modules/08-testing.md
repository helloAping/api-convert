# 模块 08：测试体系

> 对应代码：`src/test/java/` 下所有测试类
> 依赖模块：[04-端点](04-endpoints.md)、[05-Provider](05-providers.md)、[06-流式](06-streaming.md)
> 被依赖模块：无（验证层）

---

## 1. 测试概览

| 测试类 | 用例数 | 说明 |
|---|---|---|
| `ApiConvertApplicationTests` | 27 | `@SpringBootTest` 集成测试：上下文加载、渠道 CRUD、AUTH 渠道上传与路由、模型管理、请求日志分页、统计仪表盘、路由解析、工具请求优先路由、系统路由配置、轮询、加权、会话粘性、失败避让、额度不足、流式路由失败日志、密钥多限制项、重复窗口单位校验、重复上游模型校验、删除渠道清理密钥渠道授权并禁用失去最后显式渠道的密钥、失败请求计数、模型白名单直连约束、密钥级非流式/流式失败切换 |
| `OpenAiCompatibleProviderClientTests` | 1 | Provider Client URL 构建逻辑 |
| `DeepSeekChatProviderClientTests` | 2 | DeepSeek Chat thinking 模式下 assistant 历史消息的 `reasoning_content` 兜底与保留 |
| `AnthropicProviderClientTests` | 1 | DeepSeek Anthropic 独立供应商 thinking 内容块兼容 |
| `AnthropicToOpenAiCompatibleAdapterTests` | 2 | Anthropic → OpenAI 工具参数、工具消息、响应工具块转换 |
| `OpenAiResponsesRequestAdapterTests` | 2 | Responses 原生参数保留、`function_call/function_call_output` 与统一工具消息互转 |
| `ResponsesToOpenAiCompatibleAdapterTests` | 8 | Responses → OpenAI/DeepSeek Chat 请求参数、工具调用响应、reasoning 续轮转换、工具调用与工具结果顺序修复、无结果工具调用裁剪 |
| `ResponsesToAnthropicAdapterTests` | 2 | Responses → Anthropic `system`/`tools`/`tool_use`/`tool_result` 转换、连续工具调用合并 |
| `RealTimeResponsesTransformerTests` | 4 | Codex SSE 兼容：累计文本去重、原生 Responses SSE 解析、Chat `reasoning_content`/`tool_calls` 转 Responses `reasoning`/`function_call`、流结束 JSON 合法性 |
| `EndpointTypeTests` | 1 | 公开端点类型枚举与路径映射 |
| `AiProviderClientTests` | 1 | Provider 默认不支持能力的异常边界 |
| `DateTimeConfigTests` | 1 | 全局 `ObjectMapper` 支持超过 Jackson 默认上限的 base64 字符串 |

## 2. 运行测试

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
```

当前总量：12 个测试类、52 个用例。

## 3. 已知问题

- MyBatis 3.5.19 下 `BoundSql` 参数映射为不可变，`RequestLogMapper` 分页查询通过复制参数映射解决
- `/api/admin/request-logs` 已有集成测试覆盖 page/pageSize 行为
