# Agents

Sub-agent 角色契约目录。每个 `*.md` 文件描述一个独立 sub-agent 的身份、输入、输出、协作契约和不要做。Sub-agent 由 `.agent/hooks/*.yml` pipeline 通过 `.agent/skills/workflow-runner` skill 串行调用。

## 现有 sub-agent

| 角色文件 | 职责 | 被哪个 pipeline 引用 |
|---|---|---|
| `planner.md` | 把用户需求梳理成可被 coder 直接执行的 `plan.md` | `feature-development` |
| `plan-reviewer.md` | 检查 `plan.md` 是否对齐需求、是否符合项目规范 | `feature-development` |
| `coder.md` | 按 `plan.md` 改代码、补测试、写数据库迁移 | `feature-development` |
| `code-reviewer.md` | 检查 coder 写出的代码是否符合规范 | `feature-development` |
| `diff-reader.md` | 把 PR 改动整理成 `pr-diff.md`（文件清单 + 同步状态） | `pr-review` |
| `security-auditor.md` | 专项安全审计：密钥、日志、SQL 注入、鉴权 | `pr-review` |
| `spec-reviewer.md` | 规范审查：命名、注释、Lombok、迁移、文档同步 | `pr-review` |
| `reviewer-summary.md` | 把安全 + 规范两份审查报告汇总成最终结论 | `pr-review` |

## 编写规范

新增 sub-agent 时必须遵循 `.agent/rules/hooks.md` 的"Sub-agent 角色契约"小节：

- 包含"身份 / 输入 / 输出 / 协作契约 / 不要做"五小节
- 必须在产出文件中输出明确的 `status:` 字段（`ok` / `approved` / `rejected` / `blocked`）
- 不要直接对接其他 sub-agent；通过文件契约传递信息
- 命名用 kebab-case（`plan-reviewer.md` 而不是 `PlanReviewer.md`）
