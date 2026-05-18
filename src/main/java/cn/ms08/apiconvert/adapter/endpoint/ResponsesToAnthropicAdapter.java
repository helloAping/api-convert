package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * OpenAI Responses endpoint -> Anthropic Messages upstream.
 */
@Component
public class ResponsesToAnthropicAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponsesToAnthropicAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> OPENAI_ONLY_FIELDS = Set.of(
            "instructions", "parallel_tool_calls", "store", "include", "background",
            "conversation", "prompt", "text", "prompt_cache_key", "client_metadata", "metadata",
            "previous_response_id", "truncation", "stream_options", "frequency_penalty",
            "presence_penalty", "seed", "reasoning", "reasoning_effort", "max_completion_tokens");

    private final OpenAiResponsesResponseAdapter responseAdapter;

    public ResponsesToAnthropicAdapter(OpenAiResponsesResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.OPENAI_RESPONSES;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions(), request.messages());
        return new UnifiedChatRequest(
                request.model(),
                normalizeMessagesForAnthropic(request.messages()),
                request.stream(),
                request.temperature(),
                request.maxTokens() == null ? 4096 : request.maxTokens(),
                request.responseFormat(),
                cleaned);
    }

    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiResponsesResponse) {
            return response;
        }
        log.debug("Adapt response: OPENAI_RESPONSES -> ANTHROPIC, model={}", publicModel);
        OpenAiResponsesResponse adapted = responseAdapter.toOpenAiResponses(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions, List<UnifiedMessage> messages) {
        Map<String, Object> cleaned = rawOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawOptions);
        Object instructions = cleaned.get("instructions");
        OPENAI_ONLY_FIELDS.forEach(cleaned::remove);
        if (instructions != null && StringUtils.hasText(String.valueOf(instructions))) {
            cleaned.put("system", String.valueOf(instructions));
        } else {
            firstSystemMessage(messages).ifPresent(system -> cleaned.putIfAbsent("system", system));
        }

        Object tools = rawOptions == null ? null : rawOptions.get("tools");
        if (tools instanceof List<?> toolsList) {
            List<Map<String, Object>> anthropicTools = convertToolsToAnthropic((List<Object>) toolsList);
            if (!anthropicTools.isEmpty()) {
                cleaned.put("tools", anthropicTools);
            }
        }
        convertToolChoice(cleaned);
        return cleaned;
    }

    private java.util.Optional<String> firstSystemMessage(List<UnifiedMessage> messages) {
        if (messages == null) {
            return java.util.Optional.empty();
        }
        return messages.stream()
                .filter(message -> "system".equals(message.role()))
                .map(UnifiedMessage::content)
                .filter(value -> value != null && StringUtils.hasText(String.valueOf(value)))
                .map(String::valueOf)
                .findFirst();
    }

    private List<UnifiedMessage> normalizeMessagesForAnthropic(List<UnifiedMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        List<UnifiedMessage> result = new ArrayList<>();
        for (UnifiedMessage message : messages) {
            if ("system".equals(message.role())) {
                continue;
            }
            if ("tool".equals(message.role())) {
                result.add(toAnthropicToolResultMessage(message));
                continue;
            }
            result.add(new UnifiedMessage(
                    "developer".equals(message.role()) ? "user" : message.role(),
                    toAnthropicContent(message),
                    message.name(),
                    message.finishReason(),
                    message.options()));
        }
        return result;
    }

    private UnifiedMessage toAnthropicToolResultMessage(UnifiedMessage message) {
        String toolUseId = message.options() != null && message.options().get("tool_call_id") != null
                ? String.valueOf(message.options().get("tool_call_id")) : "";
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", message.content() != null ? message.content() : "");
        return new UnifiedMessage("user", List.of(block), null);
    }

    private Object toAnthropicContent(UnifiedMessage message) {
        List<Object> blocks = new ArrayList<>();
        addTextBlock(blocks, message.content());
        if ("assistant".equals(message.role()) && message.options() != null
                && message.options().get("tool_calls") instanceof List<?> toolCalls) {
            for (Object toolCall : toolCalls) {
                if (toolCall instanceof Map<?, ?> toolCallMap) {
                    blocks.add(toAnthropicToolUseBlock(toolCallMap));
                }
            }
        }
        if (blocks.isEmpty()) {
            return "";
        }
        if (blocks.size() == 1 && blocks.getFirst() instanceof Map<?, ?> block
                && "text".equals(block.get("type"))) {
            return block.get("text");
        }
        return blocks;
    }

    private void addTextBlock(List<Object> blocks, Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    addResponsesContentPart(blocks, map);
                } else {
                    blocks.add(Map.of("type", "text", "text", String.valueOf(item)));
                }
            }
            return;
        }
        if (StringUtils.hasText(String.valueOf(content))) {
            blocks.add(Map.of("type", "text", "text", String.valueOf(content)));
        }
    }

    private void addResponsesContentPart(List<Object> blocks, Map<?, ?> part) {
        String type = part.get("type") == null ? "text" : String.valueOf(part.get("type"));
        if ("input_text".equals(type) || "output_text".equals(type) || "text".equals(type)
                || "refusal".equals(type) || "reasoning".equals(type) || "thinking".equals(type)) {
            Object text = part.get("text");
            blocks.add(Map.of("type", "text", "text", text != null ? String.valueOf(text) : ""));
            return;
        }
        if ("input_image".equals(type) || "image_url".equals(type)) {
            Object imageUrl = part.get("image_url");
            Object url = imageUrl instanceof Map<?, ?> map ? map.get("url") : imageUrl;
            if (url == null) {
                url = part.get("url");
            }
            if (url != null) {
                blocks.add(Map.of("type", "image", "source",
                        Map.of("type", "url", "url", String.valueOf(url))));
            }
            return;
        }
        blocks.add(Map.of("type", "text", "text", part.toString()));
    }

    private List<Map<String, Object>> convertToolsToAnthropic(List<Object> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object tool : tools) {
            if (!(tool instanceof Map<?, ?> toolMap)) {
                continue;
            }
            if (!"function".equals(String.valueOf(toolMap.get("type")))) {
                continue;
            }
            Map<?, ?> function = toolMap.get("function") instanceof Map<?, ?> map ? map : toolMap;
            Object name = function.get("name");
            if (name == null) {
                continue;
            }
            Map<String, Object> anthropicTool = new LinkedHashMap<>();
            anthropicTool.put("name", String.valueOf(name));
            if (function.get("description") != null) {
                anthropicTool.put("description", String.valueOf(function.get("description")));
            }
            Object parameters = function.get("parameters");
            anthropicTool.put("input_schema", parameters != null ? parameters : Map.of("type", "object", "properties", Map.of()));
            if (function.get("strict") instanceof Boolean strict) {
                anthropicTool.put("strict", strict);
            }
            result.add(anthropicTool);
        }
        return result;
    }

    private void convertToolChoice(Map<String, Object> rawOptions) {
        Object toolChoice = rawOptions.get("tool_choice");
        if (toolChoice == null) {
            return;
        }
        if (toolChoice instanceof Map<?, ?> tcMap) {
            String type = tcMap.containsKey("type") ? String.valueOf(tcMap.get("type")) : "auto";
            if (!"function".equals(type) && !"tool".equals(type)) {
                return;
            }
            String name = tcMap.containsKey("name") ? String.valueOf(tcMap.get("name")) : null;
            if (name == null && tcMap.get("function") instanceof Map<?, ?> funcMap) {
                name = String.valueOf(funcMap.get("name"));
            }
            rawOptions.put("tool_choice", Map.of("type", "tool", "name", name != null ? name : ""));
            return;
        }
        String type = switch (String.valueOf(toolChoice)) {
            case "required" -> "any";
            case "none" -> "none";
            default -> "auto";
        };
        rawOptions.put("tool_choice", Map.of("type", type));
    }

    private Map<String, Object> toAnthropicToolUseBlock(Map<?, ?> toolCall) {
        Map<?, ?> function = toolCall.get("function") instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_use");
        block.put("id", toolCall.get("id") != null ? String.valueOf(toolCall.get("id")) : "");
        block.put("name", function.get("name") != null ? String.valueOf(function.get("name")) : "");
        block.put("input", parseArguments(function.get("arguments")));
        return block;
    }

    private Object parseArguments(Object arguments) {
        if (!(arguments instanceof String text) || text.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of("arguments", text);
        }
    }
}
