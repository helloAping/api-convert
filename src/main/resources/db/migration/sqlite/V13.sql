-- V13：新增网关密钥限制项和模型授权表；保留旧单窗口额度字段并迁移历史配置，不删除用户数据。
CREATE TABLE IF NOT EXISTS gateway_api_key_limit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  api_key_id INTEGER NOT NULL,
  limit_type TEXT NOT NULL,
  window_value INTEGER NOT NULL,
  window_unit TEXT NOT NULL,
  limit_value NUMERIC NOT NULL,
  config_json TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_gateway_api_key_limit ON gateway_api_key_limit(api_key_id, limit_type, window_unit);
CREATE INDEX IF NOT EXISTS idx_gateway_api_key_limit_api_key ON gateway_api_key_limit(api_key_id);

CREATE TABLE IF NOT EXISTS gateway_api_key_model (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  api_key_id INTEGER NOT NULL,
  public_model TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(api_key_id) REFERENCES gateway_api_key(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_gateway_api_key_model ON gateway_api_key_model(api_key_id, public_model);
CREATE INDEX IF NOT EXISTS idx_gateway_api_key_model_api_key ON gateway_api_key_model(api_key_id);

INSERT OR IGNORE INTO gateway_api_key_limit(api_key_id, limit_type, window_value, window_unit, limit_value, config_json)
SELECT id, 'QUOTA', quota_window_value, quota_window_unit, quota_limit, NULL
FROM gateway_api_key
WHERE quota_limit IS NOT NULL
  AND quota_limit > 0
  AND quota_window_value IS NOT NULL
  AND quota_window_value > 0
  AND quota_window_unit IS NOT NULL
  AND TRIM(quota_window_unit) <> '';

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (13, 'Add API key limits and model allowlist');
