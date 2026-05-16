package cn.ms08.apiconvert.adapter;

import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 请求和网关统一请求之间的适配器。
 */
@Component
public class OpenAiResponsesRequestAdapter {

    /**
     * 将 Responses API 请求转为统一内部请求；input 可接受 String 或 content 数组。
     */
    @SuppressWarnings("unchecked")
    public UnifiedChatRequest toUnified(OpenAiResponsesRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (request.getInput() == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input is required");
        }
        List<UnifiedMessage> messages = convertInput(request.getInput());
        // 将 instructions 作为 system 消息前置，避免放入 rawOptions 后在上游 Chat Completions 接口中被忽略
        if (StringUtils.hasText(request.getInstructions())) {
            List<UnifiedMessage> result = new ArrayList<>();
            result.add(new UnifiedMessage("system", request.getInstructions(), null));
            result.addAll(messages);
            messages = result;
        }
        Map<String, Object> rawOptions = new LinkedHashMap<>(request.getAdditionalProperties());
        // Chat Completions 接口只支持 type=function 的工具，过滤掉 web_search 等其他类型
        sanitizeTools(rawOptions);
        return new UnifiedChatRequest(
                request.getModel(),
                messages,
                request.getStream(),
                request.getTemperature(),
                request.getMaxOutputTokens(),
                null,
                rawOptions
        );
    }

    /**
     * 将统一请求转为 Responses API 格式以转发到上游。
     */
    @SuppressWarnings("unchecked")
    public OpenAiResponsesRequest toProviderRequest(UnifiedChatRequest request, String providerModel, boolean stream) {
        OpenAiResponsesRequest providerRequest = new OpenAiResponsesRequest();
        providerRequest.setModel(providerModel);
        providerRequest.setInput(messagesToInput(request.messages()));
        providerRequest.setStream(stream);
        providerRequest.setTemperature(request.temperature());
        providerRequest.setMaxOutputTokens(request.maxTokens());
        if (request.rawOptions() != null) {
            request.rawOptions().forEach((key, value) -> {
                if ("instructions".equals(key)) {
                    providerRequest.setInstructions(value instanceof String s ? s : String.valueOf(value));
                } else if (!"response_format".equals(key)) {
                    providerRequest.setAdditionalProperty(key, value);
                }
            });
        }
        return providerRequest;
    }

    /**
     * 将 Responses API 的 input 字段转为统一消息列表。
     */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> convertInput(Object input) {
        if (input instanceof String text) {
            return List.of(new UnifiedMessage("user", text, null));
        }
        if (input instanceof List<?> list) {
            List<UnifiedMessage> messages = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String role = map.containsKey("role") ? String.valueOf(map.get("role")) : "user";
                    Object content = map.containsKey("content") ? map.get("content") : item;
                    // 将 Responses API 内容类型归一化为 Chat Completions 兼容格式
                    messages.add(new UnifiedMessage(role, normalizeContent(content), null));
                } else {
                    messages.add(new UnifiedMessage("user", item, null));
                }
            }
            return messages;
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input must be a string or array");
    }

    /**
     * 归一化 Responses API 内容项类型；将 input_text / input_image 等
     * Responses API 特有类型映射为 Chat Completions 兼容的类型名。
     */
    @SuppressWarnings("unchecked")
    private Object normalizeContent(Object content) {
        if (!(content instanceof List<?> contentList)) {
            return content;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object part : contentList) {
            if (part instanceof Map<?, ?> partMap) {
                Map<String, Object> normalizedPart = new LinkedHashMap<>(partMap.size());
                for (Map.Entry<?, ?> entry : partMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if ("type".equals(key)) {
                        String type = String.valueOf(value);
                        if ("input_text".equals(type)) {
                            type = "text";
                        }
                        normalizedPart.put("type", type);
                    } else {
                        normalizedPart.put(key, value);
                    }
                }
                normalized.add(normalizedPart);
            } else {
                // 非 Map 元素原样保留
                normalized.add(Map.of("type", "text", "text", String.valueOf(part)));
            }
        }
        return normalized;
    }

    /**
     * 将 rawOptions 中的 tools 数组从 Responses API 格式转为 Chat Completions 兼容格式。
     * <p>
     * Responses API 格式：{"type":"function","name":"foo","parameters":{...}}
     * Chat Completions 格式：{"type":"function","function":{"name":"foo","parameters":{...}}}
     * <br>
     * 同时过滤掉 web_search、code_interpreter 等非 function 类型。
     */
    @SuppressWarnings("unchecked")
    private void sanitizeTools(Map<String, Object> rawOptions) {
        Object tools = rawOptions.get("tools");
        if (!(tools instanceof List<?> toolsList)) {
            return;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : toolsList) {
            if (!(item instanceof Map<?, ?> tool) || !"function".equals(String.valueOf(tool.get("type")))) {
                continue;
            }
            // 将顶层 name/description/parameters/strict 嵌套到 function 子对象中
            Map<String, Object> functionDef = new LinkedHashMap<>();
            Map<String, Object> chatTool = new LinkedHashMap<>();
            chatTool.put("type", "function");
            for (Map.Entry<?, ?> entry : tool.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if ("type".equals(key)) {
                    continue;
                }
                functionDef.put(key, value);
            }
            chatTool.put("function", functionDef);
            filtered.add(chatTool);
        }
        if (filtered.isEmpty()) {
            rawOptions.remove("tools");
        } else {
            rawOptions.put("tools", filtered);
        }
    }

    /**
     * 将统一消息列表逆向转为 Responses API 的 input 格式。
     */
    private Object messagesToInput(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (UnifiedMessage message : messages) {
            items.add(Map.of("role", message.role() != null ? message.role() : "user",
                    "content", message.content() != null ? message.content() : ""));
        }
        return items;
    }
}
