package cn.ms08.apiconvert.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTests {

    /**
     * 超大请求体多为图片/视频 base64，日志脱敏阶段应直接摘要，避免额外构造 JsonNode。
     */
    @Test
    void skipsJsonParsingForLargeBodies() {
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\""
                + "a".repeat(300_000)
                + "\"}]}";

        String sanitized = LogSanitizer.sanitizeBody(body);

        assertThat(sanitized).isEqualTo("<large body omitted, original=" + body.length() + " chars>");
    }
}
