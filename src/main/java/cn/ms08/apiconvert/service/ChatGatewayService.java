package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.adapter.endpoint.EndpointProviderAdapter;
import cn.ms08.apiconvert.adapter.endpoint.EndpointProviderAdapterRegistry;
import cn.ms08.apiconvert.adapter.stream.StreamResponseTransformer;
import cn.ms08.apiconvert.adapter.stream.StreamTransformerRegistry;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.provider.AiProviderClient;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.security.GatewayPrincipal;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
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
     * 端点-供应商接口适配器注册表，跨协议路由时自动适配请求/响应格式。
     */
    private final EndpointProviderAdapterRegistry adapterRegistry;

    /**
     * 流式响应转换器注册表，流式跨协议路由时实时转换上游 SSE 格式。
     */
    private final StreamTransformerRegistry streamTransformerRegistry;
    /**
     * 用于在流式响应中写入 OpenAI 风格错误事件。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注入对话转发所需依赖。
     */
    public ChatGatewayService(RoutingService routingService, ProviderClientRegistry providerClientRegistry,
                              UsageRecorder usageRecorder, ApiKeyQuotaService apiKeyQuotaService,
                              EndpointProviderAdapterRegistry adapterRegistry,
                              StreamTransformerRegistry streamTransformerRegistry) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
        this.adapterRegistry = adapterRegistry;
        this.streamTransformerRegistry = streamTransformerRegistry;
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
        return doChat(request, servletRequest, sourceProtocol, requestType, null);
    }

    /**
     * 携带端点类型的对话转发入口，自动按 (端点类型, 供应商类型) 匹配接口适配器处理协议转换。
     */
    public UnifiedChatResponse chat(UnifiedChatRequest request, HttpServletRequest servletRequest, EndpointType endpointType) {
        return doChat(request, servletRequest, endpointType.protocol().toLowerCase(), requestTypeOf(endpointType), endpointType);
    }

    /**
     * 对话转发核心实现，支持可选的端点-供应商接口适配。
     */
    private UnifiedChatResponse doChat(UnifiedChatRequest request, HttpServletRequest servletRequest,
                                       String sourceProtocol, String requestType,
                                       EndpointType endpointType) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        boolean stream = Boolean.TRUE.equals(request.stream());
        GatewayPrincipal principal = principal(servletRequest);
        String sessionKey = routingSessionKey(servletRequest, request);
        ModelRoute route = null;
        UnifiedUsage estimatedUsage = apiKeyQuotaService.estimateUsage(request);
        try {
            if (stream) {
                throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "stream is not supported yet");
            }
            route = routingService.resolve(request, principal.apiKeyId(), principal.allowedChannelCodes(), sessionKey);
            apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
            UnifiedChatRequest adaptedRequest = applyRequestAdapter(request, endpointType, route);
            UnifiedChatResponse response = providerClientRegistry.get(route.providerType()).chat(route, adaptedRequest);
            UnifiedChatResponse adaptedResponse = applyAdapter(response, endpointType, route);
            routingService.recordSuccess(principal.apiKeyId(), route);
            apiKeyQuotaService.deduct(principal.apiKeyId(), route, adaptedResponse.usage(), estimatedUsage);
            usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, stream,
                    HttpStatus.OK.value(), System.currentTimeMillis() - start, adaptedResponse.usage());
            return adaptedResponse;
        } catch (GatewayException exception) {
            recordProviderFailure(principal, request, route, sessionKey, exception);
            log.warn("对话转发失败：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), stream, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            log.error("对话转发异常：{}", exception.getMessage(), exception);
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
        return doStream(request, servletRequest, sourceProtocol, requestType, null);
    }

    /**
     * 携带端点类型的流式转发入口，后续可为特定 (端点, 供应商) 组合提供 SSE 适配。
     */
    public StreamingResponseBody stream(UnifiedChatRequest request, HttpServletRequest servletRequest, EndpointType endpointType) {
        return doStream(request, servletRequest, endpointType.protocol().toLowerCase(), requestTypeOf(endpointType), endpointType);
    }

    /**
     * 流式转发核心实现。
     */
    private StreamingResponseBody doStream(UnifiedChatRequest request, HttpServletRequest servletRequest,
                                           String sourceProtocol, String requestType,
                                           EndpointType endpointType) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        GatewayPrincipal principal = principal(servletRequest);
        String sessionKey = routingSessionKey(servletRequest, request);
        return outputStream -> streamToClient(request, outputStream, requestId, principal, sessionKey, sourceProtocol, requestType, start, endpointType);
    }

    /**
     * 在流式写出阶段完成路由和上游调用，确保任何失败都能以 SSE error 事件返回。
     * <p>
     * 参考 CLIProxyAPI 的 ResponseStreamTransform 模式，当端点协议与供应商协议不一致时，
     * 使用 {@link StreamResponseTransformer} 实时转换上游 SSE 字节流为目标协议格式。
     * </p>
     */
    private void streamToClient(UnifiedChatRequest request, OutputStream outputStream, String requestId,
                                GatewayPrincipal principal, String sessionKey, String sourceProtocol, String requestType,
                                long start, EndpointType endpointType) throws IOException {
        MDC.put("traceId", requestId);
        ModelRoute route = null;
        UnifiedUsage estimatedUsage = apiKeyQuotaService.estimateUsage(request);
        StreamResponseTransformer.WrappedStream wrappedStream = null;
        try {
            route = routingService.resolve(request, principal.apiKeyId(), principal.allowedChannelCodes(), sessionKey);
            apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
            // 流式路径也应用端点-供应商适配器的请求转换
            UnifiedChatRequest adaptedRequest = applyRequestAdapter(request, endpointType, route);
            // 检查是否需要流式响应转换（端点与供应商协议不一致时）
            OutputStream targetStream = outputStream;
            if (endpointType != null) {
                StreamResponseTransformer transformer = streamTransformerRegistry.get(endpointType, route.providerType());
                if (transformer != null) {
                    long createdAt = java.time.Instant.now().getEpochSecond();
                    wrappedStream = transformer.wrap(outputStream, requestId, route.publicModel(), createdAt);
                    wrappedStream.sendInitialEvents();
                    targetStream = wrappedStream.outputStream();
                }
            }
            AiProviderClient client = providerClientRegistry.get(route.providerType());
            if (!client.supportsStreaming()) {
                if (wrappedStream != null) {
                    wrappedStream.writeErrorEvent("stream is not supported for provider type " + route.providerType());
                }
                throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                        "stream is not supported for provider type " + route.providerType());
            }
            log.info("上游请求：POST {} {}、渠道类型：{}、渠道编码：{}、请求体：{}",
                    route.baseUrl() + route.chatPath(),
                    formatSanitizedHeaders(route),
                    route.providerType(), route.providerCode(),
                    serializeRequest(adaptedRequest));
            UnifiedUsage usage = client.streamChat(route, adaptedRequest, targetStream);
            // 流式响应转换完成
            if (wrappedStream != null) {
                wrappedStream.complete();
            }
            long latencyMs = System.currentTimeMillis() - start;
            routingService.recordSuccess(principal.apiKeyId(), route);
            log.info("SSE 流完成 请求ID：{}、协议：{}、接口：{}、模型：{}、渠道编码：{}、渠道类型：{}、耗时：{}ms、输入Token：{}、输出Token：{}、总计Token：{}、缓存读取Token：{}",
                    requestId, sourceProtocol, requestType, request.model(),
                    route.providerCode(), route.providerType(), latencyMs,
                    safeTokens(usage != null ? usage.inputTokens() : null),
                    safeTokens(usage != null ? usage.outputTokens() : null),
                    safeTokens(usage != null ? usage.totalTokens() : null),
                    safeTokens(usage != null ? usage.cacheReadInputTokens() : null));
            apiKeyQuotaService.deduct(principal.apiKeyId(), route, usage, estimatedUsage);
            usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, true,
                    HttpStatus.OK.value(), latencyMs, usage);
        } catch (GatewayException exception) {
            recordProviderFailure(principal, request, route, sessionKey, exception);
            log.warn("流式转发失败：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), true,
                    exception.status().value(), System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            if (wrappedStream != null) {
                wrappedStream.writeErrorEvent(exception.getMessage());
            } else {
                writeOpenAiStreamError(outputStream, exception.getMessage(), exception.code().name().toLowerCase(), openAiType(exception.status()));
            }
        } catch (Exception exception) {
            log.error("流式转发异常：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType, route, request.model(), true,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), System.currentTimeMillis() - start,
                    ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            if (wrappedStream != null) {
                wrappedStream.writeErrorEvent("Internal server error");
            } else {
                writeOpenAiStreamError(outputStream, "Internal server error", ErrorCode.INTERNAL_ERROR.name().toLowerCase(), "server_error");
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 只有真实上游调用失败才累计路由失败次数，额度不足、参数错误等网关本地拒绝不触发渠道避让。
     */
    private void recordProviderFailure(GatewayPrincipal principal, UnifiedChatRequest request, ModelRoute route,
                                       String sessionKey, GatewayException exception) {
        if (exception instanceof ProviderException) {
            routingService.recordFailure(principal.apiKeyId(), request, route, sessionKey);
        }
    }

    /**
     * 从请求头和协议参数中提取稳定会话标识，用于会话粘性路由；提取不到时不启用粘性绑定。
     */
    private String routingSessionKey(HttpServletRequest servletRequest, UnifiedChatRequest request) {
        String fromHeader = firstNonBlank(
                servletRequest.getHeader("session_id"),
                servletRequest.getHeader("thread_id"),
                servletRequest.getHeader("x-client-request-id"),
                servletRequest.getHeader("x-codex-window-id")
        );
        if (fromHeader != null) {
            return fromHeader;
        }
        Map<String, Object> rawOptions = request.rawOptions();
        if (rawOptions == null || rawOptions.isEmpty()) {
            return null;
        }
        String direct = firstNonBlank(
                stringValue(rawOptions.get("prompt_cache_key")),
                stringValue(rawOptions.get("previous_response_id")),
                stringValue(rawOptions.get("session_id")),
                stringValue(rawOptions.get("thread_id")),
                stringValue(rawOptions.get("conversation_id")),
                stringValue(rawOptions.get("user"))
        );
        if (direct != null) {
            return direct;
        }
        Object metadata = rawOptions.get("client_metadata");
        if (metadata instanceof Map<?, ?> map) {
            return firstNonBlank(
                    stringValue(map.get("session_id")),
                    stringValue(map.get("thread_id")),
                    stringValue(map.get("conversation_id")),
                    stringValue(map.get("x-codex-window-id"))
            );
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
     * 格式化上游请求的脱敏请求头，仅暴露必要字段避免密钥泄露。
     */
    private String formatSanitizedHeaders(ModelRoute route) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put("Authorization", "Bearer ****");
        return headers.toString();
    }

    /**
     * 将统一请求序列化为 JSON 用于上游请求日志；序列化失败时返回错误提示。
     * 序列化后的 JSON 经过脱敏处理，对话内容（messages.content）会替换为省略标记。
     */
    private String serializeRequest(UnifiedChatRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return LogSanitizer.sanitizeBody(json);
        } catch (Exception exception) {
            return "<serialization error: " + exception.getMessage() + ">";
        }
    }

    /**
     * 应用端点-供应商接口适配器，处理跨协议响应格式转换。
     * <p>
     * 查找已注册的 {@link EndpointProviderAdapter} 进行响应转换；未找到时按以下规则处理：
     * <ul>
     *   <li>与端点默认供应商同协议 → 直接透传（仅修正 rawResponse 中的 model 为公共模型名）</li>
     *   <li>跨协议且无适配器 → 抛 {@link ErrorCode#UNSUPPORTED_FEATURE}</li>
     * </ul>
     * </p>
     */
    private UnifiedChatResponse applyAdapter(UnifiedChatResponse response, EndpointType endpointType, ModelRoute route) {
        if (endpointType == null) {
            return response;
        }
        EndpointProviderAdapter adapter = adapterRegistry.get(endpointType, route.providerType());
        if (adapter != null) {
            return adapter.adaptResponse(response, route.publicModel());
        }
        ProviderType resolvedProvider = route.providerType();
        if (endpointType.defaultProvider() != null && resolvedProvider == endpointType.defaultProvider()) {
            // 同协议透传：修正 model 为公共模型名
            Object raw = response.rawResponse();
            if (raw instanceof OpenAiResponsesResponse rr) {
                rr.setModel(route.publicModel());
            } else if (raw instanceof OpenAiChatCompletionResponse rr) {
                rr.setModel(route.publicModel());
            } else if (raw instanceof AnthropicMessageResponse rr) {
                rr.setModel(route.publicModel());
            }
            return new UnifiedChatResponse(response.id(), route.publicModel(), response.messages(), response.usage(), raw);
        }
        // 跨协议且无适配器
        throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "No adapter found for endpoint " + endpointType + " with provider " + resolvedProvider);
    }

    /**
     * 应用端点-供应商适配器的请求转换，流式/非流式路径均需执行。
     * 未找到适配器时直接返回原始请求。
     */
    private UnifiedChatRequest applyRequestAdapter(UnifiedChatRequest request, EndpointType endpointType, ModelRoute route) {
        if (endpointType == null) {
            return request;
        }
        EndpointProviderAdapter adapter = adapterRegistry.get(endpointType, route.providerType());
        if (adapter == null) {
            return request;
        }
        return adapter.adaptRequest(request);
    }

    /**
     * 将端点类型映射为请求日志用的接口类型标识符。
     */
    private static String requestTypeOf(EndpointType endpointType) {
        return switch (endpointType) {
            case CHAT_COMPLETIONS -> "chat_completions";
            case ANTHROPIC_MESSAGES -> "messages";
            case OPENAI_RESPONSES -> "responses";
            case OPENAI_MODELS -> "models";
            case HEALTH -> "health";
        };
    }

    /**
     * 将 Integer 字段按 null 安全的 0 值提取，避免日志格式化时 auto-unboxing NPE。
     */
    private static int safeTokens(Integer value) {
        return value != null ? value : 0;
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
