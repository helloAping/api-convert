-- V14：网关密钥新增多渠道失败切换开关，默认关闭以保持既有单渠道失败返回行为。
-- 字段注释：failover_enabled=请求在上游未写出即失败后是否按同模型剩余授权渠道继续尝试。
ALTER TABLE gateway_api_key ADD COLUMN failover_enabled INTEGER NOT NULL DEFAULT 0;

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (14, 'Add API key channel failover switch');
