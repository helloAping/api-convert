-- V16：渠道模型映射新增端点类型限制，允许按模型标记只兼容特定端点类型。
-- 字段说明：allowed_endpoint_types=逗号分隔的 EndpointType 名称，为空表示不限制。
ALTER TABLE ai_channel_model ADD COLUMN allowed_endpoint_types TEXT;

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (16, 'Add allowed endpoint types per channel model');
