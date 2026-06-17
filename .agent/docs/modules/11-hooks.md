# 模块 11：Agent 工作流 Hook 机制

> 对应代码：`.agent/hooks/*.yml`、`.agent/agents/*.md`、`.agent/skills/workflow-runner/`
> 依赖模块：无（自包含的工作流基础设施）
> 被依赖模块：本仓库所有多阶段任务的工作流编排

> **注意：本模块描述的是 `.agent/` 下的 AI agent 工作流 hook，与代码层 `cn.ms08.apiconvert.adapter.endpoint.ProviderHook`（供应商特化 hook）同名但完全正交。** 两者一个是 AI agent 接力编排，一个是 Java 接口处理 HTTP 请求体；编写跨模块文档时务必用全限定名 `ProviderHook` 避免歧义。

---

## 1. 背景与目标

### 1.1 痛点

复杂任务（功能开发、PR 审查、Bug 修复）天然分阶段：

- 计划 → 写代码 → 测试 → 提交
- 拉 diff → 安全审计 → 规范审查 → 汇总

直接让单个 agent 一次性完成会出现：

- 计划质量无人把关，code 阶段频繁返工
- 安全/规范审查 agent 既是运动员又是裁判
- 失败没有显式重试机制，错误成本高
- 阶段性产物不沉淀，复盘和交接困难

### 1.2 目标

提供一套 **声明式 YAML 流水线 + 同会话串行 sub-agent 调度** 的机制，让：

- 任务拆分在 YAML 中显式可见
- 阶段间通过 markdown 文件契约衔接
- 失败可打回上游 stage，且有 maxRetries 上限
- 终态报告完整（走过哪些 stage、最终状态、阻断原因）

## 2. 核心概念

| 概念 | 说明 |
|---|---|
| **Pipeline** | 一个 `.agent/hooks/<name>.yml` 文件，描述完整工作流 |
| **Stage** | 流水线中的一个阶段，由一个 sub-agent 执行 |
| **Sub-agent** | `.agent/agents/<role>.md` 描述的角色契约 |
| **Transition** | `onSuccess` / `onFailure` 声明的 stage 间跳转 |
| **Artifact** | stage 之间的产物（markdown 文件），是 stage 间的唯一契约 |
| **Status** | sub-agent 输出的 `status: ok / approved / rejected / blocked` 字段 |
| **Retry Counter** | 主流程内部状态，跟踪同一 stage 被反复执行的次数 |

## 3. 架构组件

```text
┌──────────────────────────────────────────────────────────────┐
│  .agent/hooks/                                                │
│  ├── feature-development.yml   ┐                              │
│  ├── pr-review.yml             ├─ Pipeline YAML 声明         │
│  └── schema.json               ┘                              │
│                                                               │
│  .agent/agents/                                               │
│  ├── planner.md                ┐                              │
│  ├── plan-reviewer.md          │                              │
│  ├── coder.md                  ├─ Sub-agent 角色契约          │
│  ├── code-reviewer.md          │                              │
│  ├── diff-reader.md            │                              │
│  ├── security-auditor.md       │                              │
│  ├── spec-reviewer.md          │                              │
│  └── reviewer-summary.md       ┘                              │
│                                                               │
│  .agent/skills/workflow-runner/                               │
│  └── SKILL.md                  ── 同会话内串行调度器          │
└──────────────────────────────────────────────────────────────┘
```

## 4. YAML 字段详解

### 4.1 顶层

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | string | ✅ | 固定 `'1.0'`，未来破坏性变更时升 `'2.0'` |
| `name` | string | ✅ | pipeline 唯一标识，kebab-case，必须与文件名一致 |
| `displayName` | string |  | 人类可读名称 |
| `description` | string |  | 一句话或短段落说明用途 |
| `stages` | array | ✅ | 有序 stage 列表 |
| `triggers` | object |  | 触发方式声明 |

