package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicToOpenAiCompatibleAdapterTests {

    private final AnthropicToOpenAiCompatibleAdapter adapter = new AnthropicToOpenAiCompatibleAdapter();
    private final OpenAiRequestAdapter openAiRequestAdapter = new OpenAiRequestAdapter();

    /**
     * /v1/messages 映射到 /chat 上游时，需要把 Anthropic 工具协议转换为 OpenAI Chat 工具协议。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mapsAnthropicToolRequestToOpenAiChatRequest() {
        Map<String, Object> rawOptions = new LinkedHashMap<>();
        rawOptions.put("system", "You are helpful.");
        rawOptions.put("stop_sequences", List.of("</stop>"));
        rawOptions.put("metadata", Map.of("ignored", true));
        rawOptions.put("tools", List.of(Map.of(
                "name", "lookup",
                "description", "Lookup data",
                "input_schema", Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))))));
        rawOptions.put("tool_choice", Map.of("type", "tool", "name", "lookup"));

        UnifiedChatRequest anthropicRequest = new UnifiedChatRequest(
                "public-model",
                List.of(
                        new UnifiedMessage("assistant", List.of(
                                Map.of("type", "text", "text", "I need a lookup."),
                                Map.of("type", "thinking", "thinking", "reasoning"),
                                Map.of("type", "tool_use", "id", "toolu_1", "name", "lookup",
                                        "input", Map.of("q", "abc"))), null),
                        new UnifiedMessage("user", List.of(
                                Map.of("type", "tool_result", "tool_use_id", "toolu_1", "content", "result")), null)
                ),
                false,
                null,
                128,
                null,
                rawOptions
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(anthropicRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "provider-model");

        assertThat(providerRequest.getAdditionalProperties()).doesNotContainKeys("system", "metadata", "stop_sequences");
        assertThat(providerRequest.getAdditionalProperties()).containsEntry("stop", List.of("</stop>"));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) providerRequest.getAdditionalProperties().get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst()).containsEntry("type", "function");
        assertThat((Map<String, Object>) tools.getFirst().get("function")).containsEntry("name", "lookup");

        Map<String, Object> toolChoice = (Map<String, Object>) providerRequest.getAdditionalProperties().get("tool_choice");
        assertThat(toolChoice).containsEntry("type", "function");
        assertThat((Map<String, Object>) toolChoice.get("function")).containsEntry("name", "lookup");

        assertThat(providerRequest.getMessages()).hasSize(3);
        assertThat(providerRequest.getMessages().get(0).getRole()).isEqualTo("system");

        OpenAiMessage assistant = providerRequest.getMessages().get(1);
        assertThat(assistant.getRole()).isEqualTo("assistant");
        assertThat(assistant.getReasoningContent()).isEqualTo("reasoning");
        assertThat(assistant.getToolCalls()).hasSize(1);
        assertThat(assistant.getToolCalls().getFirst()).containsEntry("id", "toolu_1");

        OpenAiMessage tool = providerRequest.getMessages().get(2);
        assertThat(tool.getRole()).isEqualTo("tool");
        assertThat(tool.getToolCallId()).isEqualTo("toolu_1");
        assertThat(tool.getContent()).isEqualTo("result");
    }

    /**
     * /chat 上游返回 tool_calls 时，/v1/messages 客户端需要收到 Anthropic tool_use 内容块。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mapsOpenAiToolCallsBackToAnthropicToolUseBlocks() {
        UnifiedChatResponse upstreamResponse = new UnifiedChatResponse(
                "chatcmpl-test",
                "provider-model",
                List.of(new UnifiedMessage("assistant", "I will use a tool.", null, "tool_calls",
                        Map.of("tool_calls", List.of(Map.of(
                                "id", "call_1",
                                "type", "function",
                                "function", Map.of("name", "lookup", "arguments", "{\"q\":\"abc\"}")))))),
                null,
                null
        );

        UnifiedChatResponse adapted = adapter.adaptResponse(upstreamResponse, "public-model");
        AnthropicMessageResponse raw = (AnthropicMessageResponse) adapted.rawResponse();

        assertThat(raw.getStopReason()).isEqualTo("tool_use");
        assertThat(raw.getContent()).hasSize(2);
        assertThat((Map<String, Object>) raw.getContent().get(0)).containsEntry("type", "text");
        Map<String, Object> toolUse = (Map<String, Object>) raw.getContent().get(1);
        assertThat(toolUse).containsEntry("type", "tool_use")
                .containsEntry("id", "call_1")
                .containsEntry("name", "lookup");
        assertThat((Map<String, Object>) toolUse.get("input")).containsEntry("q", "abc");
    }
}
