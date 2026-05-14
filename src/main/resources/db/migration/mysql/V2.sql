-- V2：将旧的供应商、端点、凭证结构增量同步为渠道结构。
-- 本脚本只新增渠道表和同步数据，保留 ai_provider、ai_endpoint、provider_credential、ai_model 等旧表，避免升级时删除用户历史数据。

CREATE TABLE IF NOT EXISTS ai_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '渠道主键',
  code VARCHAR(128) NOT NULL UNIQUE COMMENT '渠道编码，路由和模型映射使用的稳定标识',
  name VARCHAR(255) NOT NULL COMMENT '渠道名称',
  type VARCHAR(64) NOT NULL COMMENT '协议类型，例如 OPENAI_COMPATIBLE 或 ANTHROPIC',
  base_url VARCHAR(1024) NOT NULL COMMENT '上游基础地址',
  chat_path VARCHAR(512) NOT NULL DEFAULT '/v1/chat/completions' COMMENT '对话或消息请求路径',
  models_path VARCHAR(512) NOT NULL DEFAULT '/v1/models' COMMENT '模型列表请求路径',
  api_key TEXT COMMENT '上游 API Key，返回前和日志中必须脱敏',
  priority INT NOT NULL DEFAULT 100 COMMENT '路由优先级，预留给后续加权或故障转移',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '凭证状态，例如 ACTIVE 或 DISABLED',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '渠道是否启用',
  last_error TEXT COMMENT '最近一次上游错误，仅保存脱敏后的排查信息',
  last_used_at TIMESTAMP NULL COMMENT '最近使用时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI 渠道主表，整合旧供应商、端点和凭证配置';

CREATE TABLE IF NOT EXISTS ai_channel_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型映射主键',
  public_name VARCHAR(255) NOT NULL COMMENT '网关对外模型名，允许多个渠道重复承载',
  channel_code VARCHAR(128) NOT NULL COMMENT '所属渠道编码',
  provider_model VARCHAR(255) NOT NULL COMMENT '上游真实模型名',
  capabilities_json TEXT COMMENT '能力配置 JSON',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '模型映射是否启用',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_ai_channel_model_channel FOREIGN KEY(channel_code) REFERENCES ai_channel(code),
  UNIQUE KEY uk_ai_channel_model_channel_provider(channel_code, provider_model),
  KEY idx_ai_channel_model_public_name(public_name)
) COMMENT='渠道模型映射表，同一个对外模型名可以由多个渠道承载';

-- 旧数据同步：每条旧凭证转换为一个渠道；没有凭证时按供应商创建一个无密钥渠道。
INSERT IGNORE INTO ai_channel(code, name, type, base_url, chat_path, models_path, api_key, priority, status, enabled, last_error, last_used_at, created_at, updated_at)
SELECT
  CASE WHEN c.id IS NULL THEN p.code ELSE CONCAT(p.code, '-', c.id) END,
  CASE WHEN c.id IS NULL THEN p.name ELSE CONCAT(p.name, ' - ', c.name) END,
  p.type,
  e.base_url,
  e.chat_path,
  e.models_path,
  c.api_key,
  COALESCE(c.priority, 100),
  COALESCE(c.status, 'ACTIVE'),
  CASE WHEN p.enabled = TRUE AND e.enabled = TRUE AND (c.id IS NULL OR c.status = 'ACTIVE') THEN TRUE ELSE FALSE END,
  c.last_error,
  c.last_used_at,
  p.created_at,
  CURRENT_TIMESTAMP
FROM ai_provider p
JOIN ai_endpoint e ON e.provider_code = p.code
LEFT JOIN provider_credential c ON c.provider_code = p.code;

-- 旧数据同步：旧模型复制到每个可承载该供应商的渠道，保留上游真实模型名。
INSERT IGNORE INTO ai_channel_model(public_name, channel_code, provider_model, capabilities_json, enabled, created_at, updated_at)
SELECT
  m.public_name,
  CASE WHEN c.id IS NULL THEN p.code ELSE CONCAT(p.code, '-', c.id) END,
  m.provider_model,
  m.capabilities_json,
  m.enabled,
  m.created_at,
  m.updated_at
FROM ai_model m
JOIN ai_provider p ON p.code = m.provider_code
JOIN ai_endpoint e ON e.provider_code = p.code
LEFT JOIN provider_credential c ON c.provider_code = p.code;

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (2, 'Migrate provider endpoint credential tables to channel tables');
