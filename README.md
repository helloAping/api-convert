# api-convert

`api-convert` 是一个 Java 25 + Spring Boot 4 AI API 网关，用于把 OpenAI / Anthropic 风格客户端请求转发到管理端配置的上游渠道和模型。它提供统一入口、协议适配、模型路由、网关密钥、额度控制、请求日志和管理面板。

[English README](README_EN.md) | [开发文档](docs/DEVELOPMENT.md) | [Development Guide](docs/DEVELOPMENT_EN.md)

## 核心特性

- 兼容端点：`/v1/chat/completions`、`/v1/responses`、`/v1/messages`、`/v1/models`
- 上游类型：OpenAI 兼容、Anthropic、OpenAI Responses、GPT_AUTH、CLAUDE_AUTH、DeepSeek Chat、DeepSeek Anthropic、Gemini
- 协议适配：Chat Completions、Anthropic Messages、Responses API、DeepSeek、Gemini 之间按端点和上游类型转换
- 路由策略：随机、轮询、加权、会话粘性、工具请求优先、失败避让
- 网关密钥：SHA-256 鉴权、渠道/模型授权、余额、按 token 计费、滑动窗口额度限制、滑动窗口请求数限制
- 管理端：渠道、模型、网关密钥、请求日志、Dashboard、系统路由配置、AUTH 渠道授权管理
- 数据层：SQLite 默认开发库，支持 MySQL，启动时自动安装和迁移 schema

> 代码中保留了 `LOCAL` Provider 枚举作为未来扩展占位；当前版本没有对应 Provider Client。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 25、Spring Boot 4.0.6、Maven、MyBatis-Plus、Log4j2 |
| 数据库 | SQLite / MySQL |
| 前端 | Vue 3.5、Vite、TypeScript、Naive UI |
| 管理端鉴权 | Sa-Token |

## 快速启动

前置要求：

- JDK 25
- Node.js 与 npm
- 仓库内 Maven Wrapper：`mvnw.cmd` / `mvnw`

Windows PowerShell：

```powershell
.\scripts\start.ps1
```

Linux / macOS / Git Bash：

```bash
./scripts/start.sh
```

启动后：

- 后端与内置管理端入口：`http://localhost:8080`
- 前端开发服务：`http://localhost:5173`
- 默认管理账号：`admin / admin123`

指定 JDK、管理员密码或 MySQL：

```powershell
.\scripts\start.ps1 -JavaHome 'D:\path\to\jdk-25' `
  -AdminUsername admin `
  -AdminPassword 'change-me'
```

```bash
./scripts/start.sh --java-home /path/to/jdk-25 \
  --admin-username admin \
  --admin-password 'change-me'
```

生产环境不要使用默认管理端密码。更多环境变量见 [开发文档](docs/DEVELOPMENT.md#配置项)。

## 常用接口

健康检查：

```bash
curl http://localhost:8080/health
```

模型列表：

```bash
curl -H "Authorization: Bearer <gateway-api-key>" \
  http://localhost:8080/v1/models
```

OpenAI Chat Completions：

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-chat","messages":[{"role":"user","content":"hello"}],"stream":false}'
```

Anthropic Messages：

```bash
curl -X POST http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -H "anthropic-version: 2023-06-01" \
  -d '{"model":"example-chat","messages":[{"role":"user","content":"hello"}],"stream":false}'
```

OpenAI Responses API：

```bash
curl -X POST http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-chat","input":"hello","stream":false}'
```

## 管理端使用流程

1. 登录管理端。
2. 在“渠道管理”新增上游渠道并配置模型。
3. 在“模型管理”调整对外模型能力、启用状态和额度单价。
4. 在“网关密钥”创建调用方密钥，并按需配置余额、限制项、渠道授权和模型授权。
5. 在“系统配置”选择路由策略、失败避让和会话粘性参数。
6. 通过 Dashboard 和请求日志查看用量、错误和上游命中情况。

## 文档

| 文档 | 说明 |
|---|---|
| [开发文档](docs/DEVELOPMENT.md) | 架构、目录、端点、Provider、适配器、数据库迁移、前端规范 |
| [Development Guide](docs/DEVELOPMENT_EN.md) | English version of the development guide |
| [英文 README](README_EN.md) | English project overview |

## Docker

构建本地镜像：

```bash
docker build -t api-convert:local .
```

运行 SQLite：

```bash
docker run --rm -p 8080:8080 \
  -v api-convert-data:/app/data \
  -e JAVA_OPTS='-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders' \
  -e LOG_PATH=/app/data/logs \
  -e API_CONVERT_ADMIN_USERNAME=admin \
  -e API_CONVERT_ADMIN_PASSWORD='change-me' \
  api-convert:local
```

发布镜像示例：

```bash
docker pull crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:v1.0.5
```

反向代理时必须透传 `Authorization` 请求头：

```nginx
proxy_set_header Authorization $http_authorization;
```

## 构建与测试

后端：

```bash
JAVA_HOME=/path/to/jdk-25 PATH=/path/to/jdk-25/bin:$PATH mvn -q test
```

前端：

```bash
cd frontend
npm install
npm run build
```

## 开源协议

MIT License。
