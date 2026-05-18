package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anthropic Messages 响应和网关统一响应之间的适配器。
 */
@Component
public class AnthropicResponseAdapter {

    /**
     * 将 Anthropic 上游响应转换为统一响应，保留原始响应用于同协议透传。
     */
    public UnifiedChatResponse toUnified(AnthropicMessageResponse response) {
        UnifiedUsage usage = null;
        if (response.getUsage() != null) {
            Integer inputTokens = response.getUsage().getInputTokens();
            Integer outputTokens = response.getUsage().getOutputTokens();
            Integer totalTokens = inputTokens == null || outputTokens == null ? null : inputTokens + outputTokens;
            usage = new UnifiedUsage(inputTokens, outputTokens, totalTokens, response.getUsage().getCacheReadInputTokens());
        }
        return new UnifiedChatResponse(response.getId(), response.getModel(),
                List.of(new UnifiedMessage("assistant", response.getContent(), null)), usage, response);
    }

    /**
     * 将统一响应输出为 Anthropic 兼容格式；跨协议路由时会合成 Anthropic 响应体。
     */
    public AnthropicMessageResponse toAnthropic(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof AnthropicMessageResponse anthropicResponse) {
            anthropicResponse.setModel(publicModel);
            return anthropicResponse;
        }
        AnthropicMessageResponse anthropicResponse = new AnthropicMessageResponse();
        anthropicResponse.setId(response.id() == null ? "msg_" + UUID.randomUUID() : response.id());
        anthropicResponse.setType("message");
        anthropicResponse.setRole("assistant");
        anthropicResponse.setModel(publicModel);
        anthropicResponse.setStopReason("end_turn");
        anthropicResponse.setContent(response.messages().stream()
                .map(UnifiedMessage::content)
                .map(this::toContentBlock)
                .toList());
        if (response.usage() != null) {
            AnthropicMessageResponse.Usage usage = new AnthropicMessageResponse.Usage();
            usage.setInputTokens(response.usage().inputTokens());
            usage.setOutputTokens(response.usage().outputTokens());
            anthropicResponse.setUsage(usage);
        }
        return anthropicResponse;
    }

    /**
     * 将统一内容转换为 Anthropic content block；已是块结构时尽量原样返回。
     */
    private Object toContentBlock(Object content) {
        if (content instanceof Map<?, ?> || content instanceof List<?>) {
            return content;
        }
        return Map.of("type", "text", "text", content == null ? "" : String.valueOf(content));
    }
}
