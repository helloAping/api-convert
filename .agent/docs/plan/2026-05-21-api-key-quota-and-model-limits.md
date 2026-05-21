# 网关密钥额度与模型限制完善计划

## 1. 需求理解

当前网关密钥只支持一组 `quotaLimit + quotaWindowValue + quotaWindowUnit` 滑动窗口额度限制，无法同时配置“n 小时内额度上限”和“n 天内额度上限”。本次需要扩展为：

- 支持同一个网关密钥同时配置多个额度滑动窗口限制。
- 额度限制至少支持：`n 小时内最高额度`、`n 天内最高额度`，并允许两者同时生效。
- 支持请求数滑动窗口限制：`n 分钟内最多请求数`、`n 小时内最多请求数`、`n 天内最多请求数`。
- 额度限制、请求数限制以及后续新增的其他限制类型都以“限制项列表”形式并存，不需要切换模式。
- 不需要某个限制时，直接删除对应限制项；没有配置该类型限制项即表示该类型不限制。
- 网关密钥现有“可用渠道”限制保留，并新增“可用模型”限制：配置后该密钥只能使用指定模型；不配置表示允许所有模型。
- 所有限制均采用滑动窗口。

## 2. 设计选择

### 2.1 限制数据模型

新增独立限制表 `gateway_api_key_limit`，避免继续扩展 `gateway_api_key` 的单窗口字段。该表按限制类型存放限制项，设计上允许后续新增其他限制类型，而不需要再改主表结构。

建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 自增主键 | 限制记录 ID |
| `api_key_id` | bigint/int | 网关密钥 ID |
| `limit_type` | text/varchar | `QUOTA` 或 `REQUEST` |
| `window_value` | int | 滑动窗口长度，必须大于 0 |
| `window_unit` | text/varchar | `MINUTE`、`HOUR`、`DAY` |
| `limit_value` | numeric | 限制值；额度限制表示额度，请求数限制表示次数 |
| `config_json` | text/json | 预留扩展配置；当前可为空，后续新增更复杂限制时使用 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

约束：

- 唯一键：`api_key_id + limit_type + window_unit + window_value`
- `limit_type=QUOTA` 时允许 `HOUR` / `DAY`，也可保留 `MINUTE` 扩展能力但前端不暴露。
- `limit_type=REQUEST` 时支持 `MINUTE` / `HOUR` / `DAY`。
- 后续新增限制时扩展 `limit_type`，并通过 `config_json` 保存该限制类型独有参数。
- `limit_value <= 0` 不允许保存。

兼容迁移：

- 保留 `gateway_api_key` 现有 `quota_limit`、`quota_window_value`、`quota_window_unit` 字段，避免破坏历史代码或用户库。
- V13 迁移脚本把已有单窗口额度配置同步写入 `gateway_api_key_limit(limit_type='QUOTA')`。
- 新代码优先读取 `gateway_api_key_limit`；历史字段只作为迁移来源和兼容展示兜底。

### 2.2 模型授权数据模型

新增 `gateway_api_key_model` 表：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 自增主键 | 授权记录 ID |
| `api_key_id` | bigint/int | 网关密钥 ID |
| `public_model` | text/varchar | 允许使用的网关对外模型名 |
| `created_at` | timestamp | 创建时间 |

语义：

- 无记录表示允许所有模型。
- 有记录时，只允许路由到这些 `ai_channel_model.public_name`。
- 对 `channel/providerModel` 直连请求，也按该渠道模型映射记录的 `public_name` 判断是否授权，避免绕过模型授权。

## 3. 受影响文件

### 3.1 数据库与实体

- `src/main/resources/db/schema-sqlite.sql`
- `src/main/resources/db/schema-mysql.sql`
- `src/main/resources/db/migration/sqlite/V13.sql`
- `src/main/resources/db/migration/mysql/V13.sql`
- `src/main/java/cn/ms08/apiconvert/entity/GatewayApiKeyLimitEntity.java`
- `src/main/java/cn/ms08/apiconvert/entity/GatewayApiKeyModelEntity.java`
- `src/main/java/cn/ms08/apiconvert/dao/GatewayApiKeyLimitMapper.java`
- `src/main/java/cn/ms08/apiconvert/dao/GatewayApiKeyModelMapper.java`

### 3.2 鉴权与路由

- `src/main/java/cn/ms08/apiconvert/security/GatewayPrincipal.java`
  - 增加 `allowedModelNames`
  - 增加 `allowAllModels()`
