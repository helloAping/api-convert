package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
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
    private final ResponsesToDeepSeekChatAdapter deepSeekAdapter = new ResponsesToDeepSeekChatAdapter(responseAdapter);
    private final ChatCompletionsToDeepSeekChatAdapter chatDeepSeekAdapter =
            new ChatCompletionsToDeepSeekChatAdapter(new OpenAiResponseAdapter());
    private final OpenAiRequestAdapter openAiRequestAdapter = new OpenAiRequestAdapter();
    private final OpenAiResponsesRequestAdapter responsesRequestAdapter = new OpenAiResponsesRequestAdapter();

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
        // reasoning_effort 已转为显式字段
        assertThat(providerRequest.getReasoningEffort()).isEqualTo("high");

        List<Map<String, Object>> tools = providerRequest.getTools();
        assertThat(tools).hasSize(1);
        assertThat((Map<String, Object>) tools.getFirst().get("function")).containsEntry("name", "lookup");

        assertThat(providerRequest.getToolChoice()).isInstanceOfSatisfying(Map.class, tc -> {
            assertThat((Map<String, Object>) tc).containsEntry("type", "function");
            assertThat((Map<String, Object>) ((Map<?, ?>) tc).get("function")).containsEntry("name", "lookup");
        });
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

    /**
     * DeepSeek thinking 模式：assistant 消息内容包含 reasoning 类型内容块时，
     * 应提取为 reasoning_content 选项字段，而不是被 normalizeContentForChat 转为 text。
     */
    @Test
    void extractsReasoningContentFromAssistantMessage() {
        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "deepseek-model",
                List.of(
                        new UnifiedMessage("user", "hello", null),
                        new UnifiedMessage("assistant", List.of(
                                Map.of("type", "reasoning", "text", "thinking about the answer"),
                                Map.of("type", "output_text", "text", "Here is my answer.")
                        ), null)
                ),
                false, null, null, null, new LinkedHashMap<>()
        );

        UnifiedChatRequest adapted = deepSeekAdapter.adaptRequest(responsesRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "deepseek-model");

        // 2 messages: user + assistant
        assertThat(providerRequest.getMessages()).hasSize(2);
        OpenAiMessage assistantMsg = providerRequest.getMessages().get(1);
        assertThat(assistantMsg.getRole()).isEqualTo("assistant");
        // 内容应为输出文本，不含 reasoning
        assertThat(assistantMsg.getContent()).isEqualTo("Here is my answer.");
        // reasoning_content 应提取为独立字段
        assertThat(assistantMsg.getReasoningContent()).isEqualTo("thinking about the answer");
    }

    /**
     * DeepSeek thinking 模式：assistant 消息内容不含 reasoning 时，reasoning_content 应为空。
     */
    @Test
    void doesNotSetReasoningContentWhenNotPresent() {
        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "deepseek-model",
                List.of(
                        new UnifiedMessage("assistant", List.of(
                                Map.of("type", "output_text", "text", "Just a normal response.")
                        ), null)
                ),
                false, null, null, null, new LinkedHashMap<>()
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(responsesRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "deepseek-model");

        OpenAiMessage assistantMsg = providerRequest.getMessages().get(0);
        assertThat(assistantMsg.getContent()).isEqualTo("Just a normal response.");
        assertThat(assistantMsg.getReasoningContent()).isNull();
    }

    /**
     * Codex 会把 Responses 流里的 reasoning item 和 function_call item 分开回传；
     * DeepSeek Chat thinking 模式要求二者合并成带 reasoning_content 的 assistant tool_calls。
     */
    @Test
    void attachesStandaloneReasoningItemToFollowingFunctionCall() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("deepseek-model");
        request.setInput(List.of(
                Map.of("id", "rs_1", "type", "reasoning", "summary",
                        List.of(Map.of("text", "thinking before tool"))),
                Map.of("id", "fc_1", "type", "function_call", "call_id", "call_1",
                        "name", "lookup", "arguments", "{\"q\":\"abc\"}"),
                Map.of("type", "function_call_output", "call_id", "call_1", "output", "tool result")
        ));

        UnifiedChatRequest unified = responsesRequestAdapter.toUnified(request);
        UnifiedChatRequest adapted = deepSeekAdapter.adaptRequest(unified);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "deepseek-model");

        OpenAiMessage assistantMsg = providerRequest.getMessages().get(0);
        assertThat(assistantMsg.getRole()).isEqualTo("assistant");
        assertThat(assistantMsg.getContent()).isNull();
        assertThat(assistantMsg.getToolCalls()).hasSize(1);
        assertThat(assistantMsg.getReasoningContent()).isEqualTo("thinking before tool");
        assertThat(providerRequest.getMessages().get(1).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(1).getToolCallId()).isEqualTo("call_1");
    }

    /**
     * Responses API 可能在 tool_calls 和 function_call_output 之间包含普通文本消息，
     * Chat Completions 要求 tool 结果紧跟在 tool_calls 之后。验证 reorder 逻辑将
     * 中间的非 tool 消息移到 tool 块之后。
     */
    @Test
    void reordersMessagesBetweenToolCallsAndToolResults() {
        Map<String, Object> rawOptions = new LinkedHashMap<>();
        rawOptions.put("instructions", "Be concise.");
        rawOptions.put("tool_choice", "required");

        // 模拟 Responses API 输入：function_call ×2 → 文本 → function_call_output ×2
        UnifiedChatRequest responsesRequest = new UnifiedChatRequest(
                "public-model",
                List.of(
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(Map.of("id", "call_00", "type", "function",
                                        "function", Map.of("name", "a", "arguments", "{}"))))),
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(Map.of("id", "call_01", "type", "function",
                                        "function", Map.of("name", "b", "arguments", "{}"))))),
                        // 文本消息插在 tool_calls 和 tool 结果之间
                        new UnifiedMessage("assistant", "I will process the results.", null),
                        new UnifiedMessage("tool", "result_a", null, null, Map.of("tool_call_id", "call_00")),
                        new UnifiedMessage("tool", "result_b", null, null, Map.of("tool_call_id", "call_01"))
                ),
                false,
                null,
                256,
                null,
                rawOptions
        );

        UnifiedChatRequest adapted = adapter.adaptRequest(responsesRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "provider-model");

        // 第一条应为 instructions → system
        assertThat(providerRequest.getMessages().get(0).getRole()).isEqualTo("system");
        // 第二条应为合并后的 assistant（含两个 tool_calls）
        assertThat(providerRequest.getMessages().get(1).getRole()).isEqualTo("assistant");
        assertThat(providerRequest.getMessages().get(1).getToolCalls()).hasSize(2);
        // 第三、四条应为 tool 结果
        assertThat(providerRequest.getMessages().get(2).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(2).getToolCallId()).isEqualTo("call_00");
        assertThat(providerRequest.getMessages().get(2).getContent()).isEqualTo("result_a");
        assertThat(providerRequest.getMessages().get(3).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(3).getToolCallId()).isEqualTo("call_01");
        assertThat(providerRequest.getMessages().get(3).getContent()).isEqualTo("result_b");
        // 文本消息应被移到 tool 块之后
        assertThat(providerRequest.getMessages().get(4).getRole()).isEqualTo("assistant");
        assertThat(providerRequest.getMessages().get(4).getContent()).isEqualTo("I will process the results.");
    }

    /**
     * DeepSeek Chat 比部分兼容上游更严格：assistant 的每个 tool_call 都必须紧跟对应 tool 结果。
     * 当客户端历史被截断导致只有部分结果时，只保留可配对调用，避免整次请求被 400 拒绝。
     */
    @Test
    void deepSeekResponsesAdapterTrimsUnansweredToolCalls() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("deepseek-model");
        request.setInput(List.of(
                Map.of("id", "fc_1", "type", "function_call", "call_id", "call_1",
                        "name", "lookupA", "arguments", "{}"),
                Map.of("id", "fc_2", "type", "function_call", "call_id", "call_2",
                        "name", "lookupB", "arguments", "{}"),
                Map.of("role", "assistant", "content", "intervening text"),
                Map.of("type", "function_call_output", "call_id", "call_1", "output", "result_a")
        ));

        UnifiedChatRequest unified = responsesRequestAdapter.toUnified(request);
        UnifiedChatRequest adapted = deepSeekAdapter.adaptRequest(unified);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "deepseek-model");

        OpenAiMessage assistantMsg = providerRequest.getMessages().get(0);
        assertThat(assistantMsg.getRole()).isEqualTo("assistant");
        assertThat(assistantMsg.getToolCalls()).hasSize(1);
        assertThat(assistantMsg.getToolCalls().getFirst()).containsEntry("id", "call_1");
        assertThat(providerRequest.getMessages().get(1).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(1).getToolCallId()).isEqualTo("call_1");
        assertThat(providerRequest.getMessages().get(2).getContent()).isEqualTo("intervening text");
    }

    /**
     * OpenAI Chat 入口直连 DeepSeek 时也要修复历史序列，不能只覆盖 Responses 入口。
     */
    @Test
    void deepSeekChatAdapterRepairsDirectChatToolSequence() {
        UnifiedChatRequest chatRequest = new UnifiedChatRequest(
                "deepseek-model",
                List.of(
                        new UnifiedMessage("assistant", null, null, "tool_calls",
                                Map.of("tool_calls", List.of(
                                        Map.of("id", "call_1", "type", "function",
                                                "function", Map.of("name", "lookupA", "arguments", "{}")),
                                        Map.of("id", "call_2", "type", "function",
                                                "function", Map.of("name", "lookupB", "arguments", "{}"))
                                ))),
                        new UnifiedMessage("assistant", "text between call and result", null),
                        new UnifiedMessage("tool", "result_a", null, null, Map.of("tool_call_id", "call_1"))
                ),
                true,
                null,
                256,
                null,
                new LinkedHashMap<>()
        );

        UnifiedChatRequest adapted = chatDeepSeekAdapter.adaptRequest(chatRequest);
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(adapted, "deepseek-model", true);

        assertThat(providerRequest.getMessages()).hasSize(3);
        assertThat(providerRequest.getMessages().get(0).getToolCalls()).hasSize(1);
        assertThat(providerRequest.getMessages().get(0).getToolCalls().getFirst()).containsEntry("id", "call_1");
        assertThat(providerRequest.getMessages().get(1).getRole()).isEqualTo("tool");
        assertThat(providerRequest.getMessages().get(1).getToolCallId()).isEqualTo("call_1");
        assertThat(providerRequest.getMessages().get(2).getContent()).isEqualTo("text between call and result");
    }
}
