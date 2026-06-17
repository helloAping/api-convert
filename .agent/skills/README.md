# Skills

可复用 agent 工作流目录。每个 skill 一个目录，含 `SKILL.md` 描述加载条件、执行步骤与边界。Skill 与 sub-agent、commands、pipeline 的关系详见 `.agent/docs/modules/11-hooks.md` §6。

## 现有 skill

| Skill 目录 | 触发场景 | 说明 |
|---|---|---|
| `workflow-runner/` | 用户说"用 `<pipeline>` 流水线处理 ..." / 触发 `/project:run-feature` 或 `/project:review-pr` | 加载 `.agent/hooks/<name>.yml` 并在同会话内串行调度 sub-agent；维护 stage 重试计数；解析产物 `status:` 字段 |

## 编写规范

- Skill 描述必须包含"何时使用 / 加载步骤 / 执行循环 / 终态报告 / 与其他资源边界"5 个段落
- 命名用 kebab-case（`workflow-runner` 而不是 `workflowRunner`）
- Skill 可以被 sub-agent 调用，也可以被主 agent 加载；二者平级
- 不要把 slash command 直接写进 SKILL.md；命令入口放在 `.agent/commands/`
