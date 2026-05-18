package cn.ms08.apiconvert.vo.admin;

/**
 * 管理端展示的路由运行期配置。
 */
public record RoutingConfigVO(
        String mode,
        Integer failureThreshold,
        Integer failureCooldownMinutes,
        Integer stickyTtlMinutes
) {
}
