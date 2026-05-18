package cn.ms08.apiconvert.dto.admin;

/**
 * 管理端更新路由策略时提交的配置表单。
 */
public record RoutingConfigRequest(
        String mode,
        Integer failureThreshold,
        Integer failureCooldownMinutes,
        Integer stickyTtlMinutes
) {
}
