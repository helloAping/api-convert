package cn.ms08.apiconvert.adapter.protocol;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 请求和网关统一请求之间的适配器。
 */
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
        // 显式字段放入 rawOptions 以便上游请求适配和下游使用
        if (request.getStreamOptions() != null) rawOptions.put("stream_options", request.getStreamOptions());
        if (request.getTopP() != null) rawOptions.put("top_p", request.getTopP());
        if (request.getMaxCompletionTokens() != null) rawOptions.put("max_completion_tokens", request.getMaxCompletionTokens());
        if (request.getStop() != null) rawOptions.put("stop", request.getStop());
        if (request.getTools() != null) rawOptions.put("tools", request.getTools());
        if (request.getToolChoice() != null) rawOptions.put("tool_choice", request.getToolChoice());
        if (request.getUser() != null) rawOptions.put("user", request.getUser());
        if (request.getReasoningEffort() != null) rawOptions.put("reasoning_effort", request.getReasoningEffort());
        if (request.getSeed() != null) rawOptions.put("seed", request.getSeed());
        if (request.getPresencePenalty() != null) rawOptions.put("presence_penalty", request.getPresencePenalty());
        if (request.getFrequencyPenalty() != null) rawOptions.put("frequency_penalty", request.getFrequencyPenalty());
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
        Map<String, Object> options = new LinkedHashMap<>();
        if (message.getToolCalls() != null) options.put("tool_calls", message.getToolCalls());
        if (message.getToolCallId() != null) options.put("tool_call_id", message.getToolCallId());
        if (message.getReasoningContent() != null) options.put("reasoning_content", message.getReasoningContent());
        options.putAll(message.getAdditionalProperties());
        return new UnifiedMessage(message.getRole(), message.getContent(), message.getName(), null,
                options.isEmpty() ? null : options);
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
                if ("response_format".equals(key)) {
                    return;
                }
                if ("stream_options".equals(key)) {
                    // stream_options 有独立字段，使用 setStreamOptions() 处理
                    return;
                }
                if ("tools".equals(key)) {
                    // Chat Completions 仅支持 function 类型工具，过滤掉 code_interpreter 等其他类型
                    Object filtered = filterNonFunctionTools(value);
                    if (filtered != null) {
                        providerRequest.setAdditionalProperty(key, filtered);
                    }
                    return;
                }
                providerRequest.setAdditionalProperty(key, value);
            });
        }
        if (stream) {
            includeStreamUsage(providerRequest);
        }
        return providerRequest;
    }

    /**
     * 流式转发默认要求上游在最终 SSE 块返回 usage，便于请求日志统计输入、输出和缓存读取 token。
     * <p>
     * 使用显式的 {@code setStreamOptions()} 而非 {@code setAdditionalProperty()}，
     * 避免 {@code @JsonAnyGetter} 与 {@code @JsonProperty("stream_options")} 双路序列化导致重复字段。
     * </p>
     */
    private void includeStreamUsage(OpenAiChatCompletionRequest providerRequest) {
        Map<String, Object> existing = providerRequest.getStreamOptions();
        Map<String, Object> streamOptions = new LinkedHashMap<>();
        if (existing != null) {
            streamOptions.putAll(existing);
        }
        streamOptions.put("include_usage", true);
        providerRequest.setStreamOptions(streamOptions);
    }

    /**
     * 过滤工具列表，仅保留 function 类型；Chat Completions 接口不接受其他工具类型。
     */
    @SuppressWarnings("unchecked")
    private Object filterNonFunctionTools(Object tools) {
        if (!(tools instanceof List<?> toolsList)) {
            return tools;
        }
        List<Object> result = new ArrayList<>();
        for (Object item : toolsList) {
            if (item instanceof Map<?, ?> tool && "function".equals(String.valueOf(tool.get("type")))) {
                result.add(item);
            }
        }
        return result.isEmpty() ? null : result;
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
        applyMessageOptions(openAiMessage, message.options());
        return openAiMessage;
    }

    /**
     * 跨协议适配会把工具调用、工具结果和推理内容放入统一消息的 options，这里恢复成 OpenAI Chat 字段。
     */
    @SuppressWarnings("unchecked")
    private void applyMessageOptions(OpenAiMessage openAiMessage, Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("tool_calls".equals(key) && value instanceof List<?> list) {
                openAiMessage.setToolCalls((List<Map<String, Object>>) list);
            } else if ("tool_call_id".equals(key) && value != null) {
                openAiMessage.setToolCallId(String.valueOf(value));
            } else if ("reasoning_content".equals(key) && value != null) {
                openAiMessage.setReasoningContent(String.valueOf(value));
            } else {
                openAiMessage.setAdditionalProperty(key, value);
            }
        }
    }
}
