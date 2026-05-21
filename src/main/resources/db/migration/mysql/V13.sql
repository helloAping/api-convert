-- V13：新增网关密钥限制项和模型授权表；保留旧单窗口额度字段并迁移历史配置，不删除用户数据。
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

INSERT IGNORE INTO gateway_api_key_limit(api_key_id, limit_type, window_value, window_unit, limit_value, config_json)
SELECT id, 'QUOTA', quota_window_value, quota_window_unit, quota_limit, NULL
FROM gateway_api_key
WHERE quota_limit IS NOT NULL
  AND quota_limit > 0
  AND quota_window_value IS NOT NULL
  AND quota_window_value > 0
  AND quota_window_unit IS NOT NULL
  AND TRIM(quota_window_unit) <> '';

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (13, 'Add API key limits and model allowlist');
