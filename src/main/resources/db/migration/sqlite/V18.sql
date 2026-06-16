-- V18: ai_channel add capabilities JSON column for per-capability path config
ALTER TABLE ai_channel ADD COLUMN capabilities TEXT;

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (18, 'Add capabilities JSON column to ai_channel');
