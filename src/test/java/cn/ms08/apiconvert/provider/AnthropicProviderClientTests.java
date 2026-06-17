package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook;
import cn.ms08.apiconvert.adapter.endpoint.ProviderHook;
import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessage;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DeepSeek 特化 hook 在 Anthropic Messages 协议下的端到端效果：
 * 客户端 /v1/messages 上来的请求，经历 AnthropicRequestAdapter.toUnified → DeepSeekHook.preProcess
 * → AnthropicRequestAdapter.toProviderRequest 后，thinking 块必须带上 {@code thinking} 字段。
 * <p>
 * 重构前这个能力写在 {@code DeepSeekProviderClient.beforeAnthropicRequest}，重构后迁出
 * 到 {@link DeepSeekHook}，由 {@code ChatGatewayService} 在路由阶段按 ProviderType 触发。
 * </p>
 */
class AnthropicProviderClientTests {

    private final AnthropicRequestAdapter requestAdapter = new AnthropicRequestAdapter();
    private final ProviderHook hook = new DeepSeekHook();

    @Test
    @SuppressWarnings("unchecked")
    void deepSeekAnthropicRequestFillsThinkingFieldFromText() {
        AnthropicMessageRequest request = new AnthropicMessageRequest();
        request.setModel("deepseek-v4-pro");
        AnthropicMessage assistant = new AnthropicMessage();
        assistant.setRole("assistant");
        assistant.setContent(List.of(
                Map.of("type", "thinking", "text", "hidden reasoning"),
                Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of())
        ));
        request.setMessages(List.of(assistant));

        // 完整链路：provider DTO → unified → hook → provider DTO
        UnifiedChatRequest unified = requestAdapter.toUnified(request);
        UnifiedChatRequest hooked = hook.preProcess(unified, EndpointType.ANTHROPIC_MESSAGES, deepSeekRoute());
        AnthropicMessageRequest prepared = requestAdapter.toProviderRequest(hooked, "deepseek-v4-pro");

        List<Object> content = (List<Object>) prepared.getMessages().getFirst().getContent();
        Map<String, Object> thinking = (Map<String, Object>) content.getFirst();
        assertThat(thinking)
                .containsEntry("type", "thinking")
                .containsEntry("thinking", "hidden reasoning");
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-pro", "deepseek", ProviderType.DEEPSEEK, "deepseek-v4-pro",
                "https://api.deepseek.com/anthropic", "/v1/messages", "sk-test",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
