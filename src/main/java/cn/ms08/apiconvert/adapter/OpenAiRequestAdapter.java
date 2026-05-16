package cn.ms08.apiconvert.adapter;

import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiMessage;
import cn.ms08.apiconvert.dto.ResponseFormat;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenAiRequestAdapter {

    public UnifiedChatRequest toUnified(OpenAiChatCompletionRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (CollectionUtils.isEmpty(request.getMessages())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "messages is required");
        }
        ResponseFormat responseFormat = request.getResponseFormat();
        Map<String, Object> rawOptions = new LinkedHashMap<>(request.getAdditionalProperties());
        if (responseFormat == null && rawOptions.containsKey("response_format")) {
            Object raw = rawOptions.remove("response_format");
            if (raw instanceof Map<?, ?> map) {
                String type = map.containsKey("type") ? String.valueOf(map.get("type")) : null;
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonSchema = map.get("json_schema") instanceof Map
                        ? (Map<String, Object>) map.get("json_schema") : null;
                responseFormat = new ResponseFormat(type, jsonSchema);
            }
        }
        rawOptions.remove("response_format");
        return new UnifiedChatRequest(
                request.getModel(),
                request.getMessages().stream().map(this::toUnifiedMessage).toList(),
                request.getStream(),
                request.getTemperature(),
                request.getMaxTokens(),
                responseFormat,
                rawOptions
        );
    }

    private UnifiedMessage toUnifiedMessage(OpenAiMessage message) {
        return new UnifiedMessage(message.getRole(), message.getContent(), message.getName());
    }

    public OpenAiChatCompletionRequest toProviderRequest(UnifiedChatRequest request, String providerModel) {
        return toProviderRequest(request, providerModel, false);
    }

    /**
     * 将统一请求转换为 OpenAI 兼容上游请求，流式透传时保留 stream=true。
     */
    public OpenAiChatCompletionRequest toProviderRequest(UnifiedChatRequest request, String providerModel, boolean stream) {
        OpenAiChatCompletionRequest providerRequest = new OpenAiChatCompletionRequest();
        providerRequest.setModel(providerModel);
        providerRequest.setMessages(request.messages().stream().map(this::toOpenAiMessage).toList());
        providerRequest.setStream(stream);
        providerRequest.setTemperature(request.temperature());
        providerRequest.setMaxTokens(request.maxTokens());
        providerRequest.setResponseFormat(request.responseFormat());
        if (request.rawOptions() != null) {
            request.rawOptions().forEach((key, value) -> {
                if (!"response_format".equals(key)) {
                    providerRequest.setAdditionalProperty(key, value);
                }
            });
        }
        if (stream) {
            includeStreamUsage(providerRequest);
        }
        return providerRequest;
    }

    /**
     * 流式转发默认要求上游在最终 SSE 块返回 usage，便于请求日志统计输入、输出和缓存读取 token。
     */
    private void includeStreamUsage(OpenAiChatCompletionRequest providerRequest) {
        Object existing = providerRequest.getAdditionalProperties().get("stream_options");
        Map<String, Object> streamOptions = new LinkedHashMap<>();
        if (existing instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> streamOptions.put(String.valueOf(key), value));
        }
        streamOptions.put("include_usage", true);
        providerRequest.setAdditionalProperty("stream_options", streamOptions);
    }

    private OpenAiMessage toOpenAiMessage(UnifiedMessage message) {
        OpenAiMessage openAiMessage = new OpenAiMessage();
        // 将 Responses API 的 developer 角色映射为 Chat Completions 的 system 角色，
        // 因为大多数上游兼容 API 不支持 developer 角色。
        String role = message.role();
        if ("developer".equals(role)) {
            role = "system";
        }
        openAiMessage.setRole(role);
        openAiMessage.setContent(message.content());
        openAiMessage.setName(message.name());
        return openAiMessage;
    }
}
