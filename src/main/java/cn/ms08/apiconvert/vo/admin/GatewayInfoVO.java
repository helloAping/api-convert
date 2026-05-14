package cn.ms08.apiconvert.vo.admin;

import java.util.List;

/**
 * 管理端控制台展示的网关调用信息，不包含任何密钥或渠道凭证。
 */
public record GatewayInfoVO(
        String baseUrl,
        List<EndpointVO> endpoints
) {
    /**
     * 单个已支持的公开网关接口说明。
     */
    public record EndpointVO(
            String method,
            String path,
            String protocol,
            String auth,
            String description
    ) {
    }
}
