-- V15：渠道新增视频生成和图片生成路径配置，旧渠道按 OpenAI 兼容默认路径补齐。
ALTER TABLE ai_channel
  ADD COLUMN video_path VARCHAR(512) NOT NULL DEFAULT '/v1/videos' COMMENT '视频生成请求路径' AFTER chat_path,
  ADD COLUMN image_path VARCHAR(512) NOT NULL DEFAULT '/v1/images/generations' COMMENT '图片生成请求路径' AFTER video_path;

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (15, 'Add channel image and video endpoint paths');
