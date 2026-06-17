-- V18: ai_channel add capabilities JSON column for per-capability path config
ALTER TABLE ai_channel ADD COLUMN capabilities TEXT;

-- V18: ai_channel_model add allowed_capabilities column for model-level capability restriction
ALTER TABLE ai_channel_model ADD COLUMN allowed_capabilities TEXT;

-- V18: request_log add source_endpoint_type and upstream_endpoint_type columns
ALTER TABLE request_log ADD COLUMN source_endpoint_type TEXT;
ALTER TABLE request_log ADD COLUMN upstream_endpoint_type TEXT;

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (18, 'Add capabilities, allowed_capabilities, source/upstream endpoint type columns');
