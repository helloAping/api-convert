# api-convert

`api-convert` 是一个 Java 25 + Spring Boot 4 的 AI API 网关，提供 OpenAI / Anthropic 兼容入口，并通过管理端维护上游渠道、模型映射、网关 API Key 和请求日志。

## 界面预览

![管理端预览 1](img/Snipaste_2026-05-15_18-24-48.png)

![管理端预览 2](img/Snipaste_2026-05-15_18-25-26.png)

![管理端预览 3](img/Snipaste_2026-05-15_18-25-31.png)

![管理端预览 4](img/Snipaste_2026-05-15_18-25-55.png)

![管理端预览 5](img/Snipaste_2026-05-15_18-28-27.png)

## 技术栈

- 后端：Java 25、Spring Boot 4.0.6、Maven、MyBatis-Plus、Log4j2
- 数据库：默认 SQLite，可通过环境变量切换 MySQL
- 前端：Vue 3、Vite、TypeScript、Naive UI

## 本地启动

前置要求：

- JDK 25
- Node.js 与 npm
- Maven Wrapper 使用仓库内的 `mvnw.cmd` / `mvnw`

Windows PowerShell：

```powershell
.\scripts\start.ps1
```

指定 JDK 25 路径：

```powershell
.\scripts\start.ps1 -JavaHome 'D:\path\to\jdk-25'
```

启动后端时会检查 `JAVA_OPTS`，如果未开启紧凑对象头，会自动追加：

```text
-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders
```

指定管理端账号密码：

```powershell
.\scripts\start.ps1 -AdminUsername admin -AdminPassword 'change-me'
```

使用 MySQL 启动：

```powershell
.\scripts\start.ps1 -DbType mysql `
  -DatasourceUrl 'jdbc:mysql://127.0.0.1:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' `
  -DatasourceUsername api_convert `
  -DatasourcePassword 'change-me'
```

Linux / macOS / Git Bash：

```bash
./scripts/start.sh
```

指定 JDK 25 路径：

```bash
./scripts/start.sh --java-home /path/to/jdk-25
```

启动后端时会检查 `JAVA_OPTS`，如果未开启紧凑对象头，会自动追加：

```text
-XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders
```

指定管理端账号密码：

```bash
./scripts/start.sh --admin-username admin --admin-password 'change-me'
```

使用 MySQL 启动：

```bash
./scripts/start.sh --db-type mysql \
  --datasource-url 'jdbc:mysql://127.0.0.1:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  --datasource-username api_convert \
  --datasource-password 'change-me'
```

默认会同时启动：

- 后端：http://localhost:8080
- 管理端前端：http://localhost:5173

也可以只启动其中一个：

```powershell
.\scripts\start.ps1 -Target backend
.\scripts\start.ps1 -Target frontend
```

```bash
./scripts/start.sh backend
./scripts/start.sh frontend
```

管理端默认账号来自环境变量，未设置时为：

- 用户名：`admin`
- 密码：`admin123`

## 常用接口

健康检查无需鉴权：

```bash
curl http://localhost:8080/health
```

网关 API Key 需要在管理端创建，创建后可用于访问 OpenAI / Anthropic 兼容接口：

```bash
curl -H "Authorization: Bearer <gateway-api-key>" http://localhost:8080/v1/models
```

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <gateway-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"example-chat","messages":[{"role":"user","content":"hello"}],"stream":false}'
```

## 重要配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端监听端口 |
| `API_CONVERT_TIME_ZONE` | `Asia/Shanghai` | 应用时区 |
| `API_CONVERT_DB_TYPE` | `sqlite` | 数据库类型：`sqlite` 或 `mysql` |
| `API_CONVERT_SQLITE_PATH` | `${user.dir}/api-convert.db` | SQLite 文件路径 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:${api-convert.database.sqlite-path}` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | 空 | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `API_CONVERT_DB_INSTALL_ENABLED` | `true` | 是否启动时自动安装/升级表结构 |
| `API_CONVERT_SECURITY_ENABLED` | `true` | 是否启用网关 API Key 鉴权 |
| `API_CONVERT_ADMIN_USERNAME` | `admin` | 管理端账号 |
| `API_CONVERT_ADMIN_PASSWORD` | `admin123` | 管理端密码 |
| `LOG_LEVEL` / `APP_LOG_LEVEL` | 见 Log4j2 配置 | 日志级别 |
| `SQL_LOG_LEVEL` / `SQL_PARAM_LOG_LEVEL` | 见 Log4j2 配置 | SQL 日志级别 |

生产环境不要使用默认管理端密码。

MySQL JDBC URL 样例：

```text
jdbc:mysql://127.0.0.1:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

驱动类不需要单独传参，配置文件会按 `API_CONVERT_DB_TYPE` 固定选择：

- `sqlite`：`org.sqlite.JDBC`
- `mysql`：`com.mysql.cj.jdbc.Driver`

## Docker

构建镜像：

```bash
docker build -t api-convert:local .
```

使用 SQLite 运行：

```bash
docker run --rm -p 8080:8080 \
  -v api-convert-data:/app/data \
  -e API_CONVERT_ADMIN_USERNAME=admin \
  -e API_CONVERT_ADMIN_PASSWORD='change-me' \
  api-convert:local
```

容器内默认 SQLite 路径为 `/app/data/api-convert.db`。镜像构建时会编译前端并放入后端静态资源，启动后可直接访问 `http://localhost:8080` 使用管理端。

使用 MySQL 运行时示例：

```bash
docker run --rm -p 8080:8080 \
  -e API_CONVERT_DB_TYPE=mysql \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://mysql:3306/api_convert?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  -e SPRING_DATASOURCE_USERNAME=api_convert \
  -e SPRING_DATASOURCE_PASSWORD='change-me' \
  -e API_CONVERT_ADMIN_PASSWORD='change-me' \
  api-convert:local
```

## 构建与测试

后端：

```bash
mvn -q test
```

前端：

```bash
cd frontend
npm install
npm run build
```

## 开源协议

本项目使用 MIT License。该协议允许商用、修改、分发和私有使用，同时软件按“原样”提供，不提供任何形式的担保，作者和版权持有人不承担使用软件产生的责任。
