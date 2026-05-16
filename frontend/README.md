# api-convert 管理端前端

管理端使用 Vue 3 + TypeScript + Vite + Naive UI，负责维护上游渠道、模型映射、网关密钥、系统配置和请求日志。

## 本地开发

```bash
npm install
npm run dev
```

默认开发地址为 `http://localhost:5173`，后端默认地址为 `http://localhost:8080`。根目录 `scripts/start.ps1` / `scripts/start.sh` 会同时启动前后端。

管理端使用 hash 路由；未登录或接口返回 401 时会跳转到 `/#/login`，避免直接访问 `/login` 命中后端路由导致 404。

## 构建

```bash
npm run build
```

构建产物输出到 `frontend/dist`。Docker 镜像构建时会先构建前端，再复制到后端静态资源目录，最终通过后端 `http://localhost:8080` 访问管理端。

## 页面说明

| 页面 | 路由 | 用途 |
|---|---|---|
| 控制台 | `/` | 展示网关基础信息、公开调用端点、token 消耗趋势和模型/渠道/密钥分布 |
| 渠道管理 | `/channels` | 新增、编辑、删除上游渠道，配置模型映射和路由权重 |
| 模型管理 | `/models` | 聚合展示对外模型，配置额度单价、能力标记和启用状态 |
| 网关密钥 | `/api-keys` | 创建调用方密钥，限制可用渠道，配置余额和滑动窗口额度 |
| 系统配置 | `/system-config` | 配置路由模式、失败避让阈值、冷却时间和会话粘性 TTL |
| 请求日志 | `/request-logs` | 查询请求结果、调用密钥、上游渠道、token 用量、错误码和耗时 |

## 控制台仪表盘

控制台对应后端接口：

- `GET /api/admin/dashboard/stats`

仪表盘支持 `days`、`hours`、`topN` 查询参数，用于展示按天、按小时的 token 折线图，以及按模型、渠道、网关密钥名称划分的趋势折线图和分布饼图。按天 token 图支持鼠标悬停查看当天各模型消耗。

## 系统配置页面

系统配置页面对应后端接口：

- `GET /api/admin/system-config/routing`
- `PUT /api/admin/system-config/routing`

当前支持的路由模式：

- `RANDOM`：随机选择可用渠道。
- `ROUND_ROBIN`：按稳定顺序轮询。
- `WEIGHTED`：按渠道“路由权重”分配请求，权重越高流量越多。
- `SESSION_STICKY`：同一网关密钥 + 模型 + 会话标识优先复用首次命中的渠道。

失败避让按“网关密钥 + 渠道 + 上游模型”累计真实上游错误。`failureThreshold` 或 `failureCooldownMinutes` 为 `0` 时关闭避让。
