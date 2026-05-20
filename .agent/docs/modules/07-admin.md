# 模块 07：管理端与前端

> 对应代码：`controller/admin/`、`service/admin/`、`vo/admin/`、`frontend/`
> 依赖模块：[01-基础设施](01-infrastructure.md)（数据表）、[02-安全鉴权](02-security.md)（Sa-Token）
> 被依赖模块：无（面向管理员）

---

## 1. 管理端接口一览

| 控制器 | 路径前缀 | 功能 |
|---|---|---|
| `AdminAuthController` | `POST /api/admin/login` | Sa-Token 登录（无 Bearer 前缀） |
| `AdminApiKeyController` | `/api/admin/api-keys` | API Key CRUD、额度追加 |
| `AdminChannelController` | `/api/admin/channels` | 渠道 CRUD、模型抓取 |
| `AdminChannelAuthController` | `/api/admin/channels/{id}/auth/*` | AUTH 渠道授权文件上传、OAuth 授权链接、回调 URL 导入、状态查询 |
| `AdminModelController` | `/api/admin/models` | 模型映射 CRUD、能力字段 |
| `AdminRequestLogController` | `/api/admin/request-logs` | 日志查询、分页 |
| `AdminDashboardController` | `/api/admin/dashboard` | 统计仪表盘、饼图数据 |
| `AdminGatewayInfoController` | `/api/admin/gateway-info` | 端点元信息、版本信息 |
| `AdminSystemConfigController` | `/api/admin/system-config` | 路由模式、会话粘性、错误避让配置 |

## 2. 管理端鉴权

- Sa-Token 1.42，基于 `StpAdminUtil`
- 登录接口返回 Bearer token，后续请求携带 `Authorization: Bearer <token>`
- 与网关 API Key 鉴权分离（两套系统）

## 3. 管理端 API Key 管理

- 创建时生成随机 key，保存明文 + SHA-256 哈希 + 脱敏预览
- 支持配额追加（`quotaAdd`）
- 支持密钥-渠道白名单（`gateway_api_key_channel`）

## 4. 渠道管理（含 AUTH 模式）

### 4.1 普通渠道

- 选择 `ProviderType` 自动填充默认路径
- 配置 baseUrl、apiKey、请求路径、模型列表路径
- 模型抓取：从上游 API 拉取模型列表并导入

### 4.2 AUTH 渠道（V12）

- 隐藏 API Key / Base URL / 请求路径 / 模型列表路径 输入框
- 提供 `auth.json` 上传
- 生成 OAuth 授权链接（open / copy / 手动回调 URL 提交），回调入口为 `/api/admin/channels/auth/callback`
- 管理端响应脱敏身份、状态、过期时间，不暴露 token

## 5. Dashboard 统计

- 多维度聚合：模型、渠道、API Key
- 饼图 hover 交互：显示名称、总 token、请求数、占比
- 时间序列折线图：按小时/天统计 token 用量

## 6. 前端技术栈

| 组件 | 技术 |
|---|---|
| 框架 | Vue 3.5 |
| UI | Naive UI |
| 构建 | Vite |
| 优化 | Manual chunks：Vue / Naive UI+icon / Axios；仅注册模板使用的组件；提升 chunk warning 阈值 |
