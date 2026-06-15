package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI Chat Completions 端点 → Anthropic 供应商的接口适配器。
 * <p>
 * 当 {@code /v1/chat/completions} 端点的请求被路由到 {@code ANTHROPIC} 类型的上游时，
 * 上游以 Anthropic Messages 格式返回响应，此适配器将统一响应转换回 Chat Completions 格式。
 * </p>
 *
 * <h3>适配边界</h3>
 * <ul>
 *   <li><b>请求</b>：清理 {@code rawOptions} 中 OpenAI 特有但 Anthropic 上游不支持的字段
 *       （如 {@code logprobs}、{@code top_logprobs}、{@code n}、{@code stop} 等），
 *       并为 Anthropic 的 {@code max_tokens} 必填字段提供默认值。</li>
 *   <li><b>响应</b>：将 {@code AnthropicMessageResponse} 风格统一响应通过
 *       {@code OpenAiResponseAdapter.toOpenAi()} 转为 {@code OpenAiChatCompletionResponse}。</li>
 * </ul>
 */
@Component
public class ChatCompletionsToAnthropicAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsToAnthropicAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * OpenAI Chat Completions 特有但 Anthropic 上游不支持的字段。
     */
    private static final Set<String> OPENAI_ONLY_FIELDS = Set.of(
            "logprobs", "top_logprobs", "n", "stop",
            "presence_penalty", "frequency_penalty", "seed",
            "parallel_tool_calls", "stream_options",
            "store", "include", "metadata",
            "max_completion_tokens", "reasoning_effort");

    private final OpenAiResponseAdapter responseAdapter;

    public ChatCompletionsToAnthropicAdapter(OpenAiResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.CHAT_COMPLETIONS;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.OPENAI;
    }

    /**
     * 请求适配：清理 rawOptions、转换消息中 OpenAI 格式工具调用/结果到 Anthropic content block 格式、
     * 转换 tool_choice 格式到 Anthropic 兼容格式、转换 developer 角色为 user，
     * 并为 Anthropic 的 max_tokens 必填字段提供默认值。
     */
    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        // 1. 转换 developer 角色为 user
        List<UnifiedMessage> adaptedMessages = mapDeveloperRole(request.messages());

        // 2. 将 OpenAI 格式的 tool_calls/tool 消息转为 Anthropic content block 格式
        adaptedMessages = convertToolMessages(adaptedMessages);

        // 3. 将 OpenAI Chat 格式的 image_url 内容块转为 Anthropic image 块
        adaptedMessages = convertMultimodalContent(adaptedMessages);

        // 4. 清理和转换 rawOptions
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());

        // 5. Anthropic 要求 max_tokens 为必填非空整数
        Integer maxTokens = request.maxTokens();
        if (maxTokens == null) {
            maxTokens = 4096;
        }

        return new UnifiedChatRequest(
                request.model(), adaptedMessages, request.stream(),
                request.temperature(), maxTokens,
                request.responseFormat(), cleaned);
    }

    /**
     * 将 developer 角色消息映射为 user（Anthropic 不支持 developer 角色）。
     */
    private List<UnifiedMessage> mapDeveloperRole(List<UnifiedMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .map(msg -> {
                    if (!"developer".equals(msg.role())) {
                        return msg;
                    }
                    return new UnifiedMessage(
                            "user", msg.content(), msg.name(),
                            msg.finishReason(), msg.options());
                })
                .toList();
    }

    /**
     * 将 OpenAI Chat 格式的 tool_calls 和 tool 消息转为 Anthropic content block 格式。
     * <p>
     * OpenAI 格式：
     * <ul>
     *   <li>assistant 消息携带 {@code options.tool_calls} 列表</li>
     *   <li>tool 消息携带 {@code options.tool_call_id} 和字符串 content</li>
     * </ul>
     * Anthropic 格式：
     * <ul>
     *   <li>assistant 消息的 content 为包含 {@code tool_use} 块的列表</li>
     *   <li>user 消息的 content 为包含 {@code tool_result} 块的列表</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> convertToolMessages(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<UnifiedMessage> result = new ArrayList<>();
        // 收集连续的 tool 消息，合并到同一条 user 消息中
        List<Map<String, Object>> pendingToolResults = new ArrayList<>();

        for (UnifiedMessage message : messages) {
            boolean isToolMessage = "tool".equals(message.role());
            boolean hasToolCalls = message.options() != null && message.options().get("tool_calls") != null;

            if (isToolMessage) {
                // 收集 tool_result 块
                String toolCallId = message.options() != null
                        ? String.valueOf(message.options().get("tool_call_id"))
                        : "";
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", toolCallId);
                if (message.content() != null) {
                    block.put("content", String.valueOf(message.content()));
                }
                pendingToolResults.add(block);
                continue;
            }

            // 遇到非 tool 消息时，先将收集到的 tool_result 写出
            if (!pendingToolResults.isEmpty()) {
                flushToolResults(result, pendingToolResults);
            }

            if (hasToolCalls) {
                // assistant 消息带 tool_calls → 转为 content block 列表
                result.add(convertAssistantToolCalls(message));
            } else {
                result.add(message);
            }
        }

        // 处理末尾连续的 tool 消息
        if (!pendingToolResults.isEmpty()) {
            flushToolResults(result, pendingToolResults);
        }

        return result;
    }

    /**
     * 将 OpenAI assistant 消息的 tool_calls 转为 Anthropic 的 tool_use content blocks。
     */
    @SuppressWarnings("unchecked")
    private UnifiedMessage convertAssistantToolCalls(UnifiedMessage message) {
        Object rawToolCalls = message.options().get("tool_calls");
        if (!(rawToolCalls instanceof List<?> toolCalls)) {
            return message;
        }

        List<Object> contentBlocks = new ArrayList<>();
        // 保留原始文本内容
        if (message.content() != null && !String.valueOf(message.content()).isBlank()) {
            contentBlocks.add(Map.of("type", "text", "text", String.valueOf(message.content())));
        }

        for (Object call : toolCalls) {
            if (!(call instanceof Map<?, ?> callMap)) {
                continue;
            }
            Map<String, Object> toolUse = new LinkedHashMap<>();
            toolUse.put("type", "tool_use");
            toolUse.put("id", String.valueOf(callMap.get("id")));
            Object function = callMap.get("function");
            if (function instanceof Map<?, ?> functionMap) {
                toolUse.put("name", String.valueOf(functionMap.get("name")));
                toolUse.put("input", parseArguments(functionMap.get("arguments")));
            }
            contentBlocks.add(toolUse);
        }

        // 移除 tool_calls，替换为 Anthropic 格式
        Map<String, Object> newOptions = new LinkedHashMap<>(message.options());
        newOptions.remove("tool_calls");

        return new UnifiedMessage(
                message.role(), contentBlocks, message.name(),
                message.finishReason(), newOptions.isEmpty() ? null : newOptions);
    }

    /**
     * 将收集到的 tool_result 块合并为一条 Anthropic user 消息。
     */
    private void flushToolResults(List<UnifiedMessage> result, List<Map<String, Object>> toolResults) {
        result.add(new UnifiedMessage("user", new ArrayList<>(toolResults), null));
        toolResults.clear();
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

    /**
     * 清理 rawOptions：移除 OpenAI 特有字段，转换 tools 和 tool_choice 格式。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return rawOptions;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>(rawOptions);
        OPENAI_ONLY_FIELDS.forEach(cleaned::remove);

        // 转换 tools 格式
        Object tools = cleaned.get("tools");
        if (tools instanceof List<?> toolsList) {
            List<Map<String, Object>> anthropicTools = AnthropicTools.convertToolsToAnthropic((List<Object>) toolsList);
            if (anthropicTools.isEmpty()) {
                cleaned.remove("tools");
            } else {
                cleaned.put("tools", anthropicTools);
            }
        }

        // 转换 tool_choice 格式
        AnthropicTools.convertToolChoice(cleaned);

        return cleaned;
    }

    // convertToolsToAnthropic / convertToolChoice 已提取到 AnthropicTools 共享工具类

    /**
     * 将 OpenAI Chat image_url 内容块转为 Anthropic image 内容块。
     * 仅修改 content 为 List 且包含 image_url 类型条目的消息；其他消息直接透传。
     */
    private List<UnifiedMessage> convertMultimodalContent(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<UnifiedMessage> result = new ArrayList<>();
        for (UnifiedMessage message : messages) {
            if (message.content() instanceof List<?> contentList) {
                List<Object> converted = new ArrayList<>();
                boolean changed = false;
                for (Object part : contentList) {
                    if (part instanceof Map<?, ?> map) {
                        String type = map.get("type") != null ? String.valueOf(map.get("type")) : "text";
                        if ("image_url".equals(type)) {
                            Map<String, Object> anthropicBlock = AnthropicTools.convertImageToAnthropic(map);
                            if (anthropicBlock != null) {
                                converted.add(anthropicBlock);
                                changed = true;
                                continue;
                            }
                        }
                    }
                    converted.add(part);
                }
                if (changed) {
                    result.add(new UnifiedMessage(message.role(), converted, message.name(),
                            message.finishReason(), message.options()));
                } else {
                    result.add(message);
                }
            } else {
                result.add(message);
            }
        }
        return result;
    }

    /**
     * 响应适配：将 Anthropic Messages 风格统一响应转为 Chat Completions 格式。
     */
    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiChatCompletionResponse) {
            return response;
        }
        log.debug("适配响应：CHAT_COMPLETIONS → ANTHROPIC, model={}", publicModel);
        OpenAiChatCompletionResponse adapted = responseAdapter.toOpenAi(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }
}
