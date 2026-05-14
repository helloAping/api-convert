package cn.ms08.apiconvert.dto;

public record UnifiedMessage(
        String role,
        Object content,
        String name
) {
}
