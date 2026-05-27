# 模块 09：部署与运维

> 对应代码：`Dockerfile`、README 部署段落
> 依赖模块：[01-基础设施](01-infrastructure.md)（数据库、日志路径）
> 被依赖模块：无（最外层）

---

## 1. Docker 部署

### 镜像地址

```bash
crpi-vqmjtaxg5bb83uba.cn-guangzhou.personal.cr.aliyuncs.com/aping/api-convert:{版本号}
```

### 运行参数

- `--XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders'` 开启紧凑对象头
- `-v api-convert-data:/app/data` 挂载统一数据目录
- `LOG_PATH=/app/data/logs` 日志路径

## 2. Nginx 反向代理注意

必须显式透传鉴权头，否则反代后端无法读取 Bearer token：

```nginx
proxy_set_header Authorization $http_authorization;
```

## 3. 环境变量

| 变量 | 说明 |
|---|---|
| `api-convert.database.type` | `sqlite`（默认）或 `mysql` |
| `api-convert.database.install-enabled` | 自动安装/升级开关（默认 `true`） |
| `api-convert.auth.storage-dir` | AUTH 文件存储根目录 |
| `API_CONVERT_AUTH_STORAGE_DIR` | 覆盖 AUTH 文件存储路径 |
| `API_CONVERT_JACKSON_MAX_STRING_LENGTH` | JSON 单个字符串最大长度，默认 `100000000`，用于 base64 图片/视频透传 |
| `JAVA_HOME_25` | JDK 25 路径（本地开发） |

## 4. API 快速测试

```bash
# 健康检查（无需鉴权）
curl http://localhost:8080/health

# 模型列表
curl -H 'Authorization: Bearer sk-local-dev' http://localhost:8080/v1/models

# OpenAI 聊天（非流式）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "example-chat", "messages": [{"role": "user", "content": "hello"}], "stream": false}'

# OpenAI 聊天（流式）
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "example-chat", "messages": [{"role": "user", "content": "hello"}], "stream": true}'

# Anthropic 消息（非流式）
curl -X POST http://localhost:8080/v1/messages \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -H 'anthropic-version: 2023-06-01' \
  -d '{"model": "claude-3-opus", "messages": [{"role": "user", "content": "hello"}]}'

# Anthropic 消息（流式）
curl -X POST http://localhost:8080/v1/messages \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -H 'anthropic-version: 2023-06-01' \
  -d '{"model": "claude-3-opus", "messages": [{"role": "user", "content": "hello"}], "stream": true}'

# OpenAI Responses API
curl -X POST http://localhost:8080/v1/responses \
  -H 'Authorization: Bearer sk-local-dev' \
  -H 'Content-Type: application/json' \
  -d '{"model": "gpt-4o", "input": "hello", "stream": false}'
```

## 5. 本地运行

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn spring-boot:run -Dspring-boot.run.profiles=sqlite
```

打开 `http://localhost:8080`，默认账号 `admin` / `admin123`。
