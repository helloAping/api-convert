package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Anthropic API 供应商客户端，隔离 DeepSeek thinking 块回传规则。
 */
@Component
public class DeepSeekAnthropicProviderClient extends AnthropicProviderClient {

    public DeepSeekAnthropicProviderClient(RestClient.Builder restClientBuilder, AnthropicRequestAdapter requestAdapter,
                                           AnthropicResponseAdapter responseAdapter) {
        super(restClientBuilder, requestAdapter, responseAdapter);
    }

    @Override
    public ProviderType type() {
        return ProviderType.DEEPSEEK_ANTHROPIC;
    }

    /**
     * DeepSeek Anthropic API 在 thinking 模式续轮时要求 thinking 块继续携带 thinking 字段。
     */
    @Override
    protected AnthropicMessageRequest prepareRequestBody(ModelRoute route, AnthropicMessageRequest request) {
        if (request == null || request.getMessages() == null) {
            return request;
        }
        for (cn.ms08.apiconvert.dto.AnthropicMessage message : request.getMessages()) {
            message.setContent(normalizeThinkingContent(message.getContent()));
        }
        return request;
    }

    private Object normalizeThinkingContent(Object content) {
        if (!(content instanceof List<?> contentList)) {
            return content;
        }
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
