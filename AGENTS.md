# AGENTS.md

This file provides team-shared instructions for AI coding agents working in this repository.

## Required reading order

Before changing code, read:

1. `.agent/rules.md`
2. All files listed by `.agent/rules.md`
3. `.agent/docs/AI_GATEWAY_PROGRESS.md`
4. `AGENTS.local.md` if it exists locally

## Canonical agent workspace

`.agent/` is the canonical shared agent workspace for this project.

Expected layout:

```text
.agent/
├── settings.json
├── settings.local.json
├── commands/
├── rules/
├── skills/
├── agents/
├── docs/
└── scripts/
```

Do not commit personal overrides or tool-specific local links.

## Tool compatibility

Some tools expect their own directories, such as `.claude`, `.codex`, or `.opencode`. For this project, these should be local soft links or junctions to `.agent`, not separate committed directories.

Use the scripts in `.agent/scripts/` to create local links.

## Current project context

`api-convert` is a Java 25 Spring Boot AI API gateway. It aggregates AI provider endpoints, adapts OpenAI/Claude-style client protocols, and routes requests to configured provider models.

Current implementation progress is documented in `.agent/docs/AI_GATEWAY_PROGRESS.md`.
