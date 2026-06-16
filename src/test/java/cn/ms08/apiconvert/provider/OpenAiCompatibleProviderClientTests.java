package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.UnifiedUsage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 后 OpenAI 兼容上游统一由 {@link OpenAiChatCapability} 承载，原
 * OpenAiCompatibleProviderClient 已并入。本测试验证流式透传与最终 usage 解析
 * 仍然正确。
 */
class OpenAiCompatibleProviderClientTests {

    /**
     * 验证流式透传不会吞掉 SSE，同时能从最终 usage 块提取输入、输出和缓存读取 token。
     */
    @Test
    void copyOpenAiStreamParsesFinalUsageChunk() {
        OpenAiChatCapability capability = new OpenAiChatCapability(null, null, null);
        String sse = """
                data: {"id":"chatcmpl-test","choices":[{"delta":{"content":"hi"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150,"prompt_tokens_details":{"cached_tokens":80}}}

                data: [DONE]

                """;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        UnifiedUsage usage = capability.copyOpenAiStream(
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)),
                outputStream
        );

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo(sse);
        assertThat(usage.inputTokens()).isEqualTo(120);
        assertThat(usage.outputTokens()).isEqualTo(30);
        assertThat(usage.totalTokens()).isEqualTo(150);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(80);
    }
}
