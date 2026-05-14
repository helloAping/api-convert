package cn.ms08.apiconvert.adapter;

import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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
                .map(OpenAiChatCompletionResponse.Choice::getMessage)
                .map(this::toUnifiedMessage)
                .toList();
        return new UnifiedChatResponse(response.getId(), response.getModel(), messages, usage, response);
    }

    private UnifiedMessage toUnifiedMessage(OpenAiMessage message) {
        if (message == null) {
            return new UnifiedMessage("assistant", null, null);
        }
        return new UnifiedMessage(message.getRole(), message.getContent(), message.getName());
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
     * 跨协议路由时把统一消息合成为 OpenAI choices。
     */
    private List<OpenAiChatCompletionResponse.Choice> toChoices(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            messages = List.of(new UnifiedMessage("assistant", "", null));
        }
        final int[] index = {0};
        return messages.stream().map(message -> {
            OpenAiChatCompletionResponse.Choice choice = new OpenAiChatCompletionResponse.Choice();
            choice.setIndex(index[0]++);
            choice.setMessage(toOpenAiMessage(message));
            choice.setFinishReason("stop");
            return choice;
        }).toList();
    }

    /**
     * 将统一消息转换为 OpenAI 消息对象。
     */
    private OpenAiMessage toOpenAiMessage(UnifiedMessage message) {
        OpenAiMessage openAiMessage = new OpenAiMessage();
        openAiMessage.setRole(message.role() == null ? "assistant" : message.role());
        openAiMessage.setContent(message.content());
        openAiMessage.setName(message.name());
        return openAiMessage;
    }
}
