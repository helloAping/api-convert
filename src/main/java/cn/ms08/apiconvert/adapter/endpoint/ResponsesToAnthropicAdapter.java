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
            List<Map<String, Object>> anthropicTools = AnthropicTools.convertToolsToAnthropic((List<Object>) toolsList);
            if (!anthropicTools.isEmpty()) {
                cleaned.put("tools", anthropicTools);
            }
        }
        AnthropicTools.convertToolChoice(cleaned);
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
                mergeAnthropicToolResult(result, message);
                continue;
            }
            String role = "developer".equals(message.role()) ? "user" : message.role();
            Object content = toAnthropicContent(message);
            if ("assistant".equals(role) && contentHasToolUse(content)) {
                mergeAnthropicAssistant(result, content);
            } else {
                result.add(new UnifiedMessage(role, content, null));
            }
        }
        // Anthropic 要求 tool_result 紧跟在 tool_use assistant 之后，
        // 将插在中间的其它消息移到 tool_result 块之后
        reorderAnthropicToolSequence(result);
        return result;
    }

    /**
     * 判断内容块列表是否包含 tool_use 块。
     */
    @SuppressWarnings("unchecked")
    private static boolean contentHasToolUse(Object content) {
        if (!(content instanceof List<?> list)) return false;
        return list.stream().anyMatch(b -> b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")));
    }

    /**
     * 将 content 统一转为内容块列表，兼容字符串形式。
     */
    @SuppressWarnings("unchecked")
    private static List<Object> toContentList(Object content) {
        if (content instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (content instanceof String text && !text.isEmpty()) {
            return new ArrayList<>(List.of(Map.of("type", "text", "text", text)));
        }
        return new ArrayList<>();
    }

    /**
     * 合并连续 tool_result 消息。Anthropic 要求所有 tool_result 在同一条 user 消息中。
     */
    private void mergeAnthropicToolResult(List<UnifiedMessage> result, UnifiedMessage message) {
        UnifiedMessage toolResult = toAnthropicToolResultMessage(message);
        if (!result.isEmpty()) {
            UnifiedMessage last = result.getLast();
            if ("user".equals(last.role()) && last.content() instanceof List<?> lastList
                    && !lastList.isEmpty() && lastList.getLast() instanceof Map<?, ?> lastBlock
                    && "tool_result".equals(lastBlock.get("type"))
                    && toolResult.content() instanceof List<?> tc && !tc.isEmpty()) {
                List<Object> merged = new ArrayList<>(lastList);
                merged.addAll(tc);
                result.removeLast();
                result.add(new UnifiedMessage("user", merged, null));
                return;
            }
        }
        result.add(toolResult);
    }

    /**
     * 将 tool_use 块合并到上一条 assistant 消息。Anthropic 要求所有 tool_use 在同一条 assistant 消息中。
     */
    private void mergeAnthropicAssistant(List<UnifiedMessage> result, Object content) {
        if (!result.isEmpty()) {
            UnifiedMessage last = result.getLast();
            if ("assistant".equals(last.role())) {
                List<Object> merged = toContentList(last.content());
                List<Object> newBlocks = toContentList(content);
                boolean addedAny = false;
                for (Object block : newBlocks) {
                    if (block instanceof Map<?, ?> m && "tool_use".equals(m.get("type"))) {
                        merged.add(block);
                        addedAny = true;
                    }
                }
                if (addedAny) {
                    result.removeLast();
                    result.add(new UnifiedMessage("assistant", merged, null));
                    return;
                }
            }
        }
        result.add(new UnifiedMessage("assistant", content, null));
    }

    /**
     * 重排消息顺序：将 tool_use assistant 与 tool_result user 之间的非 tool_result 消息
     * 移到 tool_result 块之后。
     */
    @SuppressWarnings("unchecked")
    private static void reorderAnthropicToolSequence(List<UnifiedMessage> messages) {
        int i = 0;
        while (i < messages.size()) {
            UnifiedMessage msg = messages.get(i);
            if (!"assistant".equals(msg.role()) || !contentHasToolUse(msg.content())) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < messages.size() && !isAnthropicToolResult(messages.get(j))) {
                j++;
            }
            if (j >= messages.size() || j == i + 1) {
                i++;
                continue;
            }
            int k = j;
            while (k < messages.size() && isAnthropicToolResult(messages.get(k))) {
                k++;
            }
            List<UnifiedMessage> between = new ArrayList<>(messages.subList(i + 1, j));
            messages.subList(i + 1, j).clear();
            int toolBlockEnd = i + 1 + (k - j);
            messages.addAll(toolBlockEnd, between);
            i = toolBlockEnd + between.size();
        }
    }

    /**
     * 判断消息是否为 tool_result user 消息。
     */
    private static boolean isAnthropicToolResult(UnifiedMessage message) {
        return "user".equals(message.role())
                && message.content() instanceof List<?> list
                && !list.isEmpty()
                && list.getFirst() instanceof Map<?, ?> m
                && "tool_result".equals(m.get("type"));
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
                || "refusal".equals(type)) {
            Object text = part.get("text");
            blocks.add(Map.of("type", "text", "text", text != null ? String.valueOf(text) : ""));
            return;
        }
        // 保留 reasoning/thinking 类型，DeepSeek 的 thinking 模式下要求回传 thinking 块
        if ("reasoning".equals(type) || "thinking".equals(type)) {
            Object text = part.get("text");
            blocks.add(Map.of("type", "thinking", "thinking", text != null ? String.valueOf(text) : ""));
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

    // convertToolsToAnthropic / convertToolChoice 已提取到 AnthropicTools 共享工具类

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
