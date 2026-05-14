-- 版本 9：增加网关密钥额度、滑动窗口限制，以及模型按百万 token 计费的额度单价。
ALTER TABLE gateway_api_key
  ADD COLUMN quota_balance DECIMAL(20,6) NULL COMMENT '密钥剩余额度；NULL 表示不限总额度' AFTER status,
  ADD COLUMN quota_limit DECIMAL(20,6) NULL COMMENT '滑动窗口内最多可使用的额度；NULL 表示不限制' AFTER quota_balance,
  ADD COLUMN quota_window_value INT NULL COMMENT '滑动窗口长度数值，例如 3 小时或 7 天' AFTER quota_limit,
  ADD COLUMN quota_window_unit VARCHAR(16) NULL COMMENT '滑动窗口单位：HOUR、DAY、MONTH' AFTER quota_window_value;

ALTER TABLE ai_channel_model
  ADD COLUMN input_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万普通输入 token 消耗的额度' AFTER capabilities_json,
  ADD COLUMN output_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万输出 token 消耗的额度' AFTER input_quota_per_million,
  ADD COLUMN cache_read_quota_per_million DECIMAL(20,6) NULL COMMENT '每 100 万缓存读取输入 token 消耗的额度' AFTER output_quota_per_million;

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (9, 'Add API key quota and model quota pricing');
