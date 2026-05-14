-- 版本 8：请求日志增加缓存读取输入 token 字段，用于展示上游缓存命中用量。
ALTER TABLE request_log ADD COLUMN cache_read_input_tokens INTEGER;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (8, 'Add cache read token usage');