- `src/main/java/cn/ms08/apiconvert/security/GatewayApiKeyFilter.java`
  - 查询 `gateway_api_key_model`，写入 principal
- `src/main/java/cn/ms08/apiconvert/service/RoutingService.java`
  - `resolve(...)` 增加 allowed model 集合
  - 候选模型过滤时同时检查渠道授权与模型授权
  - 模型被密钥限制时返回 `MODEL_NOT_FOUND` 或 `UNAUTHORIZED_MODEL`（建议新增错误码时才用后者）
- `src/main/java/cn/ms08/apiconvert/service/ChatGatewayService.java`
  - 从 principal 传入 `allowedModelNames`

### 3.3 额度与请求数限制

- `src/main/java/cn/ms08/apiconvert/service/ApiKeyQuotaService.java`
  - 新增读取密钥限制列表
  - 用新表执行多个额度窗口检查
  - 新增请求数窗口检查与记录
  - 将窗口缓存从单一 `apiKeyId -> quota events` 改为按 `apiKeyId + limitType + window` 隔离
  - 请求数限制建议在请求通过路由后、调用上游前计入窗口；上游失败也算一次请求

建议内部结构：

- `LimitKey(apiKeyId, limitType, windowValue, windowUnit)`
- `LimitState(events, total)`：额度窗口 total 为 BigDecimal；请求数窗口 total 为 count
- `LimitEvent(createdAtMillis, amount)`

### 3.4 管理端后端 API

- `src/main/java/cn/ms08/apiconvert/dto/admin/ApiKeyForm.java`
  - 新增 `List<ApiKeyLimitForm> limits`
  - 新增 `List<String> modelNames`
  - 旧 `quotaLimit/quotaWindowValue/quotaWindowUnit` 可临时保留兼容
- `src/main/java/cn/ms08/apiconvert/dto/admin/ApiKeyUpdateForm.java`
  - 同上
- `src/main/java/cn/ms08/apiconvert/dto/admin/ApiKeyLimitForm.java`
  - 新 record，字段：`limitType`、`windowValue`、`windowUnit`、`limitValue`
- `src/main/java/cn/ms08/apiconvert/vo/admin/ApiKeyVO.java`
  - 新增 `limits`、`modelNames`
- `src/main/java/cn/ms08/apiconvert/vo/admin/ApiKeyCreationVO.java`
  - 新增 `limits`、`modelNames`
- `src/main/java/cn/ms08/apiconvert/service/admin/AdminApiKeyService.java`
  - 校验并保存限制列表
  - 校验并保存模型授权列表
  - 删除密钥时清理渠道、模型、限制三类关系
  - `toVO()` 返回限制列表和模型列表

模型授权校验：

- 使用 `AiChannelModelMapper` 查询 `public_name` 是否存在。
- 去重、trim、忽略空字符串。
- 空列表表示允许所有模型。

### 3.5 前端

- `frontend/src/types/index.ts`
  - 新增 `ApiKeyLimitForm` 类型
  - `ApiKeyVO` / `ApiKeyForm` / `ApiKeyUpdateForm` 增加 `limits`、`modelNames`
  - `quotaWindowUnits` 增加 `MINUTE`
- `frontend/src/views/apiKeys/ApiKeyList.vue`
  - 增加“可用模型”列
  - 创建/编辑弹窗增加模型多选
  - 创建/编辑弹窗把单窗口额度 UI 改为限制列表 UI
  - 支持添加多条限制：限制类型（额度/请求数）、窗口数值、窗口单位、上限值
  - 不提供全局“限制模式切换”；额度限制、请求数限制等限制项可同时存在
  - 删除某条限制项即关闭该限制项
  - 额度限制单位下拉默认展示小时/天；请求数限制展示分钟/小时/天
  - 列表展示格式示例：`额度：2小时内 100；1天内 500；请求：1分钟内 60次；1天内 5000次`
- `frontend/src/api/models.ts` 或现有模型 API
  - 复用管理端模型列表接口为密钥模型多选提供选项

### 3.6 测试

- `src/test/java/cn/ms08/apiconvert/ApiConvertApplicationTests.java`
  - 创建密钥时同时配置 2 小时额度和 1 天额度，校验返回 VO
  - 配置请求数限制，例如 1 分钟内 1 次，连续第二次请求被 429 拒绝
  - 配置模型白名单，允许模型能路由，未授权模型被拒绝
  - 验证 `channel/providerModel` 直连不能绕过模型白名单
  - 验证旧 `quotaLimit/quotaWindowValue/quotaWindowUnit` 请求体仍可兼容写入一条 `QUOTA` 限制（如决定保留兼容）

