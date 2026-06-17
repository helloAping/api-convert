---
name: workflow-runner
description: |
  加载并执行 .agent/hooks/<name>.yml 描述的多 agent 串行工作流。
  触发词：用户说"用 feature-development 流水线处理 X"、"跑 pr-review pipeline"、
  "按 hooks/feature-development.yml 走一遍"、或直接发 /project:run-feature / /project:review-pr。
---

# workflow-runner

> 本 skill 严格执行 token 优化策略：主 agent 内存只保留"当前 stage + 上一 stage 摘要"，所有状态 / 计数 / 完整历史写入磁盘 `stateFile`。

## 何时使用本 skill

- 用户说"用 `<pipeline>` 流水线处理 ..."
- 用户触发 `triggers.command` 声明的 slash command
- 用户粘贴 `.agent/hooks/*.yml` 路径并要求执行

## 加载步骤

1. 定位 `.agent/hooks/<name>.yml`
2. 解析顶层 `summaryMaxChars`（默认 600）/ `maxArtifactSize`（默认 6000）/ `stateFile`（默认 `.agent/hooks/.state/<name>.json`）/ `contextMap`（默认 `.agent/CONTEXT_MAP.md`）
3. 初始化 / 恢复 `stateFile` 中持久化的执行状态

## 执行循环

按当前 stage 重复以下步骤。每步严格控制主 agent 内存占用。

### Step 1：构造 sub-agent 任务

为当前 stage 构造任务包，**只包含**：

- sub-agent 角色文件（`stages[i].agent`）全文 — 紧凑版通常 ≤ 1500 字符
- `_CONVENTIONS.md` 公共约定 — 一次性告知
- `contextMap` 项目地图（默认 `.agent/CONTEXT_MAP.md`）全文 — ≤ 1k token
- **上一 stage 产物的 `## 摘要` 小节**（仅摘要）— ≤ 600 字符
- 当前 stage 的 `inputs` glob 文件路径列表 — 路径字符串，不预读
- 当前 stage 的 `outputs` glob 列表

> **不要**把上一 stage 产物全文 / AI_GATEWAY_PROGRESS.md 全文 / rules.md 全文塞进任务包。sub-agent 自己 `Read` 工具按需读取。

### Step 2：调用 sub-agent

通过 subagent / Task 工具调用。**不要**让 sub-agent 直接看主 agent 完整对话历史。

### Step 3：解析结果（轻量）

sub-agent 完成后：

- 按 `outputs` glob 收集本 stage 产出文件
- 检查每个文件 `## 摘要` 小节是否存在（`summaryRequired: true` 时）
- 检查单文件大小 ≤ `maxArtifactSize`
- 解析 `status:` 字段决定 `onSuccess` / `onFailure`
- **从主 agent 内存中丢弃**：sub-agent 完整输出 / 已读过的中间文档

### Step 4：持久化执行状态

每次 stage 切换都把以下写入 `stateFile`（覆盖式）：

```json
{
  "pipeline": "feature-development",
  "currentStage": "review-plan",
  "history": [
    { "stage": "plan", "status": "ok", "outputs": ["plan.md"], "summary": "...", "at": "2026-06-17T10:23:45Z" }
  ],
  "retryCounters": { "plan": 1 },
  "version": "1.0"
}
```

主 agent 内存**只保留**：

- `currentStage` 当前 stage name
- 上一 stage 的 `## 摘要` 文本
- 当前的 `retryCounters`（写入磁盘后即可丢弃）

### Step 5：处理重试

- 跳转目标是上游 stage → 检查该 stage 的 `retryCounters[current]`，超 `maxRetries` 则终止
- 跳转目标是当前 stage → 同样计数
- 跳转目标是 `done` → 清理 `stateFile`（删文件），向用户报告
- 跳转目标是 `stop` → 保留 `stateFile`（断点恢复用），向用户报告中断原因
- 跳转目标是其他 stage → 重置后续 stage 计数器，继续

### Step 6：循环

回到 Step 1 加载新 stage。

## 终态报告

- `done` → 删除 `stateFile`，向用户报告：流水线名 / 走过的 stage 顺序 / 最终输出路径
- `stop` → 保留 `stateFile`，向用户报告：当前 stage / 失败原因 / 重试次数 / `stateFile` 路径（可手动重跑时恢复）
- `maxRetries-exceeded` → 同上

## 状态文件操作约定

- 路径必须在 `.agent/hooks/.state/` 下
- 文件命名：`<pipeline-name>.json`
- 不在仓库提交：`.agent/hooks/.state/*.json` 应加入 `.gitignore`
- 并发执行同一 pipeline 时由调用方保证互斥（建议在文件名前加 `.<user>.<timestamp>.lock`）

## Token 优化核对清单

执行前自检：

- [ ] sub-agent 任务包不含上一 stage 全文
- [ ] sub-agent 任务包不含 `AI_GATEWAY_PROGRESS.md` / `rules.md` 全文（用 contextMap 指针代替）
- [ ] sub-agent 任务包不重复包含 `_CONVENTIONS.md`（除首次）
- [ ] 主 agent 内存只保留当前 stage + 上一 stage 摘要
- [ ] 每次 stage 切换后 `stateFile` 已更新

## 与其他资源边界

| 工具 | 职责 |
|---|---|
| `workflow-runner`（本 skill） | 加载 YAML、按 transition 调度 sub-agent、维护重试计数、状态外置 |
| `agents/*.md` | 单个 stage 的执行者；不感知 pipeline 全貌 |
| `commands/*.md` | slash command 入口 |
| `skills/*/SKILL.md`（其他） | 单个被 stage 调用的工作流 |
| `_CONVENTIONS.md` | 8 个 sub-agent 共享约定，避免在每个角色文件中重复 |
| `CONTEXT_MAP.md` | 项目地图指针，sub-agent 按需 `Read` 具体文档 |
| `.agent/hooks/.state/<name>.json` | pipeline 执行状态持久化 |
