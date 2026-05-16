# AGENTS.md

This file is the **primary entry point** for all AI coding agents (Claude, Codex, OpenCode, etc.) working in this repository.

## Required reading order

Before changing code, read in this exact order:

1. `AGENTS.md` (this file)
2. `.agent/rules.md` (index of all modular rules)
3. All files listed by `.agent/rules.md`
4. `.agent/docs/AI_GATEWAY_PROGRESS.md` (current implementation progress)
5. `AGENTS.local.md` if it exists locally

## Canonical agent workspace

`.agent/` is the canonical shared agent workspace for this project. Do not create separate committed directories for tool-specific configs.

Expected layout:

```text
.agent/
├── settings.json              # shared permissions/config, committed to git
├── settings.local.json        # personal permissions/config, ignored by git
├── commands/                  # custom slash commands
├── rules/                     # modular global instruction files
├── skills/                    # reusable workflows
├── agents/                    # sub-agent role definitions
├── docs/                      # AI-facing project docs and handoffs
└── scripts/                   # setup/link helper scripts
```

## Tool compatibility & setup

Some tools (Claude Code, Codex CLI, OpenCode) expect their own directories such as `.claude`, `.codex`, or `.opencode`.

**For this project, these must be local junctions (symlinks) pointing to `.agent` — not separate committed directories.**

### Setup (run once after cloning)

Run the setup script to create local links:

```powershell
# Windows
.agent\scripts\link-agent-workspace.ps1 -Tool all

# Or link for a specific tool:
.agent\scripts\link-agent-workspace.ps1 -Tool codex
.agent\scripts\link-agent-workspace.ps1 -Tool claude
.agent\scripts\link-agent-workspace.ps1 -Tool opencode
```

```bash
# Linux/macOS
.agent/scripts/link-agent-workspace.sh all
```

The script creates local junctions/symlinks so each tool reads `.agent/` as its own config directory.

### Verify links

```powershell
# Check existing links
Get-Item .claude, .codex, .opencode | Select-Object Name, LinkType, Target
```

## Current project context

`api-convert` is a **Java 25 Spring Boot AI API gateway**. It aggregates AI provider endpoints, adapts OpenAI/Claude-style client protocols, and routes requests to configured provider models.

Key capabilities:

- **Dual protocol entry**: OpenAI-compatible (`/v1/chat/completions`) and Anthropic Messages (`/v1/messages`)
- **Responses API**: OpenAI Responses API protocol (`/v1/responses`) with SSE streaming via `RealTimeResponsesTransformer`
- **SSE streaming passthrough**: byte-level proxy with usage extraction at stream end
- **Smart routing**: random channel selection per model, direct channel/model specification via `channel/model` format
- **Cross-protocol adapters**: 6× `EndpointProviderAdapter` implementations automatically transforming requests/responses between OpenAI Chat, Anthropic Messages, GPT Responses API, and Gemini protocols
- **Stream transformer**: pluggable `StreamResponseTransformer` abstraction for real-time SSE format conversion between endpoint types and provider protocols
- **Endpoint strategy pattern**: `EndpointType` enum + `EndpointHandler` interface, each endpoint independently handles request parsing, adaptation, and response writing
- **Provider strategy pattern**: `ProviderType` enum + `AiProviderClient` interface, supports OpenAI-compatible, Anthropic, OpenAI Responses API, Gemini upstreams
- **API key management**: gateway keys with SHA-256 auth, quota balance, sliding window rate limiting
- **Admin management panel**: full CRUD for channels, models, API keys, request logs
- **Admin auth**: Sa-Token-based login with Bearer token
- **HTTP traffic logging**: inbound/outbound logging with automatic sensitive data sanitization

### Tech stack

| Component | Technology |
|---|---|
| Runtime | Java 25 (OpenJDK 25) |
| Framework | Spring Boot 4.0.6 (Spring MVC) |
| ORM | MyBatis-Plus 3.5.16 |
| Database | SQLite (default dev) + MySQL |
| Admin auth | Sa-Token 1.42 |
| Frontend | Vue 5 + Naive UI + Vite |

### Quick run

The local JDK 25 path is stored in `AGENTS.local.md` as `JAVA_HOME_25`. If not set, ask the user and save it there.

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn spring-boot:run -Dspring-boot.run.profiles=sqlite
```

Then open `http://localhost:8080` for the admin panel (default: admin / admin123).

### Build & test

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
```

### Test API calls

```bash
# Health check (no auth)
curl http://localhost:8080/health

# Gateway info (admin endpoints metadata)
curl -H 'Authorization: Bearer sk-local-dev' http://localhost:8080/api/admin/gateway-info

# Model list
curl -H 'Authorization: Bearer sk-local-dev' http://localhost:8080/v1/models

# OpenAI chat completion
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "gpt-4o", "messages": [{"role": "user", "content": "hello"}], "stream": false}'

# OpenAI Responses API
curl -X POST http://localhost:8080/v1/responses \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "gpt-4o", "input": "hello", "stream": false}'

# Anthropic messages
curl -X POST http://localhost:8080/v1/messages \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -H 'anthropic-version: 2023-06-01' \
  -d '{"model": "claude-3-opus", "messages": [{"role": "user", "content": "hello"}]}'

# Admin login
curl -X POST http://localhost:8080/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin", "password": "admin123"}'
```

## Implementation progress

Full implementation progress is tracked in `.agent/docs/AI_GATEWAY_PROGRESS.md`. After completing any feature, update that document to reflect the current state.

## Important conventions

- Source code root: `src/main/java/cn/ms08/apiconvert/`
- DB migration scripts: `src/main/resources/db/migration/{sqlite,mysql}/`
- Management frontend: `frontend/` (Vue 5 + Naive UI)
- Follow naming conventions: `Entity` for DB tables, `DTO` for internal transfer, `VO` for API responses, `Request` for controller input
- Never commit local credentials, API keys, or personal agent config
- **文档强制同步**：新增、修改或删除功能时，必须同步更新 `.agent/docs/AI_GATEWAY_PROGRESS.md`，确保进度文档与实际功能一致后再提交代码
