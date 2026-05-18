-- Version 11: Add gateway-level system configuration for routing strategy and failure cooldown.

CREATE TABLE IF NOT EXISTS gateway_system_config (
  config_key VARCHAR(128) PRIMARY KEY COMMENT '配置键，使用稳定字符串标识系统配置项',
  config_value TEXT NOT NULL COMMENT '配置值，按具体配置项解析为字符串、数字或枚举',
  description TEXT COMMENT '配置说明，供管理端展示和排查使用',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='网关系统配置表，用于保存路由策略、失败切换等可运行时调整的全局配置';

INSERT IGNORE INTO gateway_system_config(config_key, config_value, description)
VALUES
  ('routing.mode', 'RANDOM', '路由模式：RANDOM、ROUND_ROBIN、WEIGHTED、SESSION_STICKY'),
  ('routing.failure_threshold', '0', '同一密钥+渠道+模型连续失败达到该次数后进入临时避让；0 表示关闭'),
  ('routing.failure_cooldown_minutes', '0', '失败阈值触发后的避让分钟数；0 表示关闭'),
  ('routing.sticky_ttl_minutes', '1440', '会话粘性绑定保留分钟数');

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (11, 'Add routing system configuration');
