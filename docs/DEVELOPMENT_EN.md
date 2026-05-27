# api-convert Development Guide

This guide is for maintainers and extension developers. It covers project structure, endpoints, providers, protocol adapters, database migrations, and frontend conventions. Chinese version: [DEVELOPMENT.md](DEVELOPMENT.md).

## Architecture

The main request flow is:

1. A client calls a public gateway endpoint.
2. `GatewayApiKeyFilter` authenticates the gateway API key and loads channel/model scopes.
3. `EndpointHandler` parses the entry protocol into unified request models.
4. `RoutingService` selects a `ModelRoute` using model mapping, key scopes, routing mode, and failure cooldown.
5. `EndpointProviderAdapter` converts requests and responses between the entry endpoint protocol and the upstream provider protocol.
6. `AiProviderClient` calls the upstream model service.
7. `ApiKeyQuotaService` estimates and deducts quota and records request-count windows.
8. `UsageRecorder` writes request logs; Dashboard statistics are aggregated from logs.

## Project Layout

| Path | Description |
|---|---|
| `src/main/java/cn/ms08/apiconvert/controller` | HTTP controllers for public gateway and admin APIs |
| `src/main/java/cn/ms08/apiconvert/endpoint` | Endpoint strategy layer for public protocols |
| `src/main/java/cn/ms08/apiconvert/provider` | Upstream provider strategy implementations |
| `src/main/java/cn/ms08/apiconvert/adapter` | Protocol and stream adapters |
| `src/main/java/cn/ms08/apiconvert/service` | Routing, quota, installer, logging, and business services |
| `src/main/java/cn/ms08/apiconvert/security` | Gateway API key authentication |
| `src/main/java/cn/ms08/apiconvert/entity` | MyBatis-Plus table entities |
| `src/main/java/cn/ms08/apiconvert/dao` | MyBatis-Plus mappers |
| `src/main/resources/db` | SQLite/MySQL install scripts and migrations |
| `frontend` | Vue 3.5 + Naive UI admin console |

## Public Endpoints

| Endpoint | Handler | Description |
|---|---|---|
| `GET /health` | `HealthController` | Public health check |
| `GET /v1/models` | `OpenAiModelsEndpointHandler` | OpenAI-compatible model list |
| `POST /v1/chat/completions` | `ChatCompletionsEndpointHandler` | OpenAI Chat Completions entry, streaming supported |
| `POST /v1/messages` | `AnthropicMessagesEndpointHandler` | Anthropic Messages entry, streaming supported |
| `POST /v1/responses` | `OpenAiResponsesEndpointHandler` | OpenAI Responses API entry, streaming supported |
| `POST /v1/videos` | `OpenAiVideosEndpointHandler` | OpenAI Videos API entry, non-streaming video generation |
| `POST /v1/images/generations` | `OpenAiImagesEndpointHandler` | OpenAI Images API entry, non-streaming image generation |

To add a public endpoint:

1. Add a value to `EndpointType`.
2. Implement `EndpointHandler`.
3. Ensure it can convert into the unified request/response flow, or explicitly document why it is separate.
4. Add tests and admin gateway-info output.

## Provider Development

Current usable provider types:

| Type | Client | Upstream Protocol |
|---|---|---|
| `OPENAI_COMPATIBLE` | `OpenAiCompatibleProviderClient` | Chat Completions + Videos + Images |
| `ANTHROPIC` | `AnthropicProviderClient` | Messages |
| `OPENAI_RESPONSES` | `OpenAiResponsesProviderClient` | Responses API |
| `GPT_AUTH` | `GptAuthProviderClient` | Chat Completions + Videos + Images + auth.json |
| `CLAUDE_AUTH` | `ClaudeAuthProviderClient` | Messages + auth.json |
| `DEEPSEEK_CHAT` | `DeepSeekChatProviderClient` | Chat Completions + reasoning |
| `DEEPSEEK_ANTHROPIC` | `DeepSeekAnthropicProviderClient` | Messages + thinking |
| `GEMINI` | `GeminiProviderClient` | Gemini `generateContent` |

`LOCAL` is only a reserved enum value. There is no usable Local provider client in this version.

To add a provider:

1. Add a type to `ProviderType`.
2. Implement `AiProviderClient` and register it as a Spring bean.
3. Ensure `ProviderClientRegistry` can resolve it.
4. Add `EndpointProviderAdapter` implementations for all supported public endpoints.
5. If streaming differs from the entry protocol, implement or reuse `StreamResponseTransformer`.
6. Add it to admin frontend type lists and documentation.
7. Add tests for URL construction, auth headers, request body conversion, error handling, and usage parsing.

Security requirements:

- Upstream API keys, access tokens, and refresh tokens must never appear in API responses or business logs.
- Provider errors should be converted to `ProviderException` or `GatewayException`; do not expose unsanitized upstream responses.
- AUTH files are stored under the configured data directory. APIs only expose desensitized auth status, subject, and expiration metadata.

## Adapter Development

Public endpoints and upstream providers may use different protocols, so conversion is handled by `EndpointProviderAdapter`.

Recommended naming:

```text
{EndpointProtocol}To{ProviderProtocol}Adapter
```

Examples:

