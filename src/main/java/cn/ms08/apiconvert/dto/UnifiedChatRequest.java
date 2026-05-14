package cn.ms08.apiconvert.dto;

import java.util.List;
import java.util.Map;

public record UnifiedChatRequest(
        String model,
        List<UnifiedMessage> messages,
        Boolean stream,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> rawOptions
) {
}
