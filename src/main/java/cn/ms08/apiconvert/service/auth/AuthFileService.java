package cn.ms08.apiconvert.service.auth;

import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.provider.auth.AuthCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 负责 auth.json 的解析、标准化保存和凭据读取；调用方不得记录原始 JSON。
 */
@Service
public class AuthFileService {

    private static final int MAX_AUTH_FILE_BYTES = 256 * 1024;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final AuthStorageService storageService;

    public AuthFileService(ObjectMapper objectMapper, AuthStorageService storageService) {
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public SavedAuthFile save(String providerType, String channelCode, byte[] content) {
        if (content == null || content.length == 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "auth.json 不能为空");
        }
        if (content.length > MAX_AUTH_FILE_BYTES) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "auth.json 不能超过 256KB");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject()) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "auth.json 必须是 JSON 对象");
            }
            ObjectNode normalized = normalize((ObjectNode) root, providerType);
            AuthCredential credential = parse(normalized);
            if (!credential.hasAccessToken()) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "auth.json 缺少 access_token");
            }
            Path target = storageService.channelFile(providerType, channelCode);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new SavedAuthFile(storageService.toRelative(target), maskSubject(credential.subject()), credential.expiresAt());
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "保存 auth.json 失败: " + exception.getMessage());
        }
    }

    public AuthCredential read(String relativePath) {
        try {
            Path path = storageService.resolveRelative(relativePath);
            if (!Files.exists(path)) {
                throw new GatewayException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "授权文件不存在");
            }
            return parse(objectMapper.readTree(path.toFile()));
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "读取授权文件失败: " + exception.getMessage());
        }
    }

    public void delete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        try {
            Files.deleteIfExists(storageService.resolveRelative(relativePath));
        } catch (Exception exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "删除授权文件失败: " + exception.getMessage());
        }
    }

    public AuthCredential parse(JsonNode root) {
        String accessToken = firstText(root, "access_token", "accessToken", "token");
        String refreshToken = firstText(root, "refresh_token", "refreshToken");
        String tokenType = firstText(root, "token_type", "tokenType");
        String subject = firstText(root, "subject", "email", "account", "account_id", "user_id", "id");
        return new AuthCredential(accessToken, refreshToken, tokenType, expiresAt(root), subject, root);
    }

    private ObjectNode normalize(ObjectNode root, String providerType) {
        ObjectNode normalized = root.deepCopy();
        normalized.put("provider_type", providerType);
        String accessToken = firstText(normalized, "access_token", "accessToken", "token");
        if (StringUtils.hasText(accessToken)) {
            normalized.put("access_token", accessToken);
        }
        String refreshToken = firstText(normalized, "refresh_token", "refreshToken");
        if (StringUtils.hasText(refreshToken)) {
            normalized.put("refresh_token", refreshToken);
        }
        String tokenType = firstText(normalized, "token_type", "tokenType");
        if (!StringUtils.hasText(tokenType)) {
            normalized.put("token_type", "Bearer");
        }
        return normalized;
    }

    private LocalDateTime expiresAt(JsonNode root) {
        String text = firstText(root, "expires_at", "expiresAt", "expiry");
        if (StringUtils.hasText(text)) {
            LocalDateTime parsed = parseDateTime(text);
            if (parsed != null) {
                return parsed;
            }
        }
        JsonNode expiryDate = root.path("expiry_date");
        if (expiryDate.canConvertToLong()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(expiryDate.asLong()), ZONE_ID);
        }
        JsonNode expiresIn = root.path("expires_in");
        if (expiresIn.canConvertToLong()) {
            return LocalDateTime.now(ZONE_ID).plusSeconds(expiresIn.asLong());
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            if (value.matches("\\d+")) {
                long epoch = Long.parseLong(value);
                if (epoch > 10_000_000_000L) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE_ID);
                }
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZONE_ID);
            }
            if (value.endsWith("Z") || value.contains("T")) {
                return LocalDateTime.ofInstant(Instant.parse(value), ZONE_ID);
            }
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstText(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.path(name);
            if (!node.isMissingNode() && !node.isNull() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    public String maskSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            return "";
        }
        int at = subject.indexOf('@');
        if (at > 1) {
            return subject.charAt(0) + "****" + subject.substring(at);
        }
        if (subject.length() <= 8) {
            return "****";
        }
        return subject.substring(0, 4) + "****" + subject.substring(subject.length() - 4);
    }

    public record SavedAuthFile(String relativePath, String subject, LocalDateTime expiresAt) {
    }
}
