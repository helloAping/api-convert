package cn.ms08.apiconvert.vo.admin;

/**
 * 按模型、渠道或密钥维度聚合的 token 用量。
 */
public record DashboardDimensionUsageVO(
        String key,
        String name,
        Long requestCount,
        Long successCount,
        Long failureCount,
        Long inputTokens,
        Long cacheReadInputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
