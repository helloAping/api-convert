-- V12：为 GPT_AUTH / CLAUDE_AUTH 增加授权文件引用字段；旧 API Key 渠道保持兼容。
ALTER TABLE ai_channel ADD COLUMN auth_mode VARCHAR(32) NOT NULL DEFAULT 'API_KEY' COMMENT '鉴权模式：API_KEY、AUTH_FILE、OAUTH' AFTER api_key;
ALTER TABLE ai_channel ADD COLUMN auth_file_path VARCHAR(1024) NULL COMMENT '相对 auth-dir 的授权文件路径' AFTER auth_mode;
ALTER TABLE ai_channel ADD COLUMN auth_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED' COMMENT '授权状态：NOT_CONFIGURED、AUTHORIZED、EXPIRED、ERROR' AFTER auth_file_path;
ALTER TABLE ai_channel ADD COLUMN auth_subject VARCHAR(255) NULL COMMENT '脱敏后的授权身份摘要' AFTER auth_status;
ALTER TABLE ai_channel ADD COLUMN auth_expires_at TIMESTAMP NULL COMMENT 'access token 过期时间' AFTER auth_subject;

UPDATE ai_channel
SET auth_status = CASE
  WHEN api_key IS NOT NULL AND TRIM(api_key) <> '' THEN 'AUTHORIZED'
  ELSE 'NOT_CONFIGURED'
END
WHERE auth_mode = 'API_KEY';

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (12, 'Add auth file provider support');
