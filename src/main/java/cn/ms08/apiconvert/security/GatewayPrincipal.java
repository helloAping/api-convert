package cn.ms08.apiconvert.security;

import java.util.Set;

/**
 * 已通过网关密钥认证的调用方身份，包含该密钥允许使用的渠道范围。
 */
public record GatewayPrincipal(
        Long apiKeyId,
        String name,
        Set<String> allowedChannelCodes
) {
    /**
     * 未配置渠道授权记录时表示该密钥允许使用所有渠道。
     */
    public boolean allowAllChannels() {
        return allowedChannelCodes == null || allowedChannelCodes.isEmpty();
    }
}
