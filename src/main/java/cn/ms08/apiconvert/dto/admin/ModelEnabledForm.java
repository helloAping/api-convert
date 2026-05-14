package cn.ms08.apiconvert.dto.admin;

/**
 * 管理端启用或关闭聚合模型的请求体。
 *
 * @param enabled 是否启用该对外模型下的全部渠道映射
 */
public record ModelEnabledForm(
        Boolean enabled
) {
}
