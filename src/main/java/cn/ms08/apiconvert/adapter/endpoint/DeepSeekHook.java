package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 供应商特化 hook，覆盖 Chat Completions 与 Anthropic Messages 两种源端点协议：
 * <ul>
 *   <li><b>Chat Completions 源</b>：DeepSeek 强制要求 assistant 消息带
 *   {@code reasoning_content} 字段（即使为空字符串），否则上游返回 400。
 *   hook 在请求前为每条 assistant 消息补默认值。</li>
 *   <li><b>Anthropic Messages 源</b>：DeepSeek 要求 {@code thinking} 块必须带
 *   {@code thinking} 字段（可以为空字符串），hook 在请求前补字段。</li>
 * </ul>
 *
 * <p>
 * 之前这些逻辑写在 {@code DeepSeekProviderClient.beforeChatRequest} 与
 * {@code DeepSeekProviderClient.beforeAnthropicRequest} 里，跟供应商实现耦合在
 * {@code BaseAiProviderClient} 的钩子机制上。改成 hook 后只跟 ProviderType 绑定，
 * 跟具体的协议实现解耦——其他支持相同 endpoint 协议但有相同诉求的供应商（比如未来
 * 自建 DeepSeek-兼容协议的代理）只需要再写一个 {@code @HooksForProvider(ProviderType.XXX)} 的 hook 即可。
 * </p>
 */
@Component
@HooksForProvider(ProviderType.DEEPSEEK)
public class DeepSeekHook implements ProviderHook {

    @Override
    public UnifiedChatRequest preProcess(UnifiedChatRequest request, EndpointType sourceEndpoint, ModelRoute route) {
        if (request == null || request.messages() == null) {
            return request;
        }
        if (sourceEndpoint == EndpointType.CHAT_COMPLETIONS) {
            return preProcessChat(request);
        }
        if (sourceEndpoint == EndpointType.ANTHROPIC_MESSAGES) {
            return preProcessAnthropic(request);
        }
        return request;
    }

    /**
     * Chat Completions：DeepSeek 要求 assistant 消息带 {@code reasoning_content} 字段
     * （即使为空字符串），否则 400。
     */
    private UnifiedChatRequest preProcessChat(UnifiedChatRequest request) {
        List<UnifiedMessage> original = request.messages();
        List<UnifiedMessage> messages = new ArrayList<>(original);
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            UnifiedMessage msg = messages.get(i);
            if (!"assistant".equals(msg.role())) {
                continue;
            }
            Map<String, Object> options = msg.options() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(msg.options());
            if (options.containsKey("reasoning_content")) {
                continue;
            }
            options.put("reasoning_content", "");
            messages.set(i, new UnifiedMessage(msg.role(), msg.content(), msg.name(), msg.finishReason(), options));
            changed = true;
        }
        if (!changed) {
            return request;
        }
        return new UnifiedChatRequest(
                request.model(), messages, request.stream(),
                request.temperature(), request.maxTokens(),
                request.responseFormat(), request.rawOptions());
    }

    /**
     * Anthropic Messages：DeepSeek 要求 {@code thinking} 块带 {@code thinking} 字段
     * （可以为空），原样 content 块缺字段会触发上游 400。
     */
    private UnifiedChatRequest preProcessAnthropic(UnifiedChatRequest request) {
        List<UnifiedMessage> original = request.messages();
        List<UnifiedMessage> messages = new ArrayList<>(original);
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            UnifiedMessage msg = messages.get(i);
            Object content = msg.content();
            if (!(content instanceof List<?> blocks)) {
                continue;
            }
            List<Object> mutableBlocks = new ArrayList<>(blocks);
            boolean blockChanged = false;
            for (int j = 0; j < mutableBlocks.size(); j++) {
                Object block = mutableBlocks.get(j);
                if (!(block instanceof Map<?, ?> m) || !"thinking".equals(String.valueOf(m.get("type")))) {
                    continue;
                }
                Map<String, Object> mutable = new LinkedHashMap<>((Map<String, Object>) m);
                if (!mutable.containsKey("thinking") || String.valueOf(mutable.get("thinking")).isBlank()) {
                    Object text = mutable.get("text");
                    mutable.put("thinking", text == null ? "" : String.valueOf(text));
                    mutableBlocks.set(j, mutable);
                    blockChanged = true;
                }
            }
            if (blockChanged) {
                messages.set(i, new UnifiedMessage(msg.role(), mutableBlocks, msg.name(), msg.finishReason(), msg.options()));
                changed = true;
            }
        }
        if (!changed) {
            return request;
        }
        return new UnifiedChatRequest(
                request.model(), messages, request.stream(),
                request.temperature(), request.maxTokens(),
                request.responseFormat(), request.rawOptions());
    }
}
