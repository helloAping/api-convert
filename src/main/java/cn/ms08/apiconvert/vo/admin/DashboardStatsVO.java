package cn.ms08.apiconvert.vo.admin;

import java.util.List;

/**
 * 控制台仪表盘完整统计数据。
 * <p>
 * tokenUsage 的聚合粒度由前端传入的 range 参数决定：
 * 24h/48h/72h 按小时，7d/14d/30d/90d 按天。
 */
public record DashboardStatsVO(
        DashboardSummaryVO summary,
        List<DashboardTokenPointVO> tokenUsage,
        List<DashboardDimensionUsageVO> modelDistribution,
        List<DashboardDimensionUsageVO> channelDistribution,
        List<DashboardDimensionUsageVO> apiKeyDistribution,
        List<DashboardSeriesVO> modelSeries,
        List<DashboardSeriesVO> channelSeries,
        List<DashboardSeriesVO> apiKeySeries
) {
}
