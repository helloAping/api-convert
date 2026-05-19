package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek Chat 供应商客户端，协议载荷沿用 Chat Completions，但在路由层作为独立供应商类型管理。
 */
@Component
public class DeepSeekChatProviderClient extends OpenAiCompatibleProviderClient {

    public DeepSeekChatProviderClient(RestClient.Builder restClientBuilder, OpenAiRequestAdapter requestAdapter,
                                      OpenAiResponseAdapter responseAdapter) {
        super(restClientBuilder, requestAdapter, responseAdapter);
    }

    @Override
    public ProviderType type() {
        return ProviderType.DEEPSEEK_CHAT;
    }

    @Override
    protected OpenAiChatCompletionRequest prepareRequestBody(ModelRoute route, OpenAiChatCompletionRequest request) {
        if (request == null || request.getMessages() == null) {
            return request;
        }
        for (OpenAiMessage message : request.getMessages()) {
            if ("assistant".equals(message.getRole()) && message.getReasoningContent() == null) {
                message.setReasoningContent("");
            }
        }
        return request;
    }
}
