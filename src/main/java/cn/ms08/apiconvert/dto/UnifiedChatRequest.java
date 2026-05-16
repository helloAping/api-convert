package cn.ms08.apiconvert.dto;

import java.util.List;
import java.util.Map;

/**
 * 网关统一内部对话请求。各协议适配器负责将外部请求转换为该格式。
 */
public record UnifiedChatRequest(
        String model,
        List<UnifiedMessage> messages,
        Boolean stream,
        Double temperature,
        Integer maxTokens,
        ResponseFormat responseFormat,
        Map<String, Object> rawOptions
) {
}
