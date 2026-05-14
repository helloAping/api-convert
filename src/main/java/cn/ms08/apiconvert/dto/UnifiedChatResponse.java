package cn.ms08.apiconvert.dto;

import java.util.List;

public record UnifiedChatResponse(
        String id,
        String model,
        List<UnifiedMessage> messages,
        UnifiedUsage usage,
        Object rawResponse
) {
}
