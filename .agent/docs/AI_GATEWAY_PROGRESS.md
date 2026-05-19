# AI Gateway - Implementation Progress

## Project Overview

**api-convert** is an AI API gateway that aggregates multiple AI provider endpoints, adapts OpenAI/Claude-compatible client protocols, and routes requests to configured upstream provider models.

- **Runtime**: Java 25 (OpenJDK 25)
- **Framework**: Spring Boot 4.0.6 (Spring MVC)
- **ORM**: MyBatis-Plus 3.5.16
- **Database**: SQLite (default dev) + MySQL
- **Admin auth**: Sa-Token 1.42
- **Frontend**: Vue 5 + Naive UI + Vite

---

## Architecture Overview

The gateway is organized into six architectural layers:

```
Client Request (OpenAI / Anthropic / Responses API protocol)
       |
[gateway entry / endpoint dispatch]
  GatewayController (route by path + method)
  EndpointHandler / EndpointRegistry
       |
[protocol adapters (request normalization)]
  OpenAiRequestAdapter / OpenAiResponsesRequest
  AnthropicRequestAdapter / ResponseAdapter
       |  UnifiedChatRequest
[gateway service (routing + auth + quota)]
  ChatGatewayService (orchestrator)
  RoutingService (model -> channel mapping)
  ApiKeyQuotaService (rate limit + deduction)
  UsageRecorder (request logging)
       |
[cross-protocol adapter layer]
  EndpointProviderAdapter (request/response)
  StreamResponseTransformer (SSE format)
       |
[upstream provider clients]
  AiProviderClient (7 ProviderType variants)
  OpenAiCompatible / Anthropic / OpenAiResponses
  DeepSeekChat / DeepSeekAnthropic / Gemini
       |
Upstream Provider API
```

## Implemented Features

### 1. Foundation & Infrastructure

| Module | Details |
|---|---|
| Spring Boot 4.0.6 | Spring MVC (``spring-boot-starter-webmvc``) |
| MyBatis-Plus 3.5.16 | 7 Mapper interfaces extending ``BaseMapper`` |
| Dual database | SQLite (default) + MySQL, switchable via ``api-convert.database.type`` |
| Spring JDBC | ``DatabaseInstaller`` uses ``JdbcTemplate`` for DDL execution |
| Spring RestClient | ``WebConfig`` provides ``RestClient.Builder`` bean with logging interceptor |
| Jackson datetime | Unified ``yyyy-MM-dd HH:mm:ss`` format, Asia/Shanghai timezone |
| Log4j2 | Console + app log + SQL log via ``log4j2-spring.xml`` |
| Lombok | Entity classes use ``@Getter/@Setter`` |

### 2. Database Auto-Install & Migration (``DatabaseInstaller``)

On startup:

- Checks ``api-convert.database.install-enabled`` (default ``true``)
- Selects script dir by ``api-convert.database.type`` (``sqlite`` / ``mysql``)
- Fresh install: runs ``schema-{type}.sql`` when ``gateway_schema_version`` table missing
- Incremental upgrade: reads current version from ``gateway_schema_version``, runs ``V{version}.sql`` scripts sequentially
- **Current schema version**: ``11``
- Migration scripts: 22 files (11 per database type)

**Core tables:**

| Table | Purpose |
|---|---|
| ``gateway_schema_version`` | Schema version tracking |
| ``ai_channel`` | Channel config: provider type, baseUrl, paths, upstream key |
| ``ai_channel_model`` | Model mapping: prefix, unique alias, per-M token pricing, capabilities |
| ``gateway_api_key`` | Gateway API Keys: raw text (admin copy), SHA-256 hash (auth), quota |
| ``gateway_api_key_channel`` | Key-channel scope (empty = all channels) |
| ``gateway_system_config`` | Runtime config: routing mode, session stickiness, error fallback |
| ``request_log`` | Audit log: token usage, latency, error info |

### 3. Startup Bootstrap (``GatewayBootstrapService``)

``@CommandLineRunner``, runs after ``DatabaseInstaller``. Handles gateway invocation keys only:

- Inserts ``sk-local-dev`` into ``gateway_api_key`` (raw + preview + SHA-256 hash), default ``ACTIVE``
- Backfills missing ``raw_key`` / ``key_preview`` for historical bootstrap records
- **Policy**: insert if missing; backfill raw/preview only, never overwrite existing records

### 4. Gateway API Key Authentication (``GatewayApiKeyFilter``)

- Type: ``OncePerRequestFilter``
- Can be disabled via ``api-convert.security.enabled=false``
- Public path whitelist: ``/health``
- Two key delivery methods:
  - ``Authorization: Bearer <key>``
  - ``x-api-key: <key>``
