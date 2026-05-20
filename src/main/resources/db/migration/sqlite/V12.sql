-- V12：为 GPT_AUTH / CLAUDE_AUTH 增加授权文件引用字段；旧 API Key 渠道保持兼容。
ALTER TABLE ai_channel ADD COLUMN auth_mode TEXT NOT NULL DEFAULT 'API_KEY';
ALTER TABLE ai_channel ADD COLUMN auth_file_path TEXT;
ALTER TABLE ai_channel ADD COLUMN auth_status TEXT NOT NULL DEFAULT 'NOT_CONFIGURED';
ALTER TABLE ai_channel ADD COLUMN auth_subject TEXT;
ALTER TABLE ai_channel ADD COLUMN auth_expires_at TIMESTAMP;

UPDATE ai_channel
SET auth_status = CASE
  WHEN api_key IS NOT NULL AND TRIM(api_key) <> '' THEN 'AUTHORIZED'
  ELSE 'NOT_CONFIGURED'
END
WHERE auth_mode = 'API_KEY';

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (12, 'Add auth file provider support');