## 4. 实施阶段

### 阶段 1：数据库与后端模型

1. 新增 V13 SQLite / MySQL 迁移脚本：
   - `src/main/resources/db/migration/sqlite/V13.sql`
   - `src/main/resources/db/migration/mysql/V13.sql`
2. 更新首次安装脚本：
   - `src/main/resources/db/schema-sqlite.sql`
   - `src/main/resources/db/schema-mysql.sql`
3. 新增 Entity / Mapper：
   - `GatewayApiKeyLimitEntity`
   - `GatewayApiKeyModelEntity`
   - `GatewayApiKeyLimitMapper`
   - `GatewayApiKeyModelMapper`
4. 保持 V13 脚本只新增表、索引、迁移历史数据和写入版本号，不删除旧字段。

### 阶段 2：管理端 API

1. 新增 `ApiKeyLimitForm`。
2. 扩展 `ApiKeyForm`、`ApiKeyUpdateForm`、`ApiKeyVO`、`ApiKeyCreationVO`。
3. 修改 `AdminApiKeyService`：
   - 保存/替换限制列表
   - 保存/替换模型授权列表
   - 返回 VO 时带出 limits/modelNames
   - 删除密钥时清理新增关系
4. 保留旧字段兼容逻辑，降低前后端同步发布风险。

### 阶段 3：运行时限制生效

1. 修改 `GatewayPrincipal` 和 `GatewayApiKeyFilter`，把模型白名单放入请求上下文。
2. 修改 `RoutingService`，同时应用渠道和模型白名单。
3. 修改 `ChatGatewayService`，传递模型白名单。
4. 重构 `ApiKeyQuotaService`：
   - 多额度窗口限制
   - 多请求数窗口限制
   - 所有限制使用滑动窗口
   - 按 `limit_type` 分发执行器，便于后续增加其他限制类型
   - 请求数限制在上游调用前计入，避免失败请求不计数导致绕过限流

### 阶段 4：前端管理页

1. 修改 `frontend/src/types/index.ts` 类型。
2. 修改 `frontend/src/views/apiKeys/ApiKeyList.vue`：
   - 限制列表配置 UI
   - 模型多选 UI
   - 列表展示可用模型和限制摘要
   - 限制项以新增/删除列表形式管理，不做限制类型切换
3. 复用模型列表接口加载模型选项。

### 阶段 5：测试与文档

1. 补充 `ApiConvertApplicationTests` 覆盖核心限制场景。
2. 运行：
   - `JAVA_HOME_25` 指向 JDK 25 后执行 `mvn -q test`
   - 前端涉及 TS 类型时执行 `cd frontend && npm run build` 或 `npx vue-tsc --noEmit`
3. 同步更新文档：
   - `.agent/docs/AI_GATEWAY_PROGRESS.md`
   - `.agent/docs/modules/02-security.md`
   - `.agent/docs/modules/03-routing.md`
   - `.agent/docs/modules/07-admin.md`
   - `.agent/docs/modules/08-testing.md`
   - `.agent/docs/modules/10-code-structure.md`
   - `README.md`
   - `README_EN.md`

## 5. 风险与边界

- 当前滑动窗口状态是进程内缓存，服务重启后窗口计数会清空；本计划沿用现有行为，不引入 Redis/数据库窗口存储。
- 多实例部署时，各实例窗口独立，无法形成全局请求数/额度窗口；如后续需要多实例强一致限流，应引入集中式存储。
- 额度预检仍基于估算 token，实际扣费基于上游 usage；这与现有机制一致。
- 请求数限制建议计入所有已通过鉴权和路由的请求，包括上游失败请求；否则失败重试可能绕过请求数限制。
- 模型白名单按 `public_name` 控制；直连 `channel/providerModel` 也会映射到对应 `public_name` 后再判断授权。

## 6. 待确认问题

1. 请求数限制是否应计入上游失败请求？
   - 建议：计入，防止错误请求或上游失败重试绕过限流。
2. 额度限制是否需要支持 `MINUTE`？
   - 建议：后端表结构支持，但前端首版只开放小时/天，符合当前需求。
3. 模型白名单是否只按对外模型名 `public_name` 控制？
   - 建议：是。这样不会暴露上游模型细节，也能防止 `channel/providerModel` 绕过限制。
