package cn.ms08.apiconvert.dto.admin;

import java.util.List;
import java.math.BigDecimal;

/**
 * 更新网关密钥状态和渠道授权范围的表单。
 *
 * @param status 密钥状态
 * @param channelCodes 允许使用的渠道编码列表；空列表表示允许所有渠道
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 滑动窗口单位，支持 HOUR、DAY、MONTH
 */
public record ApiKeyUpdateForm(
    String status,
    List<String> channelCodes,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit
) {}
