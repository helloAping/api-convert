package cn.ms08.apiconvert.vo.admin;

/**
 * 按时间桶聚合的 token 用量点。
 */
public record DashboardTokenPointVO(
        String label,
        Long requestCount,
        Long inputTokens,
        Long cacheReadInputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