- ``ApiKeyHasher`` computes SHA-256, matches against ``gateway_api_key`` table
- Only matches ``status=''ACTIVE''`` keys
- Sets ``GatewayPrincipal`` (apiKeyId, name) as request attribute on success
- Returns ``401 UNAUTHORIZED`` on failure

### 5. Health Check (``GET /health``)

Public endpoint (no auth). Returns database connectivity, install status, channel/model counts.

### 6. Gateway Public Endpoints

``GatewayController`` dispatches by path + method to ``EndpointHandler`` implementations:

| Method | Path | EndpointType | Handler |
|---|---|---|---|
| POST | ``/v1/chat/completions`` | CHAT_COMPLETIONS | ``ChatCompletionsEndpointHandler`` |
| POST | ``/v1/messages`` | ANTHROPIC_MESSAGES | ``AnthropicMessagesEndpointHandler`` |
| POST | ``/v1/responses`` | OPENAI_RESPONSES | ``OpenAiResponsesEndpointHandler`` |
| GET | ``/v1/models`` | OPENAI_MODELS | ``OpenAiModelsEndpointHandler`` |

Each endpoint handler:
1. Reads and parses protocol-specific request
2. Converts to ``UnifiedChatRequest`` via protocol adapter
3. Checks ``stream`` flag; dispatches to streaming or blocking path
4. Calls ``ChatGatewayService.chat()`` or ``.stream()``
5. Converts ``UnifiedChatResponse`` back to protocol-specific VO for response

### 7. Protocol Adapters (Request/Response Normalization)

Converts external protocols to/from ``UnifiedChatRequest`` / ``UnifiedChatResponse``:

| Direction | Adapter |
|---|---|
| OpenAI Chat -> Unified | ``OpenAiRequestAdapter`` |
| Unified -> OpenAI Chat | ``OpenAiResponseAdapter`` |
| Anthropic Messages -> Unified | ``AnthropicRequestAdapter`` |
| Unified -> Anthropic Messages | ``AnthropicResponseAdapter`` |
| OpenAI Responses -> Unified | ``OpenAiResponsesRequestAdapter`` |
| Unified -> OpenAI Responses | ``OpenAiResponsesResponseAdapter`` |

### 8. Gateway Service Layer (``ChatGatewayService``)

Central orchestrator responsible for:
- Reading authentication principal
- Calling ``RoutingService`` to resolve model -> channel mapping
- Applying ``ApiKeyQuotaService`` for rate limit check + quota deduction
- Looking up cross-protocol ``EndpointProviderAdapter`` for request/response adaptation
- Dispatching to appropriate ``AiProviderClient``
- Wrapping upstream SSE with ``StreamResponseTransformer`` if needed
- Recording request log via ``UsageRecorder``

### 9. Routing Service (``RoutingService``)

- Reads ``gateway_system_config`` for routing mode
- Supports three modes:
  - **Random**: weighted random channel selection per model
  - **Session stickiness**: pin channel by session ID
  - **Error fallback**: retry on next channel on upstream failure
- Direct ``channel/model`` format to bypass routing

### 10. API Key Quota (``ApiKeyQuotaService``)

- Pre-checks quota before forwarding
- Sliding window rate limiting
- Model-unit-price-based token deduction
- Supports per-M pricing for input, output, and cache-read tokens

### 11. SSE Streaming Passthrough

Four upstream clients support ``streamChat()``:
- ``OpenAiCompatibleProviderClient``
- ``AnthropicProviderClient``
- ``OpenAiResponsesProviderClient``
- ``DeepSeekChatProviderClient``

Streaming flow:
1. Upstream SSE bytes written directly to response output stream
2. Stream ended by upstream disconnect
3. Post-stream usage extraction from aggregated SSE events
4. ``UsageRecorder`` records final token counts

### 12. Cross-Protocol Endpoint-Provider Adapters

Registered in ``EndpointProviderAdapterRegistry`` by (EndpointType, ProviderType) key.

| Adapter | Source Endpoint | Target Provider |
|---|---|---|
| ``AnthropicToOpenAiCompatibleAdapter`` | ANTHROPIC_MESSAGES | OPENAI_COMPATIBLE |
| ``ChatCompletionsToAnthropicAdapter`` | CHAT_COMPLETIONS | ANTHROPIC |
| ``ChatCompletionsToDeepSeekAnthropicAdapter`` | CHAT_COMPLETIONS | DEEPSEEK_ANTHROPIC |
| ``ChatCompletionsToDeepSeekChatAdapter`` | CHAT_COMPLETIONS | DEEPSEEK_CHAT |
| ``ChatCompletionsToGeminiAdapter`` | CHAT_COMPLETIONS | GEMINI |
| ``ResponsesToOpenAiCompatibleAdapter`` | OPENAI_RESPONSES | OPENAI_COMPATIBLE |
| ``ResponsesToAnthropicAdapter`` | OPENAI_RESPONSES | ANTHROPIC |
| ``ResponsesToDeepSeekAnthropicAdapter`` | OPENAI_RESPONSES | DEEPSEEK_ANTHROPIC |
| ``ResponsesToDeepSeekChatAdapter`` | OPENAI_RESPONSES | DEEPSEEK_CHAT |
| ``AnthropicMessagesToDeepSeekAnthropicAdapter`` | ANTHROPIC_MESSAGES | DEEPSEEK_ANTHROPIC |
| ``AnthropicMessagesToGeminiAdapter`` | ANTHROPIC_MESSAGES | GEMINI |
| ``AnthropicToDeepSeekChatAdapter`` | ANTHROPIC_MESSAGES | DEEPSEEK_CHAT |

