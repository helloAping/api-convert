package cn.ms08.apiconvert.dto.admin;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 管理端请求日志查询条件。
 *
 * @param requestId 请求编号
 * @param gatewayApiKeyId 调用方网关密钥 ID
 * @param sourceProtocol 外部协议，例如 openai 或 anthropic
 * @param requestType 对话接口类型，例如 chat_completions 或 messages
 * @param providerCode 实际渠道编码
 * @param providerType 实际供应商协议类型
 * @param publicModel 请求对外模型名
 * @param success 是否成功
 * @param startTime 日志开始时间，格式 yyyy-MM-dd HH:mm:ss
 * @param endTime 日志结束时间，格式 yyyy-MM-dd HH:mm:ss
 * @param page 页码
 * @param pageSize 每页数量
 */
public record RequestLogSearchParam(
    String requestId,
    Long gatewayApiKeyId,
    String sourceProtocol,
    String requestType,
    String providerCode,
    String providerType,
    String publicModel,
    Boolean success,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime startTime,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime endTime,
    Integer page,
    Integer pageSize
) {}
