# Hook 机制开发规范

> 适用范围：在 `.agent/hooks/*.yml` 新增、修改工作流 pipeline，以及在 `.agent/agents/*.md` 新增对应的 sub-agent 角色。本规范是 `.agent/rules/api-conventions.md` 在工作流层面的延伸。

## 1. 何时使用 hook

满足以下任一条件时，**应该**用 hook pipeline 编排多 agent 接力，而不是单 agent 一次性处理：

- 任务天然分阶段（plan → review → code → review；diff → audit → summary ...）
- 阶段之间需要可追溯的产物（plan.md / review.md / code-changes.md）作为契约
- 任何阶段需要被打回重做且有重试上限
- 至少有一个独立"审查"角色需要与"实现"角色隔离

不满足上述条件时，**不要**为了 hook 而 hook——单 agent / 单 skill 即可。

## 2. 与代码层 `ProviderHook` 的命名边界

代码层 `cn.ms08.apiconvert.adapter.endpoint.ProviderHook` 是供应商特化 hook，作用在请求/响应链路上。

`.agent/hooks/` 下的 hook 是 **AI agent 工作流钩子**，作用在多 agent 协作上。

两个概念同名但完全正交：

- 前者是 Java 接口 + Spring 注册表，处理 HTTP 请求体
- 后者是 YAML 流水线 + sub-agent，处理任务拆分

**编写代码层 hook 文档时**必须用全限定名 `ProviderHook` 或 "代码层 hook / supplier hook"；**编写工作流 hook 文档时**用 "agent 工作流 / pipeline" 避免歧义。

## 3. 目录与文件命名

| 类型 | 路径 | 命名 |
|---|---|---|
| 流水线定义 | `.agent/hooks/<name>.yml` | kebab-case（如 `feature-development.yml`） |
| 流水线总览 | `.agent/hooks/README.md` | 固定文件名 |
| Schema | `.agent/hooks/schema.json` | 固定文件名 |
| Sub-agent 角色 | `.agent/agents/<role>.md` | kebab-case（如 `plan-reviewer.md`） |
| 运行 skill | `.agent/skills/workflow-runner/SKILL.md` | 固定路径 |

Pipeline `name` 字段必须与文件名一致（去掉 `.yml`）。

## 4. YAML 字段必填与可选

| 字段 | 是否必填 | 默认值 |
|---|---|---|
| `version` | ✅ | — |
| `name` | ✅ | — |
| `stages` | ✅ | — |
| `stages[].name` | ✅ | — |
| `stages[].agent` | ✅ | — |
| `stages[].onSuccess` | ✅ | — |
| `displayName` | — | 同 `name` |
| `description` | — | — |
| `stages[].inputs` | — | `[]` |
| `stages[].outputs` | — | `[]` |
| `stages[].maxRetries` | — | 3 |
| `stages[].onFailure` | — | `stop` |
| `triggers` | — | `{manual: false}` |

## 5. 阶段设计原则

- **单一职责**：一个 stage 只做一件事，输出一份明确的产物
- **可独立验证**：每个 stage 的产物（`outputs`）能让其他 agent 独立判断 status
- **声明式 transition**：stage 之间用 `onSuccess` / `onFailure` 显式声明跳转；不要让 sub-agent 自己决定下一步
- **可被打回**：审查类 stage 的 `onFailure` 应该回到上游 stage 而非 `stop`（除了阻断性问题）
- **终态显式**：必须有 `done` / `stop` 出口，避免无限循环

## 6. Sub-agent 角色契约

每个 `.agent/agents/<role>.md` 必须包含以下小节（顺序可调，标题必须保留）：

1. **身份**：1 段话说明这个 agent 是谁、做什么、不做什么
2. **输入**：列出从上游 stage 接收什么
3. **输出**：列出产出的文件路径与 markdown 结构
4. **协作契约**：`status: ok` / `status: rejected` 等的判定标准与对应跳转
5. **不要做**：明确禁止事项，避免 sub-agent 越权

## 7. status 字段约定

- `status: ok` / `status: approved` → 走 `onSuccess`
- `status: rejected` / `status: blocked` / `status: changes-requested` → 走 `onFailure`
- 缺失或拼写错误 → 默认按 `onFailure` 处理

## 8. 重试与中断

- `maxRetries` 控制同一 stage 被打回的最大次数
- 每次重试都把审查报告作为输入传给被打回的 stage
- 超过上限立即 `stop`，向用户报告：流水线名 / 走到哪个 stage / 阻断原因 / 累计重试次数

## 9. 文件契约

Sub-agent 之间不直接对话，**只通过 markdown 文件**传递信息：

