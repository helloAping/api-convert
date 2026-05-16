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

    private Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions) {
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
            result.add(new UnifiedMessage(role, normalizeContentForChat(message.content()), message.name(),
                    message.finishReason(), message.options()));
        }
        return result;
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

    private Object normalizeContentForChat(Object content) {
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
