# Commands

Slash command 入口目录。`*.md` 文件会被工具注册成 `/project:<name>` 命令，可绑定到 `.agent/hooks/*.yml` pipeline 的 `triggers.command` 字段。

## 现有 command

| 命令 | 触发的 pipeline | 说明 |
|---|---|---|
| `/project:run-feature` | `feature-development` | 标准功能开发：plan → review → code → review |
| `/project:review-pr` | `pr-review` | PR 审查：diff → security → spec → summary |

## 编写规范

- Command 文件命名直接用命令名（去掉 `/project:` 前缀），kebab-case
- Command 文件应当简短：触发条件、绑定 pipeline、参数说明
- 不要在 command 里写执行细节；执行逻辑在 `.agent/skills/workflow-runner/SKILL.md` 和 pipeline YAML 中
- 新增 command 时必须同时更新对应 pipeline 的 `triggers.command` 字段
