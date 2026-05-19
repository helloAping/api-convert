package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter between OpenAI Responses API requests and the gateway unified request.
 */
@Component
public class OpenAiResponsesRequestAdapter {

    public static final java.util.Set<String> CHAT_COMPLETION_CONTENT_TYPES =
            java.util.Set.of("text", "image_url", "video_url");

    /**
     * Convert a Responses API request into the unified model.
     *
     * Keep Responses-native options in rawOptions. Provider-specific cleanup is done by
     * EndpointProviderAdapter implementations after routing, otherwise native Responses
     * upstreams would receive Chat-Completions-shaped parameters.
     */
    public UnifiedChatRequest toUnified(OpenAiResponsesRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (request.getInput() == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input is required");
        }

        Map<String, Object> rawOptions = new LinkedHashMap<>(request.getAdditionalProperties());
        if (StringUtils.hasText(request.getInstructions())) rawOptions.put("instructions", request.getInstructions());
        if (request.getTopP() != null) rawOptions.put("top_p", request.getTopP());
        if (request.getToolChoice() != null) rawOptions.put("tool_choice", request.getToolChoice());
        if (request.getTools() != null) rawOptions.put("tools", request.getTools());
        if (request.getReasoning() != null) rawOptions.put("reasoning", request.getReasoning());
        if (request.getPreviousResponseId() != null) rawOptions.put("previous_response_id", request.getPreviousResponseId());
        if (request.getTruncation() != null) rawOptions.put("truncation", request.getTruncation());

        return new UnifiedChatRequest(
                request.getModel(),
                convertInput(request.getInput()),
                request.getStream(),
                request.getTemperature(),
                request.getMaxOutputTokens(),
                null,
                rawOptions
        );
    }

    @SuppressWarnings("unchecked")
    public OpenAiResponsesRequest toProviderRequest(UnifiedChatRequest request, String providerModel, boolean stream) {
        OpenAiResponsesRequest providerRequest = new OpenAiResponsesRequest();
        providerRequest.setModel(providerModel);
        providerRequest.setInput(messagesToInput(request.messages()));
        providerRequest.setStream(stream);
        providerRequest.setTemperature(request.temperature());
        providerRequest.setMaxOutputTokens(request.maxTokens());
        if (request.rawOptions() != null) {
            request.rawOptions().forEach((key, value) -> {
                if ("instructions".equals(key)) {
                    providerRequest.setInstructions(value instanceof String s ? s : String.valueOf(value));
                } else if ("top_p".equals(key) && value instanceof Number n) {
                    providerRequest.setTopP(n.doubleValue());
                } else if ("tool_choice".equals(key)) {
                    providerRequest.setToolChoice(value);
                } else if ("tools".equals(key) && value instanceof List<?> tl) {
                    providerRequest.setTools((List<Map<String, Object>>) tl);
                } else if ("reasoning".equals(key) && value instanceof Map<?, ?> rm) {
                    providerRequest.setReasoning((Map<String, Object>) rm);
                } else if ("previous_response_id".equals(key) && value instanceof String pr) {
                    providerRequest.setPreviousResponseId(pr);
                } else if ("truncation".equals(key) && value instanceof String tr) {
                    providerRequest.setTruncation(tr);
                } else if (!"response_format".equals(key)) {
                    providerRequest.setAdditionalProperty(key, value);
                }
            });
        }
        return providerRequest;
    }

