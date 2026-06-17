# CONTEXT_MAP（项目地图）

> Sub-agent 默认加载这份地图；不确定读什么时查指针，再 `Read` 工具按需加载具体文档。**不要**全量加载下列文档。

## 0. 入口

- `AGENTS.md` → `.agent/rules.md` → 5 份 rules + `hooks.md`
- `.agent/docs/AI_GATEWAY_PROGRESS.md` → 当前实现状态（按章节 `Read`）
- `AGENTS.local.md` → 本机 JDK 25 路径
- `docs/DEVELOPMENT.md` → 人类面向的开发文档

## 1. 模块文档（.agent/docs/modules/）

01-infrastructure · 02-security · 03-routing · 04-endpoints · 05-providers · 06-streaming · 07-admin · 08-testing · 09-deployment · 10-code-structure · **11-hooks**

## 2. 关键代码包

`endpoint` (EndpointType/Handler/Registry/GatewayController) · `adapter.endpoint` (EndpointProviderAdapter/ProviderHook/DeepSeekHook) · `adapter.protocol` (OpenAi/Anthropic/Responses RequestAdapter) · `adapter.stream` (StreamResponseTransformer/ResponsesStreamTransformer) · `provider` (AiProviderClient/Registry/ProviderType/各 ProviderClient) · `service` (ChatGatewayService/RoutingService/ApiKeyQuotaService/UsageRecorder) · `security` (GatewayApiKeyFilter/GatewayPrincipal) · `dto/vo/entity` (ModelRoute/UnifiedChatRequest 等) · `exception` (GatewayException/ErrorCode/ProviderException)

## 3. 数据库迁移

首次安装：`db/schema-*.sql`（**只**创建缺失对象，不 DROP）。升级：`db/migration/{sqlite,mysql}/V{version}.sql`，启动时 `DatabaseInstaller` 按版本执行；末尾 `gateway_schema_version`。渠道/端点/凭证/模型**只能**通过管理端或 DB 维护。

## 4. 测试命令

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
cd frontend && npx vue-tsc --noEmit
```

## 5. 文档同步

新增/修改/删除功能 → **必须**更新 `.agent/docs/AI_GATEWAY_PROGRESS.md`（AGENTS.md §文档强制同步）。公开 API 变化时 → 同步更新 `.agent/docs/API_REFERENCE.md`。

## 6. 命名规范

`Entity`=DB 表 · `DTO`=查询 · `VO`=响应 · `Request`=入参 · Mapper 继承 `BaseMapper<Entity>` · 查询优先 `LambdaQueryWrapper` · Lombok `@Getter/@Setter`，**不**用 `@Data`

## 7. Agent 工作流入口

Pipeline `.agent/hooks/<name>.yml` · Sub-agent `.agent/agents/<role>.md` · 公共约定 `.agent/agents/_CONVENTIONS.md` · 执行器 `.agent/skills/workflow-runner/SKILL.md` · 设计 `.agent/docs/modules/11-hooks.md` · 规范 `.agent/rules/hooks.md`
