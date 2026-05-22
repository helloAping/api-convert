package cn.ms08.apiconvert.security;

import java.util.Set;

/**
 * 已通过网关密钥认证的调用方身份，包含该密钥允许使用的渠道和模型范围。
 */
public record GatewayPrincipal(
        Long apiKeyId,
        String name,
        Set<String> allowedChannelCodes,
        Set<String> allowedModelNames,
        Boolean failoverEnabled
) {
    /**
     * 未配置渠道授权记录时表示该密钥允许使用所有渠道。
     */
    public boolean allowAllChannels() {
        return allowedChannelCodes == null || allowedChannelCodes.isEmpty();
    }

    /**
     * 未配置模型授权记录时表示该密钥允许使用所有对外模型。
     */
    public boolean allowAllModels() {
        return allowedModelNames == null || allowedModelNames.isEmpty();
    }

    /**
     * 仅该密钥显式开启时，请求才会在上游未写出即失败后尝试同模型的其他渠道。
     */
    public boolean supportsFailover() {
        return Boolean.TRUE.equals(failoverEnabled);
    }
}
