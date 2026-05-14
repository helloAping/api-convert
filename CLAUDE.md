# Claude Code compatibility entry

This repository uses `.agent/` as the canonical shared AI-agent workspace.

Read `AGENTS.md` first, then follow:

- `.agent/rules.md`
- `.agent/docs/AI_GATEWAY_PROGRESS.md`

If Claude tooling expects `.claude/`, create a local link to `.agent/` with:

```bash
bash .agent/scripts/link-agent-workspace.sh claude
```

or on Windows PowerShell:

```powershell
.agent\scripts\link-agent-workspace.ps1 -Tool claude
```