Plus shared utilities:
- ``ChatToolSequenceNormalizer`` - normalizes tool call sequencing for strict APIs
- ``AnthropicTools`` - Anthropic tool format helpers

### 13. Stream Response Transformers

Registered in ``StreamTransformerRegistry``:

| Transformer | Purpose |
|---|---|
| ``ResponsesSseTransformer`` | Upstream SSE -> Responses API SSE format |
| ``RealTimeResponsesTransformer`` | Responses API realtime SSE (Response resource model) |
| ``ResponsesStreamTransformerFactory`` | Factory producing transformers per (endpoint, provider) |

### 14. Provider Clients

Registered in ``ProviderClientRegistry`` by ``ProviderType``:

| ProviderType | Implementation | Protocols |
|---|---|---|
| OPENAI_COMPATIBLE | ``OpenAiCompatibleProviderClient`` | Chat Completions |
| ANTHROPIC | ``AnthropicProviderClient`` | Anthropic Messages |
| OPENAI_RESPONSES | ``OpenAiResponsesProviderClient`` | Responses API |
| DEEPSEEK_CHAT | ``DeepSeekChatProviderClient`` | Chat Completions |
| DEEPSEEK_ANTHROPIC | ``DeepSeekAnthropicProviderClient`` | Anthropic Messages |
| GEMINI | ``GeminiProviderClient`` | Gemini |
| LOCAL | (placeholder) | - |

Each client implements:
- ``chat()`` - blocking request
- ``streamChat()`` (4 providers) - SSE streaming
- ``models()`` - upstream model discovery
- ``quota()`` - real-time quota query

### 15. Admin Backend API

9 controllers under ``/api/admin/``:

| Controller | Endpoints | Purpose |
|---|---|---|
| ``AdminAuthController`` | POST login/logout, GET me | Admin authentication |
| ``AdminChannelController`` | CRUD + POST /models + GET /{id}/quota | Channel management |
| ``AdminModelController`` | List, get, update quota/enabled/capabilities | Model management |
| ``AdminApiKeyController`` | CRUD + POST /{id}/quota | API Key management |
| ``AdminDashboardController`` | GET /stats | Dashboard statistics |
| ``AdminRequestLogController`` | List + get | Request log viewer |
| ``AdminSystemConfigController`` | GET/PUT /routing | Routing config |
| ``AdminGatewayInfoController`` | GET | Gateway metadata |

### 16. Admin Authentication (Sa-Token)

