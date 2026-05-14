-- V4：新增网关密钥渠道授权表。没有授权记录表示密钥允许使用全部渠道。

-- 表注释：网关密钥渠道授权表，用于限制外部调用密钥可使用的渠道范围。
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

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (4, 'Add gateway api key channel scope');