    private List<UnifiedMessage> convertInput(Object input) {
        if (input instanceof String text) {
            return List.of(new UnifiedMessage("user", text, null));
        }
        if (input instanceof List<?> list) {
            List<UnifiedMessage> messages = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    messages.add(toUnifiedInputItem(map));
                } else {
                    messages.add(new UnifiedMessage("user", item, null));
                }
            }
            return messages;
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input must be a string or array");
    }

    private UnifiedMessage toUnifiedInputItem(Map<?, ?> map) {
        String type = map.get("type") == null ? null : String.valueOf(map.get("type"));
        if ("reasoning".equals(type)) {
            return new UnifiedMessage("assistant",
                    List.of(Map.of("type", "reasoning", "text", extractReasoningText(map))), null);
        }
        if ("function_call_output".equals(type)) {
            Map<String, Object> options = new LinkedHashMap<>();
            Object callId = map.get("call_id");
            if (callId != null) {
                options.put("tool_call_id", String.valueOf(callId));
            }
            return new UnifiedMessage("tool", map.get("output"), null, null,
                    options.isEmpty() ? null : options);
        }
        if ("function_call".equals(type)) {
            String callId = map.get("call_id") != null ? String.valueOf(map.get("call_id"))
                    : map.get("id") != null ? String.valueOf(map.get("id")) : null;
            Map<String, Object> function = new LinkedHashMap<>();
            if (map.get("name") != null) {
                function.put("name", String.valueOf(map.get("name")));
            }
            function.put("arguments", map.get("arguments") != null ? String.valueOf(map.get("arguments")) : "{}");

            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("id", callId != null ? callId : "call_" + UUID.randomUUID().toString().replace("-", ""));
            toolCall.put("type", "function");
            toolCall.put("function", function);
            return new UnifiedMessage("assistant", null, null, "tool_calls",
                    Map.of("tool_calls", List.of(toolCall)));
        }

        String role = map.containsKey("role") ? String.valueOf(map.get("role")) : "user";
        Object content = map.containsKey("content") ? map.get("content") : map;
        return new UnifiedMessage(role, content, null);
    }

    /**
     * 提取 Responses API 独立 reasoning item 中的 summary 文本，交给目标供应商适配器决定如何使用。
     */
    private String extractReasoningText(Map<?, ?> map) {
        Object summary = map.get("summary");
        if (summary instanceof List<?> summaryList) {
            StringBuilder builder = new StringBuilder();
            for (Object item : summaryList) {
                if (item instanceof Map<?, ?> summaryMap) {
                    Object text = summaryMap.get("text");
                    if (text != null) {
                        builder.append(text);
                    }
                } else if (item != null) {
                    builder.append(item);
                }
            }
            return builder.toString();
        }
        Object text = map.get("text");
        return text == null ? "" : String.valueOf(text);
    }

    /**
     * Normalize Responses content parts to Chat Completions compatible content parts.
     */
    public Object normalizeContentForChat(Object content) {
        if (!(content instanceof List<?> contentList)) {
            return content;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object part : contentList) {
            if (part instanceof Map<?, ?> partMap) {
                Map<String, Object> normalizedPart = new LinkedHashMap<>(partMap.size());
                for (Map.Entry<?, ?> entry : partMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if ("type".equals(key)) {
                        String type = String.valueOf(value);
                        if ("input_text".equals(type) || "output_text".equals(type)
                                || "refusal".equals(type) || "reasoning".equals(type)
                                || "redacted_output".equals(type) || "thinking".equals(type)) {
                            type = "text";
                        } else if ("input_image".equals(type)) {
                            type = "image_url";
                        } else if ("input_video".equals(type)) {
                            type = "video_url";
                        } else if (!CHAT_COMPLETION_CONTENT_TYPES.contains(type)) {
                            type = "text";
                        }
                        normalizedPart.put("type", type);
                    } else {
                        normalizedPart.put(key, value);
                    }
                }
                normalized.add(normalizedPart);
            } else {
                normalized.add(Map.of("type", "text", "text", String.valueOf(part)));
            }
        }
        if (normalized.size() == 1) {
            Map<String, Object> only = normalized.getFirst();
            if ("text".equals(only.get("type")) && only.get("text") instanceof String s) {
                return s;
            }
        }
        return normalized;
    }

    private Object messagesToInput(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (UnifiedMessage message : messages) {
            if ("tool".equals(message.role()) && message.options() != null
                    && message.options().get("tool_call_id") != null) {
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("type", "function_call_output");
                output.put("call_id", String.valueOf(message.options().get("tool_call_id")));
                output.put("output", message.content() != null ? message.content() : "");
                items.add(output);
                continue;
            }
            if ("assistant".equals(message.role()) && message.options() != null
                    && message.options().get("tool_calls") instanceof List<?> toolCalls) {
                if (message.content() != null && !String.valueOf(message.content()).isBlank()) {
                    items.add(Map.of("role", "assistant", "content", message.content()));
                }
                for (Object toolCall : toolCalls) {
                    if (toolCall instanceof Map<?, ?> toolCallMap) {
                        items.add(toResponsesFunctionCall(toolCallMap));
                    }
                }
                continue;
            }
            items.add(Map.of("role", message.role() != null ? message.role() : "user",
                    "content", message.content() != null ? message.content() : ""));
        }
        return items;
    }

    private Map<String, Object> toResponsesFunctionCall(Map<?, ?> toolCall) {
        Map<String, Object> item = new LinkedHashMap<>();
        Object id = toolCall.get("id");
        item.put("id", id != null ? String.valueOf(id) : "fc_" + UUID.randomUUID().toString().replace("-", ""));
        item.put("call_id", id != null ? String.valueOf(id) : item.get("id"));
        item.put("type", "function_call");
        if (toolCall.get("function") instanceof Map<?, ?> function) {
            if (function.get("name") != null) {
                item.put("name", String.valueOf(function.get("name")));
            }
            item.put("arguments", function.get("arguments") != null ? String.valueOf(function.get("arguments")) : "{}");
        }
        return item;
    }
}
