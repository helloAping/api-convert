-- V17: 合并 ProviderType 枚举值，将旧协议维度值迁移为供应商身份值
UPDATE ai_channel SET type = 'OPENAI' WHERE type IN ('OPENAI_COMPATIBLE', 'OPENAI_RESPONSES');
UPDATE ai_channel SET type = 'DEEPSEEK' WHERE type IN ('DEEPSEEK_CHAT', 'DEEPSEEK_ANTHROPIC');
UPDATE ai_channel SET type = 'VOLC_CODINGPLAN' WHERE type IN ('VOLC_CODINGPLAN_CHAT', 'VOLC_CODINGPLAN_ANTHROPIC');
UPDATE ai_channel SET type = 'OPENCODE' WHERE type IN ('OPENCODE_CHAT', 'OPENCODE_ANTHROPIC');

INSERT INTO gateway_schema_version(version, description)
VALUES (17, 'Merge ProviderType enum values to supplier identity');
