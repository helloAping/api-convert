# 模块 01：基础设施与数据层

> 对应代码：`config/`、`DatabaseInstaller`、`GatewayBootstrapService`、`entity/`、`dao/`
> 相关模块：[02-安全鉴权](02-security.md)、[03-路由](03-routing.md)、[04-端点](04-endpoints.md)、[09-部署](09-deployment.md)

---

## 1. 基础框架

| 模块 | 说明 |
|---|---|
| Spring Boot 4.0.6 | Web 层使用 Spring MVC（`spring-boot-starter-webmvc`） |
| MyBatis-Plus 3.5.16 | ORM 层，8 个业务 Mapper 接口均继承 `BaseMapper` |
| 双数据库支持 | SQLite（默认开发）和 MySQL，通过 `api-convert.database.type` 切换 |
| Spring JDBC | `DatabaseInstaller` 使用 `JdbcTemplate` 执行安装脚本和检查 |
| Spring RestClient | `WebConfig` 提供 `RestClient.Builder` bean，各 Provider Client 使用它调用上游 |
| Log4j2 | 使用 `log4j2-spring.xml` 输出控制台、应用日志和 SQL 日志 |
| Lombok | Entity 类使用 `@Getter/@Setter`，避免手写 |

## 2. 数据库自动安装与增量升级 (`DatabaseInstaller`)

启动时自动执行：

- 检查 `api-convert.database.install-enabled`（默认 `true`）
- 根据 `api-convert.database.type`（`sqlite` / `mysql`）选择脚本
- 首次安装：`gateway_schema_version` 不存在时，只执行 `schema-sqlite.sql` 或 `schema-mysql.sql`
- 增量升级：`gateway_schema_version` 已存在时，从当前版本逐个执行 `src/main/resources/db/migration/{sqlite,mysql}/V{version}.sql`
- **当前结构版本：`14`**
- 首次安装脚本不得删除用户表；如版本 SQL 需要替换表或删除字段，必须先在脚本内完成备份或数据同步

### 核心数据表

| 表名 | 用途 |
|---|---|
| `gateway_schema_version` | 安装版本追踪 |
| `ai_channel` | 渠道配置：供应商类型、baseUrl、请求路径、模型列表路径、上游 API Key、AUTH 类型渠道的 auth.json 文件引用 |
| `ai_channel_model` | 渠道模型映射：模型前缀、唯一别名、1M 输入/输出/缓存读取额度单价、能力字段（vision、tools_support、json_mode_support、context_length） |
| `gateway_api_key` | 网关 API Key：明文（管理端复制）、SHA-256 哈希（鉴权）、余额、同步失败切换开关、旧单窗口额度字段兼容 |
| `gateway_api_key_channel` | 网关密钥可用渠道范围；无记录表示允许全部渠道 |
| `gateway_api_key_model` | 网关密钥可用模型范围；无记录表示允许全部模型 |
| `gateway_api_key_limit` | 网关密钥可并存限制项，当前支持额度限制和请求数限制，预留扩展配置 |
| `gateway_system_config` | 运行期系统配置：路由模式、会话粘性、错误避让参数 |
| `request_log` | 请求审计日志（token 用量、延迟、错误信息） |

### V12 AUTH 文件存储（新增）

- `ai_channel` 新增 `auth_mode`、`auth_file_path`、`auth_status`、`auth_subject`、`auth_expires_at`
- SQLite 默认保存到数据库父目录同级的 `auth-dir`；MySQL 默认 `/opt/data/auth-dir`
- 可通过 `api-convert.auth.storage-dir` 或 `API_CONVERT_AUTH_STORAGE_DIR` 覆盖
- 授权文件按 `{providerType}/{channelCode}.json` 隔离保存，管理端响应脱敏身份/状态/过期时间，不暴露 access token

### V13 网关密钥限制项与模型授权（新增）

- 新增 `gateway_api_key_limit`：按 `limit_type + window_unit + window_value` 保存可并存限制，当前支持 `QUOTA` 和 `REQUEST`
- 新增 `gateway_api_key_model`：按对外模型名限制网关密钥可调用模型，空列表表示允许全部模型
- 迁移脚本会把旧 `gateway_api_key.quota_limit/quota_window_*` 同步为一条 `QUOTA` 限制项，不删除旧字段以兼容历史接口

### V14 网关密钥失败切换开关（新增）

- `gateway_api_key` 新增 `failover_enabled`，默认关闭，避免改变历史密钥的单渠道失败返回行为
- 开启后当前渠道上游在未向客户端写出响应前失败时，按同模型剩余授权渠道继续尝试；流式请求一旦已经写出 SSE 字节，就继续使用当前渠道结果或错误事件

## 3. 启动引导数据 (`GatewayBootstrapService`)

`@CommandLineRunner`，在 `DatabaseInstaller` 之后运行：

- 写入 `gateway_api_key`：`sk-local-dev`（明文、脱敏预览、SHA-256 哈希），默认 `ACTIVE`
- 升级后历史 bootstrap 记录如缺少明文，会用配置补齐 `raw_key` 和 `key_preview`
- **渠道、端点、上游密钥和模型不再从配置文件引导**，必须通过管理端或数据库写入

策略：网关密钥不存在时插入；历史缺少明文时只补齐 `raw_key` 和 `key_preview`，不覆盖其他配置。
