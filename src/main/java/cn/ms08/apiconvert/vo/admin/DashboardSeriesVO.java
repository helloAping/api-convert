package cn.ms08.apiconvert.vo.admin;

import java.util.List;

/**
 * 按维度拆分的折线图数据序列。
 */
public record DashboardSeriesVO(
        String key,
        String name,
        List<DashboardSeriesPointVO> points
) {
}
