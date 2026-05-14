-- V6：网关密钥增加明文字段。
-- 字段注释：raw_key=明文密钥，仅用于管理端复制；接口响应展示和日志打印必须脱敏。
-- 历史密钥只有哈希值，无法从哈希反推出明文，因此升级时不回填历史 raw_key。

ALTER TABLE gateway_api_key ADD COLUMN raw_key TEXT;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (6, 'Gateway API key plaintext copy schema');
