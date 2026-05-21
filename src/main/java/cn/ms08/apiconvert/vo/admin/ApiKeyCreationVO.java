package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网关密钥创建响应；rawKey 是外部工具请求网关时携带的明文密钥。
 *
 * @param id 密钥 ID
 * @param name 密钥名称
 * @param rawKey 外部工具实际调用 OpenAI/Anthropic 兼容接口时携带的明文密钥
 * @param keyPreview 脱敏展示值，不能用于调用
 * @param status 密钥状态
 * @param quotaBalance 剩余额度；为空表示不限总额度
 * @param quotaLimit 滑动窗口内最多可消耗的额度；为空表示不限制
 * @param quotaWindowValue 滑动窗口长度数值
 * @param quotaWindowUnit 兼容旧字段的滑动窗口单位，管理端当前支持 HOUR、DAY
 * @param channelCodes 允许使用的渠道编码列表，空列表表示允许所有渠道
 * @param modelNames 允许使用的对外模型名列表，空列表表示允许所有模型
 * @param limits 可并存的滑动窗口限制项列表
 */
public record ApiKeyCreationVO(
    Long id,
    String name,
    String rawKey,
    String keyPreview,
    String status,
    BigDecimal quotaBalance,
    BigDecimal quotaLimit,
    Integer quotaWindowValue,
    String quotaWindowUnit,
    List<String> channelCodes,
    List<String> modelNames,
    List<ApiKeyLimitVO> limits
) {}
