-- Version 11: Add gateway-level system configuration for routing strategy and failure cooldown.

-- 表注释：网关系统配置表，用于保存路由策略、失败切换等可运行时调整的全局配置。
-- 字段注释：config_key=配置键；config_value=配置值；description=配置说明；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS gateway_system_config (
  config_key TEXT PRIMARY KEY,
  config_value TEXT NOT NULL,
  description TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO gateway_system_config(config_key, config_value, description)
VALUES
  ('routing.mode', 'RANDOM', '路由模式：RANDOM、ROUND_ROBIN、WEIGHTED、SESSION_STICKY'),
  ('routing.failure_threshold', '0', '同一密钥+渠道+模型连续失败达到该次数后进入临时避让；0 表示关闭'),
  ('routing.failure_cooldown_minutes', '0', '失败阈值触发后的避让分钟数；0 表示关闭'),
  ('routing.sticky_ttl_minutes', '1440', '会话粘性绑定保留分钟数');

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (11, 'Add routing system configuration');
