package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        return ProviderType.ANTHROPIC;
    }

    /**
     * 请求适配：清理 rawOptions、转换 tool_choice 格式到 Anthropic 兼容格式、
     * 转换 developer 角色为 user，并为 Anthropic 的 max_tokens 必填字段提供默认值。
     */
    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        // 1. 转换 developer 角色为 user
        List<UnifiedMessage> adaptedMessages = mapDeveloperRole(request.messages());

        // 2. 清理和转换 rawOptions
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());

        // 3. Anthropic 要求 max_tokens 为必填非空整数
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
            List<Map<String, Object>> anthropicTools = convertToolsToAnthropic((List<Object>) toolsList);
            if (anthropicTools.isEmpty()) {
                cleaned.remove("tools");
            } else {
                cleaned.put("tools", anthropicTools);
            }
        }

        // 转换 tool_choice 格式
        convertToolChoice(cleaned);

        return cleaned;
    }

    /**
     * 将 OpenAI 格式的 tools 转为 Anthropic 格式。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToolsToAnthropic(List<Object> openaiTools) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object tool : openaiTools) {
            if (!(tool instanceof Map<?, ?> toolMap)) {
                continue;
            }
            String type = String.valueOf(toolMap.get("type"));
            if (!"function".equals(type)) {
                continue;
            }
            Object function = toolMap.get("function");
            if (!(function instanceof Map<?, ?> funcMap)) {
                continue;
            }
            Map<String, Object> anthropicTool = new LinkedHashMap<>();
            anthropicTool.put("name", String.valueOf(funcMap.get("name")));
            if (funcMap.get("description") != null) {
                anthropicTool.put("description", String.valueOf(funcMap.get("description")));
            }
            Object params = funcMap.get("parameters");
            anthropicTool.put("input_schema", params != null ? params : Map.of("type", "object", "properties", Map.of()));
            if (funcMap.get("strict") instanceof Boolean strict) {
                anthropicTool.put("strict", strict);
            }
            result.add(anthropicTool);
        }
        return result;
    }

    /**
     * 将 tool_choice 从 OpenAI 格式转为 Anthropic 格式。
     */
    @SuppressWarnings("unchecked")
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
        String tc = String.valueOf(toolChoice);
        String type = switch (tc) {
            case "required" -> "any";
            case "none" -> "none";
            default -> "auto";
        };
        rawOptions.put("tool_choice", Map.of("type", type));
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
