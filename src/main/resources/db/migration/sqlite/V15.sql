-- V15：渠道新增视频生成和图片生成路径配置，旧渠道按 OpenAI 兼容默认路径补齐。
-- 字段说明：video_path=视频生成请求路径；image_path=图片生成请求路径。
ALTER TABLE ai_channel ADD COLUMN video_path TEXT NOT NULL DEFAULT '/v1/videos';
ALTER TABLE ai_channel ADD COLUMN image_path TEXT NOT NULL DEFAULT '/v1/images/generations';

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (15, 'Add channel image and video endpoint paths');
