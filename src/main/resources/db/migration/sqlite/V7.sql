-- V7：增强请求日志元数据，明确记录对话接口类型和实际供应商协议类型。
-- 本版本只新增字段和索引，不删除或重建用户日志数据。

-- 字段注释：request_type=对话接口类型，例如 chat_completions 或 messages；provider_type=实际供应商协议类型，例如 OPENAI_COMPATIBLE 或 ANTHROPIC。
ALTER TABLE request_log ADD COLUMN request_type TEXT NOT NULL DEFAULT 'chat_completions';
ALTER TABLE request_log ADD COLUMN provider_type TEXT;

CREATE INDEX IF NOT EXISTS idx_request_log_created_at ON request_log(created_at);
CREATE INDEX IF NOT EXISTS idx_request_log_request_type ON request_log(source_protocol, request_type);
CREATE INDEX IF NOT EXISTS idx_request_log_provider_code ON request_log(provider_code);

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (7, 'Enhance request log metadata');
