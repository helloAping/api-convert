-- V3：模型映射支持别名，并移除对外模型名唯一限制。
-- SQLite 修改约束需要重建表；删除旧表前先创建备份表，并将数据同步到新表后再替换结构。

-- 备份旧模型映射数据，保留升级前原始记录，满足表替换前的数据保护要求。
CREATE TABLE IF NOT EXISTS ai_channel_model_backup_v3 AS SELECT * FROM ai_channel_model;

-- 表注释：渠道模型映射表，支持同一对外模型名由多个渠道承载，别名非空时全局唯一。
-- 字段注释：id=模型映射主键；public_name=网关对外模型名；model_alias=用户手动别名；channel_code=所属渠道编码；provider_model=上游真实模型名；capabilities_json=能力配置 JSON；enabled=是否启用；created_at=创建时间；updated_at=更新时间。
CREATE TABLE IF NOT EXISTS ai_channel_model_v3 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  public_name TEXT NOT NULL,
  model_alias TEXT,
  channel_code TEXT NOT NULL,
  provider_model TEXT NOT NULL,
  capabilities_json TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(channel_code) REFERENCES ai_channel(code)
);

-- 数据同步：先将旧表所有数据写入新结构，新增别名字段暂为空，历史模型展示保持不变。
INSERT OR IGNORE INTO ai_channel_model_v3(id, public_name, model_alias, channel_code, provider_model, capabilities_json, enabled, created_at, updated_at)
SELECT id, public_name, NULL, channel_code, provider_model, capabilities_json, enabled, created_at, updated_at
FROM ai_channel_model;

-- 结构替换：已完成备份和数据同步后删除旧表，随后将新表改名为正式表。
DROP TABLE ai_channel_model;
ALTER TABLE ai_channel_model_v3 RENAME TO ai_channel_model;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_model_channel_provider ON ai_channel_model(channel_code, provider_model);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_model_alias ON ai_channel_model(model_alias);
CREATE INDEX IF NOT EXISTS idx_ai_channel_model_public_name ON ai_channel_model(public_name);

INSERT OR IGNORE INTO gateway_schema_version(version, description) VALUES (3, 'Allow duplicated public model names and add model alias');
