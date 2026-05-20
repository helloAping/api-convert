# api-convert

`api-convert` is a Java 25 + Spring Boot 4 AI API gateway. It exposes OpenAI-compatible and Anthropic-compatible API entry points, routes requests to configured upstream providers, and provides an admin console for channels, model mappings, gateway API keys, quotas, routing configuration, and request logs.

[中文 README](README.md)

## Features

- OpenAI Chat Completions compatible endpoint: `/v1/chat/completions`
- OpenAI Responses API compatible endpoint: `/v1/responses`
- Anthropic Messages compatible endpoint: `/v1/messages`
- OpenAI-compatible model list endpoint: `/v1/models`
- Provider support for OpenAI-compatible APIs, Anthropic, OpenAI Responses, GPT_AUTH, CLAUDE_AUTH, DeepSeek, and Gemini
- Cross-protocol adapters between OpenAI Chat, Anthropic Messages, OpenAI Responses, DeepSeek, and Gemini-style protocols
- SSE streaming passthrough and real-time Responses API stream conversion
- Routing modes: random, round-robin, weighted, and session-sticky
- Tool-aware routing and upstream failure cooldown
- Gateway API key authentication, quota balance, sliding-window rate limits, and token-based billing
- Request logs, dashboard statistics, and sensitive data sanitization
- Admin console for channels, models, API keys, request logs, routing configuration, and OAuth AUTH channels

## Screenshots

![Admin preview 1](img/Snipaste_2026-05-15_18-24-48.png)

![Admin preview 2](img/Snipaste_2026-05-15_18-25-26.png)

![Admin preview 3](img/Snipaste_2026-05-15_18-25-31.png)

![Admin preview 4](img/Snipaste_2026-05-15_18-25-55.png)

![Admin preview 5](img/Snipaste_2026-05-15_18-28-27.png)

## Tech Stack

- Backend: Java 25, Spring Boot 4.0.6, Maven, MyBatis-Plus, Log4j2
- Database: SQLite by default, MySQL optional
- Frontend: Vue 3.5, Vite, TypeScript, Naive UI
- Admin auth: Sa-Token

## Supported Provider Types

| Type | Protocol | Auth | Streaming | Notes |
|---|---|---|---|---|
| `OPENAI_COMPATIBLE` | Chat Completions | Bearer API key | Yes | Generic OpenAI-compatible upstream |
| `ANTHROPIC` | Messages | Bearer API key + version | Yes | Anthropic / Claude style |
| `OPENAI_RESPONSES` | Responses API | Bearer API key | Yes | Native OpenAI Responses upstream |
| `GPT_AUTH` | Chat Completions | `auth.json` / OAuth | Yes | GPT auth-file channel |
| `CLAUDE_AUTH` | Messages | `auth.json` / OAuth | Yes | Claude auth-file channel |
| `DEEPSEEK_CHAT` | Chat Completions + reasoning | Bearer API key | Yes | DeepSeek Chat style |
| `DEEPSEEK_ANTHROPIC` | Messages + thinking | Bearer API key + version | Yes | DeepSeek Anthropic style |
| `GEMINI` | Gemini `generateContent` | `x-goog-api-key` | No | Google Gemini |

## Quick Start

### Requirements

- JDK 25
- Node.js and npm
- Maven Wrapper from this repository: `mvnw.cmd` / `mvnw`

### Windows PowerShell

```powershell
.\scripts\start.ps1
```

Specify a JDK 25 path:

```powershell
.\scripts\start.ps1 -JavaHome 'D:\path\to\jdk-25'
```

Use MySQL:

```powershell
.\scripts\start.ps1 -DbType mysql `
  -DatasourceUrl 'jdbc:mysql://127.0.0.1:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' `
  -DatasourceUsername api_convert `
  -DatasourcePassword 'change-me'
```

### Linux / macOS / Git Bash

```bash
./scripts/start.sh
```

Specify a JDK 25 path:

```bash
./scripts/start.sh --java-home /path/to/jdk-25
```

Use MySQL:

```bash
./scripts/start.sh --db-type mysql \
  --datasource-url 'jdbc:mysql://127.0.0.1:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  --datasource-username api_convert \
  --datasource-password 'change-me'
```

The startup scripts run both services by default:

- Backend: `http://localhost:8080`
- Frontend dev server: `http://localhost:5173`

You can run only one side:

```powershell
.\scripts\start.ps1 -Target backend
.\scripts\start.ps1 -Target frontend
```

```bash
./scripts/start.sh backend
./scripts/start.sh frontend
```

Default admin account:

```text
Username: admin
Password: admin123
```

Change the default password in production.

## Common API Calls

Health check:

