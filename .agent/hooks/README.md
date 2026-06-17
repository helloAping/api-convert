# .agent/hooks — Agent 工作流 Hook 机制

> 与代码层 `cn.ms08.apiconvert.adapter.endpoint.ProviderHook`（供应商特化 hook）**同名但不同概念**。本目录的 hook 是 AI agent 之间的协作钩子。

## 是什么

一组 YAML 流水线，描述"主 agent 把任务分给哪些 sub-agent、按什么顺序接力、失败时跳到哪"。每个 stage 是一个 sub-agent，通过 `onSuccess` / `onFailure` 串联。

典型场景：

- 新功能开发：梳理计划 → 审视计划 → 写代码 → 审视代码
- PR 审查：拉 diff → 安全审计 → 规范审查 → 总结
- Bug 修复：复现 → 定位 → 修复 → 回归

## 目录约定

```text
.agent/hooks/
├── README.md                       # 本文件：总览 + 快速使用
├── schema.json                     # YAML schema，供编辑器校验
├── feature-development.yml         # 标准功能开发流水线
├── pr-review.yml                   # PR 审查流水线
└── <your-pipeline>.yml             # 团队自定义流水线
```

## 快速使用

通过 `workflow-runner` skill 加载并执行：

```text
用 feature-development 流水线处理这个需求：<需求描述>
```

主 agent 加载 `workflow-runner` skill，按 YAML 顺序串行调用 `agents/<role>.md` 描述的 sub-agent，每个 sub-agent 通过 `status: ok` / `status: blocked` 报告结果，主流程按 `onSuccess` / `onFailure` 决定下一步。

## YAML 字段速查

| 字段 | 必填 | 说明 |
|---|---|---|
| `version` | ✅ | 固定 `1.0` |
| `name` | ✅ | pipeline 唯一标识，kebab-case |
| `displayName` |  | 人类可读名称 |
| `description` |  | 一句话说明用途 |
| `stages` | ✅ | 有序 stage 数组 |
| `stages[].name` | ✅ | stage 唯一标识，被 transition 引用 |
| `stages[].agent` | ✅ | 相对 `.agent/` 的 sub-agent 文件路径 |
| `stages[].inputs` |  | 该 stage 读取的文件 glob |
| `stages[].outputs` |  | 该 stage 产出的文件 glob |
| `stages[].maxRetries` |  | 被打回重做的最大次数，默认 3 |
| `stages[].onSuccess` |  | 成功后跳转（stage name / `done` / `stop`） |
| `stages[].onFailure` |  | 失败后跳转，默认 `stop` |
| `triggers.manual` |  | 是否允许手动触发 |
| `triggers.command` |  | 绑定的 slash command |

## 编写新 pipeline

参考 `.agent/rules/hooks.md` 开发规范与 `.agent/docs/modules/11-hooks.md` 设计文档。

## 现有 pipeline

| 流水线 | 用途 | 入口 |
|---|---|---|
| `feature-development` | 标准功能开发：plan → review → code → review | `workflow-runner` skill |
| `pr-review` | PR 审查：diff → security → spec → summary | `workflow-runner` skill |
