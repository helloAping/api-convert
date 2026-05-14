-- V2：将旧的供应商、端点、凭证结构增量同步为渠道结构。
-- 本脚本只新增渠道表和同步数据，保留 ai_provider、ai_endpoint、provider_credential、ai_model 等旧表，避免升级时删除用户历史数据。

-- 表注释：AI 渠道主表，整合旧供应商、端点和凭证配置。
-- 字段注释：id=渠道主键；code=渠道编码；name=渠道名称；type=协议类型；base_url=上游基础地址；chat_path=对话请求路径；models_path=模型列表路径；api_key=上游密钥；priority=路由优先级；status=凭证状态；enabled=是否启用；last_error=最近上游错误；last_used_at=最近使用时间；created_at=创建时间；updated_at=更新时间。
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

-- 表注释：渠道模型映射表，同一个对外模型名可以由多个渠道承载。
-- 字段注释：id=模型映射主键；public_name=网关对外模型名；channel_code=所属渠道编码；provider_model=上游真实模型名；capabilities_json=能力配置 JSON；enabled=是否启用；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS ai_channel_model (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  public_name TEXT NOT NULL,
  channel_code TEXT NOT NULL,
  provider_model TEXT NOT NULL,
  capabilities_json TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(channel_code) REFERENCES ai_channel(code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_model_channel_provider ON ai_channel_model(channel_code, provider_model);
CREATE INDEX IF NOT EXISTS idx_ai_channel_model_public_name ON ai_channel_model(public_name);

-- 旧数据同步：每条旧凭证转换为一个渠道；没有凭证时按供应商创建一个无密钥渠道。
INSERT OR IGNORE INTO ai_channel(code, name, type, base_url, chat_path, models_path, api_key, priority, status, enabled, last_error, last_used_at, created_at, updated_at)
SELECT
  CASE WHEN c.id IS NULL THEN p.code ELSE p.code || '-' || c.id END,
  CASE WHEN c.id IS NULL THEN p.name ELSE p.name || ' - ' || c.name END,
  p.type,
  e.base_url,
  e.chat_path,
  e.models_path,
  c.api_key,
  COALESCE(c.priority, 100),
  COALESCE(c.status, 'ACTIVE'),
  CASE WHEN p.enabled = 1 AND e.enabled = 1 AND (c.id IS NULL OR c.status = 'ACTIVE') THEN 1 ELSE 0 END,
  c.last_error,
  c.last_used_at,
  p.created_at,
  CURRENT_TIMESTAMP
FROM ai_provider p
JOIN ai_endpoint e ON e.provider_code = p.code
LEFT JOIN provider_credential c ON c.provider_code = p.code;

-- 旧数据同步：旧模型复制到每个可承载该供应商的渠道，保留上游真实模型名。
INSERT OR IGNORE INTO ai_channel_model(public_name, channel_code, provider_model, capabilities_json, enabled, created_at, updated_at)
SELECT
  m.public_name,
  CASE WHEN c.id IS NULL THEN p.code ELSE p.code || '-' || c.id END,
  m.provider_model,
  m.capabilities_json,
  m.enabled,
  m.created_at,
  m.updated_at
FROM ai_model m
JOIN ai_provider p ON p.code = m.provider_code
JOIN ai_endpoint e ON e.provider_code = p.code
LEFT JOIN provider_credential c ON c.provider_code = p.code;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (2, 'Migrate provider endpoint credential tables to channel tables');
