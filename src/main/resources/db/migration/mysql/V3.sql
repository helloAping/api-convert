-- V3：模型映射支持别名，并移除对外模型名唯一限制。

ALTER TABLE ai_channel_model
  ADD COLUMN model_alias VARCHAR(255) NULL COMMENT '用户手动别名，非空时必须全局唯一' AFTER public_name;

-- 约束调整：移除旧版本可能存在的 public_name 唯一索引，让多个渠道可以承载同一个对外模型名。
SET @public_name_unique_index := (
  SELECT INDEX_NAME
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_channel_model'
    AND COLUMN_NAME = 'public_name'
    AND NON_UNIQUE = 0
  LIMIT 1
);
SET @drop_public_name_unique_sql := IF(@public_name_unique_index IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ai_channel_model DROP INDEX `', @public_name_unique_index, '`'));
PREPARE drop_public_name_unique_stmt FROM @drop_public_name_unique_sql;
EXECUTE drop_public_name_unique_stmt;
DEALLOCATE PREPARE drop_public_name_unique_stmt;

ALTER TABLE ai_channel_model
  ADD UNIQUE KEY uk_ai_channel_model_alias(model_alias);

INSERT IGNORE INTO gateway_schema_version(version, description) VALUES (3, 'Allow duplicated public model names and add model alias');
