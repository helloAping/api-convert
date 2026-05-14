-- V5：网关密钥增加脱敏展示字段。
-- 字段注释：key_preview=脱敏展示值，仅用于前端列表展示，不能用于鉴权。

ALTER TABLE gateway_api_key ADD COLUMN key_preview TEXT;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (5, 'Add gateway api key preview field');
