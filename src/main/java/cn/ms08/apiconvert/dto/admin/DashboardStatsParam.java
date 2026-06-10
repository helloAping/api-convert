package cn.ms08.apiconvert.dto.admin;

/**
 * 控制台仪表盘统计查询参数。
 *
 * @param range 时间范围，支持 24h / 48h / 72h / 7d / 14d / 30d，默认 7d
 * @param topN 模型、渠道、密钥维度返回的 Top N 数量，默认 6
 */
public record DashboardStatsParam(
        String range,
        Integer topN
) {
}
