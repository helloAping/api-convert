package cn.ms08.apiconvert.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 渠道能力配置，每个能力对应一种上游端点类型及其独立请求路径。
 *
 * @param type 端点类型名称，对应 EndpointType 枚举值，如 CHAT_COMPLETIONS、ANTHROPIC_MESSAGES
 * @param path 该能力对应的上游请求路径，如 /v1/chat/completions、/v1/messages
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelCapability(
    String type,
    String path
) {}
