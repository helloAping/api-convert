package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleProviderClientTests {

    @Test
    void copyOpenAiStreamParsesFinalUsageChunk() throws Exception {
        var client = new BaseAiProviderClient(null,
                new OpenAiRequestAdapter(), new OpenAiResponseAdapter(),
                new AnthropicRequestAdapter(), new AnthropicResponseAdapter(),
                new OpenAiResponsesRequestAdapter(), new OpenAiResponsesResponseAdapter()) {
            @Override public ProviderType type() { return ProviderType.OPENAI; }
            @Override public List<ProviderModel> models(ProviderModelFetchRequest r) { return List.of(); }
            @Override public ProviderQuota quota(ProviderQuotaFetchRequest r) { return null; }
        };
        String sse = """
                data: {"id":"chatcmpl-test","choices":[{"delta":{"content":"hi"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150,"prompt_tokens_details":{"cached_tokens":80}}}

                data: [DONE]

                """;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Method method = BaseAiProviderClient.class.getDeclaredMethod(
                "copyOpenAiStream", java.io.InputStream.class, java.io.OutputStream.class);
        method.setAccessible(true);
        UnifiedUsage usage = (UnifiedUsage) method.invoke(client,
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), outputStream);

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo(sse);
        assertThat(usage.inputTokens()).isEqualTo(120);
        assertThat(usage.outputTokens()).isEqualTo(30);
        assertThat(usage.totalTokens()).isEqualTo(150);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(80);
    }
}
