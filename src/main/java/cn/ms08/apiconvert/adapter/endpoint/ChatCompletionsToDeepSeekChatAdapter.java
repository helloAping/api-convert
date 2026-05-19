package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chat Completions endpoint -> DeepSeek Chat upstream, same protocol response adapter.
 */
@Component
public class ChatCompletionsToDeepSeekChatAdapter implements EndpointProviderAdapter {

    private final OpenAiResponseAdapter responseAdapter;

    public ChatCompletionsToDeepSeekChatAdapter(OpenAiResponseAdapter responseAdapter) {
        this.responseAdapter = responseAdapter;
    }

    @Override
    public EndpointType sourceEndpoint() {
        return EndpointType.CHAT_COMPLETIONS;
    }

    @Override
    public ProviderType targetProvider() {
        return ProviderType.DEEPSEEK_CHAT;
    }

    @Override
    public UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        List<UnifiedMessage> messages = ChatToolSequenceNormalizer.normalizeForStrictChat(request.messages());
        return new UnifiedChatRequest(
                request.model(), messages, request.stream(), request.temperature(), request.maxTokens(),
                request.responseFormat(), request.rawOptions());
    }

    @Override
    public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
        OpenAiChatCompletionResponse adapted = responseAdapter.toOpenAi(response, publicModel);
        return new UnifiedChatResponse(response.id(), publicModel, response.messages(), response.usage(), adapted);
    }
}
