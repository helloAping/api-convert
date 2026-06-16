package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatProviderClientTests {

    @Test
    void deepSeekChatAssistantMessagesAlwaysCarryReasoningContentField() throws Exception {
        DeepSeekProviderClient client = newDeepSeekClient();
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setContent("previous answer");
        OpenAiMessage toolCallAssistant = new OpenAiMessage();
        toolCallAssistant.setRole("assistant");
        toolCallAssistant.setToolCalls(List.of(Map.of(
                "id", "call_1", "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant, toolCallAssistant));

        OpenAiChatCompletionRequest prepared = invokeBeforeChatRequest(client, deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("");
        assertThat(prepared.getMessages().get(1).getReasoningContent()).isEqualTo("");
    }

    @Test
    void deepSeekChatKeepsExistingReasoningContent() throws Exception {
        DeepSeekProviderClient client = newDeepSeekClient();
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setReasoningContent("real thinking");
        assistant.setToolCalls(List.of(Map.of(
                "id", "call_1", "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant));

        OpenAiChatCompletionRequest prepared = invokeBeforeChatRequest(client, deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("real thinking");
    }

    private DeepSeekProviderClient newDeepSeekClient() {
        return new DeepSeekProviderClient(null,
                new OpenAiRequestAdapter(), new OpenAiResponseAdapter(),
                new AnthropicRequestAdapter(), new AnthropicResponseAdapter(),
                new OpenAiResponsesRequestAdapter(), new OpenAiResponsesResponseAdapter());
    }

    private OpenAiChatCompletionRequest invokeBeforeChatRequest(DeepSeekProviderClient client,
                                                                 ModelRoute route,
                                                                 OpenAiChatCompletionRequest request) throws Exception {
        Method method = BaseAiProviderClient.class.getDeclaredMethod(
                "beforeChatRequest", ModelRoute.class, OpenAiChatCompletionRequest.class);
        method.setAccessible(true);
        return (OpenAiChatCompletionRequest) method.invoke(client, route, request);
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-flash", "deepseek", ProviderType.DEEPSEEK, "deepseek-v4-flash",
                "https://api.deepseek.com", "/v1/chat/completions", "sk-test",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
