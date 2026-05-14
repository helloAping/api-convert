package cn.ms08.apiconvert.dto.admin;

import java.util.List;
import java.math.BigDecimal;

/**
 * 创建网关密钥的表单；channelCodes 为空表示该密钥允许使用所有渠道。
 *
 * @param name 密钥展示名称
 * @param channelCodes 允许使用的渠道编码列表
 * @param quotaBalance 初始剩余额度；为空表示不限总额度
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 滑动窗口单位，支持 HOUR、DAY、MONTH
 */
public record ApiKeyForm(
    String name,
    List<String> channelCodes,
    BigDecimal quotaBalance,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit
) {}
