# Persistence and API Model Rules

Use the following suffixes consistently:

- `Entity`: database table mapping object. Entity classes are used by MyBatis-Plus and map directly to database tables.
- `DTO`: database query return object. Use DTO for custom query projections, joined query results, aggregate rows, or internal data transfer from persistence/query layers.
- `VO`: response object returned to frontend/API clients.
- `Request`: controller request body/query object received from clients.

Rules:

- Do not use `DTO` for frontend responses; use `VO`.
- Do not use `Entity` as a controller request or response object unless the endpoint is strictly internal and there is no boundary conversion need.
- Do not put database annotations on `DTO`, `VO`, or `Request` classes unless they are intentionally used by a persistence framework.
- Keep controller input validation on `Request` classes.
- Keep API protocol-specific request models under `cn.ms08.apiconvert.dto` and response models under `cn.ms08.apiconvert.vo`.

## MyBatis-Plus

- Mapper interfaces should extend `BaseMapper<Entity>`.
- Prefer `LambdaQueryWrapper` for type-safe queries.
- Keep SQL install scripts under `src/main/resources/db/`.
- Keep `Entity` classes under `cn.ms08.apiconvert.entity`.
- Keep mapper classes under `cn.ms08.apiconvert.dao`.
- 渠道、端点、上游密钥和模型只能通过管理端接口或数据库表维护，不允许再新增 `providers`、`models` 等配置文件引导项来自动写入业务数据。

## 数据库升级约束

- 首次安装脚本 `src/main/resources/db/schema-*.sql` 只能创建缺失对象和写入当前版本号，不允许通过 `DROP TABLE`、重命名旧表或清空数据来升级用户库。
- 结构升级必须按版本写入 `src/main/resources/db/migration/{sqlite,mysql}/V{version}.sql`，启动时由 `DatabaseInstaller` 按当前版本逐个执行。
- 每个版本 SQL 必须包含该版本所有新增、删除、字段调整、索引调整和必要的数据同步语句，并在最后写入 `gateway_schema_version`。
- 如确实需要删除表、替换表或删除字段，版本 SQL 中必须先提供明确的数据备份或数据同步逻辑，并用中文注释说明删除前的数据保护方式。
- 新增表和字段必须补充中文表注释、字段注释；SQLite 不支持原生注释时，必须在 SQL 中用中文注释说明表和字段含义。
