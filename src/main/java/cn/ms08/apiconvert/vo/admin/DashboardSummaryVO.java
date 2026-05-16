package cn.ms08.apiconvert.vo.admin;

/**
 * 控制台仪表盘总览统计。
 */
public record DashboardSummaryVO(
        Long requestCount,
        Long successCount,
        Long failureCount,
        Long inputTokens,
        Long cacheReadInputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
