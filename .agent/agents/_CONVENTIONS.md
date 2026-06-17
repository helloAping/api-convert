# _CONVENTIONS — Sub-agent 公共约定

> 所有 `.agent/agents/*.md` 角色文件默认遵循本文档。角色文件**只描述差异化的部分**（身份 / 阶段特有输出），通用约定不必重复写入。

## 1. Status 字段契约

任何 stage 产出的 markdown 文件**首行或首屏**必须包含：

```markdown
# <报告标题>

status: ok | approved | rejected | blocked | changes-requested
```

- `ok` / `approved` → 触发 `onSuccess`
- `rejected` / `blocked` / `changes-requested` → 触发 `onFailure`
- 缺失 / 拼写错误 → 默认按 `onFailure` 处理

## 2. 摘要契约（强制）

每个 stage 产出文件**必须**包含 `## 摘要` 小节：

- 长度 ≤ 200 token（约 600 字符 / 100 行内中文）
- 内容：本次执行的关键结论 + 给下一 stage 的关键交接信息
- 下一 stage 的 workflow-runner **默认只把摘要 + 文件路径**喂给 sub-agent，不传全文
- 若无摘要，workflow-runner 会**自动提取文件前 30 行**作为兜底摘要

## 3. 文件大小硬上限

- 单个 stage 产物 ≤ 6000 字符（约 1500 token）
- 超过时 sub-agent **必须**拆为：
  - 主报告：≤ 6000 字符，含 `## 摘要` + 关键决策
  - 附录文件：路径在主报告 `## 附录` 小节列出，供下一 stage 按需 `Read` 工具读取

## 4. 不要做（通用）

- 不要直接调用其他 sub-agent；通过文件契约交接
- 不要修改不属于本 stage `outputs` 声明的文件
- 不要 commit / push / 创建 PR
- 不要把 secrets（API Key、token、密码）写入产物
- 不要回退到更早 stage 自行决定的 status（status 决定权在审查 agent 而非实现 agent）
- 不要静默拍板未在 plan.md 覆盖的设计点；停下来 `status: blocked`

## 5. 输入上下文优先级

sub-agent 收到多份输入时按以下顺序决定注意力：

1. **上一 stage 的 `## 摘要`** — 最优先
2. **本 stage 的 `inputs` 显式声明的文件** — 按需 `Read` 工具按需读取
3. **CONTEXT_MAP.md 项目地图** — 不知道读什么时从这里查指针
4. **角色文件（自身）** — 行为边界

不要把上述 4 项全部加载到上下文；按需 `Read`。
