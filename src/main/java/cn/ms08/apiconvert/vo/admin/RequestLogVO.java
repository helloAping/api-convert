package cn.ms08.apiconvert.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 管理端请求日志视图，展示对话接口调用的路由、耗时、token 和错误信息。
 */
public record RequestLogVO(
    Long id,
    String requestId,
    Long gatewayApiKeyId,
    String sourceProtocol,
    String requestType,
    String providerCode,
    String providerType,
    String publicModel,
    String providerModel,
    Boolean stream,
    Boolean success,
    Integer httpStatus,
    Long latencyMs,
    Integer inputTokens,
    Integer cacheReadInputTokens,
    Integer outputTokens,
    Integer totalTokens,
    String errorCode,
    String errorMessage,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime createdAt
) {}
