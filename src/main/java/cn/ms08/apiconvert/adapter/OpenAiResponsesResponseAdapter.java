package cn.ms08.apiconvert.adapter;

import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI Responses API 响应和网关统一响应之间的适配器。
 */
@Component
public class OpenAiResponsesResponseAdapter {

    /**
     * 将 Responses API 上游响应转为统一响应，保留原始响应用于同协议透传。
     */
    public UnifiedChatResponse toUnified(OpenAiResponsesResponse response) {
        UnifiedUsage usage = null;
        if (response.getUsage() != null) {
            usage = new UnifiedUsage(
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens(),
                    response.getUsage().getTotalTokens(),
                    null
            );
        }
        List<UnifiedMessage> messages = new ArrayList<>();
        if (response.getOutput() != null) {
            for (Object item : response.getOutput()) {
                if (item instanceof Map<?, ?> map) {
                    String role = map.containsKey("role") ? String.valueOf(map.get("role")) : "assistant";
                    messages.add(new UnifiedMessage(role, item, null));
                } else {
                    messages.add(new UnifiedMessage("assistant", item, null));
                }
            }
        }
        return new UnifiedChatResponse(response.getId(), response.getModel(), messages, usage, response);
    }

    /**
     * 将统一响应输出为 Responses API 兼容格式；跨协议路由时会合成 Responses 响应体。
     */
    @SuppressWarnings("unchecked")
    public OpenAiResponsesResponse toOpenAiResponses(UnifiedChatResponse response, String publicModel) {
        if (response.rawResponse() instanceof OpenAiResponsesResponse responsesResponse) {
            responsesResponse.setModel(publicModel);
            return responsesResponse;
        }
        OpenAiResponsesResponse result = new OpenAiResponsesResponse();
        result.setId(response.id() == null ? "resp_" + UUID.randomUUID() : response.id());
        result.setObjectValue("response");
        result.setStatus("completed");
        result.setModel(publicModel);
        result.setCreatedAt(System.currentTimeMillis() / 1000);
        List<Object> output = new ArrayList<>();
        for (UnifiedMessage message : response.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role() != null ? message.role() : "assistant");
            if (message.content() instanceof Map<?, ?> contentMap) {
                item.putAll((Map<String, Object>) contentMap);
            } else {
                item.put("content", List.of(Map.of("type", "output_text",
                        "text", message.content() != null ? String.valueOf(message.content()) : "")));
            }
            output.add(item);
        }
        result.setOutput(output);
        if (response.usage() != null) {
            OpenAiResponsesResponse.Usage usage = new OpenAiResponsesResponse.Usage();
            usage.setInputTokens(response.usage().inputTokens());
            usage.setOutputTokens(response.usage().outputTokens());
            usage.setTotalTokens(response.usage().totalTokens());
            result.setUsage(usage);
        }
        return result;
    }
}