### 4.2 `stages[]`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | ✅ | stage 唯一标识，kebab-case，transition 引用此字段 |
| `agent` | string | ✅ | 相对 `.agent/` 的 sub-agent 文件路径 |
| `displayName` | string |  | 人类可读名称 |
| `inputs` | string[] |  | 读取的文件 glob（相对仓库根） |
| `outputs` | string[] |  | 产出的文件 glob（相对仓库根） |
| `maxRetries` | int |  | 被打回重做的最大次数，默认 3 |
| `onSuccess` | string | ✅ | 成功后跳转（stage name / `done` / `stop`） |
| `onFailure` | string |  | 失败后跳转（stage name / `done` / `stop`），默认 `stop` |

### 4.3 `triggers`

| 字段 | 类型 | 说明 |
|---|---|---|
| `manual` | bool | 是否允许用户手动触发 |
| `command` | string | 绑定的 slash command，例如 `/project:run-feature` |

## 5. 执行时序

```text
用户： "用 feature-development 处理 X"
   │
   ▼
主 agent 加载 .agent/skills/workflow-runner/SKILL.md
   │
   ▼
读取 .agent/hooks/feature-development.yml，校验 schema
   │
   ▼
取 stages[0] = plan → 加载 agents/planner.md 作为 sub-agent
   │
   ▼
sub-agent 执行 → 产出 plan.md（含 status: ok）
   │
   ▼
主流程解析 plan.md 状态 → 走 onSuccess = review-plan
   │
   ▼
取 stages[1] = review-plan → 加载 agents/plan-reviewer.md
   │
   ▼
sub-agent 执行 → 产出 plan-review.md（含 status: rejected）
   │
   ▼
主流程解析 → 走 onFailure = plan；retry 计数 +1
   │
   ▼
未超 maxRetries → 回到 stages[0] = plan，把 plan-review.md 作为 inputs 传入
   │
   ▼
（重试上限或 stage 全 ok 之后） → 走 onSuccess = done
   │
   ▼
主 agent 向用户报告最终状态
```

## 6. 与其它 `.agent` 资源的边界

| 资源 | 职责 | 与本机制的关系 |
|---|---|---|
| `agents/*.md` | 单个 sub-agent 角色契约 | **被** pipeline 引用 |
| `commands/*.md` | slash command 入口 | 通过 `triggers.command` 绑定到 pipeline |
| `skills/*/SKILL.md` | 可复用单 agent 工作流 | **被** sub-agent 调用，或**与** workflow-runner 平级 |
| `rules/*.md` | 项目级开发规范 | **约束** pipeline / agent 编写 |
| `docs/AI_GATEWAY_PROGRESS.md` | 进度文档 | **同步约束**（新增 pipeline 时必须更新） |
| `docs/modules/*.md` | 模块设计文档 | **互补**，本模块就是 11-hooks.md |

## 7. 编写一个 pipeline 的步骤

1. **识别阶段**：把任务拆成 N 个有清晰边界的阶段
2. **为每个阶段写 sub-agent**：在 `.agent/agents/` 下新增 `<role>.md`，**开头一行**声明 `> 遵循 .agent/agents/_CONVENTIONS.md`，差异化部分按"身份 / 输入 / 输出 / status 判定"四段写。**不**重复通用约定
3. **画 transition 图**：标出每个 stage 的 onSuccess / onFailure 跳到哪个 stage 或 `done` / `stop`
4. **写 pipeline YAML**：在 `.agent/hooks/` 下新增 `<name>.yml`，对照 schema.json 校验字段；**必须**声明 `contextMap` / `summaryMaxChars` / `maxArtifactSize` / `stateFile`
5. **更新 README**：把新 pipeline 加入 `.agent/hooks/README.md` 的"现有 pipeline"表格
6. **更新设计文档**：把新 pipeline 作为示例加入本文件
7. **更新进度文档**：在 `.agent/docs/AI_GATEWAY_PROGRESS.md` 的"近期更新"小节记录
8. **跑一次 dry run**：用 `workflow-runner` skill 加载并执行，确认文件契约和 status 字段都符合预期，并核验 token 优化自检清单（见 §10）

## 8. 示例

### 8.1 完整示例（feature-development）

参考 `.agent/hooks/feature-development.yml`：

