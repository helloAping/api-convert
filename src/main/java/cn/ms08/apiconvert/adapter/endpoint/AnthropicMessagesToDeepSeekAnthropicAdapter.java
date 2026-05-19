package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import org.springframework.stereotype.Component;

/**
 * Anthropic Messages endpoint -> DeepSeek Anthropic API upstream, same protocol response adapter.
 */
@Component
public class AnthropicMessagesToDeepSeekAnthropicAdapter implements EndpointProviderAdapter {

    private final AnthropicResponseAdapter responseAdapter;

    public AnthropicMessagesToDeepSeekAnthropicAdapter(AnthropicResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.ANTHROPIC_MESSAGES;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_ANTHROPIC;
    }

    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        return request;
    }

    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        AnthropicMessageResponse adapted = responseAdapter.toAnthropic(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }
}
