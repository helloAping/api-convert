package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * OpenAI Responses endpoint -> DeepSeek Anthropic API upstream.
 */
@Component
public class ResponsesToDeepSeekAnthropicAdapter extends ResponsesToAnthropicAdapter {

    public ResponsesToDeepSeekAnthropicAdapter(OpenAiResponsesResponseAdapter responseAdapter) {
        super(responseAdapter);
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_ANTHROPIC;
    }
}