```yaml
version: '1.0'
name: feature-development
displayName: 标准功能开发流水线
description: |
  plan → review-plan → code → review-code 四段流水线
stages:
  - name: plan
    agent: agents/planner.md
    inputs: [AGENTS.md, .agent/docs/AI_GATEWAY_PROGRESS.md, .agent/rules.md]
    outputs: [plan.md]
    onSuccess: review-plan
  - name: review-plan
    agent: agents/plan-reviewer.md
    inputs: [plan.md]
    outputs: [plan-review.md]
    maxRetries: 3
    onSuccess: code
    onFailure: plan
  - name: code
    agent: agents/coder.md
    inputs: [plan.md, plan-review.md]
    outputs: [src/**/*, frontend/src/**/*, code-changes.md]
    maxRetries: 3
    onSuccess: review-code
    onFailure: stop
  - name: review-code
    agent: agents/code-reviewer.md
    inputs: [plan.md, plan-review.md, code-changes.md]
    outputs: [code-review.md]
    maxRetries: 3
    onSuccess: done
    onFailure: code
triggers:
  manual: true
  command: /project:run-feature
```

### 8.2 简化的最小示例

只有 plan → done：

```yaml
version: '1.0'
name: quick-plan
stages:
  - name: plan
    agent: agents/planner.md
    inputs: [AGENTS.md, .agent/docs/AI_GATEWAY_PROGRESS.md]
    outputs: [plan.md]
    onSuccess: done
```

> 提示：单 stage 的任务退化为单 agent 即可，不必引入 pipeline；只有 ≥2 阶段且需要 transition / 重试控制时才用本机制。

## 9. 失败处理

| 场景 | 行为 |
|---|---|
| stage 产物缺失 status 字段 | 默认按 `onFailure` 处理 |
| stage 产物缺失 `## 摘要` 小节（`summaryRequired: true` 时） | workflow-runner 拒绝该 stage 完成并提示补摘要 |
| 单文件 > `maxArtifactSize` | 拒绝该 stage 完成并要求拆成"主报告 + 附录" |
| `onSuccess` / `onFailure` 引用不存在的 stage name | 主流程终止并向用户报错（配置错误） |
| `maxRetries` 耗尽 | 终止流水线，向用户报告：pipeline 名 / 走到哪个 stage / 累计重试次数 / 最后一轮审查报告 / `stateFile` 路径 |
| 用户在流水线执行中打断 | 立即终止，保留 `stateFile`（断点恢复用） |

## 10. Token 优化策略（v1.1 强制约束）

v1 设计时未约束 token 消耗，导致主 agent 上下文膨胀、单次完整 pipeline 跑下来总 token 经常超过 10k。v1.1 加入以下 5 项硬性优化，单次完整 pipeline 目标 ≤ 5000 token（不含 sub-agent 自身生成）。

### 10.1 优化手段

| 手段 | 落地位置 | 预期收益 |
|---|---|---|
| **公共约定外置** | `.agent/agents/_CONVENTIONS.md` | 8 个 sub-agent 文件共享 ~30% 重复段落，角色文件瘦身 30-50% |
| **项目地图 + 按需加载** | `.agent/CONTEXT_MAP.md` (≤ 1500 token) + sub-agent 用 `Read` 按需 | plan 阶段输入从 ~2750 token 降至 ~500 |
| **stage 摘要契约** | YAML `summaryMaxChars` + 强制 `## 摘要` 小节 | 阶段间传递从全文 → 摘要（≤ 600 字符） |
| **大文件硬上限** | YAML `maxArtifactSize` (默认 6000) | 防止单 stage 产物膨胀到几万字符 |
| **状态外置到磁盘** | YAML `stateFile` (默认 `.agent/hooks/.state/<name>.json`) | 主 agent 常驻从 ~1500 token 降至 ~300 token；支持断点恢复 |

### 10.2 主 agent 内存边界

主 agent 在 pipeline 执行期间**只**允许持有：

- `currentStage` 当前 stage name
- 上一 stage 的 `## 摘要` 文本（≤ 600 字符）
- 当前 `retryCounters`（每次 stage 切换写入 `stateFile` 后即可丢弃）

