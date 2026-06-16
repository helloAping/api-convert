package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessage;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.endpoint.EndpointType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 后 DeepSeek 的 Anthropic 行为由 {@link DeepSeekProviderClient} 的 Anthropic Messages
 * capability 承载，覆盖 {@code prepareRequestBody} 把 thinking 块中的 text 字段同步成 thinking 字段。
 */
class AnthropicProviderClientTests {

    /**
     * DeepSeek Anthropic 兼容接口在 thinking 模式连续时，请求 thinking 块必须携带 thinking 字段。
     */
    @Test
    @SuppressWarnings("unchecked")
    void deepSeekAnthropicRequestFillsThinkingFieldFromText() throws Exception {
        AnthropicMessagesCapability anthropic = newDeepSeekAnthropicCapability();
        AnthropicMessageRequest request = new AnthropicMessageRequest();
        AnthropicMessage assistant = new AnthropicMessage();
        assistant.setRole("assistant");
        assistant.setContent(List.of(
                Map.of("type", "thinking", "text", "hidden reasoning"),
                Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of())
        ));
        request.setMessages(List.of(assistant));

        AnthropicMessageRequest prepared = invokePrepareRequestBody(anthropic, deepSeekRoute(), request);

        List<Object> content = (List<Object>) prepared.getMessages().getFirst().getContent();
        Map<String, Object> thinking = (Map<String, Object>) content.getFirst();
        assertThat(thinking)
                .containsEntry("type", "thinking")
                .containsEntry("thinking", "hidden reasoning");
    }

    private AnthropicMessagesCapability newDeepSeekAnthropicCapability() {
        DeepSeekProviderClient client = new DeepSeekProviderClient(
                null,
                new OpenAiRequestAdapter(),
                new OpenAiResponseAdapter(),
                new AnthropicRequestAdapter(),
                new AnthropicResponseAdapter()
        );
        return (AnthropicMessagesCapability) client.capabilities().get(EndpointType.ANTHROPIC_MESSAGES);
    }

    private AnthropicMessageRequest invokePrepareRequestBody(AnthropicMessagesCapability cap,
                                                             ModelRoute route,
                                                             AnthropicMessageRequest request) throws Exception {
        Method method = AnthropicMessagesCapability.class.getDeclaredMethod(
                "prepareRequestBody", ModelRoute.class, AnthropicMessageRequest.class);
        method.setAccessible(true);
        return (AnthropicMessageRequest) method.invoke(cap, route, request);
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-pro",
                "deepseek",
                ProviderType.DEEPSEEK,
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
