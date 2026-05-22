package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端网关密钥视图；rawKey 会返回给前端用于复制，前端展示时必须脱敏。
 *
 * @param id 密钥 ID
 * @param name 密钥名称
 * @param rawKey 明文密钥，仅管理端使用，日志必须脱敏
 * @param keyPrefix 哈希前缀，用于排查但不能作为真实密钥
 * @param keyPreview 脱敏展示值，不能用于调用
 * @param status 密钥状态
 * @param failoverEnabled 是否开启请求上游未写出即失败后的同模型多渠道切换
 * @param quotaBalance 剩余额度；为空表示不限总额度
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 兼容旧字段的滑动窗口单位，管理端当前支持 HOUR、DAY
 * @param channelCodes 允许使用的渠道编码列表，空列表表示允许所有渠道
 * @param modelNames 允许使用的对外模型名列表，空列表表示允许所有模型
 * @param limits 可并存的滑动窗口限制项列表
 */
public record ApiKeyVO(
    Long id,
    String name,
    String rawKey,
    String keyPrefix,
    String keyPreview,
    String status,
    Boolean failoverEnabled,
    BigDecimal quotaBalance,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit,
    List<String> channelCodes,
    List<String> modelNames,
    List<ApiKeyLimitVO> limits
) {}
