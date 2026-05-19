package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesToAnthropicAdapterTests {

    private final ResponsesToAnthropicAdapter adapter =
            new ResponsesToAnthropicAdapter(new OpenAiResponsesResponseAdapter());
    private final AnthropicRequestAdapter anthropicRequestAdapter = new AnthropicRequestAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void mapsResponsesRequestToAnthropicMessagesUpstream() {
        Map<String, Object> rawOptions = new LinkedHashMap<>();
        rawOptions.put("instructions", "Be concise.");
        rawOptions.put("reasoning", Map.of("effort", "high"));
        rawOptions.put("tools", List.of(Map.of(
                "type", "function",
                "name", "lookup",
                "parameters", Map.of("type", "object"))));
        rawOptions.put("tool_choice", Map.of("type", "function", "name", "lookup"));

        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "public-model",
                List.of(
                        new UnifiedMessage("user", List.of(Map.of("type", "input_text", "text", "hello")), null),
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(Map.of(
                                        "id", "call_1",
                                        "type", "function",
                                        "function", Map.of("name", "lookup", "arguments", "{\"q\":\"abc\"}"))))),
                        new UnifiedMessage("tool", "result", null, null, Map.of("tool_call_id", "call_1"))
                ),
                false,
                null,
                null,
                null,
                rawOptions
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(responsesRequest);
        AnthropicMessageRequest providerRequest = anthropicRequestAdapter.toProviderRequest(adapted, "claude-provider");

        assertThat(providerRequest.getSystem()).isEqualTo("Be concise.");
        assertThat(providerRequest.getMaxTokens()).isEqualTo(4096);
        assertThat(providerRequest.getAdditionalProperties()).doesNotContainKeys("instructions", "reasoning");

        List<Map<String, Object>> tools =
                (List<Map<String, Object>>) providerRequest.getAdditionalProperties().get("tools");
        assertThat(tools.getFirst()).containsEntry("name", "lookup")
                .containsKey("input_schema");

        Map<String, Object> toolChoice =
                (Map<String, Object>) providerRequest.getAdditionalProperties().get("tool_choice");
        assertThat(toolChoice).containsEntry("type", "tool")
                .containsEntry("name", "lookup");

        assertThat(providerRequest.getMessages()).hasSize(3);
        assertThat(providerRequest.getMessages().get(0).getContent()).isEqualTo("hello");
        List<Object> toolUseContent = (List<Object>) providerRequest.getMessages().get(1).getContent();
        assertThat((Map<String, Object>) toolUseContent.getFirst()).containsEntry("type", "tool_use")
                .containsEntry("id", "call_1")
                .containsEntry("name", "lookup");
        List<Object> toolResultContent = (List<Object>) providerRequest.getMessages().get(2).getContent();
        assertThat((Map<String, Object>) toolResultContent.getFirst()).containsEntry("type", "tool_result")
                .containsEntry("tool_use_id", "call_1")
                .containsEntry("content", "result");
    }

    /**
     * Responses API 将每个 function_call 输入项转为独立的 assistant 消息，
     * Anthropic 要求所有 tool_use 在同一条 assistant 消息中。验证合并逻辑。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mergesConsecutiveToolCallsIntoOneAssistant() {
        Map<String, Object> rawOptions = new LinkedHashMap<>();
        rawOptions.put("instructions", "Be concise.");
        rawOptions.put("tool_choice", "required");

        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "public-model",
                List.of(
                        new UnifiedMessage("user", "hi", null),
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(Map.of(
                                        "id", "call_00", "type", "function",
                                        "function", Map.of("name", "tool_a", "arguments", "{}"))))),
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(Map.of(
                                        "id", "call_01", "type", "function",
                                        "function", Map.of("name", "tool_b", "arguments", "{}"))))),
                        new UnifiedMessage("tool", "result_a", null, null, Map.of("tool_call_id", "call_00")),
                        new UnifiedMessage("tool", "result_b", null, null, Map.of("tool_call_id", "call_01"))
                ),
                false,
                null,
                null,
                null,
                rawOptions
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(responsesRequest);
        AnthropicMessageRequest providerRequest = anthropicRequestAdapter.toProviderRequest(adapted, "claude-provider");

        // 3 条消息：user + assistant(合并后的 tool_use) + user(合并后的 tool_result)
        assertThat(providerRequest.getMessages()).hasSize(3);

        // assistant 消息应包含两个 tool_use 块
        List<Object> assistantContent = (List<Object>) providerRequest.getMessages().get(1).getContent();
        assertThat(assistantContent).hasSize(2);
        assertThat((Map<String, Object>) assistantContent.get(0)).containsEntry("type", "tool_use")
                .containsEntry("id", "call_00").containsEntry("name", "tool_a");
        assertThat((Map<String, Object>) assistantContent.get(1)).containsEntry("type", "tool_use")
                .containsEntry("id", "call_01").containsEntry("name", "tool_b");

        // tool_result 消息应包含两个 tool_result 块
        List<Object> toolResultContent = (List<Object>) providerRequest.getMessages().get(2).getContent();
        assertThat(toolResultContent).hasSize(2);
        assertThat((Map<String, Object>) toolResultContent.get(0)).containsEntry("type", "tool_result")
                .containsEntry("tool_use_id", "call_00").containsEntry("content", "result_a");
        assertThat((Map<String, Object>) toolResultContent.get(1)).containsEntry("type", "tool_result")
                .containsEntry("tool_use_id", "call_01").containsEntry("content", "result_b");
    }
}
