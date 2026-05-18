package cn.ms08.apiconvert.vo.admin;

import java.util.List;

/**
 * 控制台仪表盘完整统计数据。
 */
public record DashboardStatsVO(
        DashboardSummaryVO summary,
        List<DashboardTokenPointVO> dailyTokenUsage,
        List<DashboardTokenPointVO> hourlyTokenUsage,
        List<DashboardDimensionUsageVO> modelDistribution,
        List<DashboardDimensionUsageVO> channelDistribution,
        List<DashboardDimensionUsageVO> apiKeyDistribution,
        List<DashboardSeriesVO> modelSeries,
        List<DashboardSeriesVO> channelSeries,
        List<DashboardSeriesVO> apiKeySeries
) {
}
