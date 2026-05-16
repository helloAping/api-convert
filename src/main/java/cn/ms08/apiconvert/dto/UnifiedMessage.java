package cn.ms08.apiconvert.dto;

import java.util.Map;

/**
 * 网关统一内部消息。各协议适配器负责将外部消息转换为该格式。
 * finishReason 用于跨协议时保留上游的完成原因，options 携带 tool_calls 等消息级元数据。
 */
public record UnifiedMessage(
        String role,
        Object content,
        String name,
        String finishReason,
        Map<String, Object> options
) {
    public UnifiedMessage(String role, Object content, String name) {
        this(role, content, name, null, null);
    }

    public UnifiedMessage(String role, Object content, String name, String finishReason) {
        this(role, content, name, finishReason, null);
    }
}
