package cn.ms08.apiconvert.dto;

/**
 * 公共模型存在多个候选渠道时使用的路由策略。
 */
public enum RoutingMode {
    /**
     * 保持历史行为，从可用候选渠道中随机选择。
     */
    RANDOM,
    /**
     * 按候选渠道稳定顺序轮询选择。
     */
    ROUND_ROBIN,
    /**
     * 按渠道 priority 字段作为权重做平滑加权轮询，数值越大分配越多请求。
     */
    WEIGHTED,
    /**
     * 同一密钥、模型和会话标识优先复用首次命中的渠道，用于提升上游缓存命中率。
     */
    SESSION_STICKY
}
