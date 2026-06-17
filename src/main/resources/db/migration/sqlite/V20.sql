-- V20: ai_channel 新增 audio_speech_path / audio_transcription_path 字段，
-- OPENAI / CUSTOM / MIMO_TOKEN_PLAN / DEEPSEEK / OPENCODE / VOLC_CODINGPLAN / GPT_AUTH
-- 按 OpenAI 官方默认路径补齐，其余供应商按需手动调整。
ALTER TABLE ai_channel ADD COLUMN audio_speech_path TEXT;
ALTER TABLE ai_channel ADD COLUMN audio_transcription_path TEXT;

UPDATE ai_channel SET audio_speech_path = '/v1/audio/speech'
WHERE type IN ('OPENAI', 'CUSTOM', 'MIMO_TOKEN_PLAN', 'DEEPSEEK', 'OPENCODE', 'VOLC_CODINGPLAN', 'GPT_AUTH');
UPDATE ai_channel SET audio_transcription_path = '/v1/audio/transcriptions'
WHERE type IN ('OPENAI', 'CUSTOM', 'MIMO_TOKEN_PLAN', 'DEEPSEEK', 'OPENCODE', 'VOLC_CODINGPLAN', 'GPT_AUTH');

INSERT OR IGNORE INTO gateway_schema_version(version, description)
VALUES (20, 'Add ai_channel audio_speech_path / audio_transcription_path for /v1/audio/* endpoints');
