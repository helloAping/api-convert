package cn.ms08.apiconvert.dto;

/**
 * 路由运行期配置，来自 gateway_system_config 表并带默认值兜底。
 */
public record RoutingConfig(
        RoutingMode mode,
        int failureThreshold,
        int failureCooldownMinutes,
        int stickyTtlMinutes
) {
    /**
     * 是否启用同一密钥+渠道+模型的错误临时避让。
     */
    public boolean failureCooldownEnabled() {
        return failureThreshold > 0 && failureCooldownMinutes > 0;
    }
}
