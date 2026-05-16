package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Anthropic Messages 端点 → Gemini 供应商的接口适配器。
 * <p>
 * 当 {@code /v1/messages} 端点的请求被路由到 {@code GEMINI} 类型的上游时，
 * 上游以 Gemini generateContent 格式返回响应（已转为统一格式），此适配器将统一响应
 * 转换回 Anthropic Messages 格式。
 * </p>
 *
 * <h3>适配边界</h3>
 * <ul>
 *   <li><b>请求</b>：清理 {@code rawOptions} 中 Anthropic 特有但 Gemini 不支持的字段，
 *       并将 {@code developer} 角色消息映射为 {@code user}。</li>
 *   <li><b>响应</b>：将 Gemini 风格统一响应通过 {@code AnthropicResponseAdapter.toAnthropic()}
 *       转为 {@code AnthropicMessageResponse}。</li>
 * </ul>
 */
@Component
public class AnthropicMessagesToGeminiAdapter implements EndpointProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicMessagesToGeminiAdapter.class);

    /**
     * Anthropic Messages 特有但 Gemini 上游不支持的字段。
     */
    private static final Set<String> ANTHROPIC_ONLY_FIELDS = Set.of(
            "thinking", "context_management", "output_config",
            "anthropic_version", "anthropic_beta",
            "prompt_cache_key", "client_metadata", "store", "include",
            "stream_options");

    /**
     * Anthropic 内容块类型列表，Gemini 不识别这些类型，需在请求适配时过滤。
     */
    private static final Set<String> ANTHROPIC_CONTENT_TYPES = Set.of(
            "thinking", "redacted_thinking", "tool_use", "tool_result");

    private final AnthropicResponseAdapter responseAdapter;

    public AnthropicMessagesToGeminiAdapter(AnthropicResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.ANTHROPIC_MESSAGES;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.GEMINI;
    }

    /**
     * 请求适配：清理 Anthropic 特有字段，过滤计费头系统消息，
     * 移除消息内容块中的 Anthropic 特有类型（如 thinking）。
     */
    @Override
    @SuppressWarnings("unchecked")
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        // 1. 转换 developer 角色为 user
        List<UnifiedMessage> adaptedMessages = mapDeveloperRole(request.messages());
        // 2. 过滤 Anthropic 内容块类型
        adaptedMessages = cleanMessageContent(adaptedMessages);
        // 3. 清理 rawOptions
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
        ANTHROPIC_ONLY_FIELDS.forEach(cleaned::remove);

        // 清理 system 字段中的 x-anthropic-billing-header
        Object system = cleaned.get("system");
        if (system instanceof List<?> systemList) {
            List<Object> filtered = new ArrayList<>();
            for (Object entry : systemList) {
                if (entry instanceof Map<?, ?> block) {
                    String text = block.containsKey("text") ? String.valueOf(block.get("text")) : "";
                    if (text.contains("x-anthropic-billing-header")) {
                        continue;
                    }
                }
                filtered.add(entry);
            }
            if (filtered.isEmpty()) {
                cleaned.remove("system");
            } else {
                cleaned.put("system", filtered);
            }
        } else if (system instanceof String systemStr) {
            if (systemStr.contains("x-anthropic-billing-header")) {
                cleaned.remove("system");
            }
        }
        return cleaned;
    }

    /**
     * 清理消息内容块：过滤掉 Anthropic 特有但 Gemini 不理解的类型（thinking 等）。
     */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> cleanMessageContent(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        return messages.stream()
                .map(this::filterContentBlocks)
                .toList();
    }

    /**
     * 过滤单条消息的 content 字段中的 Anthropic 特有内容块。
     */
    @SuppressWarnings("unchecked")
    private UnifiedMessage filterContentBlocks(UnifiedMessage message) {
        Object content = message.content();
        if (!(content instanceof List<?> contentList)) {
            return message;
        }
        List<Object> filtered = new ArrayList<>();
        for (Object block : contentList) {
            if (!(block instanceof Map<?, ?> blockMap)) {
                filtered.add(block);
                continue;
            }
            String type = blockMap.containsKey("type") ? String.valueOf(blockMap.get("type")) : "";
            if (ANTHROPIC_CONTENT_TYPES.contains(type)) {
                continue;
            }
            filtered.add(block);
        }
        if (filtered.size() == 1 && filtered.get(0) instanceof Map<?, ?> single) {
            String singleType = single.containsKey("type") ? String.valueOf(single.get("type")) : "";
            if ("text".equals(singleType) && single.containsKey("text")) {
                return new UnifiedMessage(message.role(), String.valueOf(single.get("text")),
                        message.name(), message.finishReason(), message.options());
            }
        }
        if (filtered.isEmpty()) {
            return new UnifiedMessage(message.role(), "", message.name(), message.finishReason(), message.options());
        }
        return new UnifiedMessage(message.role(), filtered, message.name(), message.finishReason(), message.options());
    }

    /**
     * 响应适配：将 Gemini 风格统一响应转为 Anthropic Messages 格式。
     */
    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof AnthropicMessageResponse) {
            return response;
        }
        log.debug("适配响应：ANTHROPIC_MESSAGES → GEMINI, model={}", publicModel);
        AnthropicMessageResponse adapted = responseAdapter.toAnthropic(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }
}
