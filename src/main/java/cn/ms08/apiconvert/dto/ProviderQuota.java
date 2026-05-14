package cn.ms08.apiconvert.dto;

import java.math.BigDecimal;

/**
 * 供应商额度查询结果，不落库，仅用于管理端实时展示。
 *
 * @param supported 当前供应商实现是否支持通用额度查询
 * @param summary 面向管理员展示的额度摘要
 * @param balance 当前余额或剩余额度
 * @param used 已使用额度，供应商未返回时为空
 * @param available 可用额度，供应商未返回时为空
 * @param currency 币种或计量单位
 * @param rawSummary 脱敏后的上游原始响应摘要，便于排查不同供应商字段差异
 */
public record ProviderQuota(
        boolean supported,
        String summary,
        BigDecimal balance,
        BigDecimal used,
        BigDecimal available,
        String currency,
        String rawSummary
) {
}
