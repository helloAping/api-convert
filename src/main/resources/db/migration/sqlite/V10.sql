-- Version 10: Replace capabilities_json with structured capability columns.
ALTER TABLE ai_channel_model ADD COLUMN vision INTEGER;
ALTER TABLE ai_channel_model ADD COLUMN tools_support INTEGER;
ALTER TABLE ai_channel_model ADD COLUMN json_mode_support INTEGER;
ALTER TABLE ai_channel_model ADD COLUMN context_length BIGINT;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (10, 'Add structured capability columns');
