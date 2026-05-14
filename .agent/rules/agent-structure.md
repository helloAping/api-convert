# Agent Structure Rules

## Standard layout

Use this repository-level AI workspace layout:

```text
AGENTS.md                    # team-shared agent instructions, committed to git
AGENTS.local.md              # personal overrides, ignored by git
.agent/
├── settings.json            # shared permissions/config, committed to git
├── settings.local.json      # personal permissions/config, ignored by git
├── commands/                # custom slash commands
├── rules/                   # modular global instruction files
├── skills/                  # reusable workflows
├── agents/                  # sub-agent role definitions
├── docs/                    # AI-facing project docs and handoffs
└── scripts/                 # setup/link helper scripts
```

## Placement rules

- Put durable team-shared agent instructions in `AGENTS.md`.
- Put personal local overrides in `AGENTS.local.md`; do not commit it.
- Put modular rules in `.agent/rules/*.md`.
- Put AI-facing progress notes, implementation summaries, and handoff docs in `.agent/docs/`.
- Do not create progress notes, implementation summaries, plans, or handoff documents in the project root.
- Root-level documentation should be limited to stable project docs and agent entry files such as `AGENTS.md`, `CLAUDE.md`, `HELP.md`, or `README.md`.

## Tool compatibility

- `.agent` is the canonical directory name for this project.
- Do not create a separate committed `.claude` directory for shared rules.
- If a tool expects `.claude`, `.codex`, or `.opencode`, create local soft links or junctions pointing to `.agent`.
- Use `.agent/scripts/link-agent-workspace.*` helper scripts for local tool compatibility.