禁止持有：

- 上一 stage 产物全文
- `AI_GATEWAY_PROGRESS.md` / `rules.md` / `AGENTS.md` 全文
- 完整 sub-agent 角色文件（仅在调用时通过 subagent 任务包传入，不进主 agent 上下文）
- 已完成 stage 的完整 history（写入 `stateFile` 即可）

### 10.3 sub-agent 任务包结构

每次调用 sub-agent 时构造：

```text
任务包 = {
  1. _CONVENTIONS.md 全文          (≈ 1200 字符，首次调用时传入)
  2. contextMap 全文               (≤ 1500 token)
  3. 上一 stage 产物的 "## 摘要"    (≤ 600 字符)
  4. 当前 stage 的 inputs 路径列表  (字符串，不预读)
  5. 当前 stage 的 outputs glob     (字符串)
  6. sub-agent 角色文件            (≤ 2000 字符，紧凑版)
}
```

sub-agent 收到任务包后，**自己**用 `Read` 工具按需读取 `inputs` 列表中的文件；主 agent 不预先读取。

### 10.4 `stateFile` schema

```json
{
  "pipeline": "feature-development",
  "version": "1.0",
  "currentStage": "review-plan",
  "history": [
    { "stage": "plan", "status": "ok", "outputs": ["plan.md"], "summary": "...", "at": "2026-06-17T10:23:45Z" }
  ],
  "retryCounters": { "plan": 1 }
}
```

文件位置：`.agent/hooks/.state/<pipeline>.json`，**应加入 `.gitignore`**。

### 10.5 优化效果估算

| 阶段 | v1 (token) | v1.1 (token) | 节省 |
|---|---|---|---|
| 主 agent 常驻 | 1500 | 300 | 80% |
| plan stage 输入 | 2750 | 500 | 82% |
| review-plan 输入 | 1375 | 700 | 49% |
| code stage 输入 | 1750 | 900 | 49% |
| review-code 输入 | 3000 | 1200 | 60% |
| **合计** | **~10375** | **~3600** | **65%** |

## 11. 已知限制与未来扩展

### 11.1 v1.1 限制

- **同会话串行**：每个 stage 共享主 agent 上下文（但通过 token 优化控制在 ≤ 5000 token）
- **顺序 stage**：不支持并行 / 分叉 / 条件分支
- **人工兜底弱**：stage 失败时仅向用户报告，不能在 stage 中途挂起等待人工介入

### 11.2 可能的 v2 扩展

- **并行 stage**：声明式 DAG（`dependsOn: [stage-a, stage-b]`）让多个审查 agent 并行
- **Stream hook**：流式 sub-agent 输出（如长代码生成）的实时查看与中断
- **多流水线组合**：`onSuccess: <other-pipeline-name>` 跨流水线串联
- **可观测性**：把 pipeline 执行轨迹写入 `.agent/docs/pipeline-runs/<timestamp>.md`
- **跨机器状态共享**：把 `stateFile` 升级为可被多个 agent 工具读写的远端存储

## 12. 索引

| 文档 | 说明 |
|---|---|
| `.agent/hooks/README.md` | 流水线总览 + 快速使用 |
| `.agent/hooks/schema.json` | YAML schema 校验（含 token 优化字段） |
| `.agent/hooks/feature-development.yml` | 标准功能开发流水线（4 段） |
| `.agent/hooks/pr-review.yml` | PR 审查流水线（4 段） |
| `.agent/hooks/.state/<pipeline>.json` | workflow-runner 持久化执行状态（gitignore） |
| `.agent/rules/hooks.md` | 编写新 pipeline 的开发规范（含 token 优化 13 节） |
| `.agent/skills/workflow-runner/SKILL.md` | 执行 pipeline 的 skill |
| `.agent/agents/_CONVENTIONS.md` | 8 个 sub-agent 公共约定（status / 摘要 / 大小上限 / 不要做） |
| `.agent/agents/*.md` | 8 个 sub-agent 角色契约（紧凑版） |
| `.agent/CONTEXT_MAP.md` | 项目地图指针（≤ 1500 token，按需加载） |
