package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Anthropic Messages 端点到 OpenAI Chat Completions 上游的协议适配器。
 */
@Component
public class AnthropicToOpenAiCompatibleAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicToOpenAiCompatibleAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Anthropic 专有或 Chat Completions 根字段不接受的参数，跨到 /chat 上游时必须移除。
     */
    private static final Set<String> ANTHROPIC_ONLY_FIELDS = Set.of(
            "thinking", "context_management", "output_config",
            "anthropic_version", "anthropic_beta",
            "client_metadata", "prompt_cache_key", "store", "include",
            "stream_options", "container", "mcp_servers", "metadata",
            "service_tier", "top_k", "system", "stop_sequences");

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.ANTHROPIC_MESSAGES;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    /**
     * 将 Anthropic 的 system、tools、tool_choice、tool_use 和 tool_result 转成 OpenAI Chat 格式。
     */
    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        Object system = request.rawOptions() == null ? null : request.rawOptions().get("system");
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());
        List<UnifiedMessage> adaptedMessages = adaptMessagesForOpenAi(request.messages(), system);
        return new UnifiedChatRequest(
                request.model(), adaptedMessages, request.stream(),
                request.temperature(), request.maxTokens(),
                request.responseFormat(), cleaned);
    }

    private Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return rawOptions;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>(rawOptions);

        if (cleaned.containsKey("stop_sequences")) {
            cleaned.put("stop", cleaned.get("stop_sequences"));
        }
        if (cleaned.containsKey("tools")) {
            Object tools = mapAnthropicTools(cleaned.get("tools"));
            if (tools == null) {
                cleaned.remove("tools");
            } else {
                cleaned.put("tools", tools);
            }
        }
        if (cleaned.containsKey("tool_choice")) {
            Object toolChoice = mapAnthropicToolChoice(cleaned.get("tool_choice"));
            if (toolChoice == null) {
                cleaned.remove("tool_choice");
            } else {
                cleaned.put("tool_choice", toolChoice);
            }
        }

        ANTHROPIC_ONLY_FIELDS.forEach(cleaned::remove);
        return cleaned;
    }

    private List<UnifiedMessage> adaptMessagesForOpenAi(List<UnifiedMessage> messages, Object system) {
        List<UnifiedMessage> adapted = new ArrayList<>();
        String systemText = toText(system);
        if (hasText(systemText) && !systemText.contains("x-anthropic-billing-header")) {
            adapted.add(new UnifiedMessage("system", systemText, null));
        }
        if (messages == null || messages.isEmpty()) {
            return adapted;
        }
        for (UnifiedMessage message : messages) {
            adapted.addAll(adaptMessageForOpenAi(message));
        }
        return adapted;
    }

    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> adaptMessageForOpenAi(UnifiedMessage message) {
        Object content = message.content();
        if (!(content instanceof List<?> contentList)) {
            return List.of(message);
        }

        List<String> textParts = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        List<UnifiedMessage> toolMessages = new ArrayList<>();
        Map<String, Object> options = message.options() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(message.options());

        for (Object block : contentList) {
            if (!(block instanceof Map<?, ?> blockMap)) {
                addIfText(textParts, block);
                continue;
            }
            String type = blockMap.get("type") == null ? "" : String.valueOf(blockMap.get("type"));
            if ("text".equals(type)) {
                addIfText(textParts, blockMap.get("text"));
            } else if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                Object thinking = blockMap.get("thinking") == null ? blockMap.get("text") : blockMap.get("thinking");
                if (thinking != null) {
                    options.put("reasoning_content", String.valueOf(thinking));
                }
            } else if ("tool_use".equals(type) && "assistant".equals(message.role())) {
                toolCalls.add(toOpenAiToolCall(blockMap));
            } else if ("tool_result".equals(type)) {
                Map<String, Object> toolOptions = new LinkedHashMap<>();
                if (blockMap.get("tool_use_id") != null) {
                    toolOptions.put("tool_call_id", String.valueOf(blockMap.get("tool_use_id")));
                }
                toolMessages.add(new UnifiedMessage("tool", toText(blockMap.get("content")),
                        null, null, toolOptions.isEmpty() ? null : toolOptions));
            } else if (blockMap.containsKey("text")) {
                addIfText(textParts, blockMap.get("text"));
            }
        }

        List<UnifiedMessage> result = new ArrayList<>(toolMessages);
        if (!toolCalls.isEmpty()) {
            options.put("tool_calls", toolCalls);
        }
        String text = String.join("\n", textParts);
        if (!toolCalls.isEmpty() || hasText(text) || result.isEmpty()) {
            result.add(new UnifiedMessage(message.role(), hasText(text) ? text : null,
                    message.name(), message.finishReason(), options.isEmpty() ? null : options));
        }
        return result;
    }

    private Object mapAnthropicTools(Object tools) {
        if (!(tools instanceof List<?> toolsList)) {
            return tools;
        }
        List<Object> result = new ArrayList<>();
        for (Object item : toolsList) {
            if (!(item instanceof Map<?, ?> tool) || tool.get("name") == null) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", String.valueOf(tool.get("name")));
            if (tool.get("description") != null) {
                function.put("description", tool.get("description"));
            }
            function.put("parameters", tool.get("input_schema") == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : tool.get("input_schema"));
            result.add(Map.of("type", "function", "function", function));
        }
        return result.isEmpty() ? null : result;
    }

    private Object mapAnthropicToolChoice(Object toolChoice) {
        if (toolChoice instanceof String choice) {
            return choice;
        }
        if (!(toolChoice instanceof Map<?, ?> choice)) {
            return toolChoice;
        }
        String type = choice.get("type") == null ? "" : String.valueOf(choice.get("type"));
        return switch (type) {
            case "none", "auto" -> type;
            case "any" -> "required";
            case "tool" -> choice.get("name") == null ? "required" : Map.of(
                    "type", "function",
                    "function", Map.of("name", String.valueOf(choice.get("name"))));
            default -> null;
        };
    }

    private Map<String, Object> toOpenAiToolCall(Map<?, ?> blockMap) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", String.valueOf(blockMap.get("name")));
        function.put("arguments", toJsonString(blockMap.get("input")));
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", String.valueOf(blockMap.get("id")));
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
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof AnthropicMessageResponse) {
            return response;
        }
        log.debug("Adapt response: ANTHROPIC_MESSAGES -> OPENAI_COMPATIBLE, model={}", publicModel);
        AnthropicMessageResponse adapted = toAnthropicMessages(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }

    private AnthropicMessageResponse toAnthropicMessages(UnifiedChatResponse response, String publicModel) {
        AnthropicMessageResponse anthropicResponse = new AnthropicMessageResponse();
        anthropicResponse.setId(response.id() == null ? "msg_" + UUID.randomUUID() : response.id());
        anthropicResponse.setType("message");
        anthropicResponse.setRole("assistant");
        anthropicResponse.setModel(publicModel);
        anthropicResponse.setStopReason(mapFinishReason(response.messages()));
        anthropicResponse.setContent(mapContent(response.messages()));
        if (response.usage() != null) {
            AnthropicMessageResponse.Usage usage = new AnthropicMessageResponse.Usage();
            usage.setInputTokens(response.usage().inputTokens());
            usage.setOutputTokens(response.usage().outputTokens());
            usage.setCacheReadInputTokens(response.usage().cacheReadInputTokens());
            anthropicResponse.setUsage(usage);
        }
        return anthropicResponse;
    }

    private String mapFinishReason(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "end_turn";
        }
        UnifiedMessage last = messages.get(messages.size() - 1);
        if (last.finishReason() == null) {
            return "end_turn";
        }
        return switch (last.finishReason()) {
            case "tool_calls", "function_call" -> "tool_use";
            case "length" -> "max_tokens";
            case "stop" -> "end_turn";
            default -> last.finishReason();
        };
    }

    private List<Object> mapContent(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .flatMap(this::toAnthropicContentBlocks)
                .toList();
    }

    private Stream<Object> toAnthropicContentBlocks(UnifiedMessage message) {
        List<Object> blocks = new ArrayList<>();
        Object reasoningContent = message.options() == null ? null : message.options().get("reasoning_content");
        if (reasoningContent != null && !String.valueOf(reasoningContent).isBlank()) {
            blocks.add(Map.of("type", "thinking", "thinking", String.valueOf(reasoningContent)));
        }
        addAnthropicTextBlocks(blocks, message.content());
        Object toolCalls = message.options() == null ? null : message.options().get("tool_calls");
        if (toolCalls instanceof List<?> calls) {
            for (Object call : calls) {
                if (call instanceof Map<?, ?> callMap) {
                    blocks.add(toAnthropicToolUse(callMap));
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(Map.of("type", "text", "text", ""));
        }
        return blocks.stream();
    }

    private void addAnthropicTextBlocks(List<Object> blocks, Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof List<?> list) {
            blocks.addAll(list);
            return;
        }
        if (content instanceof Map<?, ?> map) {
            blocks.add(map);
            return;
        }
        String text = String.valueOf(content);
        if (!text.isBlank()) {
            blocks.add(Map.of("type", "text", "text", text));
        }
    }

    private Object toAnthropicToolUse(Map<?, ?> callMap) {
        Map<?, ?> function = callMap.get("function") instanceof Map<?, ?> map ? map : Collections.emptyMap();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_use");
        block.put("id", String.valueOf(callMap.get("id")));
        block.put("name", String.valueOf(function.get("name")));
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

    private void addIfText(List<String> textParts, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            textParts.add(String.valueOf(value));
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String toText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> block && block.get("text") != null) {
                    addIfText(parts, block.get("text"));
                } else if (item instanceof String text) {
                    addIfText(parts, text);
                }
            }
            return String.join("\n", parts);
        }
        if (content instanceof Map<?, ?> map && map.get("text") != null) {
            return String.valueOf(map.get("text"));
        }
        return String.valueOf(content);
    }
}
