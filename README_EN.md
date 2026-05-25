# api-convert

`api-convert` is a Java 25 + Spring Boot 4 AI API gateway. It forwards OpenAI / Anthropic-style client requests to admin-configured upstream channels and models, with unified endpoints, protocol adapters, model routing, gateway API keys, quota controls, request logs, and an admin console.

[中文 README](README.md) | [Development Guide](docs/DEVELOPMENT_EN.md) | [开发文档](docs/DEVELOPMENT.md)

## Features

- Compatible endpoints: `/v1/chat/completions`, `/v1/responses`, `/v1/videos`, `/v1/images/generations`, `/v1/messages`, `/v1/models`
- Upstream types: OpenAI-compatible, Anthropic, OpenAI Responses, GPT_AUTH, CLAUDE_AUTH, DeepSeek Chat, DeepSeek Anthropic, Gemini
- Protocol adapters between Chat Completions, Anthropic Messages, Responses API, DeepSeek, and Gemini formats
- Routing modes: random, round-robin, weighted, session-sticky, tool-aware routing, failure cooldown
- Gateway keys: SHA-256 authentication, channel/model allowlists, balance, token-based billing, sliding-window quota limits, sliding-window request-count limits
- Admin console: channels, models, gateway keys, request logs, dashboard, routing configuration, AUTH channel authorization
- Data layer: SQLite by default, MySQL supported, automatic schema installation and migration at startup

> `LOCAL` is reserved in the Provider enum for future extension. This version does not include a Local provider client.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4.0.6, Maven, MyBatis-Plus, Log4j2 |
| Database | SQLite / MySQL |
| Frontend | Vue 3.5, Vite, TypeScript, Naive UI |
| Admin auth | Sa-Token |

## Quick Start

Requirements:

- Git
- Node.js and npm
- Maven Wrapper from this repository: `mvnw.cmd` / `mvnw`, called internally by the start script

### Download JDK and Start

Extract the JDK next to the repository, not inside the `api-convert` git worktree. Maven-related work, backend startup, and frontend startup are handled inside the start script.

Windows PowerShell:

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

Linux x64:

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

macOS Apple Silicon:

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

For macOS Intel, replace `aarch64` in the URL above with `x64`.

Mainland China mirror directories:

| System | Tsinghua TUNA Mirror Directory |
|---|---|
| Windows x64 | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/windows/` |
| Linux x64 | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/linux/` |
| macOS Intel | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/x64/mac/` |
| macOS Apple Silicon | `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/aarch64/mac/` |

After startup:

- Backend and built-in admin entry: `http://localhost:8080`
- Frontend dev server: `http://localhost:5173`
- Default admin account: `admin / admin123`

More startup parameters:

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

Do not use the default admin password in production. See [Development Guide](docs/DEVELOPMENT_EN.md#configuration) for more configuration options.

## Common API Calls

Health check:

```bash
curl http://localhost:8080/health
```

Model list:

```bash
curl -H "Authorization: Bearer <gateway-api-key>" \
  http://localhost:8080/v1/models
```

OpenAI Chat Completions:

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-chat","messages":[{"role":"user","content":"hello"}],"stream":false}'
```

Anthropic Messages:

```bash
curl -X POST http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -H "anthropic-version: 2023-06-01" \
  -d '{"model":"example-chat","messages":[{"role":"user","content":"hello"}],"stream":false}'
```

OpenAI Responses API:

```bash
curl -X POST http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-chat","input":"hello","stream":false}'
```

OpenAI Videos API:

```bash
curl -X POST http://localhost:8080/v1/videos \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-video","prompt":"A cinematic city skyline at sunset","seconds":4,"size":"1280x720"}'
```

OpenAI Images API:

```bash
curl -X POST http://localhost:8080/v1/images/generations \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-image","prompt":"A clean product render on a white background","size":"1024x1024","n":1}'
```

## Admin Workflow

1. Log in to the admin console.
2. Create upstream channels and configure their models in Channel Management.
3. Adjust public model capabilities, enabled status, and quota pricing in Model Management.
4. Create gateway API keys and configure balance, limit items, channel allowlists, and model allowlists as needed.
5. Choose routing mode, failure cooldown, and session-stickiness settings in System Configuration.
6. Use Dashboard and Request Logs to inspect usage, errors, and upstream channel selection.

## Documentation

| Document | Description |
|---|---|
| [Development Guide](docs/DEVELOPMENT_EN.md) | Architecture, packages, endpoints, providers, adapters, DB migrations, frontend rules |
| [开发文档](docs/DEVELOPMENT.md) | 中文开发文档 |
| [Chinese README](README.md) | 中文项目概览 |

## Docker

Build a local image:

```bash
docker build -t api-convert:local .
```

Run with SQLite:

```bash
docker run --rm -p 8080:8080 \
  -v api-convert-data:/app/data \
  -e JAVA_OPTS='-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders' \
  -e LOG_PATH=/app/data/logs \
  -e API_CONVERT_ADMIN_USERNAME=admin \
  -e API_CONVERT_ADMIN_PASSWORD='change-me' \
  api-convert:local
```

Release image example:

```bash
docker pull crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:v1.0.5
```

When using a reverse proxy, forward the `Authorization` header:

```nginx
proxy_set_header Authorization $http_authorization;
```

## Build and Test

Local startup and backend build are wrapped by the start script. Pass the JDK path directly:

```powershell
.\scripts\start.ps1 -JavaHome "..\jdk-25"
```

```bash
./scripts/start.sh --java-home ../jdk-25
```

## License

MIT License.
