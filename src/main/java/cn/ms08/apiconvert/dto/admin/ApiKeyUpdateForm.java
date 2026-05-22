package cn.ms08.apiconvert.dto.admin;

import java.util.List;
import java.math.BigDecimal;

/**
 * 更新网关密钥状态、授权范围和滑动窗口限制的表单。
 *
 * @param status 密钥状态
 * @param failoverEnabled 是否开启请求上游未写出即失败后的同模型多渠道切换
 * @param channelCodes 允许使用的渠道编码列表；空列表表示允许所有渠道
 * @param modelNames 允许使用的对外模型名列表；空列表表示允许所有模型
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 兼容旧字段的滑动窗口单位，管理端当前支持 HOUR、DAY
 * @param limits 可并存的限制项列表；为空列表表示不启用任何窗口限制
 */
public record ApiKeyUpdateForm(
    String status,
    Boolean failoverEnabled,
    List<String> channelCodes,
    List<String> modelNames,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit,
    List<ApiKeyLimitForm> limits
) {}
