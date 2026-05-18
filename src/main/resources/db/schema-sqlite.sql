-- 首次安装脚本：只创建缺失对象，不删除任何用户表或历史数据。

-- 表注释：网关数据库结构版本，用于判断是否需要执行增量迁移。
-- 字段注释：version=结构版本号；installed_at=安装时间；description=版本说明。
CREATE TABLE IF NOT EXISTS gateway_schema_version (
  version INTEGER PRIMARY KEY,
  installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description TEXT NOT NULL
);

-- 表注释：AI 渠道主表，整合供应商、端点和凭证配置。
-- 字段注释：id=渠道主键；code=渠道编码；name=渠道名称；type=协议类型；base_url=上游基础地址；chat_path=对话请求路径；models_path=模型列表路径；api_key=上游 API Key；priority=路由优先级；status=凭证状态；enabled=是否启用；last_error=最近一次上游错误；last_used_at=最近使用时间；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS ai_channel (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  base_url TEXT NOT NULL,
  chat_path TEXT NOT NULL DEFAULT '/v1/chat/completions',
  models_path TEXT NOT NULL DEFAULT '/v1/models',
  api_key TEXT,
  priority INTEGER NOT NULL DEFAULT 100,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  enabled INTEGER NOT NULL DEFAULT 1,
  last_error TEXT,
  last_used_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 表注释：渠道模型映射表，同一对外模型名可以由多个渠道承载。
-- 字段注释：id=模型映射主键；public_name=网关对外模型名；model_alias=用户手动别名；channel_code=所属渠道编码；provider_model=上游真实模型名；capabilities_json=能力配置 JSON（已弃用）；vision=是否支持视觉输入；tools_support=是否支持工具调用；json_mode_support=是否支持 JSON 模式输出；context_length=最大上下文窗口（token 数）；input_quota_per_million=每 100 万普通输入 token 消耗额度；output_quota_per_million=每 100 万输出 token 消耗额度；cache_read_quota_per_million=每 100 万缓存读取输入 token 消耗额度；enabled=是否启用；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS ai_channel_model (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  public_name TEXT NOT NULL,
  model_alias TEXT,
  channel_code TEXT NOT NULL,
  provider_model TEXT NOT NULL,
  capabilities_json TEXT,
  vision INTEGER,
  tools_support INTEGER,
  json_mode_support INTEGER,
  context_length BIGINT,
  input_quota_per_million NUMERIC,
  output_quota_per_million NUMERIC,
  cache_read_quota_per_million NUMERIC,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(channel_code) REFERENCES ai_channel(code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_model_channel_provider ON ai_channel_model(channel_code, provider_model);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_model_alias ON ai_channel_model(model_alias);
CREATE INDEX IF NOT EXISTS idx_ai_channel_model_public_name ON ai_channel_model(public_name);

-- 表注释：网关调用方 API Key 表，明文用于管理端复制，哈希用于鉴权匹配。
-- 字段注释：id=密钥主键；name=密钥名称；raw_key=明文密钥；api_key_hash=API Key 哈希；key_preview=脱敏展示值；status=状态；quota_balance=剩余额度，空表示不限总额度；quota_limit=滑动窗口内最多可用额度，空表示不限；quota_window_value=窗口长度数值；quota_window_unit=窗口单位 HOUR/DAY/MONTH；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS gateway_api_key (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  raw_key TEXT,
  api_key_hash TEXT NOT NULL UNIQUE,
  key_preview TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  quota_balance NUMERIC,
  quota_limit NUMERIC,
  quota_window_value INTEGER,
  quota_window_unit TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 表注释：网关密钥渠道授权表，没有授权记录表示允许所有渠道。
-- 字段注释：id=授权记录主键；api_key_id=网关密钥 ID；channel_code=允许使用的渠道编码；created_at=创建时间。
CREATE TABLE IF NOT EXISTS gateway_api_key_channel (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  api_key_id INTEGER NOT NULL,
  channel_code TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id),
  FOREIGN KEY(channel_code) REFERENCES ai_channel(code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_gateway_api_key_channel ON gateway_api_key_channel(api_key_id, channel_code);
CREATE INDEX IF NOT EXISTS idx_gateway_api_key_channel_api_key ON gateway_api_key_channel(api_key_id);

-- 表注释：请求日志表，记录网关调用结果和用量统计。
-- 字段注释：id=日志主键；request_id=请求 ID；gateway_api_key_id=调用方密钥 ID；source_protocol=来源协议；request_type=对话接口类型；provider_code=实际渠道编码；provider_type=实际供应商协议类型；public_model=请求对外模型名；provider_model=上游模型名；stream=是否流式；success=是否成功；http_status=HTTP 状态码；latency_ms=耗时毫秒；input_tokens=输入 token；cache_read_input_tokens=缓存读取输入 token；output_tokens=输出 token；total_tokens=总 token；error_code=错误码；error_message=错误信息；created_at=创建时间。
CREATE TABLE IF NOT EXISTS request_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id TEXT NOT NULL,
  gateway_api_key_id INTEGER,
  source_protocol TEXT NOT NULL,
  request_type TEXT NOT NULL DEFAULT 'chat_completions',
  provider_code TEXT,
  provider_type TEXT,
  public_model TEXT,
  provider_model TEXT,
  stream INTEGER NOT NULL DEFAULT 0,
  success INTEGER NOT NULL DEFAULT 0,
  http_status INTEGER,
  latency_ms INTEGER,
  input_tokens INTEGER,
  cache_read_input_tokens INTEGER,
  output_tokens INTEGER,
  total_tokens INTEGER,
  error_code TEXT,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_request_log_created_at ON request_log(created_at);
CREATE INDEX IF NOT EXISTS idx_request_log_request_type ON request_log(source_protocol, request_type);
CREATE INDEX IF NOT EXISTS idx_request_log_provider_code ON request_log(provider_code);

-- 表注释：网关系统配置表，用于保存路由策略、失败切换等可运行时调整的全局配置。
-- 字段注释：config_key=配置键；config_value=配置值；description=配置说明；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS gateway_system_config (
  config_key TEXT PRIMARY KEY,
  config_value TEXT NOT NULL,
  description TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO gateway_system_config(config_key, config_value, description)
VALUES
  ('routing.mode', 'RANDOM', '路由模式：RANDOM、ROUND_ROBIN、WEIGHTED、SESSION_STICKY'),
  ('routing.failure_threshold', '0', '同一密钥+渠道+模型连续失败达到该次数后进入临时避让；0 表示关闭'),
  ('routing.failure_cooldown_minutes', '0', '失败阈值触发后的避让分钟数；0 表示关闭'),
  ('routing.sticky_ttl_minutes', '1440', '会话粘性绑定保留分钟数');

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (11, 'Add routing system configuration');
