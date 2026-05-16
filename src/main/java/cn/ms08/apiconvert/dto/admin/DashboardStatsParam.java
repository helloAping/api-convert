package cn.ms08.apiconvert.dto.admin;

/**
 * 控制台仪表盘统计查询参数。
 *
 * @param days 按天统计的最近天数
 * @param hours 按小时统计的最近小时数
 * @param topN 模型、渠道、密钥维度返回的 Top N 数量
 */
public record DashboardStatsParam(
        Integer days,
        Integer hours,
        Integer topN
) {
}
