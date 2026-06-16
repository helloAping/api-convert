package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 后 DeepSeek 的 Chat 行为由 {@link DeepSeekProviderClient} 内 OpenAI Chat capability 承载，
 * 该 capability 是匿名子类，覆盖 {@code prepareRequestBody} 来兜底 reasoning_content 字段。
 */
class DeepSeekChatProviderClientTests {

    @Test
    void deepSeekChatAssistantMessagesAlwaysCarryReasoningContentField() throws Exception {
        OpenAiChatCapability chat = newDeepSeekChatCapability();
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

        OpenAiChatCompletionRequest prepared = invokePrepareRequestBody(chat, deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("");
        assertThat(prepared.getMessages().get(1).getReasoningContent()).isEqualTo("");
    }

    @Test
    void deepSeekChatKeepsExistingReasoningContent() throws Exception {
        OpenAiChatCapability chat = newDeepSeekChatCapability();
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setReasoningContent("real thinking");
        assistant.setToolCalls(List.of(Map.of(
                "id", "call_1",
                "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant));

        OpenAiChatCompletionRequest prepared = invokePrepareRequestBody(chat, deepSeekRoute(), request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("real thinking");
    }

    private OpenAiChatCapability newDeepSeekChatCapability() {
        DeepSeekProviderClient client = new DeepSeekProviderClient(
                null,
                new OpenAiRequestAdapter(),
                new OpenAiResponseAdapter(),
                new AnthropicRequestAdapter(),
                new AnthropicResponseAdapter()
        );
        return (OpenAiChatCapability) client.capabilities().get(EndpointType.CHAT_COMPLETIONS);
    }

    private OpenAiChatCompletionRequest invokePrepareRequestBody(OpenAiChatCapability cap,
                                                                 ModelRoute route,
                                                                 OpenAiChatCompletionRequest request) throws Exception {
        Method method = OpenAiChatCapability.class.getDeclaredMethod(
                "prepareRequestBody", ModelRoute.class, OpenAiChatCompletionRequest.class);
        method.setAccessible(true);
        return (OpenAiChatCompletionRequest) method.invoke(cap, route, request);
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-flash",
                "deepseek",
                ProviderType.DEEPSEEK,
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