- `ResponsesToOpenAiCompatibleAdapter`
- `ResponsesToAnthropicAdapter`
- `ChatCompletionsToDeepSeekChatAdapter`
- `AnthropicMessagesToGeminiAdapter`

Requirements:

- Request adapters should preserve model, system prompts, messages, tools, stream flag, and provider-specific options.
- Response adapters should output unified response models so quota and logs can read usage fields.
- Tool-call sequences must satisfy the target provider constraints; DeepSeek Chat uses `ChatToolSequenceNormalizer` for strict ordering.
- Unsupported capabilities should throw `UNSUPPORTED_FEATURE`; do not silently drop important fields.
- New adapters should cover non-streaming and streaming behavior. Add a `StreamResponseTransformer` when streaming formats differ.

## Routing and Model Rules

Supported model request formats:

| Format | Meaning |
|---|---|
| `public-model` | Match `ai_channel_model.public_name`; routing mode chooses a channel |
| `channel/provider-model` | Directly select a channel and upstream model |

Rules:

- The same public model name may be served by multiple channels for random, round-robin, weighted, or sticky routing.
- The same channel cannot store duplicate `provider_model` values.
- Non-empty `model_alias` values must be globally unique.
- Gateway keys may restrict both channels and public model names; direct routing must pass the same scope checks.

## Gateway Keys and Limits

Related tables:

| Table | Description |
|---|---|
| `gateway_api_key` | Gateway key record. Raw key is for admin copy only; hash is used for authentication |
| `gateway_api_key_channel` | Allowed channels for a key. Empty means all channels |
| `gateway_api_key_model` | Allowed public models for a key. Empty means all models |
| `gateway_api_key_limit` | Extensible limit items, currently quota and request count |

Limit types:

- `QUOTA`: sliding-window quota limit, supports `HOUR` and `DAY`.
- `REQUEST`: sliding-window request-count limit, supports `MINUTE`, `HOUR`, and `DAY`.
- Request count is recorded after successful routing, so failed upstream calls are counted.
- Each key can have only one row per limit type and window unit.

## Database Migration Rules

- Install scripts: `src/main/resources/db/schema-sqlite.sql`, `schema-mysql.sql`.
- Versioned migrations: `src/main/resources/db/migration/{sqlite,mysql}/V{version}.sql`.
- `DatabaseInstaller.CURRENT_SCHEMA_VERSION` must match the latest migration version.
- New tables/columns must update SQLite schema, MySQL schema, and both migration directories.
- SQLite does not support native comments, so SQL scripts must include comments describing tables and fields.
- Before deleting or replacing tables, migration scripts must back up or synchronize data and explain the protection path in comments.

## Frontend Rules

- Stack: Vue 3.5, `<script setup lang="ts">`, Naive UI, Vite.
- Shared types live in `frontend/src/types/index.ts`.
- API wrappers live in `frontend/src/api/`.
- CRUD pages follow table + modal form + API call + toast error handling.
- Gateway keys and upstream keys are displayed desensitized. Raw gateway keys are only shown immediately after creation for admin copy.
- Channel model selection must deduplicate by upstream model ID to avoid duplicate mappings in the same channel.

## JDK 25 Installation

The project must be built and run with JDK 25. Directory creation, JDK download, repository clone, and start-script examples are documented in the root [README_EN.md](../README_EN.md#download-jdk-and-start).

## Tests and Verification

Backend:

```bash
JAVA_HOME=/path/to/jdk-25 PATH=/path/to/jdk-25/bin:$PATH mvn -q test
```

Frontend:

```bash
cd frontend
npm run build
```

New features should cover:

- Admin API save and read paths.
- Routing scope checks and failure paths.
- Provider request body, response body, and error handling.
- Adapter tool calls, streaming output, and usage parsing.
- Compatibility after database migrations.

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | Backend server port |
| `API_CONVERT_TIME_ZONE` | `Asia/Shanghai` | Application time zone |
| `API_CONVERT_JACKSON_MAX_STRING_LENGTH` | `100000000` | Maximum length of a single JSON string, used for base64 image/video passthrough |
| `API_CONVERT_DB_TYPE` | `sqlite` | Database type: `sqlite` or `mysql` |
| `API_CONVERT_SQLITE_PATH` | `${user.dir}/api-convert.db` | SQLite database path |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${api-convert.database.sqlite-path}` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | empty | Database username |
| `SPRING_DATASOURCE_PASSWORD` | empty | Database password |
| `API_CONVERT_DB_INSTALL_ENABLED` | `true` | Enable automatic schema install/upgrade |
| `API_CONVERT_SECURITY_ENABLED` | `true` | Enable gateway API key authentication |
| `API_CONVERT_ADMIN_USERNAME` | `admin` | Admin username |
| `API_CONVERT_ADMIN_PASSWORD` | `admin123` | Admin password |
| `API_CONVERT_AUTH_STORAGE_DIR` | empty | AUTH channel credential storage directory |
| `LOG_PATH` | `logs` | Log output directory |

## Release Checklist

1. Update database schema and progress documentation.
2. Run backend tests and frontend build.
3. Confirm README and development docs match actual capabilities.
4. Create a version tag, for example `v1.0.5`.
5. Build and push the Docker image.
