package cn.ms08.apiconvert.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.ms08.apiconvert.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<?> handleGatewayException(GatewayException exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(exception.status())
                    .body(ApiResponse.error(exception.status().value(), exception.getMessage()));
        }
        if (isAnthropicRequest(request)) {
            return ResponseEntity.status(exception.status())
                    .body(anthropicError(exception.getMessage(), openAiType(exception.status())));
        }
        return ResponseEntity.status(exception.status())
                .body(error(exception.getMessage(), exception.code().name().toLowerCase(), openAiType(exception.status())));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> handleNotLogin(NotLoginException e, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Not logged in"));
        }
        if (isAnthropicRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(anthropicError("Not authenticated", "authentication_error"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("Not authenticated", "unauthorized", "authentication_error"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "Not found: " + e.getResourcePath()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Internal server error"));
        }
        if (isAnthropicRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(anthropicError("Internal server error", "api_error"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("Internal server error", ErrorCode.INTERNAL_ERROR.name().toLowerCase(), "server_error"));
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/");
    }

    /**
     * Anthropic 兼容入口需要返回 Anthropic 风格错误体，避免外部工具按 OpenAI 错误解析失败。
     */
    private boolean isAnthropicRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/v1/messages");
    }

    private Map<String, Object> error(String message, String code, String type) {
        return Map.of("error", Map.of(
                "message", message,
                "type", type,
                "code", code
        ));
    }

    /**
     * 构建 Anthropic 兼容错误响应；message 中的上游内容已由供应商客户端做脱敏处理。
     */
    private Map<String, Object> anthropicError(String message, String type) {
        return Map.of("type", "error", "error", Map.of(
                "type", type,
                "message", message
        ));
    }

    private String openAiType(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return "authentication_error";
        }
        if (status.is4xxClientError()) {
            return "invalid_request_error";
        }
        return "server_error";
    }
}
