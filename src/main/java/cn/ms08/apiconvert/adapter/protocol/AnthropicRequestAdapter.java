package cn.ms08.apiconvert.adapter.protocol;

import cn.ms08.apiconvert.dto.AnthropicMessage;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Anthropic Messages 请求和网关统一请求之间的适配器。
 */
@Component
public class AnthropicRequestAdapter {

    public UnifiedChatRequest toUnified(AnthropicMessageRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (CollectionUtils.isEmpty(request.getMessages())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "messages is required");
        }
        Map<String, Object> rawOptions = new LinkedHashMap<>(request.getAdditionalProperties());
        if (request.getSystem() != null) {
            rawOptions.put("system", request.getSystem());
        }
        return new UnifiedChatRequest(
                request.getModel(),
                request.getMessages().stream().map(this::toUnifiedMessage).toList(),
                request.getStream(),
                request.getTemperature(),
                request.getMaxTokens(),
                null,
                rawOptions
        );
    }

    public AnthropicMessageRequest toProviderRequest(UnifiedChatRequest request, String providerModel) {
        return toProviderRequest(request, providerModel, false);
    }

    public AnthropicMessageRequest toProviderRequest(UnifiedChatRequest request, String providerModel, boolean stream) {
        AnthropicMessageRequest providerRequest = new AnthropicMessageRequest();
        providerRequest.setModel(providerModel);
        providerRequest.setMessages(request.messages().stream()
                .filter(message -> !"system".equals(message.role()))
                .map(this::toAnthropicMessage)
                .toList());
        providerRequest.setStream(stream);
        providerRequest.setTemperature(request.temperature());
        providerRequest.setMaxTokens(request.maxTokens());
        if (request.rawOptions() != null) {
            request.rawOptions().forEach((key, value) -> {
                if ("system".equals(key)) {
                    providerRequest.setSystem(value);
                } else if (!"response_format".equals(key)) {
                    providerRequest.setAdditionalProperty(key, value);
                }
            });
        }
        if (providerRequest.getSystem() == null) {
            String system = request.messages().stream()
                    .filter(message -> "system".equals(message.role()))
                    .map(UnifiedMessage::content)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .findFirst()
                    .orElse(null);
            providerRequest.setSystem(system);
        }
        return providerRequest;
    }

    private UnifiedMessage toUnifiedMessage(AnthropicMessage message) {
        return new UnifiedMessage(message.getRole(), message.getContent(), null);
    }

    private AnthropicMessage toAnthropicMessage(UnifiedMessage message) {
        AnthropicMessage anthropicMessage = new AnthropicMessage();
        anthropicMessage.setRole(message.role());
        anthropicMessage.setContent(message.content());
        return anthropicMessage;
    }
}
