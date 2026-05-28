# api-convert

`api-convert` 是一个 Java 25 + Spring Boot 4 AI API 网关，用于把 OpenAI / Anthropic 风格客户端请求转发到管理端配置的上游渠道和模型。它提供统一入口、协议适配、模型路由、网关密钥、额度控制、请求日志和管理面板。

[English README](README_EN.md) | [开发文档](docs/DEVELOPMENT.md) | [Development Guide](docs/DEVELOPMENT_EN.md)

## 核心特性

- 兼容端点：`/v1/chat/completions`、`/v1/responses`、`/v1/videos`、`/v1/images/generations`、`/v1/messages`、`/v1/models`
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

- Git
- Node.js 与 npm
- 仓库内 Maven Wrapper：`mvnw.cmd` / `mvnw`，由启动脚本内部调用

### 下载 JDK 并启动

JDK 建议解压到仓库同级目录，不要放进 `api-convert` git 工作树。Maven 相关操作、后端启动和前端启动都由启动脚本内部封装。

Windows PowerShell：

```powershell
mkdir api-convert-work
cd api-convert-work
git clone https://gitee.com/skwyl/api-convert.git

Invoke-WebRequest `
  -Uri "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse" `
  -OutFile "jdk-25.zip"
mkdir jdk-25
tar -xf jdk-25.zip -C jdk-25 --strip-components=1

cd api-convert
.\scripts\start.ps1 -JavaHome "..\jdk-25" `
  -AdminUsername admin `
  -AdminPassword "change-me"
```

Linux x64：

```bash
mkdir -p api-convert-work
cd api-convert-work
git clone https://gitee.com/skwyl/api-convert.git

curl -L "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
  -o jdk-25.tar.gz
mkdir -p jdk-25
tar -xzf jdk-25.tar.gz -C jdk-25 --strip-components=1

cd api-convert
./scripts/start.sh --java-home ../jdk-25 \
  --admin-username admin \
  --admin-password 'change-me'
```

macOS Apple Silicon：

```bash
mkdir -p api-convert-work
cd api-convert-work
git clone https://gitee.com/skwyl/api-convert.git

curl -L "https://api.adoptium.net/v3/binary/latest/25/ga/mac/aarch64/jdk/hotspot/normal/eclipse" \
  -o jdk-25.tar.gz
mkdir -p jdk-25
tar -xzf jdk-25.tar.gz -C jdk-25 --strip-components=3

cd api-convert
./scripts/start.sh --java-home ../jdk-25 \
  --admin-username admin \
  --admin-password 'change-me'
```

macOS Intel 把上面 URL 中的 `aarch64` 改成 `x64`。

国内镜像目录：

| 系统 | 清华 TUNA 镜像目录 |
|---|---|
| Windows x64 | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/windows/` |
| Linux x64 | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/linux/` |
| macOS Intel | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/mac/` |
| macOS Apple Silicon | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/aarch64/mac/` |

启动后：

- 后端与内置管理端入口：`http://localhost:8080`
- 前端开发服务：`http://localhost:5173`
- 默认管理账号：`admin / admin123`

更多启动参数示例：

```powershell
.\scripts\start.ps1 -JavaHome '..\jdk-25' `
  -AdminUsername admin `
  -AdminPassword 'change-me' `
  -BackendPort 8080 `
  -DbType mysql `
  -DatasourceUrl 'jdbc:mysql://127.0.0.1:3306/api_convert?useSSL=false&serverTimezone=Asia/Shanghai' `
  -DatasourceUsername root `
  -DatasourcePassword 'mysql-password'
```

```bash
./scripts/start.sh --java-home ../jdk-25 \
  --admin-username admin \
  --admin-password 'change-me' \
  --backend-port 8080 \
  --db-type mysql \
  --datasource-url 'jdbc:mysql://127.0.0.1:3306/api_convert?useSSL=false&serverTimezone=Asia/Shanghai' \
  --datasource-username root \
  --datasource-password 'mysql-password'
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

OpenAI Videos API：

```bash
curl -X POST http://localhost:8080/v1/videos \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-video","prompt":"A cinematic city skyline at sunset","seconds":4,"size":"1280x720"}'
```

OpenAI Images API：

```bash
curl -X POST http://localhost:8080/v1/images/generations \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-image","prompt":"A clean product render on a white background","size":"1024x1024","n":1}'
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

## Nginx 反向代理配置

通过 Nginx 暴露服务时，建议按下面的 `location` 配置透传 `Authorization`、禁用代理缓存、放宽大请求体限制，并关闭 SSE 缓冲以保证流式响应即时回传。将 `http://your_host:port` 替换为实际后端地址，例如 `http://127.0.0.1:8080`；如果上游启用了 HTTPS，则改为 `https://your_host:port`。

```nginx
location ^~ / {
    proxy_pass http://your_host:port;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Port $server_port;
    proxy_pass_request_headers on;
    proxy_set_header Authorization $http_authorization;
    proxy_http_version 1.1;

    add_header X-Cache $upstream_cache_status;
    proxy_ssl_server_name off;
    proxy_ssl_name $proxy_host;

    # 禁用缓存，避免 304 或缓存命中影响 API 响应。
    proxy_no_cache 1;
    proxy_cache_bypass 1;
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    expires off;

    # base64 大 payload 解码和上游处理可能需要较长时间。
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    proxy_connect_timeout 60s;

    # 支持图片、视频等 base64 大请求体。
    client_max_body_size 50m;

    # 流式 SSE：关闭缓冲，让上游事件即时回传。
    proxy_buffering off;
    proxy_request_buffering off;
    proxy_cache off;
    proxy_set_header Connection '';
    chunked_transfer_encoding on;
}
```

## 构建与测试

本地启动和后端构建由启动脚本封装，直接通过脚本指定 JDK 路径：

```powershell
.\scripts\start.ps1 -JavaHome "..\jdk-25"
```

```bash
./scripts/start.sh --java-home ../jdk-25
```

## 开源协议

MIT License。
