package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI Chat Completions 响应和网关统一响应之间的适配器。
 */
@Component
public class OpenAiResponseAdapter {

    public UnifiedChatResponse toUnified(OpenAiChatCompletionResponse response) {
        UnifiedUsage usage = null;
        if (response.getUsage() != null) {
            usage = new UnifiedUsage(
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCompletionTokens(),
                    response.getUsage().getTotalTokens(),
                    cacheReadInputTokens(response.getUsage())
            );
        }
        List<UnifiedMessage> messages = response.getChoices() == null ? List.of() : response.getChoices().stream()
                .map(choice -> toUnifiedMessage(choice.getMessage(), choice.getFinishReason()))
                .toList();
        String systemFingerprint = response.getSystemFingerprint();
        UnifiedChatResponse unified = new UnifiedChatResponse(response.getId(), response.getModel(), messages, usage, response);
        if (systemFingerprint != null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("system_fingerprint", systemFingerprint);
            // store as additional info on the unified response
        }
        return unified;
    }

    private UnifiedMessage toUnifiedMessage(OpenAiMessage message, String finishReason) {
        if (message == null) {
            return new UnifiedMessage("assistant", null, null, finishReason);
        }
        Map<String, Object> options = new LinkedHashMap<>();
        if (message.getToolCalls() != null) options.put("tool_calls", message.getToolCalls());
        if (message.getToolCallId() != null) options.put("tool_call_id", message.getToolCallId());
        if (message.getReasoningContent() != null) options.put("reasoning_content", message.getReasoningContent());
        options.putAll(message.getAdditionalProperties());
        return new UnifiedMessage(message.getRole(), message.getContent(), message.getName(), finishReason,
                options.isEmpty() ? null : options);
    }

    public OpenAiChatCompletionResponse toOpenAi(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiChatCompletionResponse openAiResponse) {
            openAiResponse.setModel(publicModel);
            return openAiResponse;
        }
        OpenAiChatCompletionResponse openAiResponse = new OpenAiChatCompletionResponse();
        openAiResponse.setId(response.id() == null ? "chatcmpl-" + UUID.randomUUID() : response.id());
        openAiResponse.setObject("chat.completion");
        openAiResponse.setCreated(System.currentTimeMillis() / 1000);
        openAiResponse.setModel(publicModel);
        openAiResponse.setChoices(toChoices(response.messages()));
        if (response.usage() != null) {
            OpenAiChatCompletionResponse.Usage usage = new OpenAiChatCompletionResponse.Usage();
            usage.setPromptTokens(response.usage().inputTokens());
            usage.setCompletionTokens(response.usage().outputTokens());
            usage.setTotalTokens(response.usage().totalTokens());
            usage.setCachedTokens(response.usage().cacheReadInputTokens());
            openAiResponse.setUsage(usage);
        }
        return openAiResponse;
    }

    /**
     * 兼容 OpenAI 标准 prompt_tokens_details.cached_tokens 以及常见代理网关的缓存命中字段。
     */
    private Integer cacheReadInputTokens(OpenAiChatCompletionResponse.Usage usage) {
        if (usage.getPromptTokensDetails() != null && usage.getPromptTokensDetails().getCachedTokens() != null) {
            return usage.getPromptTokensDetails().getCachedTokens();
        }
        if (usage.getCachedTokens() != null) {
            return usage.getCachedTokens();
        }
        if (usage.getCacheReadInputTokens() != null) {
            return usage.getCacheReadInputTokens();
        }
        return usage.getPromptCacheHitTokens();
    }

    /**
     * 跨协议路由时把统一消息合成为 OpenAI choices，保留实际的 finish_reason 和 tool_calls。
     */
    private List<OpenAiChatCompletionResponse.Choice> toChoices(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            messages = List.of(new UnifiedMessage("assistant", "", null, "stop"));
        }
        final int[] index = {0};
        return messages.stream().map(message -> {
            OpenAiChatCompletionResponse.Choice choice = new OpenAiChatCompletionResponse.Choice();
            choice.setIndex(index[0]++);
            choice.setMessage(toOpenAiMessage(message));
            // 使用实际 finish_reason，未设置时默认 "stop"
            choice.setFinishReason(message.finishReason() != null ? message.finishReason() : "stop");
            return choice;
        }).toList();
    }

    /**
     * 将统一消息转换为 OpenAI 消息对象，携带 tool_calls、reasoning_content 等元数据。
     */
    @SuppressWarnings("unchecked")
    private OpenAiMessage toOpenAiMessage(UnifiedMessage message) {
        OpenAiMessage openAiMessage = new OpenAiMessage();
        openAiMessage.setRole(message.role() == null ? "assistant" : message.role());
        openAiMessage.setContent(message.content());
        openAiMessage.setName(message.name());
        if (message.options() != null) {
            Object toolCalls = message.options().get("tool_calls");
            if (toolCalls instanceof List<?> tcList) {
                openAiMessage.setToolCalls((List<Map<String, Object>>) tcList);
            }
            Object toolCallId = message.options().get("tool_call_id");
            if (toolCallId instanceof String tci) {
                openAiMessage.setToolCallId(tci);
            }
            Object reasoningContent = message.options().get("reasoning_content");
            if (reasoningContent instanceof String rc) {
                openAiMessage.setReasoningContent(rc);
            }
        }
        return openAiMessage;
    }
}
