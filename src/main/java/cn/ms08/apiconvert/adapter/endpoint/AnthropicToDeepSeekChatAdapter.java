package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * Anthropic Messages endpoint -> DeepSeek Chat upstream.
 */
@Component
public class AnthropicToDeepSeekChatAdapter extends AnthropicToOpenAiCompatibleAdapter {

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_CHAT;
    }
}
