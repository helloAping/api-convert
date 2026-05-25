-- 首次安装脚本：只创建缺失对象，不删除任何用户表或历史数据。

CREATE TABLE IF NOT EXISTS gateway_schema_version (
  version INT PRIMARY KEY COMMENT '结构版本号',
  installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  description TEXT NOT NULL COMMENT '版本说明'
) COMMENT='网关数据库结构版本，用于判断是否需要执行增量迁移';

CREATE TABLE IF NOT EXISTS ai_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '渠道主键',
  code VARCHAR(128) NOT NULL UNIQUE COMMENT '渠道编码，路由和模型映射使用的稳定标识',
  name VARCHAR(255) NOT NULL COMMENT '渠道名称',
  type VARCHAR(64) NOT NULL COMMENT '协议类型，例如 OPENAI_COMPATIBLE 或 ANTHROPIC',
  base_url VARCHAR(1024) NOT NULL COMMENT '上游基础地址',
  chat_path VARCHAR(512) NOT NULL DEFAULT '/v1/chat/completions' COMMENT '对话或消息请求路径',
  video_path VARCHAR(512) NOT NULL DEFAULT '/v1/videos' COMMENT '视频生成请求路径',
  image_path VARCHAR(512) NOT NULL DEFAULT '/v1/images/generations' COMMENT '图片生成请求路径',
  models_path VARCHAR(512) NOT NULL DEFAULT '/v1/models' COMMENT '模型列表请求路径',
  api_key TEXT COMMENT '上游 API Key，返回前和日志中必须脱敏',
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'API_KEY' COMMENT '鉴权模式：API_KEY、AUTH_FILE、OAUTH',
  auth_file_path VARCHAR(1024) NULL COMMENT '相对 auth-dir 的授权文件路径',
  auth_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED' COMMENT '授权状态：NOT_CONFIGURED、AUTHORIZED、EXPIRED、ERROR',
  auth_subject VARCHAR(255) NULL COMMENT '脱敏后的授权身份摘要',
  auth_expires_at TIMESTAMP NULL COMMENT 'access token 过期时间',
  priority INT NOT NULL DEFAULT 100 COMMENT '路由优先级，预留给后续加权或故障转移',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '凭证状态，例如 ACTIVE 或 DISABLED',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '渠道是否启用',
  last_error TEXT COMMENT '最近一次上游错误，仅保存脱敏后的排查信息',
  last_used_at TIMESTAMP NULL COMMENT '最近使用时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI 渠道主表，整合供应商、端点和凭证配置';

CREATE TABLE IF NOT EXISTS ai_channel_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型映射主键',
  public_name VARCHAR(255) NOT NULL COMMENT '网关对外模型名，允许多个渠道重复承载',
  model_alias VARCHAR(255) NULL COMMENT '用户手动别名，非空时必须全局唯一',
  channel_code VARCHAR(128) NOT NULL COMMENT '所属渠道编码',
  provider_model VARCHAR(255) NOT NULL COMMENT '上游真实模型名',
  capabilities_json TEXT COMMENT '能力配置 JSON（已弃用，请使用结构化字段）',
  vision BOOLEAN NULL COMMENT '是否支持图片/视觉输入',
  tools_support BOOLEAN NULL COMMENT '是否支持工具/函数调用',
  json_mode_support BOOLEAN NULL COMMENT '是否支持 JSON 输出模式',
  context_length BIGINT NULL COMMENT '最大上下文窗口（token 数）',
  input_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万普通输入 token 消耗的额度',
  output_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万输出 token 消耗的额度',
  cache_read_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万缓存读取输入 token 消耗的额度',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '模型映射是否启用',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_ai_channel_model_channel FOREIGN KEY(channel_code) REFERENCES ai_channel(code),
  UNIQUE KEY uk_ai_channel_model_channel_provider(channel_code, provider_model),
  UNIQUE KEY uk_ai_channel_model_alias(model_alias),
  KEY idx_ai_channel_model_public_name(public_name)
) COMMENT='渠道模型映射表，同一对外模型名可以由多个渠道承载';

CREATE TABLE IF NOT EXISTS gateway_api_key (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '密钥主键',
  name VARCHAR(255) NOT NULL COMMENT '密钥名称',
  raw_key TEXT COMMENT '明文密钥，仅管理端复制使用，日志必须脱敏',
  api_key_hash VARCHAR(128) NOT NULL UNIQUE COMMENT 'API Key 哈希值，用于鉴权匹配',
  key_preview VARCHAR(64) COMMENT '脱敏展示值，不能用于鉴权',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '密钥状态',
  failover_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '上游未写出即失败后是否切换同模型其他渠道',
  quota_balance DECIMAL(20,6) NULL COMMENT '密钥剩余额度；NULL 表示不限总额度',
  quota_limit DECIMAL(20,6) NULL COMMENT '滑动窗口内最多可使用的额度；NULL 表示不限制',
  quota_window_value INT NULL COMMENT '滑动窗口长度数值，例如 3 小时或 7 天',
  quota_window_unit VARCHAR(16) NULL COMMENT '滑动窗口单位：HOUR、DAY、MONTH',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='网关调用方 API Key 表，明文用于管理端复制，哈希用于鉴权匹配';

CREATE TABLE IF NOT EXISTS gateway_api_key_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '授权记录主键',
  api_key_id BIGINT NOT NULL COMMENT '网关密钥 ID',
  channel_code VARCHAR(128) NOT NULL COMMENT '允许使用的渠道编码',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CONSTRAINT fk_gateway_api_key_channel_key FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id),
  CONSTRAINT fk_gateway_api_key_channel_channel FOREIGN KEY(channel_code) REFERENCES ai_channel(code),
  UNIQUE KEY uk_gateway_api_key_channel(api_key_id, channel_code),
  KEY idx_gateway_api_key_channel_api_key(api_key_id)
) COMMENT='网关密钥渠道授权表，没有授权记录表示允许所有渠道';

