package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * Chat Completions endpoint -> DeepSeek Anthropic API upstream.
 */
@Component
public class ChatCompletionsToDeepSeekAnthropicAdapter extends ChatCompletionsToAnthropicAdapter {

    public ChatCompletionsToDeepSeekAnthropicAdapter(OpenAiResponseAdapter responseAdapter) {
        super(responseAdapter);
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_ANTHROPIC;
    }
}
