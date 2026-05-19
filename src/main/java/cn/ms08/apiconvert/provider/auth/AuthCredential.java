package cn.ms08.apiconvert.provider.auth;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * auth.json 的标准化视图，只暴露上游调用所需的非敏感元信息和访问令牌。
 */
public record AuthCredential(
        String accessToken,
        String refreshToken,
        String tokenType,
        LocalDateTime expiresAt,
        String subject,
        JsonNode raw
) {
    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
