package cn.ms08.apiconvert.vo.admin;

/**
 * OAuth 登录启动结果，前端使用 authorizationUrl 打开供应商授权页。
 */
public record ChannelAuthStartVO(
        Long channelId,
        String providerType,
        String authorizationUrl,
        String state
) {
}
