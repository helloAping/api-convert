-- Version 10: Replace capabilities_json with structured capability columns.
ALTER TABLE ai_channel_model
  ADD COLUMN vision BOOLEAN NULL COMMENT '是否支持图片/视觉输入' AFTER capabilities_json,
  ADD COLUMN tools_support BOOLEAN NULL COMMENT '是否支持工具/函数调用' AFTER vision,
  ADD COLUMN json_mode_support BOOLEAN NULL COMMENT '是否支持 JSON 输出模式' AFTER tools_support,
  ADD COLUMN context_length BIGINT NULL COMMENT '最大上下文窗口（token 数）' AFTER json_mode_support;

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (10, 'Add structured capability columns');
