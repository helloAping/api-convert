package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatProviderClientTests {

    @Test
    void deepSeekChatAssistantMessagesAlwaysCarryReasoningContentField() {
        DeepSeekChatProviderClient client = new DeepSeekChatProviderClient(
                null, new OpenAiRequestAdapter(), new OpenAiResponseAdapter());
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setContent("previous answer");
        OpenAiMessage toolCallAssistant = new OpenAiMessage();
        toolCallAssistant.setRole("assistant");
        toolCallAssistant.setToolCalls(List.of(Map.of(
                "id", "call_1",
                "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant, toolCallAssistant));

        OpenAiChatCompletionRequest prepared = client.prepareRequestBody(deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("");
        assertThat(prepared.getMessages().get(1).getReasoningContent()).isEqualTo("");
    }

    @Test
    void deepSeekChatKeepsExistingReasoningContent() {
        DeepSeekChatProviderClient client = new DeepSeekChatProviderClient(
                null, new OpenAiRequestAdapter(), new OpenAiResponseAdapter());
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setReasoningContent("real thinking");
        assistant.setToolCalls(List.of(Map.of(
                "id", "call_1",
                "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant));

        OpenAiChatCompletionRequest prepared = client.prepareRequestBody(deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("real thinking");
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-flash",
                "deepseek",
                ProviderType.DEEPSEEK_CHAT,
                "deepseek-v4-flash",
                "https://api.deepseek.com",
                "/v1/chat/completions",
                "sk-test",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
