package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponsesRequestAdapterTests {

    private final OpenAiResponsesRequestAdapter adapter = new OpenAiResponsesRequestAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void preservesResponsesNativeOptionsForNativeUpstream() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-5.1");
        request.setInput("hello");
        request.setInstructions("Be concise.");
        request.setTools(List.of(Map.of(
                "type", "function",
                "name", "lookup",
                "description", "Lookup data",
                "parameters", Map.of("type", "object"))));
        request.setReasoning(Map.of("effort", "high"));
        request.setPreviousResponseId("resp_prev");

        UnifiedChatRequest unified = adapter.toUnified(request);

        assertThat(unified.messages()).hasSize(1);
        assertThat(unified.messages().getFirst().role()).isEqualTo("user");
        assertThat(unified.rawOptions()).containsEntry("instructions", "Be concise.")
                .containsEntry("reasoning", Map.of("effort", "high"))
                .containsEntry("previous_response_id", "resp_prev");

        List<Map<String, Object>> tools = (List<Map<String, Object>>) unified.rawOptions().get("tools");
        assertThat(tools.getFirst()).containsEntry("name", "lookup");
        assertThat(tools.getFirst()).doesNotContainKey("function");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsResponsesFunctionCallItemsToUnifiedToolMessagesAndBack() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-5.1");
        request.setInput(List.of(
                Map.of("type", "function_call", "call_id", "call_1", "name", "lookup", "arguments", "{\"q\":\"abc\"}"),
                Map.of("type", "function_call_output", "call_id", "call_1", "output", "result")
        ));

        UnifiedChatRequest unified = adapter.toUnified(request);

        assertThat(unified.messages()).hasSize(2);
        assertThat(unified.messages().get(0).options()).containsKey("tool_calls");
        assertThat(unified.messages().get(1).role()).isEqualTo("tool");
        assertThat(unified.messages().get(1).options()).containsEntry("tool_call_id", "call_1");

        OpenAiResponsesRequest roundTrip = adapter.toProviderRequest(unified, "gpt-5.1", false);
        List<Map<String, Object>> input = (List<Map<String, Object>>) roundTrip.getInput();
        assertThat(input.get(0)).containsEntry("type", "function_call")
                .containsEntry("call_id", "call_1")
                .containsEntry("name", "lookup");
        assertThat(input.get(1)).containsEntry("type", "function_call_output")
                .containsEntry("call_id", "call_1")
                .containsEntry("output", "result");
    }
}
