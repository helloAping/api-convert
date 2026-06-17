-- V19: ai_channel 新增 embedding_path 嵌入请求路径字段，OPENAI / CUSTOM / MIMO_TOKEN_PLAN / DEEPSEEK
-- 等 OpenAI 兼容渠道按 /v1/embeddings 默认值补齐，其余供应商按需手动调整。
ALTER TABLE ai_channel
  ADD COLUMN embedding_path VARCHAR(512) NULL COMMENT '嵌入请求路径' AFTER image_path;

UPDATE ai_channel SET embedding_path = '/v1/embeddings'
WHERE type IN ('OPENAI', 'CUSTOM', 'MIMO_TOKEN_PLAN', 'DEEPSEEK', 'OPENCODE', 'VOLC_CODINGPLAN');

INSERT IGNORE INTO gateway_schema_version(version, description)
VALUES (19, 'Add ai_channel.embedding_path for /v1/embeddings endpoint');
