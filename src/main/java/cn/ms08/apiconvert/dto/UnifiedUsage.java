package cn.ms08.apiconvert.dto;

public record UnifiedUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer cacheReadInputTokens
) {
    /**
     * 兼容旧调用点：未提供缓存命中用量时按空值处理。
     */
    public UnifiedUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        this(inputTokens, outputTokens, totalTokens, null);
    }
}
