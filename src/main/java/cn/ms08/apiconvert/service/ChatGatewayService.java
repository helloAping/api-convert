package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.provider.AiProviderClient;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.security.GatewayPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 统一对话网关服务，负责鉴权身份读取、模型路由、上游转发和请求日志记录。
 */
@Service
public class ChatGatewayService {

    /**
     * 对话网关专用日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(ChatGatewayService.class);

    /**
     * OpenAI Chat Completions 兼容接口类型。
     */
    private static final String REQUEST_TYPE_CHAT_COMPLETIONS = "chat_completions";

    /**
     * 根据模型和密钥授权范围选择实际渠道。
     */
    private final RoutingService routingService;
    /**
     * 按渠道协议类型找到上游客户端实现。
     */
    private final ProviderClientRegistry providerClientRegistry;
    /**
     * 写入对话请求日志，日志失败不影响主链路。
     */
    private final UsageRecorder usageRecorder;
    /**
     * 按密钥余额、滑动窗口和模型单价计算并扣减额度。
     */
    private final ApiKeyQuotaService apiKeyQuotaService;
    /**
     * 用于在流式响应中写入 OpenAI 风格错误事件。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注入对话转发所需依赖。
     */
    public ChatGatewayService(RoutingService routingService, ProviderClientRegistry providerClientRegistry,
                              UsageRecorder usageRecorder, ApiKeyQuotaService apiKeyQuotaService) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
    }

    public UnifiedChatResponse chat(UnifiedChatRequest request, HttpServletRequest servletRequest) {
        return chat(request, servletRequest, "openai", REQUEST_TYPE_CHAT_COMPLETIONS);
    }

    /**
     * 按来源协议记录请求日志，并将统一请求路由到实际承载该模型的随机渠道。
     */
    public UnifiedChatResponse chat(UnifiedChatRequest request, HttpServletRequest servletRequest, String sourceProtocol) {
        return chat(request, servletRequest, sourceProtocol, sourceProtocol);
    }

    /**
     * 按来源协议和接口类型记录每次对话调用，包含失败、耗时、token 和实际渠道信息。
     */
    public UnifiedChatResponse chat(UnifiedChatRequest request, HttpServletRequest servletRequest, String sourceProtocol, String requestType) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        boolean stream = Boolean.TRUE.equals(request.stream());
        GatewayPrincipal principal = principal(servletRequest);
        ModelRoute route = null;
        UnifiedUsage estimatedUsage = apiKeyQuotaService.estimateUsage(request);
        try {
            if (stream) {
                throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "stream is not supported yet");
            }
            route = routingService.resolve(request.model(), principal.allowedChannelCodes());
            apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
            UnifiedChatResponse response = providerClientRegistry.get(route.providerType()).chat(route, request);
            apiKeyQuotaService.deduct(principal.apiKeyId(), route, response.usage(), estimatedUsage);
            usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, stream,
                    HttpStatus.OK.value(), System.currentTimeMillis() - start, response.usage());
            return response;
        } catch (GatewayException exception) {
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), stream, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), stream,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), System.currentTimeMillis() - start,
                    ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            throw exception;
        }
    }

    /**
     * 返回 OpenAI 兼容 SSE 透传响应体；路由阶段失败仍返回普通错误，已开始流式写出后的错误写成 SSE 事件。
     */
    public StreamingResponseBody stream(UnifiedChatRequest request, HttpServletRequest servletRequest) {
        return stream(request, servletRequest, "openai", REQUEST_TYPE_CHAT_COMPLETIONS);
    }

    /**
     * 按来源协议记录流式请求，并将上游 SSE 字节流直接透传给客户端。
     */
    public StreamingResponseBody stream(UnifiedChatRequest request, HttpServletRequest servletRequest, String sourceProtocol, String requestType) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        GatewayPrincipal principal = principal(servletRequest);
        return outputStream -> streamToClient(request, outputStream, requestId, principal, sourceProtocol, requestType, start);
    }

    /**
     * 在流式写出阶段完成路由和上游调用，确保任何失败都能以 SSE error 事件返回。
     */
    private void streamToClient(UnifiedChatRequest request, OutputStream outputStream, String requestId,
                                GatewayPrincipal principal, String sourceProtocol, String requestType,
                                long start) throws IOException {
        ModelRoute route = null;
        UnifiedUsage estimatedUsage = apiKeyQuotaService.estimateUsage(request);
        try {
            route = routingService.resolve(request.model(), principal.allowedChannelCodes());
            apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
            AiProviderClient client = providerClientRegistry.get(route.providerType());
            if (!client.supportsStreaming()) {
                throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                        "stream is not supported for provider type " + route.providerType());
            }
            UnifiedUsage usage = client.streamChat(route, request, outputStream);
            long latencyMs = System.currentTimeMillis() - start;
            log.info("SSE 流完成 请求ID：{}、协议：{}、接口：{}、模型：{}、渠道编码：{}、渠道类型：{}、耗时：{}ms、输入Token：{}、输出Token：{}、总计Token：{}、缓存读取Token：{}",
                    requestId, sourceProtocol, requestType, request.model(),
                    route.providerCode(), route.providerType(), latencyMs,
                    usage != null ? usage.inputTokens() : 0,
                    usage != null ? usage.outputTokens() : 0,
                    usage != null ? usage.totalTokens() : 0,
                    usage != null ? usage.cacheReadInputTokens() : 0);
            apiKeyQuotaService.deduct(principal.apiKeyId(), route, usage, estimatedUsage);
            usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, true,
                    HttpStatus.OK.value(), latencyMs, usage);
        } catch (GatewayException exception) {
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), true,
                    exception.status().value(), System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            writeOpenAiStreamError(outputStream, exception.getMessage(), exception.code().name().toLowerCase(), openAiType(exception.status()));
        } catch (Exception exception) {
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), true,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), System.currentTimeMillis() - start,
                    ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            writeOpenAiStreamError(outputStream, "Internal server error", ErrorCode.INTERNAL_ERROR.name().toLowerCase(), "server_error");
        }
    }

    /**
     * 在 SSE 连接中写入 OpenAI 风格错误块，避免 text/event-stream 客户端收到空响应。
     */
    private void writeOpenAiStreamError(OutputStream outputStream, String message, String code, String type) throws IOException {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("error", java.util.Map.of(
                "message", message,
                "type", type,
                "code", code
        )));
        outputStream.write(("data: " + payload + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * OpenAI 兼容错误类型映射，和全局异常处理保持一致。
     */
    private String openAiType(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return "authentication_error";
        }
        if (status.is4xxClientError()) {
            return "invalid_request_error";
        }
        return "server_error";
    }

    /**
     * 读取鉴权过滤器写入的调用方身份；安全关闭时返回一个不限制渠道的匿名身份。
     */
    private GatewayPrincipal principal(HttpServletRequest request) {
        Object principal = request.getAttribute(GatewayApiKeyFilter.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof GatewayPrincipal gatewayPrincipal) {
            return gatewayPrincipal;
        }
        return new GatewayPrincipal(null, "anonymous", java.util.Set.of());
    }
}
