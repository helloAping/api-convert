# 模块 02：安全鉴权与限流

> 对应代码：`security/`、`GatewayApiKeyFilter`、`ApiKeyHasher`、`ApiKeyQuotaService`
> 依赖模块：[01-基础设施](01-infrastructure.md)（数据库表 `gateway_api_key`、`gateway_api_key_channel`）
> 被依赖模块：[03-路由](03-routing.md)、[04-端点](04-endpoints.md)

---

## 1. 请求鉴权 (`GatewayApiKeyFilter`)

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

## 2. 密钥额度与计费 (`ApiKeyQuotaService`)

### 2.1 模型计费配置

`ai_channel_model` 支持配置：
- `input_quota_per_million` — 每 100 万输入 token 消耗额度
- `output_quota_per_million` — 每 100 万输出 token 消耗额度
- `cache_read_quota_per_million` — 每 100 万缓存读取 token 消耗额度

### 2.2 密钥额度控制

`gateway_api_key` 字段：
- `quota_balance` — 总余额；空表示不限制总额度（兼容历史密钥）
- `quota_limit` + `quota_window_value` + `quota_window_unit`（`HOUR`/`DAY`/`MONTH`）— 滑动窗口限制

### 2.3 额度计算链路

请求完成后，由 `ChatGatewayService` 调用 `ApiKeyQuotaService.deduct()`：
1. 根据 `UnifiedUsage` 中 `promptTokens`、`completionTokens`、`cacheReadTokens` 计算消耗
2. 按比例换算成额度（根据 `ai_channel_model` 的百万级单价）
3. 从 `quota_balance` 中扣减
4. 滑动窗口检查：超限则返回 `QUOTA_EXCEEDED`（429）

## 3. 异常码

| 错误 | HTTP 码 | 触发场景 |
|---|---|---|
| `UNAUTHORIZED` | 401 | Key 不存在、非 ACTIVE、SHA-256 不匹配 |
| `QUOTA_EXCEEDED` | 429 | 滑动窗口超限或余额不足 |
