# 模块 03：路由与调度

> 对应代码：`service/RoutingService`、`service/SystemConfigService`、`service/UsageRecorder`
> 依赖模块：[01-基础设施](01-infrastructure.md)（数据库表）、[02-安全鉴权](02-security.md)（密钥鉴权结果）
> 被依赖模块：[04-端点](04-endpoints.md)、[05-Provider](05-providers.md)

---

## 1. 模型路由解析 (`RoutingService`)

### 1.1 模型写法支持

| 写法 | 匹配规则 | 说明 |
|---|---|---|
| `example-chat` | 按 `ai_channel_model.public_name` 匹配 | 默认路由，自动选择可用渠道 |
| `channel-a/example-chat` | 按 `channel_code + provider_model` | 直接指定渠道模型 |

### 1.2 路由查找链路

1. 模型映射查找（`ai_channel_model`）
2. 按网关密钥的渠道白名单和模型白名单过滤候选；两者为空分别表示允许全部渠道/模型
3. 渠道主配置查找（`ai_channel`）
4. 返回 `ModelRoute`（含 `providerType`、`baseUrl`、`apiKey`、`chatPath` 等）

模型白名单按 `ai_channel_model.public_name` 判断，也会作用于 `channel/provider_model` 直连写法，避免调用方绕过对外模型授权。

### 1.3 多渠道路由策略

系统配置字段 `routing_mode`：

| 模式 | 机制 |
|---|---|
| `RANDOM` | 随机选择一个可用渠道 |
| `ROUND_ROBIN` | 轮询 |
| `WEIGHTED` | 按渠道 `priority` 权重平滑加权轮询 |
| `SESSION_STICKY` | 会话粘性：从请求头/参数提取 `session_id`、`thread_id`、`x-client-request-id`、`prompt_cache_key` 等稳定标识，同一网关密钥 + 模型 + 会话在 TTL 内优先命中首次渠道 |

### 1.4 工具请求优先路由

- 请求携带 `tools` 时，同名模型中优先选择 `tools_support=true` 的渠道
- 未配置任何 `tools_support=true` 时保持原有策略
- 避免 Codex 等工具调用请求命中不支持工具的渠道

### 1.5 错误避让机制

- 上游 `ProviderException` 按「网关密钥 + 渠道 + 模型」累计失败次数
- 达到配置阈值后，该密钥在冷却时间内避让该渠道模型，切换其他可用渠道
- 成功调用清除对应失败状态

### 1.6 密钥级失败切换

- 网关密钥 `failover_enabled=true` 时，请求会解析同模型所有可用候选；第一个候选仍由当前路由策略选择
- 当前渠道尝试失败且尚未向客户端写出响应字节时，网关使用原始统一请求重新按下一个候选渠道执行适配和转发
- 可切换失败包括供应商无额度/限流/鉴权失败、上游 400 参数或响应异常、路由能力不支持以及候选渠道尝试中的运行时异常；网关本地鉴权失败、密钥额度不足、模型不存在、无路由等全局拒绝不切换
- 额度预检会按每个候选渠道单价执行；请求数窗口仍按一次入口请求记录，成功后只按最终成功渠道扣费和记录成功日志
- 流式请求只有在还没有写出任何 SSE 字节时才切换；一旦已经写给客户端，就继续使用该渠道的流或错误事件，避免破坏客户端协议

### 1.7 失败响应码

| 场景 | 错误码 | HTTP |
|---|---|---|
| 模型不存在 | `MODEL_NOT_FOUND` | 404 |
| 厂商不存在 | `PROVIDER_NOT_FOUND` | 404 |
| 厂商未启用 | `PROVIDER_UNAVAILABLE` | 503 |
| 无有效凭证 | `PROVIDER_AUTH_FAILED` | 502 |

## 2. 请求日志 (`UsageRecorder`)

- 每次请求完成后记录到 `request_log` 表
- 字段：请求协议、端点类型、模型、渠道、token 用量、延迟、错误信息、状态码、用户标识等
- 流式请求：结束时从最后一个 SSE 块提取 `usage`
- 用于 Dashboard 统计与审计追溯

## 3. 系统配置 (`SystemConfigService`)

`gateway_system_config` 运行期配置项：
- 路由模式（`RANDOM`/`ROUND_ROBIN`/`WEIGHTED`/`SESSION_STICKY`）
- 会话粘性 TTL
- 错误避让阈值与冷却时间
