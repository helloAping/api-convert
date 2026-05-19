package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI Responses endpoint -> OpenAI-compatible Chat Completions upstream.
 */
@Component
public class ResponsesToOpenAiCompatibleAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponsesToOpenAiCompatibleAdapter.class);

    private static final Set<String> RESPONSES_ONLY_FIELDS = Set.of(
            "instructions", "previous_response_id", "truncation", "include", "store", "background",
            "conversation", "prompt", "text", "reasoning");

    private static final Set<String> CHAT_COMPLETION_CONTENT_TYPES = Set.of("text", "image_url", "video_url");

    private final OpenAiResponsesResponseAdapter responseAdapter;

    public ResponsesToOpenAiCompatibleAdapter(OpenAiResponsesResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.OPENAI_RESPONSES;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());
        List<UnifiedMessage> messages = normalizeMessagesForChat(request.messages(), request.rawOptions());
        return new UnifiedChatRequest(
                request.model(), messages, request.stream(), request.temperature(), request.maxTokens(),
                request.responseFormat(), cleaned);
    }

    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiResponsesResponse) {
            return response;
        }
        log.debug("Adapt response: OPENAI_RESPONSES -> OPENAI_COMPATIBLE, model={}", publicModel);
        OpenAiResponsesResponse adapted = responseAdapter.toOpenAiResponses(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }

    protected Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return rawOptions;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>(rawOptions);
        Object reasoning = cleaned.get("reasoning");
        RESPONSES_ONLY_FIELDS.forEach(cleaned::remove);
        if (reasoning instanceof Map<?, ?> reasoningMap) {
            Object effort = reasoningMap.get("effort");
            if (effort instanceof String effortString && StringUtils.hasText(effortString)) {
                cleaned.put("reasoning_effort", effortString);
            }
        }
        convertTools(cleaned);
        convertToolChoice(cleaned);
        return cleaned;
    }

    private List<UnifiedMessage> normalizeMessagesForChat(List<UnifiedMessage> messages, Map<String, Object> rawOptions) {
        List<UnifiedMessage> result = new ArrayList<>();
        Object instructions = rawOptions == null ? null : rawOptions.get("instructions");
        if (instructions != null && StringUtils.hasText(String.valueOf(instructions))) {
            result.add(new UnifiedMessage("system", String.valueOf(instructions), null));
        }
        if (messages == null) {
            return result;
        }
        for (UnifiedMessage message : messages) {
            String role = "developer".equals(message.role()) ? "system" : message.role();
            // Responses API 允许连续多个 function_call 输入项，但 Chat Completions 要求
            // 所有 tool_calls 合并到同一条 assistant 消息中，否则上游会报
            // "assistant message with tool_calls must be followed by tool messages responding to each tool_call_id"
            if ("assistant".equals(role) && hasToolCalls(message)) {
                mergeToolCallsIntoLastAssistant(result, message);
            } else {
                result.add(new UnifiedMessage(role, normalizeContentForChat(message.content()), message.name(),
                        message.finishReason(), message.options()));
            }
        }
        // Chat Completions 要求 tool 结果消息紧跟在 tool_calls assistant 之后，
        // 不能存在其他角色消息。将插在 tool_calls 和 tool 结果之间的消息移到 tool 块之后。
        reorderToolSequence(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    protected static boolean hasToolCalls(UnifiedMessage message) {
        return message.options() != null
                && message.options().get("tool_calls") instanceof List<?> list
                && !list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    protected void mergeToolCallsIntoLastAssistant(List<UnifiedMessage> result, UnifiedMessage current) {
        if (!result.isEmpty()) {
            UnifiedMessage last = result.getLast();
            if ("assistant".equals(last.role()) && hasToolCalls(last)) {
                // 合并 tool_calls 到上一条 assistant 消息
                List<Object> existingCalls = (List<Object>) last.options().get("tool_calls");
                List<Object> newCalls = (List<Object>) current.options().get("tool_calls");
                List<Object> merged = new ArrayList<>(existingCalls);
                merged.addAll(newCalls);
                Map<String, Object> mergedOptions = new LinkedHashMap<>(last.options());
                mergedOptions.put("tool_calls", merged);
                copyAbsentMessageOptions(mergedOptions, current.options());
                result.removeLast();
                result.add(new UnifiedMessage("assistant", last.content(), last.name(), "tool_calls", mergedOptions));
                return;
            }
        }
        result.add(new UnifiedMessage("assistant", normalizeContentForChat(current.content()), current.name(),
                current.finishReason(), current.options()));
    }

    protected static void copyAbsentMessageOptions(Map<String, Object> target, Map<String, Object> source) {
        if (source == null) {
            return;
        }
        source.forEach((key, value) -> {
            if (!"tool_calls".equals(key)) {
                target.putIfAbsent(key, value);
            }
        });
    }

    /**
     * 从 assistant 消息内容中提取 reasoning 类型块，供特定上游适配器决定如何使用。
     *
     * @return ReasoningParts 或 null（无 reasoning 内容时）
     */
    @SuppressWarnings("unchecked")
    protected static ReasoningParts extractReasoningParts(Object content) {
        if (!(content instanceof List<?> contentList)) {
            return null;
        }
        String reasoningText = null;
        List<Object> filtered = new ArrayList<>();
        for (Object part : contentList) {
            if (part instanceof Map<?, ?> m && "reasoning".equals(String.valueOf(m.get("type")))) {
                Object text = m.get("text");
                if (text != null && reasoningText == null) {
                    reasoningText = String.valueOf(text);
                }
                continue;
            }
            filtered.add(part);
        }
        if (reasoningText == null) {
            return null;
        }
        return new ReasoningParts(filtered, reasoningText);
    }

    protected record ReasoningParts(List<?> filtered, String text) {
        boolean hasOnlyReasoning() {
            return filtered == null || filtered.isEmpty();
        }

        Map<String, Object> toOptions(Map<String, Object> original) {
            Map<String, Object> opts = original != null ? new LinkedHashMap<>(original) : new LinkedHashMap<>();
            opts.put("reasoning_content", text);
            return opts;
        }
    }

    /**
     * 判断消息是否为 tool 角色（function_call_output）。
     */
    private static boolean isToolMessage(UnifiedMessage message) {
        return "tool".equals(message.role());
    }

    /**
     * 调整消息顺序：将 tool_calls assistant 与后续 tool 结果消息之间的非 tool 消息
     * 移到 tool 块之后。Chat Completions 不允许在 tool_calls 和 tool 结果之间存在
     * 其他角色消息。
     */
    protected static void reorderToolSequence(List<UnifiedMessage> messages) {
        int i = 0;
        while (i < messages.size()) {
            UnifiedMessage msg = messages.get(i);
            if (!"assistant".equals(msg.role()) || !hasToolCalls(msg)) {
                i++;
                continue;
            }
            // 查找第一个 tool 结果消息的位置
            int j = i + 1;
            while (j < messages.size() && !isToolMessage(messages.get(j))) {
                j++;
            }
            if (j >= messages.size() || j == i + 1) {
                i++;
                continue; // 无 intervening 消息或没有 tool 结果
            }
            // 查找连续 tool 结果块的结束位置
            int k = j;
            while (k < messages.size() && isToolMessage(messages.get(k))) {
                k++;
            }
            // 收集并移除 tool_calls 与首个 tool 之间的非 tool 消息
            List<UnifiedMessage> nonToolBlock = new ArrayList<>(messages.subList(i + 1, j));
            messages.subList(i + 1, j).clear();
            // 插入到 tool 块之后
            int toolBlockEnd = i + 1 + (k - j);
            messages.addAll(toolBlockEnd, nonToolBlock);
            i = toolBlockEnd + nonToolBlock.size();
        }
    }

    @SuppressWarnings("unchecked")
    private void convertTools(Map<String, Object> rawOptions) {
        Object tools = rawOptions.get("tools");
        if (!(tools instanceof List<?> toolsList)) {
            return;
        }
        List<Object> converted = new ArrayList<>();
        for (Object item : toolsList) {
            if (!(item instanceof Map<?, ?> tool)) {
                converted.add(item);
                continue;
            }
            if (!"function".equals(String.valueOf(tool.get("type")))) {
                continue;
            }
            if (tool.get("function") instanceof Map<?, ?>) {
                converted.add(item);
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : tool.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!"type".equals(key)) {
                    function.put(key, entry.getValue());
                }
            }
            converted.add(Map.of("type", "function", "function", function));
        }
        if (converted.isEmpty()) {
            rawOptions.remove("tools");
        } else {
            rawOptions.put("tools", converted);
        }
    }

    private void convertToolChoice(Map<String, Object> rawOptions) {
        Object toolChoice = rawOptions.get("tool_choice");
        if (!(toolChoice instanceof Map<?, ?> tcMap)) {
            return;
        }
        if (!"function".equals(String.valueOf(tcMap.get("type")))) {
            return;
        }
        if (tcMap.get("function") instanceof Map<?, ?>) {
            return;
        }
        Object name = tcMap.get("name");
        if (name != null) {
            rawOptions.put("tool_choice", Map.of("type", "function", "function", Map.of("name", String.valueOf(name))));
        }
    }

    protected Object normalizeContentForChat(Object content) {
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
                    normalizedPart.put(key, "type".equals(key) ? chatContentType(String.valueOf(value)) : value);
                }
                normalized.add(normalizedPart);
            } else {
                normalized.add(Map.of("type", "text", "text", String.valueOf(part)));
            }
        }
        if (normalized.size() == 1) {
            Map<String, Object> only = normalized.getFirst();
            if ("text".equals(only.get("type")) && only.get("text") instanceof String text) {
                return text;
            }
        }
        return normalized;
    }

    private String chatContentType(String type) {
        if ("input_text".equals(type) || "output_text".equals(type)
                || "refusal".equals(type) || "reasoning".equals(type)
                || "redacted_output".equals(type) || "thinking".equals(type)) {
            return "text";
        }
        if ("input_image".equals(type)) {
            return "image_url";
        }
        if ("input_video".equals(type)) {
            return "video_url";
        }
        return CHAT_COMPLETION_CONTENT_TYPES.contains(type) ? type : "text";
    }
}
