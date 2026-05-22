-- V14：网关密钥新增多渠道失败切换开关，默认关闭以保持既有单渠道失败返回行为。
ALTER TABLE gateway_api_key
  ADD COLUMN failover_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '上游未写出即失败后是否切换同模型其他渠道'
  AFTER status;

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (14, 'Add API key channel failover switch');
