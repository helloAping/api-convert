package cn.ms08.apiconvert.vo.admin;

/**
 * 维度折线图中的单个时间点。
 */
public record DashboardSeriesPointVO(
        String label,
        Long totalTokens
) {
}
