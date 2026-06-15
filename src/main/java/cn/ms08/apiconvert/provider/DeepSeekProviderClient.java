package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 供应商 — 支持 Chat Completions 和 Anthropic Messages 两种端点。
 */
@Component
public class DeepSeekProviderClient implements AiProviderClient {

    private final OpenAiChatCapability chatCapability;
    private final AnthropicMessagesCapability anthropicCapability;

    public DeepSeekProviderClient(RestClient.Builder restClientBuilder,
                                  OpenAiRequestAdapter openAiRequestAdapter,
                                  OpenAiResponseAdapter openAiResponseAdapter,
                                  AnthropicRequestAdapter anthropicRequestAdapter,
                                  AnthropicResponseAdapter anthropicResponseAdapter) {
        this.chatCapability = new DeepSeekChatCapability(restClientBuilder, openAiRequestAdapter, openAiResponseAdapter);
        this.anthropicCapability = new DeepSeekAnthropicCapability(restClientBuilder, anthropicRequestAdapter, anthropicResponseAdapter);
    }

    @Override
    public ProviderType type() {
        return ProviderType.DEEPSEEK;
    }

    @Override
    public Map<EndpointType, EndpointCapability> capabilities() {
        return Map.of(
                EndpointType.CHAT_COMPLETIONS, chatCapability,
                EndpointType.ANTHROPIC_MESSAGES, anthropicCapability
        );
    }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        // Use Chat capability's model fetch via the parent class
        return List.of();
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "DeepSeek 暂不支持通用余额查询，请在供应商控制台查看。",
                null, null, null, "", "");
    }

    // ---- Chat capability with reasoning_content fallback ----

    private static class DeepSeekChatCapability extends OpenAiChatCapability {
        DeepSeekChatCapability(RestClient.Builder b, OpenAiRequestAdapter ra, OpenAiResponseAdapter rpa) {
            super(b, ra, rpa);
        }

        @Override
        protected OpenAiChatCompletionRequest prepareRequestBody(ModelRoute route, OpenAiChatCompletionRequest request) {
            if (request != null && request.getMessages() != null) {
                for (OpenAiMessage message : request.getMessages()) {
                    if ("assistant".equals(message.getRole()) && message.getReasoningContent() == null) {
                        message.setReasoningContent("");
                    }
                }
            }
            return request;
        }
    }

    // ---- Anthropic capability with thinking content normalization ----

    private static class DeepSeekAnthropicCapability extends AnthropicMessagesCapability {
        DeepSeekAnthropicCapability(RestClient.Builder b, AnthropicRequestAdapter ra, AnthropicResponseAdapter rpa) {
            super(b, ra, rpa);
        }

        @Override
        protected AnthropicMessageRequest prepareRequestBody(ModelRoute route, AnthropicMessageRequest request) {
            if (request != null && request.getMessages() != null) {
                for (cn.ms08.apiconvert.dto.AnthropicMessage message : request.getMessages()) {
                    message.setContent(normalizeThinkingContent(message.getContent()));
                }
            }
            return request;
        }

        private Object normalizeThinkingContent(Object content) {
            if (!(content instanceof List<?> contentList)) return content;
            List<Object> normalized = new ArrayList<>(contentList.size());
            for (Object block : contentList) {
                if (!(block instanceof Map<?, ?> blockMap)) {
                    normalized.add(block);
                    continue;
                }
                String type = blockMap.containsKey("type") ? String.valueOf(blockMap.get("type")) : "";
                if (!"thinking".equals(type)) {
                    normalized.add(block);
                    continue;
                }
                Map<String, Object> normalizedBlock = new LinkedHashMap<>();
                blockMap.forEach((key, value) -> normalizedBlock.put(String.valueOf(key), value));
                if (normalizedBlock.get("thinking") == null) {
                    Object text = normalizedBlock.get("text");
                    normalizedBlock.put("thinking", text == null ? "" : String.valueOf(text));
                }
                normalized.add(normalizedBlock);
            }
            return normalized;
        }
    }
}
