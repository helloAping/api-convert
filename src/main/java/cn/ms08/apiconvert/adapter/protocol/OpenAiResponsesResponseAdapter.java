package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter between OpenAI Responses API responses and the gateway unified response.
 */
@Component
public class OpenAiResponsesResponseAdapter {

    public UnifiedChatResponse toUnified(OpenAiResponsesResponse response) {
        UnifiedUsage usage = null;
        if (response.getUsage() != null) {
            usage = new UnifiedUsage(
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens(),
                    response.getUsage().getTotalTokens(),
                    null
            );
        }
        List<UnifiedMessage> messages = new ArrayList<>();
        if (response.getOutput() != null) {
            for (Object item : response.getOutput()) {
                if (item instanceof Map<?, ?> map) {
                    addOutputItem(messages, map);
                } else {
                    messages.add(new UnifiedMessage("assistant", item, null));
                }
            }
        }
        return new UnifiedChatResponse(response.getId(), response.getModel(), messages, usage, response);
    }

    public OpenAiResponsesResponse toOpenAiResponses(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiResponsesResponse responsesResponse) {
            responsesResponse.setModel(publicModel);
            return responsesResponse;
        }
        OpenAiResponsesResponse result = new OpenAiResponsesResponse();
        result.setId(response.id() == null ? "resp_" + UUID.randomUUID() : response.id());
        result.setObjectValue("response");
        result.setStatus("completed");
        result.setModel(publicModel);
        result.setCreatedAt(System.currentTimeMillis() / 1000);

        List<Object> output = new ArrayList<>();
        if (response.messages() != null) {
            for (UnifiedMessage message : response.messages()) {
                if (!"assistant".equals(message.role())) {
                    continue;
                }
                addMessageOutput(output, message);
                addToolCallOutput(output, message.options());
            }
        }
        if (output.isEmpty()) {
            output.add(messageItem(List.of(outputText(""))));
        }
        result.setOutput(output);

        if (response.usage() != null) {
            OpenAiResponsesResponse.Usage usage = new OpenAiResponsesResponse.Usage();
            usage.setInputTokens(response.usage().inputTokens());
            usage.setOutputTokens(response.usage().outputTokens());
            usage.setTotalTokens(response.usage().totalTokens());
            result.setUsage(usage);
        }
        return result;
    }

    private void addOutputItem(List<UnifiedMessage> messages, Map<?, ?> map) {
        String type = map.get("type") == null ? "message" : String.valueOf(map.get("type"));
        if ("message".equals(type)) {
            String role = map.containsKey("role") ? String.valueOf(map.get("role")) : "assistant";
            messages.add(new UnifiedMessage(role, map.get("content"), null));
            return;
        }
        if ("function_call".equals(type)) {
            messages.add(new UnifiedMessage("assistant", null, null, "tool_calls",
                    Map.of("tool_calls", List.of(toOpenAiToolCall(map)))));
            return;
        }
        messages.add(new UnifiedMessage("assistant", map, null));
    }

    private void addMessageOutput(List<Object> output, UnifiedMessage message) {
        List<Map<String, Object>> content = new ArrayList<>();
        addContentParts(output, content, message.content());
        if (!content.isEmpty()) {
            output.add(messageItem(content));
        }
    }

    @SuppressWarnings("unchecked")
    private void addToolCallOutput(List<Object> output, Map<String, Object> options) {
        if (options == null || !(options.get("tool_calls") instanceof List<?> toolCalls)) {
            return;
        }
        for (Object toolCall : toolCalls) {
            if (toolCall instanceof Map<?, ?> toolCallMap) {
                output.add(functionCallItem(toolCallMap));
            }
        }
    }

    private void addContentParts(List<Object> output, List<Map<String, Object>> content, Object messageContent) {
        if (messageContent == null) {
            return;
        }
        if (messageContent instanceof List<?> list) {
            for (Object part : list) {
                if (part instanceof Map<?, ?> partMap) {
                    String type = partMap.get("type") == null ? "text" : String.valueOf(partMap.get("type"));
                    if ("tool_use".equals(type)) {
                        output.add(functionCallItemFromAnthropicBlock(partMap));
                    } else if ("text".equals(type) || "output_text".equals(type) || "input_text".equals(type)
                            || "thinking".equals(type) || "reasoning".equals(type)) {
                        Object text = partMap.get("text") != null ? partMap.get("text") : partMap.get("thinking");
                        content.add(outputText(text == null ? "" : String.valueOf(text)));
                    }
                } else if (part != null) {
                    content.add(outputText(String.valueOf(part)));
                }
            }
            return;
        }
        String text = String.valueOf(messageContent);
        if (StringUtils.hasText(text)) {
            content.add(outputText(text));
        }
    }

    private Map<String, Object> messageItem(List<Map<String, Object>> content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "item_" + UUID.randomUUID().toString().replace("-", ""));
        item.put("object", "item");
        item.put("type", "message");
        item.put("role", "assistant");
        item.put("status", "completed");
        item.put("content", content);
        return item;
    }

    private Map<String, Object> outputText(String text) {
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "output_text");
        textContent.put("text", text);
        textContent.put("annotations", List.of());
        return textContent;
    }

    private Map<String, Object> functionCallItem(Map<?, ?> toolCall) {
        Map<?, ?> function = toolCall.get("function") instanceof Map<?, ?> map ? map : Map.of();
        String id = toolCall.get("id") != null ? String.valueOf(toolCall.get("id"))
                : "call_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "fc_" + UUID.randomUUID().toString().replace("-", ""));
        item.put("type", "function_call");
        item.put("status", "completed");
        item.put("call_id", id);
        item.put("name", function.get("name") != null ? String.valueOf(function.get("name")) : "");
        item.put("arguments", function.get("arguments") != null ? String.valueOf(function.get("arguments")) : "{}");
        return item;
    }

    private Map<String, Object> functionCallItemFromAnthropicBlock(Map<?, ?> block) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "fc_" + UUID.randomUUID().toString().replace("-", ""));
        item.put("type", "function_call");
        item.put("status", "completed");
        item.put("call_id", block.get("id") != null ? String.valueOf(block.get("id")) : "");
        item.put("name", block.get("name") != null ? String.valueOf(block.get("name")) : "");
        item.put("arguments", toJsonString(block.get("input")));
        return item;
    }

    private Map<String, Object> toOpenAiToolCall(Map<?, ?> item) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", item.get("name") != null ? String.valueOf(item.get("name")) : "");
        function.put("arguments", item.get("arguments") != null ? String.valueOf(item.get("arguments")) : "{}");
        Map<String, Object> toolCall = new LinkedHashMap<>();
        Object callId = item.get("call_id") != null ? item.get("call_id") : item.get("id");
        toolCall.put("id", callId != null ? String.valueOf(callId) : "call_" + UUID.randomUUID().toString().replace("-", ""));
        toolCall.put("type", "function");
        toolCall.put("function", function);
        return toolCall;
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }
}
