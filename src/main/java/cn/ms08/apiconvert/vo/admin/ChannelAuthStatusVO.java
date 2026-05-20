package cn.ms08.apiconvert.vo.admin;

import java.time.LocalDateTime;

/**
 * 管理端展示的渠道授权状态，不包含 access token 或 refresh token。
 */
public record ChannelAuthStatusVO(
        Long channelId,
        String providerType,
        String authMode,
        String authStatus,
        String authSubject,
        LocalDateTime authExpiresAt,
        Boolean hasAuthFile
) {
}
