package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessage;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicProviderClientTests {

    /**
     * DeepSeek Anthropic 兼容接口在 thinking 模式续轮时要求 thinking 块必须携带 thinking 字段。
     */
    @Test
    @SuppressWarnings("unchecked")
    void deepSeekAnthropicRequestFillsThinkingFieldFromText() {
        DeepSeekAnthropicProviderClient client = new DeepSeekAnthropicProviderClient(
                null, new AnthropicRequestAdapter(), new AnthropicResponseAdapter());
        AnthropicMessageRequest request = new AnthropicMessageRequest();
        AnthropicMessage assistant = new AnthropicMessage();
        assistant.setRole("assistant");
        assistant.setContent(List.of(
                Map.of("type", "thinking", "text", "hidden reasoning"),
                Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of())
        ));
        request.setMessages(List.of(assistant));

        AnthropicMessageRequest prepared = client.prepareRequestBody(deepSeekRoute(), request);

        List<Object> content = (List<Object>) prepared.getMessages().getFirst().getContent();
        Map<String, Object> thinking = (Map<String, Object>) content.getFirst();
        assertThat(thinking)
                .containsEntry("type", "thinking")
                .containsEntry("thinking", "hidden reasoning");
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-pro",
                "deepseek",
                ProviderType.DEEPSEEK_ANTHROPIC,
                "deepseek-v4-pro",
                "https://api.deepseek.com/anthropic",
                "/v1/messages",
                "sk-test",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