CREATE TABLE IF NOT EXISTS gateway_api_key_limit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '限制项主键',
  api_key_id BIGINT NOT NULL COMMENT '网关密钥 ID',
  limit_type VARCHAR(32) NOT NULL COMMENT '限制类型：QUOTA、REQUEST 或未来扩展类型',
  window_value INT NOT NULL COMMENT '滑动窗口长度数值',
  window_unit VARCHAR(16) NOT NULL COMMENT '滑动窗口单位：MINUTE、HOUR、DAY',
  limit_value DECIMAL(20,6) NOT NULL COMMENT '限制阈值；额度限制表示额度，请求数限制表示次数',
  config_json TEXT NULL COMMENT '预留扩展配置，不能保存密钥明文',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_gateway_api_key_limit_key FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id),
  UNIQUE KEY uk_gateway_api_key_limit(api_key_id, limit_type, window_unit),
  KEY idx_gateway_api_key_limit_api_key(api_key_id)
) COMMENT='网关密钥限制项表，按限制类型保存额度、请求数等可扩展滑动窗口限制';

CREATE TABLE IF NOT EXISTS gateway_api_key_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '授权记录主键',
  api_key_id BIGINT NOT NULL COMMENT '网关密钥 ID',
  public_model VARCHAR(255) NOT NULL COMMENT '允许调用的网关对外模型名',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CONSTRAINT fk_gateway_api_key_model_key FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id),
  UNIQUE KEY uk_gateway_api_key_model(api_key_id, public_model),
  KEY idx_gateway_api_key_model_api_key(api_key_id)
) COMMENT='网关密钥模型授权表，没有授权记录表示允许所有对外模型';

CREATE TABLE IF NOT EXISTS request_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志主键',
  request_id VARCHAR(128) NOT NULL COMMENT '请求 ID',
  gateway_api_key_id BIGINT COMMENT '调用方密钥 ID',
  source_protocol VARCHAR(64) NOT NULL COMMENT '来源协议',
  request_type VARCHAR(64) NOT NULL DEFAULT 'chat_completions' COMMENT '对话接口类型，例如 chat_completions 或 messages',
  provider_code VARCHAR(128) COMMENT '实际渠道编码',
  provider_type VARCHAR(64) COMMENT '实际供应商协议类型',
  public_model VARCHAR(255) COMMENT '请求对外模型名',
  provider_model VARCHAR(255) COMMENT '上游模型名',
  stream BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否流式请求',
  success BOOLEAN NOT NULL DEFAULT FALSE COMMENT '请求是否成功',
  http_status INT COMMENT 'HTTP 状态码',
  latency_ms BIGINT COMMENT '耗时毫秒',
  input_tokens INT COMMENT '输入 token 数',
  cache_read_input_tokens INT COMMENT '缓存读取输入 token 数',
  output_tokens INT COMMENT '输出 token 数',
  total_tokens INT COMMENT '总 token 数',
  error_code VARCHAR(128) COMMENT '错误码',
  error_message TEXT COMMENT '错误信息',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_request_log_created_at(created_at),
  KEY idx_request_log_request_type(source_protocol, request_type),
  KEY idx_request_log_provider_code(provider_code)
) COMMENT='请求日志表，记录网关调用结果和用量统计';

CREATE TABLE IF NOT EXISTS gateway_system_config (
  config_key VARCHAR(128) PRIMARY KEY COMMENT '配置键，使用稳定字符串标识系统配置项',
  config_value TEXT NOT NULL COMMENT '配置值，按具体配置项解析为字符串、数字或枚举',
  description TEXT COMMENT '配置说明，供管理端展示和排查使用',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='网关系统配置表，用于保存路由策略、失败切换等可运行时调整的全局配置';

INSERT IGNORE INTO gateway_system_config(config_key, config_value, description)
VALUES
  ('routing.mode', 'RANDOM', '路由模式：RANDOM、ROUND_ROBIN、WEIGHTED、SESSION_STICKY'),
  ('routing.failure_threshold', '0', '同一密钥+渠道+模型连续失败达到该次数后进入临时避让；0 表示关闭'),
  ('routing.failure_cooldown_minutes', '0', '失败阈值触发后的避让分钟数；0 表示关闭'),
  ('routing.sticky_ttl_minutes', '1440', '会话粘性绑定保留分钟数');

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (15, 'Add channel image and video endpoint paths');
