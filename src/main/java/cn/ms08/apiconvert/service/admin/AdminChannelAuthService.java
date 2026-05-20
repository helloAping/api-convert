package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.config.GatewayProperties;
import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.provider.auth.AuthCredential;
import cn.ms08.apiconvert.service.auth.AuthFileService;
import cn.ms08.apiconvert.vo.admin.ChannelAuthStartVO;
import cn.ms08.apiconvert.vo.admin.ChannelAuthStatusVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminChannelAuthService {

    private static final String AUTH_FILE = "AUTH_FILE";
    private static final String OAUTH = "OAUTH";
    private static final String AUTHORIZED = "AUTHORIZED";
    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final String GPT_AUTHORIZATION_URI = "https://auth.openai.com/oauth/authorize";
    private static final String GPT_TOKEN_URI = "https://auth.openai.com/oauth/token";
    private static final String GPT_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    private static final String GPT_SCOPE = "openid email profile offline_access";
    private static final String GPT_REDIRECT_URI = "http://localhost:1455/auth/callback";

    private static final String CLAUDE_AUTHORIZATION_URI = "https://claude.ai/oauth/authorize";
    private static final String CLAUDE_TOKEN_URI = "https://console.anthropic.com/v1/oauth/token";
    private static final String CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
    private static final String CLAUDE_SCOPE = "org:create_api_key user:profile user:inference";
    private static final String CLAUDE_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback";

    private final AiChannelMapper channelMapper;
    private final AuthFileService authFileService;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final Map<String, PendingOAuth> pendingOAuth = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminChannelAuthService(AiChannelMapper channelMapper, AuthFileService authFileService,
                                   GatewayProperties properties, ObjectMapper objectMapper,
                                   RestClient.Builder restClientBuilder) {
        this.channelMapper = channelMapper;
        this.authFileService = authFileService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public ChannelAuthStatusVO upload(Long channelId, MultipartFile file) {
        AiChannelEntity channel = authChannel(channelId);
        if (file == null || file.isEmpty()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Please choose auth.json");
        }
        try {
            var saved = authFileService.save(channel.getType(), channel.getCode(), file.getBytes());
            channel.setAuthMode(AUTH_FILE);
            channel.setAuthFilePath(saved.relativePath());
            channel.setAuthStatus(AUTHORIZED);
            channel.setAuthSubject(saved.subject());
            channel.setAuthExpiresAt(saved.expiresAt());
            channelMapper.updateById(channel);
            return status(channel);
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Failed to upload auth.json: " + exception.getMessage());
        }
    }

    public ChannelAuthStartVO start(Long channelId, String callbackBaseUrl) {
        AiChannelEntity channel = authChannel(channelId);
        OAuthRuntimeConfig config = runtimeOAuthConfig(channel.getType(), callbackBaseUrl);

        String state = UUID.randomUUID().toString();
        String codeVerifier = codeVerifier();
        pendingOAuth.put(state, new PendingOAuth(
                channelId,
                channel.getType(),
                config.redirectUri(),
                config.tokenUri(),
                config.clientId(),
                config.clientSecret(),
                codeVerifier,
                config.supportsPkce(),
                LocalDateTime.now(ZONE_ID).plusMinutes(10)
        ));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(config.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("state", state);
        if (StringUtils.hasText(config.scope())) {
            builder.queryParam("scope", config.scope());
        }
        if (config.supportsPkce()) {
            builder.queryParam("code_challenge", codeChallenge(codeVerifier));
            builder.queryParam("code_challenge_method", "S256");
        }
        config.extraAuthorizationParams().forEach(builder::queryParam);
        return new ChannelAuthStartVO(channelId, channel.getType(), builder.build().encode().toUriString(), state);
    }

    public ChannelAuthStatusVO callbackFromUrl(Long channelId, String callbackUrl) {
        requireText(callbackUrl, "callbackUrl cannot be blank");
        Map<String, String> params = queryParams(callbackUrl);
        String code = params.get("code");
        String state = params.get("state");
        requireText(code, "callbackUrl is missing code");
        requireText(state, "callbackUrl is missing state");
        PendingOAuth pending = pendingOAuth.get(state);
        if (pending == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "OAuth state expired or invalid");
        }
        if (!pending.channelId().equals(channelId)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "OAuth state does not belong to this channel");
        }
        return callback(code, state);
    }

    public ChannelAuthStatusVO callback(String code, String state) {
        requireText(code, "code cannot be blank");
        requireText(state, "state cannot be blank");
        PendingOAuth pending = pendingOAuth.remove(state);
        if (pending == null || pending.expiresAt().isBefore(LocalDateTime.now(ZONE_ID))) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "OAuth state expired or invalid");
        }
        AiChannelEntity channel = authChannel(pending.channelId());
        JsonNode tokenResponse = exchangeToken(pending, code);
        AuthCredential credential = authFileService.parse(tokenResponse);
        if (!credential.hasAccessToken()) {
            throw new GatewayException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "OAuth token response is missing access_token");
        }
        var saved = authFileService.save(channel.getType(), channel.getCode(),
                tokenResponse.toString().getBytes(StandardCharsets.UTF_8));
        channel.setAuthMode(OAUTH);
        channel.setAuthFilePath(saved.relativePath());
        channel.setAuthStatus(AUTHORIZED);
        channel.setAuthSubject(saved.subject());
        channel.setAuthExpiresAt(saved.expiresAt());
        channelMapper.updateById(channel);
        return status(channel);
    }

    public ChannelAuthStatusVO status(Long channelId) {
        return status(authChannel(channelId));
    }

    public ChannelAuthStatusVO delete(Long channelId) {
        AiChannelEntity channel = authChannel(channelId);
        authFileService.delete(channel.getAuthFilePath());
        channel.setAuthMode(AUTH_FILE);
        channel.setAuthFilePath(null);
        channel.setAuthStatus(NOT_CONFIGURED);
        channel.setAuthSubject(null);
        channel.setAuthExpiresAt(null);
        channelMapper.updateById(channel);
        return status(channel);
    }

    private JsonNode exchangeToken(PendingOAuth pending, String code) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", pending.redirectUri());
            form.add("client_id", pending.clientId());
            if (StringUtils.hasText(pending.clientSecret())) {
                form.add("client_secret", pending.clientSecret());
            }
            if (pending.supportsPkce()) {
                form.add("code_verifier", pending.codeVerifier());
            }
            String body = restClientBuilder.clone()
                    .build()
                    .post()
                    .uri(pending.tokenUri())
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientException exception) {
            throw new GatewayException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "OAuth token request failed: " + exception.getMessage());
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Failed to parse OAuth token response: " + exception.getMessage());
        }
    }

    private OAuthRuntimeConfig runtimeOAuthConfig(String providerType, String callbackBaseUrl) {
        GatewayProperties.OAuthProvider config = oauthConfig(providerType);
        return switch (providerType) {
            case "GPT_AUTH" -> configuredOrBuiltIn(
                    config,
                    GPT_AUTHORIZATION_URI,
                    GPT_TOKEN_URI,
                    GPT_CLIENT_ID,
                    null,
                    GPT_SCOPE,
                    GPT_REDIRECT_URI,
                    true,
                    Map.of(
                            "codex_cli_simplified_flow", "true",
                            "id_token_add_organizations", "true",
                            "prompt", "login"
                    )
            );
            case "CLAUDE_AUTH" -> configuredOrBuiltIn(
                    config,
                    CLAUDE_AUTHORIZATION_URI,
                    CLAUDE_TOKEN_URI,
                    CLAUDE_CLIENT_ID,
                    null,
                    CLAUDE_SCOPE,
                    CLAUDE_REDIRECT_URI,
                    true,
                    Map.of()
            );
            default -> throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Unsupported OAuth provider type: " + providerType);
        };
    }

    private OAuthRuntimeConfig configuredOrBuiltIn(GatewayProperties.OAuthProvider config,
                                                   String authorizationUri,
                                                   String tokenUri,
                                                   String clientId,
                                                   String clientSecret,
                                                   String scope,
                                                   String redirectUri,
                                                   boolean supportsPkce,
                                                   Map<String, String> extraAuthorizationParams) {
        String effectiveAuthorizationUri = textOr(config.getAuthorizationUri(), authorizationUri);
        String effectiveTokenUri = textOr(config.getTokenUri(), tokenUri);
        String effectiveClientId = textOr(config.getClientId(), clientId);
        String effectiveClientSecret = textOr(config.getClientSecret(), clientSecret);
        String effectiveScope = textOr(config.getScope(), scope);
        String effectiveRedirectUri = textOr(config.getRedirectUri(), redirectUri);

        requireText(effectiveAuthorizationUri, "OAuth authorization URI is not configured");
        requireText(effectiveTokenUri, "OAuth token URI is not configured");
        requireText(effectiveClientId, "OAuth clientId is not configured");
        requireText(effectiveRedirectUri, "OAuth redirect URI is not configured");
        return new OAuthRuntimeConfig(
                effectiveAuthorizationUri,
                effectiveTokenUri,
                effectiveClientId,
                effectiveClientSecret,
                effectiveScope,
                effectiveRedirectUri,
                supportsPkce,
                extraAuthorizationParams
        );
    }

    private GatewayProperties.OAuthProvider oauthConfig(String providerType) {
        GatewayProperties.OAuth oauth = properties.getAuth().getOauth();
        return switch (providerType) {
            case "GPT_AUTH" -> oauth.getGptAuth();
            case "CLAUDE_AUTH" -> oauth.getClaudeAuth();
            default -> throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Unsupported OAuth provider type: " + providerType);
        };
    }

    private AiChannelEntity authChannel(Long channelId) {
        AiChannelEntity channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "Channel not found");
        }
        try {
            ProviderType type = ProviderType.valueOf(channel.getType());
            if (type != ProviderType.GPT_AUTH && type != ProviderType.CLAUDE_AUTH) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Current channel is not an AUTH provider");
            }
        } catch (IllegalArgumentException exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Unknown provider type: " + channel.getType());
        }
        return channel;
    }

    private ChannelAuthStatusVO status(AiChannelEntity channel) {
        return new ChannelAuthStatusVO(
                channel.getId(),
                channel.getType(),
                channel.getAuthMode(),
                channel.getAuthStatus(),
                channel.getAuthSubject(),
                channel.getAuthExpiresAt(),
                StringUtils.hasText(channel.getAuthFilePath())
        );
    }

    private Map<String, String> queryParams(String url) {
        try {
            String query = URI.create(url).getRawQuery();
            if (!StringUtils.hasText(query)) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "callbackUrl has no query string");
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (String pair : query.split("&")) {
                int index = pair.indexOf('=');
                String name = index >= 0 ? pair.substring(0, index) : pair;
                String value = index >= 0 ? pair.substring(index + 1) : "";
                params.put(urlDecode(name), urlDecode(value));
            }
            return params;
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Invalid callbackUrl: " + exception.getMessage());
        }
    }

    private String codeVerifier() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate OAuth code challenge");
        }
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
        }
    }

    private record OAuthRuntimeConfig(String authorizationUri, String tokenUri, String clientId,
                                      String clientSecret, String scope, String redirectUri,
                                      boolean supportsPkce, Map<String, String> extraAuthorizationParams) {
    }

    private record PendingOAuth(Long channelId, String providerType, String redirectUri, String tokenUri,
                                String clientId, String clientSecret, String codeVerifier,
                                boolean supportsPkce, LocalDateTime expiresAt) {
    }
}
