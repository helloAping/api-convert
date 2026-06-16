package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessage;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicProviderClientTests {

    @Test
    @SuppressWarnings("unchecked")
    void deepSeekAnthropicRequestFillsThinkingFieldFromText() throws Exception {
        DeepSeekProviderClient client = new DeepSeekProviderClient(
                null,
                new OpenAiRequestAdapter(),
                new OpenAiResponseAdapter(),
                new AnthropicRequestAdapter(),
                new AnthropicResponseAdapter(),
                new OpenAiResponsesRequestAdapter(),
                new OpenAiResponsesResponseAdapter()
        );
        AnthropicMessageRequest request = new AnthropicMessageRequest();
        AnthropicMessage assistant = new AnthropicMessage();
        assistant.setRole("assistant");
        assistant.setContent(List.of(
                Map.of("type", "thinking", "text", "hidden reasoning"),
                Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of())
        ));
        request.setMessages(List.of(assistant));

        AnthropicMessageRequest prepared = invokeBeforeAnthropicRequest(client, deepSeekRoute(), request);

        List<Object> content = (List<Object>) prepared.getMessages().getFirst().getContent();
        Map<String, Object> thinking = (Map<String, Object>) content.getFirst();
        assertThat(thinking)
                .containsEntry("type", "thinking")
                .containsEntry("thinking", "hidden reasoning");
    }

    private AnthropicMessageRequest invokeBeforeAnthropicRequest(DeepSeekProviderClient client,
                                                                  ModelRoute route,
                                                                  AnthropicMessageRequest request) throws Exception {
        Method method = BaseAiProviderClient.class.getDeclaredMethod(
                "beforeAnthropicRequest", ModelRoute.class, AnthropicMessageRequest.class);
        method.setAccessible(true);
        return (AnthropicMessageRequest) method.invoke(client, route, request);
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-pro", "deepseek", ProviderType.DEEPSEEK, "deepseek-v4-pro",
                "https://api.deepseek.com/anthropic", "/v1/messages", "sk-test",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
