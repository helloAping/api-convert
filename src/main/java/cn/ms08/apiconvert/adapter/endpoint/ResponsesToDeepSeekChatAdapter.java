package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses endpoint -> DeepSeek Chat upstream.
 */
@Component
public class ResponsesToDeepSeekChatAdapter extends ResponsesToOpenAiCompatibleAdapter {

    public ResponsesToDeepSeekChatAdapter(OpenAiResponsesResponseAdapter responseAdapter) {
        super(responseAdapter);
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_CHAT;
    }

    /**
     * DeepSeek Chat thinking 模式要求历史 assistant tool_calls 回传 reasoning_content。
     */
    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        Map<String, Object> cleaned = cleanRawOptions(request.rawOptions());
        List<UnifiedMessage> messages = normalizeMessagesForDeepSeekChat(request.messages(), request.rawOptions());
        return new UnifiedChatRequest(
                request.model(), messages, request.stream(), request.temperature(), request.maxTokens(),
                request.responseFormat(), cleaned);
    }

    private List<UnifiedMessage> normalizeMessagesForDeepSeekChat(List<UnifiedMessage> messages, Map<String, Object> rawOptions) {
        List<UnifiedMessage> result = new ArrayList<>();
        String pendingReasoningContent = null;
        Object instructions = rawOptions == null ? null : rawOptions.get("instructions");
        if (instructions != null && StringUtils.hasText(String.valueOf(instructions))) {
            result.add(new UnifiedMessage("system", String.valueOf(instructions), null));
        }
        if (messages == null) {
            return result;
        }
        for (UnifiedMessage message : messages) {
            String role = "developer".equals(message.role()) ? "system" : message.role();
            ReasoningParts reasoningParts = "assistant".equals(role) ? extractReasoningParts(message.content()) : null;
            if ("assistant".equals(role) && hasToolCalls(message)) {
                UnifiedMessage normalized = withReasoningForChat(message, reasoningParts, pendingReasoningContent);
                pendingReasoningContent = null;
                mergeToolCallsIntoLastAssistant(result, normalized);
            } else if ("assistant".equals(role) && reasoningParts != null) {
                if (reasoningParts.hasOnlyReasoning()) {
                    pendingReasoningContent = mergeReasoningText(pendingReasoningContent, reasoningParts.text());
                    continue;
                }
                result.add(new UnifiedMessage(role, normalizeContentForChat(reasoningParts.filtered()), message.name(),
                        message.finishReason(), reasoningParts.toOptions(message.options())));
            } else {
                if (!"assistant".equals(role)) {
                    pendingReasoningContent = null;
                }
                result.add(new UnifiedMessage(role, normalizeContentForChat(message.content()), message.name(),
                        message.finishReason(), message.options()));
            }
        }
        reorderToolSequence(result);
        return ChatToolSequenceNormalizer.normalizeForStrictChat(result);
    }

    private UnifiedMessage withReasoningForChat(UnifiedMessage message, ReasoningParts parts, String pendingReasoningContent) {
        Object content = parts == null ? normalizeContentForChat(message.content()) : normalizeContentForChat(parts.filtered());
        Map<String, Object> options = message.options() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(message.options());
        if (parts != null) {
            options.put("reasoning_content", parts.text());
        } else if (StringUtils.hasText(pendingReasoningContent)) {
            options.putIfAbsent("reasoning_content", pendingReasoningContent);
        }
        return new UnifiedMessage("assistant", content, message.name(), message.finishReason(),
                options.isEmpty() ? null : options);
    }

    private static String mergeReasoningText(String existing, String next) {
        if (!StringUtils.hasText(existing)) {
            return next;
        }
        if (!StringUtils.hasText(next)) {
            return existing;
        }
        return existing + next;
    }
}
