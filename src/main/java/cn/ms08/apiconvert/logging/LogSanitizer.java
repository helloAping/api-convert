package cn.ms08.apiconvert.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日志输出和供应商错误消息共用的脱敏工具。
 *
 * <p>所有请求/响应日志在写入正文或请求头前都必须先经过本类处理。</p>
 */
public final class LogSanitizer {

    /**
     * 写入日志或暴露到供应商错误详情中的正文最大长度。
     */
    private static final int MAX_BODY_LENGTH = 4096;
    /**
     * 可能包含凭证或 token 的值统一替换为该掩码。
     */
    private static final String MASK = "****";
    /**
     * 仅用于尽力解析 JSON 并脱敏的轻量级映射器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * 绝不能明文写入日志的请求头和 JSON 字段名。
     */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "authorization", "x-api-key", "api-key", "apikey", "api_key",
            "key", "token", "access_token", "refresh_token", "password",
            "secret", "client_secret", "rawkey", "raw_key"
    );
    /**
     * 对非法 JSON 但包含类似 JSON 密钥字段的正文进行兜底脱敏。
     */
    private static final Pattern JSON_SECRET_PATTERN = Pattern.compile(
            "(?i)(\"(?:authorization|x-api-key|api[-_]?key|key|token|access_token|refresh_token|password|secret|client_secret|rawKey|raw_key)\"\\s*:\\s*\")([^\"]*)(\")"
    );

    private LogSanitizer() {
    }

    /**
     * 对敏感字段脱敏，并截断过大的正文。
     */
    public static String sanitizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String trimmed = body.trim();
        String sanitized = sanitizeJson(trimmed);
        if (sanitized == null) {
            sanitized = JSON_SECRET_PATTERN.matcher(trimmed).replaceAll("$1" + MASK + "$3");
        }
        return truncate(sanitized);
    }

    /**
     * 对敏感请求头值脱敏，同时保留非敏感请求头用于排查问题。
     */
    public static String sanitizeHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        HttpHeaders sanitized = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (isSensitiveName(name)) {
                sanitized.put(name, List.of(MASK));
            } else {
                sanitized.put(name, values);
            }
        });
        return sanitized.toString();
    }

    /**
     * 尝试按结构化 JSON 脱敏；正文不是合法 JSON 时返回 null。
     */
    private static String sanitizeJson(String body) {
        if (!(body.startsWith("{") || body.startsWith("["))) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            maskJson(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 递归替换 JSON 对象和数组中敏感字段名对应的值。
     */
    private static void maskJson(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = object.get(fieldName);
                if (isSensitiveName(fieldName)) {
                    object.put(fieldName, MASK);
                } else {
                    maskJson(child);
                }
            });
        } else if (node.isArray()) {
            node.forEach(LogSanitizer::maskJson);
        }
    }

    /**
     * 匹配 token、密钥、secret 和密码的精确名称及常见变体名称。
     */
    private static boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase().replace("-", "_");
        if (SENSITIVE_FIELD_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("api_key");
    }

    /**
     * 保持日志可读，避免超大的供应商响应刷屏应用日志。
     */
    private static String truncate(String value) {
        if (value.length() <= MAX_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
    }
}
