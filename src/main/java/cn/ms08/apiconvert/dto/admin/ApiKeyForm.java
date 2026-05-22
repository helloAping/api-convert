package cn.ms08.apiconvert.dto.admin;

import java.util.List;
import java.math.BigDecimal;

/**
 * 创建网关密钥的表单；channelCodes/modelNames 为空表示该密钥允许使用所有渠道/模型。
 *
 * @param name 密钥展示名称
 * @param failoverEnabled 是否开启请求上游未写出即失败后的同模型多渠道切换
 * @param channelCodes 允许使用的渠道编码列表
 * @param modelNames 允许使用的对外模型名列表
 * @param quotaBalance 初始剩余额度；为空表示不限总额度
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 兼容旧字段的滑动窗口单位，管理端当前支持 HOUR、DAY
 * @param limits 可并存的限制项列表；为空列表表示不启用任何窗口限制
 */
public record ApiKeyForm(
    String name,
    Boolean failoverEnabled,
    List<String> channelCodes,
    List<String> modelNames,
    BigDecimal quotaBalance,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit,
    List<ApiKeyLimitForm> limits
) {}
