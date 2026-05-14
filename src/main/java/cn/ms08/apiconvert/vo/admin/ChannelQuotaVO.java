package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;

/**
 * 管理端实时展示的渠道额度结果，不持久化。
 *
 * @param channelId 渠道主键
 * @param channelCode 渠道编码
 * @param supported 当前供应商实现是否支持额度查询
 * @param summary 额度摘要
 * @param balance 当前余额或剩余额度
 * @param used 已使用额度
 * @param available 可用额度
 * @param currency 币种或计量单位
 * @param rawSummary 脱敏后的上游响应摘要
 */
public record ChannelQuotaVO(
        Long channelId,
        String channelCode,
        boolean supported,
        String summary,
        BigDecimal balance,
        BigDecimal used,
        BigDecimal available,
        String currency,
        String rawSummary
) {
}
