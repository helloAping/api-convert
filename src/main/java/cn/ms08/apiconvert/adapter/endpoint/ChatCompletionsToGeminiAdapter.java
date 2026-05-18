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
 * OpenAI Chat Completions 端点 → Gemini 供应商的接口适配器。
 * <p>
 * 当 {@code /v1/chat/completions} 端点的请求被路由到 {@code GEMINI} 类型的上游时，
 * 上游以 Gemini generateContent 格式返回响应（已转为统一格式），此适配器将统一响应
 * 转换回 Chat Completions 格式。
 * </p>
 *
 * <h3>适配边界</h3>
 * <ul>
 *   <li><b>请求</b>：清理 {@code rawOptions} 中 OpenAI 特有但 Gemini 上游不支持的字段，
 *       并将 {@code developer} 角色消息映射为 {@code user}。</li>
 *   <li><b>响应</b>：将 Gemini 风格统一响应通过 {@code OpenAiResponseAdapter.toOpenAi()}
 *       转为 {@code OpenAiChatCompletionResponse}。</li>
 * </ul>
 */
@Component
public class ChatCompletionsToGeminiAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsToGeminiAdapter.class);

    /**
     * OpenAI Chat Completions 特有但 Gemini 上游不支持的字段。
     */
    private static final Set<String> OPENAI_ONLY_FIELDS = Set.of(
            "logprobs", "top_logprobs", "n", "stop",
            "presence_penalty", "frequency_penalty", "seed",
            "parallel_tool_calls", "stream_options",
            "store", "include", "metadata",
            "max_completion_tokens", "reasoning_effort",
            "tool_choice", "tools", "response_format");

    private final OpenAiResponseAdapter responseAdapter;

    public ChatCompletionsToGeminiAdapter(OpenAiResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.CHAT_COMPLETIONS;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.GEMINI;
    }

    /**
     * 请求适配：清理 OpenAI 特有字段，转换 developer 角色为 user。
     */
    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        // 1. 转换 developer 角色为 user
        List<UnifiedMessage> adaptedMessages = mapDeveloperRole(request.messages());

        // 2. 清理 rawOptions
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());

        return new UnifiedChatRequest(
                request.model(), adaptedMessages, request.stream(),
                request.temperature(), request.maxTokens(),
                request.responseFormat(), cleaned);
    }

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

    private Map<String, Object> cleanRawOptions(Map<String, Object> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return rawOptions;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>(rawOptions);
        OPENAI_ONLY_FIELDS.forEach(cleaned::remove);
        return cleaned;
    }

    /**
     * 响应适配：将 Gemini 风格统一响应转为 Chat Completions 格式。
     */
    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiChatCompletionResponse) {
            return response;
        }
        log.debug("适配响应：CHAT_COMPLETIONS → GEMINI, model={}", publicModel);
        OpenAiChatCompletionResponse adapted = responseAdapter.toOpenAi(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }
}