- ``SaTokenWebConfig``: Bearer token style, 30min timeout
- ``SaTokenFilter``: lightweight Servlet filter, only guards ``/api/admin/*`` routes
- Login stores session, returns token to frontend

### 17. HTTP Traffic Logging

- ``HttpTrafficLoggingFilter``: inbound request/response logging (auto-sanitized)
- ``RestClientLoggingInterceptor``: outbound upstream request/response logging
- ``LogSanitizer``: strips API keys and sensitive fields from log output

---

## Codebase Inventory

### Java Source (main, 115+ files)

```
src/main/java/cn/ms08/apiconvert/
├── config/                    (8 files: DateTime, MyBatis, SaToken, SPA, Web, Properties)
├── controller/                (2 files: GatewayController, HealthController)
├── controller/admin/           (9 admin REST controllers)
├── endpoint/                   (6 files: EndpointType, Handler, Registry, ProtocolFormat + 4 handlers)
├── adapter/
│   ├── endpoint/  (17 files: 12 adapters + interface + registry + helpers)
│   ├── protocol/  (6 files: request/response adapters for 3 protocols)
│   └── stream/    (5 files: transformer interface + 4 implementations)
├── provider/                  (9 files: ProviderType enum + 6 clients + interface + registry)
├── service/                   (13 files: 7 core + 6 admin services)
├── entity/                    (6 DB entity classes)
├── dto/                        (18 DTO/Request classes)
├── vo/                          (15 VO/Response classes)
├── security/                  (3 files: ApiKeyHasher, GatewayPrincipal, GatewayApiKeyFilter)
├── logging/                   (3 files: HttpTrafficLoggingFilter, LogSanitizer, RestClientLoggingInterceptor)
└── exception/                 (4 files: ErrorCode, GatewayException, ProviderException, GlobalExceptionHandler)
```

### Database Migration Scripts

```
src/main/resources/db/
├── schema-sqlite.sql
├── schema-mysql.sql
└── migration/
    ├── sqlite/  V2.sql .. V11.sql (10 scripts)
    └── mysql/   V2.sql .. V11.sql (10 scripts)
```

---

## Frontend

### Stack

- **Framework**: Vue 5 + Vite + TypeScript
- **UI**: Naive UI (component-level registration)
- **State**: Local state (``ref``/``reactive``), no Pinia/Vuex
- **Routing**: Hash mode (``vue-router``), login guard via ``router.beforeEach``
- **API Layer**: Axios instance with Bearer token interceptor + 401 redirect

### Pages (6 views)

| Route | View | Features |
|---|---|---|
| ``/login`` | ``LoginView.vue`` | Admin login |
| ``/`` | ``DashboardView.vue`` | SVG pie charts with hover segments |
| ``/channels`` | ``ChannelList.vue`` | CRUD + model discovery + quota fetch |
| ``/models`` | ``ModelList.vue`` | CRUD + quota/enabled/capabilities toggle |
| ``/api-keys`` | ``ApiKeyList.vue`` | CRUD + quota management |
| ``/request-logs`` | ``RequestLogList.vue`` | Paginated log viewer |
| ``/system-config`` | ``SystemConfigView.vue`` | Routing mode config |

### API Modules (9 files)

``auth.ts``, ``channels.ts``, ``models.ts``, ``apiKeys.ts``, ``dashboard.ts``, ``requestLogs.ts``, ``systemConfig.ts``, ``gatewayInfo.ts``, ``request.ts``

### Build Optimization

- Vite manual chunks for Vue, Naive UI/icon, Axios
- Component-level Naive UI registration (explicit global names)
- Chunk warning threshold adjusted

---

## Tests

9 test files:

| Test | Type | Area |
|---|---|---|
| ``OpenAiCompatibleProviderClientTests`` | Unit | Provider client |
| ``AnthropicProviderClientTests`` | Unit | Provider client |
| ``DeepSeekChatProviderClientTests`` | Unit | Provider client |
| ``AnthropicToOpenAiCompatibleAdapterTests`` | Unit | Cross-protocol adapter |
| ``ResponsesToAnthropicAdapterTests`` | Unit | Cross-protocol adapter |
| ``ResponsesToOpenAiCompatibleAdapterTests`` | Unit | Cross-protocol adapter |
| ``OpenAiResponsesRequestAdapterTests`` | Unit | Protocol adapter |
| ``RealTimeResponsesTransformerTests`` | Unit | Stream transformer |
| ``ApiConvertApplicationTests`` | Smoke | Context load |

**Gaps**: channel/model/admin Controller integration, auth Filter, RoutingService, DashboardService, DatabaseInstaller.

---

## Pending Features

| Priority | Feature | Description |
|---|---|---|
| P2 | Credential encryption | ``ai_channel.api_key`` encrypted storage or external KMS |
| P2 | Integration tests | SQLite install, health check, auth failure, streaming proxy |
| P3 | Additional providers | Local model client implementation |

---

## Docker Deployment

- JDK 25 compact object headers: ``JAVA_OPTS=''-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders''``
- Volume: ``-v api-convert-data:/app/data``, ``LOG_PATH=/app/data/logs``
- Image: ``crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:{version}``
- Nginx must set ``proxy_set_header Authorization $http_authorization;`` for auth header passthrough

---

## Change Log

### Backend
- DeepSeek split into ``DEEPSEEK_CHAT`` (Chat Completions) and ``DEEPSEEK_ANTHROPIC`` (Anthropic API) provider types. ``ANTHROPIC`` no longer contains DeepSeek URL compatibility.
- Endpoint adapters added for DeepSeek providers; ``/v1/responses`` -> ``DEEPSEEK_CHAT`` restores ``reasoning`` as ``reasoning_content``.
- ``DEEPSEEK_CHAT`` normalizes tool call history: matching results moved next to ``tool_calls``, unanswered calls trimmed to prevent 400 errors.
- Fixed request-log pagination on MyBatis 3.5.19 by copying immutable ``BoundSql`` parameter mappings.

### Frontend
- Dashboard pie charts: hoverable SVG segments with name, tokens, requests, and share percentage.
- Production build: Vite manual chunks for vendor deps, component-level Naive UI registration, adjusted chunk size warning.
