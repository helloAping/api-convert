package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenCode 供应商 — 支持 Chat Completions 和 Anthropic Messages 两种端点。
 */
@Component
public class OpenCodeProviderClient implements AiProviderClient {

    private final OpenAiChatCapability chatCapability;
    private final AnthropicMessagesCapability anthropicCapability;

    public OpenCodeProviderClient(RestClient.Builder restClientBuilder,
                                  OpenAiRequestAdapter openAiRequestAdapter,
                                  OpenAiResponseAdapter openAiResponseAdapter,
                                  AnthropicRequestAdapter anthropicRequestAdapter,
                                  AnthropicResponseAdapter anthropicResponseAdapter) {
        this.chatCapability = new OpenAiChatCapability(restClientBuilder, openAiRequestAdapter, openAiResponseAdapter);
        this.anthropicCapability = new AnthropicMessagesCapability(restClientBuilder, anthropicRequestAdapter, anthropicResponseAdapter);
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENCODE;
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
        return List.of();
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "OpenCode 暂不支持通用余额查询，请在供应商控制台查看。",
                null, null, null, "", "");
    }
}
