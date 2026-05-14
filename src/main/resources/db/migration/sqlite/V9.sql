-- 版本 9：增加网关密钥额度、滑动窗口限制，以及模型按百万 token 计费的额度单价。
ALTER TABLE gateway_api_key ADD COLUMN quota_balance NUMERIC;
ALTER TABLE gateway_api_key ADD COLUMN quota_limit NUMERIC;
ALTER TABLE gateway_api_key ADD COLUMN quota_window_value INTEGER;
ALTER TABLE gateway_api_key ADD COLUMN quota_window_unit TEXT;

ALTER TABLE ai_channel_model ADD COLUMN input_quota_per_million NUMERIC;
ALTER TABLE ai_channel_model ADD COLUMN output_quota_per_million NUMERIC;
ALTER TABLE ai_channel_model ADD COLUMN cache_read_quota_per_million NUMERIC;

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (9, 'Add API key quota and model quota pricing');
