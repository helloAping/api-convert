-- V4：新增网关密钥渠道授权表。没有授权记录表示密钥允许使用全部渠道。

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

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (4, 'Add gateway api key channel scope');