- 上游产物放在 `outputs` 声明的路径（默认仓库根）
- 下游读取按 `inputs` 声明的 glob 收集
- 每个 stage 的输出报告必须包含 `status:` 行（位于首行或显眼的固定小节）

## 10. 与现有结构的关系

| 现有 | 与本规范的关系 |
|---|---|
| `.agent/skills/workflow-runner/SKILL.md` | 唯一被授权执行 pipeline 的 skill |
| `.agent/agents/*.md` | 复用本规范的 sub-agent 契约 |
| `.agent/commands/*.md` | 可通过 `triggers.command` 把 slash command 绑定到 pipeline |
| `.agent/docs/AI_GATEWAY_PROGRESS.md` | 新增 pipeline 后必须同步更新（AGENTS.md 强制） |
| `AGENTS.md` | 文档强制同步条款同样适用 |

## 11. 修改与删除

- 修改 pipeline：调整 YAML 时必须同步更新 README 表格与设计文档中的示例
- 删除 pipeline：必须从 README、设计文档、AI_GATEWAY_PROGRESS.md 三处同步清除
- 任何修改 / 删除必须在 commit message 中说明影响范围

## 12. 检查清单（提交前自检）

- [ ] YAML 通过 `.agent/hooks/schema.json` 字段校验
- [ ] 至少 2 个 stage（单一 stage 退化为 skill 即可）
- [ ] 有 `done` 或 `stop` 出口
- [ ] 所有引用的 `agents/*.md` 文件存在
- [ ] 每个 sub-agent 角色文件在文档开头一行声明 `> 遵循 .agent/agents/_CONVENTIONS.md`，**不**重复写入通用约定
- [ ] 角色文件大小 ≤ 2000 字符（约 500 token）；超过时要把通用部分外置到 `_CONVENTIONS.md`
- [ ] `.agent/hooks/README.md` 表格已加入新 pipeline
- [ ] `.agent/docs/modules/11-hooks.md` 示例与之一致
- [ ] `.agent/docs/AI_GATEWAY_PROGRESS.md` 已同步

## 13. Token 优化（强制）

本节是 v1.1 加入的硬性约束，目的：单次完整 pipeline 的总 token 消耗控制在 ≤ 5000（不含 sub-agent 自身生成的中间思考）。

### 13.1 公共约定外置

- 通用段落（status 字段约定 / 摘要契约 / 大小上限 / 不要做 / 输入优先级）必须写在 `.agent/agents/_CONVENTIONS.md`
- 角色文件**只**写差异化部分（身份 / 阶段特有输入 / 阶段特有输出 / status 判定）
- 不允许把通用"不要做"等段落复制到每个角色文件

### 13.2 项目地图（按需加载）

- pipeline 顶层显式声明 `contextMap`（默认 `.agent/CONTEXT_MAP.md`）
- sub-agent 任务包**只**包含 contextMap 全文（≤ 1k token），**不**包含 `AGENTS.md` / `rules.md` / `AI_GATEWAY_PROGRESS.md` 全文
- sub-agent 需要更多信息时**自己**用 `Read` 工具按需读取

### 13.3 摘要契约（强制）

- pipeline 顶层 `summaryMaxChars`（默认 600）
- 每个 stage 产物必须含 `## 摘要` 小节；长度 ≤ `summaryMaxChars`
- workflow-runner 把"上一 stage 的 `## 摘要` 文本 + 文件路径"喂给下一 stage，**不**传产物全文

### 13.4 大小硬上限

- pipeline 顶层 `maxArtifactSize`（默认 6000 字符）
- 单个 stage 产物超过 `maxArtifactSize` 时 sub-agent **必须**拆成"主报告（≤ `maxArtifactSize`）+ 附录文件（路径在主报告列出）"
- workflow-runner 拒绝该 stage 完成并提示拆分

### 13.5 状态外置

- pipeline 顶层声明 `stateFile`（默认 `.agent/hooks/.state/<pipeline>.json`）
- 每次 stage 切换把 `{currentStage, history, retryCounters, version}` 写入 `stateFile`
- 主 agent 内存**只**保留：当前 stage name + 上一 stage 摘要
- `stateFile` 加入 `.gitignore`

### 13.6 主 agent 内存自检清单

执行 pipeline 前确认：

- [ ] 主 agent 上下文不含上一 stage 产物全文
- [ ] 主 agent 上下文不含完整的 `AI_GATEWAY_PROGRESS.md` / `rules.md`
- [ ] 主 agent 上下文不重复持有 `_CONVENTIONS.md`（首次声明后即可丢弃）
- [ ] 历史 stage 的 `## 摘要` 在下一 stage 完成后即从主 agent 内存丢弃
