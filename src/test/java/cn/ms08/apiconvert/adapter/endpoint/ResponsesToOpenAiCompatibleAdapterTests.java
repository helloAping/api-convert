package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesToOpenAiCompatibleAdapterTests {

    private final OpenAiResponsesResponseAdapter responseAdapter = new OpenAiResponsesResponseAdapter();
    private final ResponsesToOpenAiCompatibleAdapter adapter = new ResponsesToOpenAiCompatibleAdapter(responseAdapter);
    private final OpenAiRequestAdapter openAiRequestAdapter = new OpenAiRequestAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void mapsResponsesRequestToChatCompletionsUpstream() {
        Map<String, Object> rawOptions = new LinkedHashMap<>();
        rawOptions.put("instructions", "Be concise.");
        rawOptions.put("reasoning", Map.of("effort", "high"));
        rawOptions.put("previous_response_id", "resp_prev");
        rawOptions.put("tools", List.of(
                Map.of("type", "function", "name", "lookup", "parameters", Map.of("type", "object")),
                Map.of("type", "web_search_preview")));
        rawOptions.put("tool_choice", Map.of("type", "function", "name", "lookup"));

        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "public-model",
                List.of(
                        new UnifiedMessage("user", List.of(Map.of("type", "input_text", "text", "hello")), null),
                        new UnifiedMessage("tool", "tool result", null, null, Map.of("tool_call_id", "call_1"))
                ),
                false,
                null,
                256,
                null,
                rawOptions
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(responsesRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "provider-model");

        assertThat(providerRequest.getMessages()).hasSize(3);
        assertThat(providerRequest.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(providerRequest.getMessages().get(1).getContent()).isEqualTo("hello");
        assertThat(providerRequest.getMessages().get(2).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(2).getToolCallId()).isEqualTo("call_1");

        assertThat(providerRequest.getAdditionalProperties()).doesNotContainKeys(
                "instructions", "reasoning", "previous_response_id");
        assertThat(providerRequest.getAdditionalProperties()).containsEntry("reasoning_effort", "high");

        List<Map<String, Object>> tools = (List<Map<String, Object>>) providerRequest.getAdditionalProperties().get("tools");
        assertThat(tools).hasSize(1);
        assertThat((Map<String, Object>) tools.getFirst().get("function")).containsEntry("name", "lookup");

        Map<String, Object> toolChoice = (Map<String, Object>) providerRequest.getAdditionalProperties().get("tool_choice");
        assertThat((Map<String, Object>) toolChoice.get("function")).containsEntry("name", "lookup");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsChatToolCallsBackToResponsesFunctionCallItems() {
        OpenAiMessage message = new OpenAiMessage();
        message.setRole("assistant");
        message.setContent("I will call a tool.");
        message.setToolCalls(List.of(Map.of(
                "id", "call_1",
                "type", "function",
                "function", Map.of("name", "lookup", "arguments", "{\"q\":\"abc\"}"))));

        UnifiedChatResponse upstreamResponse = new UnifiedChatResponse(
                "chatcmpl-test",
                "provider-model",
                List.of(new UnifiedMessage("assistant", "I will call a tool.", null, "tool_calls",
                        Map.of("tool_calls", message.getToolCalls()))),
                null,
                null
        );

        UnifiedChatResponse adapted = adapter.adaptResponse(upstreamResponse, "public-model");
        OpenAiResponsesResponse raw = (OpenAiResponsesResponse) adapted.rawResponse();

        assertThat(raw.getOutput()).hasSize(2);
        Map<String, Object> textItem = (Map<String, Object>) raw.getOutput().get(0);
        assertThat(textItem).containsEntry("type", "message");
        Map<String, Object> callItem = (Map<String, Object>) raw.getOutput().get(1);
        assertThat(callItem).containsEntry("type", "function_call")
                .containsEntry("call_id", "call_1")
                .containsEntry("name", "lookup")
                .containsEntry("arguments", "{\"q\":\"abc\"}");
    }
}
