-- V7：增强请求日志元数据，明确记录对话接口类型和实际供应商协议类型。
-- 本版本只新增字段和索引，不删除或重建用户日志数据。

ALTER TABLE request_log
  ADD COLUMN request_type VARCHAR(64) NOT NULL DEFAULT 'chat_completions' COMMENT '对话接口类型，例如 chat_completions 或 messages' AFTER source_protocol,
  ADD COLUMN provider_type VARCHAR(64) COMMENT '实际供应商协议类型' AFTER provider_code,
  ADD KEY idx_request_log_created_at(created_at),
  ADD KEY idx_request_log_request_type(source_protocol, request_type),
  ADD KEY idx_request_log_provider_code(provider_code);

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (7, 'Enhance request log metadata');