```bash
curl http://localhost:8080/health
```

List models:

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

## Admin Workflow

1. Open `http://localhost:8080` and log in to the admin console.
2. Create an upstream channel in Channel Management.
3. For regular channels, configure provider type, base URL, request path, API key, and upstream models.
4. For `GPT_AUTH` / `CLAUDE_AUTH` channels, upload `auth.json` or import credentials through OAuth.
5. Configure public model names, quota pricing, capabilities, and enabled status in Model Management.
6. Create gateway API keys and optionally restrict their available channels or quota.
7. Adjust routing mode, failure cooldown, and session stickiness in System Configuration.
8. Use Dashboard and Request Logs to inspect traffic, token usage, upstream channels, latency, and errors.

## GPT / Claude AUTH Channels

`GPT_AUTH` and `CLAUDE_AUTH` are designed for auth-file based upstream access. The admin console supports:

- Uploading `auth.json`
- Generating an OAuth authorization link
- Importing credentials by pasting the OAuth callback URL
- Displaying desensitized auth status, subject, and expiration time

AUTH channel files are stored separately by provider type and channel code. The default storage path is derived from the data directory, and it can be overridden by `API_CONVERT_AUTH_STORAGE_DIR`.

AUTH-related admin endpoints:

| Endpoint | Description |
|---|---|
| `POST /api/admin/channels/{id}/auth/upload` | Upload `auth.json` |
| `POST /api/admin/channels/{id}/auth/start` | Create an OAuth authorization link |
| `GET /api/admin/channels/auth/callback` | OAuth callback endpoint |
| `POST /api/admin/channels/{id}/auth/callback-url` | Import credentials from a callback URL |
| `GET /api/admin/channels/{id}/auth/status` | Query auth status |

## Routing

When multiple active channels provide the same public model, the gateway selects one according to the system routing mode.

| Mode | Description |
|---|---|
| `RANDOM` | Randomly selects an available channel |
| `ROUND_ROBIN` | Rotates through channels in stable order |
| `WEIGHTED` | Uses channel priority for smooth weighted round-robin |
| `SESSION_STICKY` | Reuses the first selected channel for the same API key, model, and session ID |

Session stickiness can read stable identifiers from request headers and protocol parameters, such as `session_id`, `thread_id`, `x-client-request-id`, `prompt_cache_key`, `previous_response_id`, and `client_metadata`.

Failure cooldown tracks upstream failures by gateway API key, channel, and upstream model. After the threshold is reached, the gateway temporarily avoids that channel for the key and model.

## Configuration

Important environment variables:

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | Backend server port |
| `API_CONVERT_TIME_ZONE` | `Asia/Shanghai` | Application time zone |
| `API_CONVERT_DB_TYPE` | `sqlite` | Database type: `sqlite` or `mysql` |
| `API_CONVERT_SQLITE_PATH` | `${user.dir}/api-convert.db` | SQLite database path |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${api-convert.database.sqlite-path}` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | empty | Database username |
| `SPRING_DATASOURCE_PASSWORD` | empty | Database password |
| `API_CONVERT_DB_INSTALL_ENABLED` | `true` | Enable automatic schema install/upgrade |
| `API_CONVERT_SECURITY_ENABLED` | `true` | Enable gateway API key authentication |
| `API_CONVERT_ADMIN_USERNAME` | `admin` | Admin username |
| `API_CONVERT_ADMIN_PASSWORD` | `admin123` | Admin password |
| `API_CONVERT_AUTH_STORAGE_DIR` | empty | Storage directory for AUTH channel credential files |
| `LOG_PATH` | `logs` | Log output directory |

## Docker

Build locally:

```bash
docker build -t api-convert:local .
```

Published image:

```text
crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:{version}
```

Pull a release image:

```bash
docker pull crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:v1.0.3
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

Run with MySQL:

```bash
docker run --rm -p 8080:8080 \
  -v api-convert-data:/app/data \
  -e JAVA_OPTS='-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders' \
  -e LOG_PATH=/app/data/logs \
  -e API_CONVERT_DB_TYPE=mysql \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://mysql:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  -e SPRING_DATASOURCE_USERNAME=api_convert \
  -e SPRING_DATASOURCE_PASSWORD='change-me' \
  -e API_CONVERT_ADMIN_PASSWORD='change-me' \
  api-convert:local
```

When using Nginx or another reverse proxy, make sure the `Authorization` header is forwarded:

```nginx
proxy_set_header Authorization $http_authorization;
```

## Build and Test

Backend:

```bash
mvn -q test
```

Frontend:

```bash
cd frontend
npm install
npm run build
```

## License

This project is released under the MIT License.
