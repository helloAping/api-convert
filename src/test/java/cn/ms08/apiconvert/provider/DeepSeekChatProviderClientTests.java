package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook;
import cn.ms08.apiconvert.adapter.endpoint.ProviderHook;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DeepSeek 特化 hook 在 OpenAI Chat Completions 协议下的端到端效果：
 * 客户端 /v1/chat/completions 上来的请求，经历 OpenAiRequestAdapter.toUnified → DeepSeekHook.preProcess
 * → OpenAiRequestAdapter.toProviderRequest 后，assistant 消息必须带上 {@code reasoning_content}
 * 字段（即使为空字符串），否则 DeepSeek Chat 上游会返回 400。
 * <p>
 * 重构前这个能力写在 {@code DeepSeekProviderClient.beforeChatRequest}，重构后迁出到
 * {@link DeepSeekHook}，由 {@code ChatGatewayService} 在路由阶段按 ProviderType 触发。
 * </p>
 */
class DeepSeekChatProviderClientTests {

    private final OpenAiRequestAdapter requestAdapter = new OpenAiRequestAdapter();
    private final ProviderHook hook = new DeepSeekHook();

    @Test
    void deepSeekChatAssistantMessagesAlwaysCarryReasoningContentField() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("deepseek-v4-flash");
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setContent("previous answer");
        OpenAiMessage toolCallAssistant = new OpenAiMessage();
        toolCallAssistant.setRole("assistant");
        toolCallAssistant.setToolCalls(List.of(Map.of(
                "id", "call_1", "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant, toolCallAssistant));

        OpenAiChatCompletionRequest prepared = invokeHook(request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("");
        assertThat(prepared.getMessages().get(1).getReasoningContent()).isEqualTo("");
    }

    @Test
    void deepSeekChatKeepsExistingReasoningContent() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("deepseek-v4-flash");
        OpenAiMessage assistant = new OpenAiMessage();
        assistant.setRole("assistant");
        assistant.setReasoningContent("real thinking");
        assistant.setToolCalls(List.of(Map.of(
                "id", "call_1", "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{}"))));
        request.setMessages(List.of(assistant));

        OpenAiChatCompletionRequest prepared = invokeHook(request);

        assertThat(prepared.getMessages().getFirst().getReasoningContent()).isEqualTo("real thinking");
    }

    /**
     * 端到端跑一遍 Chat Completions 的 hook 链路：provider DTO → unified → DeepSeekHook → provider DTO。
     */
    private OpenAiChatCompletionRequest invokeHook(OpenAiChatCompletionRequest request) {
        UnifiedChatRequest unified = requestAdapter.toUnified(request);
        UnifiedChatRequest hooked = hook.preProcess(unified, EndpointType.CHAT_COMPLETIONS, deepSeekRoute());
        return requestAdapter.toProviderRequest(hooked, "deepseek-v4-flash");
    }

    private ModelRoute deepSeekRoute() {
        return new ModelRoute(
                "deepseek-v4-flash", "deepseek", ProviderType.DEEPSEEK, "deepseek-v4-flash",
                "https://api.deepseek.com", "/v1/chat/completions", "sk-test",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
