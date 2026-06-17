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
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
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
     * 上游请求日志只需要排障摘要，超过该长度的多模态内容不再额外序列化成完整 JSON。
     */
    private static final int MAX_LOG_CONTENT_CHARS = 8192;

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
     * 供应商特化 hook 注册表，按 ProviderType 索引——处理 DeepSeek reasoning_content
     * 兜底、Gemini generationConfig 拆分等渠道特化逻辑，与 {@link #adapterRegistry}（跨协议格式转换）正交。
     */
    private final cn.ms08.apiconvert.adapter.endpoint.ProviderHookRegistry hookRegistry;
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
                              StreamTransformerRegistry streamTransformerRegistry,
                              cn.ms08.apiconvert.adapter.endpoint.ProviderHookRegistry hookRegistry) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
        this.adapterRegistry = adapterRegistry;
        this.streamTransformerRegistry = streamTransformerRegistry;
        this.hookRegistry = hookRegistry;
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
            List<ModelRoute> routes = resolveChatRoutes(request, principal, sessionKey, endpointType);
            apiKeyQuotaService.recordRequest(principal.apiKeyId());
            for (int index = 0; index < routes.size(); index++) {
                route = routes.get(index);
                try {
                    apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
                    EndpointType upstreamEndpoint = route.effectiveEndpoint(endpointType);
                    UnifiedChatRequest adaptedRequest = applyRequestAdapter(request, endpointType, upstreamEndpoint, route);
                    AiProviderClient client = providerClientRegistry.get(route.providerType());
                    UnifiedChatResponse response = dispatchProtocol(client, route, adaptedRequest, upstreamEndpoint);
                    UnifiedChatResponse adaptedResponse = applyAdapter(response, endpointType, upstreamEndpoint, route);
                    routingService.recordSuccess(principal.apiKeyId(), route);
                    apiKeyQuotaService.deduct(principal.apiKeyId(), route, adaptedResponse.usage(), estimatedUsage);
                    usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                            endpointType != null ? endpointType.name() : null,
                            upstreamEndpoint.name(),
                            route, stream,
                            HttpStatus.OK.value(), System.currentTimeMillis() - start, adaptedResponse.usage());
                    return adaptedResponse;
                } catch (Exception exception) {
                    if (!shouldTryNextRoute(principal, index, routes, exception)) {
                        throw exception;
                    }
                    recordProviderFailure(principal, request, route, sessionKey, exception);
                    int retryStatus;
                    String retryCode;
                    String retryMsg;
                    if (exception instanceof GatewayException gatewayException) {
                        retryStatus = gatewayException.status().value();
                        retryCode = gatewayException.code().name();
                        retryMsg = gatewayException.getMessage();
                    } else {
                        retryStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
                        retryCode = ErrorCode.INTERNAL_ERROR.name();
                        retryMsg = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
                    }
                    usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                            endpointType != null ? endpointType.name() : null,
                            route.effectiveEndpoint(endpointType).name(),
                            route, request.model(), stream,
                            retryStatus, System.currentTimeMillis() - start, retryCode, retryMsg);
                    log.warn("同步请求当前渠道失败，切换备用渠道继续尝试：模型={}、失败渠道={}、错误类型={}、错误={}",
                            request.model(), route.providerCode(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
                }
            }
            throw new GatewayException(ErrorCode.ROUTE_NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE, "No available route for model: " + request.model());
        } catch (GatewayException exception) {
            recordProviderFailure(principal, request, route, sessionKey, exception);
            log.warn("对话转发失败：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                    endpointType != null ? endpointType.name() : null,
                    endpointType != null ? endpointType.name() : null,
                    route, request.model(), stream, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            log.error("对话转发异常：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                    endpointType != null ? endpointType.name() : null,
                    endpointType != null ? endpointType.name() : null,
                    route, request.model(), stream,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), System.currentTimeMillis() - start,
                    ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            throw exception;
        }
    }

    /**
     * 按密钥开关决定请求是否解析同模型的多渠道失败切换候选。
     */
    private List<ModelRoute> resolveChatRoutes(UnifiedChatRequest request, GatewayPrincipal principal, String sessionKey,
                                               EndpointType endpointType) {
        if (principal.supportsFailover()) {
            return routingService.resolveFailoverRoutes(request, principal.apiKeyId(), principal.allowedChannelCodes(),
                    principal.allowedModelNames(), sessionKey, endpointType);
        }
        return List.of(routingService.resolve(request, principal.apiKeyId(), principal.allowedChannelCodes(),
                principal.allowedModelNames(), sessionKey, endpointType));
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
        CountingOutputStream attemptOutput = null;
        try {
            List<ModelRoute> routes = resolveChatRoutes(request, principal, sessionKey, endpointType);
            apiKeyQuotaService.recordRequest(principal.apiKeyId());
            for (int index = 0; index < routes.size(); index++) {
                route = routes.get(index);
                wrappedStream = null;
                attemptOutput = new CountingOutputStream(outputStream);
                try {
                    apiKeyQuotaService.assertEnough(principal.apiKeyId(), route, estimatedUsage);
                    // upstreamEndpoint 仅决定"上游走哪个端点类型 / 路径"，不影响 dispatch 方法。
                    // 修复前 effectiveEndpoint 会改写 dispatch 端点，导致 Codex /v1/responses 被改走
                    // /v1/chat/completions 再用 ResponsesStreamTransformer 补全，触发断流。
                    EndpointType upstreamEndpoint = route.effectiveEndpoint(endpointType);
                    // 请求体必须按上游协议改写（X→Y 适配），因此仍用 upstreamEndpoint
                    UnifiedChatRequest adaptedRequest = applyRequestAdapter(request, endpointType, upstreamEndpoint, route);
                    // dispatch 严格按客户端端点走；只有"上游 SSE 协议 ≠ 客户端 SSE 协议"时才挂 transformer
                    EndpointType dispatchEndpoint = endpointType != null ? endpointType : upstreamEndpoint;
                    // 检查是否需要流式响应转换（dispatch 端点与上游协议不一致时）
                    OutputStream targetStream = attemptOutput;
                    if (dispatchEndpoint != null && upstreamEndpoint != null && upstreamEndpoint != dispatchEndpoint) {
                        StreamResponseTransformer transformer = streamTransformerRegistry.getForUpstream(dispatchEndpoint, upstreamEndpoint);
                        if (transformer == null) {
                            transformer = streamTransformerRegistry.get(dispatchEndpoint, route.providerType());
                        }
                        if (transformer == null) {
                            transformer = streamTransformerRegistry.get(dispatchEndpoint, adapterProvider(route.providerType()));
                        }
                        if (transformer != null) {
                            long createdAt = java.time.Instant.now().getEpochSecond();
                            wrappedStream = transformer.wrap(attemptOutput, requestId, route.publicModel(), createdAt);
                            if (!principal.supportsFailover()) {
                                wrappedStream.sendInitialEvents();
                            }
                            targetStream = wrappedStream.outputStream();
                        }
                    }
                    AiProviderClient streamClient = providerClientRegistry.get(route.providerType());
                    if (!streamClient.supportsStreaming(dispatchEndpoint)) {
                        throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                                "stream is not supported for provider type " + route.providerType());
                    }
                    log.info("上游请求：POST {} {}、渠道类型：{}、渠道编码：{}、请求体：{}",
                            route.baseUrl() + route.resolvedChatPath(upstreamEndpoint),
                            formatSanitizedHeaders(route),
                            route.providerType(), route.providerCode(),
                            serializeRequest(adaptedRequest));
                    UnifiedUsage usage = dispatchStreamProtocol(streamClient, route, adaptedRequest, targetStream, dispatchEndpoint);
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
                    usageRecorder.recordSuccess(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                            endpointType != null ? endpointType.name() : null,
                            upstreamEndpoint.name(),
                            route, true,
                            HttpStatus.OK.value(), latencyMs, usage);
                    return;
                } catch (Exception exception) {
                    if (shouldTryNextRoute(principal, index, routes, exception, attemptOutput)) {
                        recordProviderFailure(principal, request, route, sessionKey, exception);
                        int retryStatus;
                        String retryCode;
                        String retryMsg;
                        if (exception instanceof GatewayException gatewayException) {
                            retryStatus = gatewayException.status().value();
                            retryCode = gatewayException.code().name();
                            retryMsg = gatewayException.getMessage();
                        } else if (exception instanceof ProviderException providerException) {
                            retryStatus = providerException.status().value();
                            retryCode = providerException.code().name();
                            retryMsg = providerException.getMessage();
                        } else {
                            retryStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
                            retryCode = ErrorCode.INTERNAL_ERROR.name();
                            retryMsg = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
                        }
                        usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                                endpointType != null ? endpointType.name() : null,
                                route.effectiveEndpoint(endpointType).name(),
                                route, request.model(), true,
                                retryStatus, System.currentTimeMillis() - start, retryCode, retryMsg);
                        log.warn("流式请求当前渠道未写出即失败，切换备用渠道继续尝试：模型={}、失败渠道={}、错误类型={}、错误={}",
                                request.model(), route.providerCode(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
                        wrappedStream = null;
                        continue;
                    }
                    throw exception;
                }
            }
            throw new GatewayException(ErrorCode.ROUTE_NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE, "No available route for model: " + request.model());
        } catch (GatewayException exception) {
            if (isClientDisconnect(exception)) {
                log.debug("SSE 客户端断开：模型={}、请求ID：{}", request.model(), requestId);
            } else {
                recordProviderFailure(principal, request, route, sessionKey, exception);
                log.warn("流式转发失败：{}", exception.getMessage(), exception);
            }
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                    endpointType != null ? endpointType.name() : null,
                    endpointType != null ? endpointType.name() : null,
                    route, request.model(), true,
                    exception.status().value(), System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            if (!isClientDisconnect(exception)) {
                writeStreamErrorSafely(wrappedStream, outputStream, exception.getMessage(), exception.code().name().toLowerCase(), openAiType(exception.status()));
            }
        } catch (Exception exception) {
            if (isClientDisconnect(exception)) {
                log.debug("SSE 客户端断开：模型={}、请求ID：{}", request.model(), requestId);
            } else {
                log.error("流式转发异常：{}", exception.getMessage(), exception);
            }
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), sourceProtocol, requestType,
                    endpointType != null ? endpointType.name() : null,
                    endpointType != null ? endpointType.name() : null,
                    route, request.model(), true,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), System.currentTimeMillis() - start,
                    ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            if (!isClientDisconnect(exception)) {
                writeStreamErrorSafely(wrappedStream, outputStream, "Internal server error", ErrorCode.INTERNAL_ERROR.name().toLowerCase(), "server_error");
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 只有真实上游调用失败才累计路由失败次数，额度不足、参数错误等网关本地拒绝不触发渠道避让。
     */
    private void recordProviderFailure(GatewayPrincipal principal, UnifiedChatRequest request, ModelRoute route,
                                       String sessionKey, Exception exception) {
        if (exception instanceof ProviderException) {
            routingService.recordFailure(principal.apiKeyId(), request, route, sessionKey);
        }
    }

    /**
     * 判断本次渠道尝试失败后是否应继续尝试下一个候选；本地鉴权、额度和模型解析类拒绝不应被重试掩盖。
     */
    private boolean shouldTryNextRoute(GatewayPrincipal principal, int index, List<ModelRoute> routes, Exception exception) {
        return shouldTryNextRoute(principal, index, routes, exception, null);
    }

    /**
     * 流式请求只有在尚未向客户端写出任何字节时才允许失败切换，避免破坏已建立的 SSE 响应。
     */
    private boolean shouldTryNextRoute(GatewayPrincipal principal, int index, List<ModelRoute> routes,
                                       Exception exception, CountingOutputStream attemptOutput) {
        return principal.supportsFailover()
                && index < routes.size() - 1
                && (attemptOutput == null || !attemptOutput.hasWritten())
                && !isClientDisconnect(exception)
                && isRouteAttemptFailure(exception);
    }

    /**
     * 渠道尝试内的上游错误、协议不支持和代码异常均可切换；网关本地全局拒绝直接返回。
     */
    private boolean isRouteAttemptFailure(Exception exception) {
        if (!(exception instanceof GatewayException gatewayException)) {
            return true;
        }
        return switch (gatewayException.code()) {
            case PROVIDER_NOT_FOUND, PROVIDER_UNAVAILABLE, PROVIDER_AUTH_FAILED, PROVIDER_RATE_LIMITED,
                 PROVIDER_TIMEOUT, PROVIDER_BAD_RESPONSE, UNSUPPORTED_FEATURE, INTERNAL_ERROR -> true;
            case INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN, MODEL_NOT_FOUND, ROUTE_NOT_FOUND, QUOTA_INSUFFICIENT -> false;
        };
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
        if (containsLargeLogContent(request)) {
            return "<large request omitted>";
        }
        try {
            String json = objectMapper.writeValueAsString(request);
            return LogSanitizer.sanitizeBody(json);
        } catch (Exception exception) {
            return "<serialization error: " + exception.getMessage() + ">";
        }
    }

    /**
     * 检测消息和透传参数中的大文本/base64，避免日志路径复制大对象造成请求后内存峰值。
     */
    private boolean containsLargeLogContent(UnifiedChatRequest request) {
        if (request == null) {
            return false;
        }
        if (request.messages() != null) {
            for (cn.ms08.apiconvert.dto.UnifiedMessage message : request.messages()) {
                if (message != null
                        && (contentLooksLarge(message.content()) || contentLooksLarge(message.options()))) {
                    return true;
                }
            }
        }
        return contentLooksLarge(request.rawOptions());
    }

    /**
     * 递归检查常见 JSON 结构中的字符串长度，不对非字符串对象调用 toString 以免再次复制大内容。
     */
    private boolean contentLooksLarge(Object content) {
        if (content == null) {
            return false;
        }
        if (content instanceof CharSequence text) {
            return text.length() > MAX_LOG_CONTENT_CHARS;
        }
        if (content instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (contentLooksLarge(value)) {
                    return true;
                }
            }
            return false;
        }
        if (content instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (contentLooksLarge(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 应用端点-供应商接口适配器，处理跨协议响应格式转换。
     * <p>
     * 适配器按 (源端点, 目标端点) 维度匹配——上游端点由 {@code ModelRoute.effectiveEndpoint} 解析，
     * 不再绑定具体供应商身份：任何支持目标端点协议的供应商（OPENAI/DEEPSEEK/MIMO_TOKEN_PLAN ...）
     * 都可以复用同一份适配器。
     * </p>
     * <p>
     * 查找已注册的 {@link EndpointProviderAdapter} 进行响应转换；未找到时按以下规则处理：
     * <ul>
     *   <li>源端点 == 目标端点（同协议）→ 直接透传（仅修正 rawResponse 中的 model 为公共模型名）</li>
     *   <li>跨协议且无适配器 → 抛 {@link ErrorCode#UNSUPPORTED_FEATURE}</li>
     * </ul>
     * </p>
     * <p>
     * <b>同协议但有 provider 特化的情况</b>（如 DeepSeek/Gemini）：适配器 {@code targetEndpoint} 与
     * {@code sourceEndpoint} 相同（CHAT→CHAT / ANTHROPIC→ANTHROPIC），仍然需要调用以执行
     * DeepSeek 工具序列归一化、Gemini rawOptions 清理、响应格式重建等 provider 维度特化逻辑。
     * 因此查找顺序固定为 (源, 目标) → (源, 供应商) → (源, adapterProvider(供应商))，找到即调用；
     * 真没有适配器时再走 passthrough（仅 source==target）或抛错。
     * </p>
     */
    private UnifiedChatResponse applyAdapter(UnifiedChatResponse response, EndpointType sourceEndpoint, EndpointType targetEndpoint, ModelRoute route) {
        if (sourceEndpoint == null) {
            return response;
        }
        // Layer 2：跨协议适配器 adaptResponse。
        // 查找顺序：先 (源, 供应商) 命中 provider 维度覆盖（DeepSeek / Gemini 等在同 (source, target) 下
        // 仍有 provider 特化的场景），再 (源, 目标) 命中通用跨协议适配器，最后 (源, adapterProvider(供应商))
        // 兜底历史 key。找不到任何适配器时：同协议走 passthrough，跨协议抛错。
        EndpointProviderAdapter adapter = adapterRegistry.get(sourceEndpoint, route.providerType());
        if (adapter == null) {
            adapter = adapterRegistry.get(sourceEndpoint, targetEndpoint);
        }
        if (adapter == null) {
            adapter = adapterRegistry.get(sourceEndpoint, adapterProvider(route.providerType()));
        }
        if (adapter == null) {
            if (sourceEndpoint == targetEndpoint || targetEndpoint == null) {
                return passthrough(sourceEndpoint, route, response);
            }
            throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                    "No adapter found for source endpoint " + sourceEndpoint
                            + " -> target endpoint " + targetEndpoint
                            + " (provider=" + route.providerType() + ")");
        }
        UnifiedChatResponse adapted = adapter.adaptResponse(response, route.publicModel());
        // Layer 1：hook postProcess（源端点格式下的供应商特化）
        cn.ms08.apiconvert.adapter.endpoint.ProviderHook hook = hookRegistry.get(route.providerType());
        if (hook != null) {
            adapted = hook.postProcess(adapted, sourceEndpoint, route);
        }
        return adapted;
    }

    private UnifiedChatResponse passthrough(EndpointType sourceEndpoint, ModelRoute route, UnifiedChatResponse response) {
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

    /**
     * 根据上游端点类型将请求分发到供应商对应的协议方法。
     */
    private UnifiedChatResponse dispatchProtocol(AiProviderClient client, ModelRoute route,
                                                  UnifiedChatRequest request, EndpointType endpointType) {
        return switch (endpointType) {
            case CHAT_COMPLETIONS, OPENAI_VIDEOS, OPENAI_IMAGES -> client.chat(route, request);
            case ANTHROPIC_MESSAGES -> client.messages(route, request);
            case OPENAI_RESPONSES -> client.responses(route, request);
            default -> throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                    "Unsupported upstream endpoint: " + endpointType);
        };
    }

    /**
     * 根据上游端点类型将流式请求分发到供应商对应的流式协议方法。
     */
    private UnifiedUsage dispatchStreamProtocol(AiProviderClient client, ModelRoute route,
                                                 UnifiedChatRequest request, OutputStream outputStream,
                                                 EndpointType endpointType) {
        return switch (endpointType) {
            case CHAT_COMPLETIONS -> client.streamChat(route, request, outputStream);
            case ANTHROPIC_MESSAGES -> client.streamMessages(route, request, outputStream);
            case OPENAI_RESPONSES -> client.streamResponses(route, request, outputStream);
            default -> throw new GatewayException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                    "Unsupported upstream endpoint: " + endpointType);
        };
    }

    /**
     * 应用端点-供应商适配器的请求转换，流式/非流式路径均需执行。
     * <p>
     * 处理顺序：
     * <ol>
     *   <li>始终先执行 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook#preProcess}，
     *   让供应商特化（如 DeepSeek 兜 reasoning_content=""）在源端点格式下补字段；</li>
     *   <li>按 (源, 目标) → (源, 供应商) → (源, adapterProvider(供应商)) 顺序查适配器，
     *   命中即调用 {@code adaptRequest}；同协议下 DeepSeek/Gemini 适配器仍会跑（工具序列归一化、
     *   rawOptions 清理等 provider 特化）；</li>
     *   <li>未命中时直接返回原请求，不报错——上游 provider 通常能容忍未改写的请求体，
     *   而跨协议请求的兜底由 {@link #applyAdapter} 负责。</li>
     * </ol>
     * </p>
     */
    private UnifiedChatRequest applyRequestAdapter(UnifiedChatRequest request, EndpointType sourceEndpoint, EndpointType targetEndpoint, ModelRoute route) {
        if (targetEndpoint == null) {
            return request;
        }
        // Layer 1：hook preProcess（源端点格式下的供应商特化）
        cn.ms08.apiconvert.adapter.endpoint.ProviderHook hook = hookRegistry.get(route.providerType());
        if (hook != null) {
            request = hook.preProcess(request, sourceEndpoint, route);
        }
        // Layer 2：跨协议 / 同协议 provider 特化适配器 adaptRequest。
        // 查找顺序：先 (源, 供应商) 命中 provider 维度覆盖，再 (源, 目标) 命中通用跨协议适配器，
        // 最后 (源, adapterProvider(供应商)) 兜底历史 key。
        EndpointProviderAdapter adapter = adapterRegistry.get(sourceEndpoint, route.providerType());
        if (adapter == null) {
            adapter = adapterRegistry.get(sourceEndpoint, targetEndpoint);
        }
        if (adapter == null) {
            adapter = adapterRegistry.get(sourceEndpoint, adapterProvider(route.providerType()));
        }
        if (adapter == null) {
            return request;
        }
        return adapter.adaptRequest(request);
    }

    private ProviderType adapterProvider(ProviderType providerType) {
        return switch (providerType) {
            case GPT_AUTH -> ProviderType.OPENAI;
            case CLAUDE_AUTH -> ProviderType.OPENAI;
            default -> providerType;
        };
    }

    /**
     * 将端点类型映射为请求日志用的接口类型标识符。
     */
    private static String requestTypeOf(EndpointType endpointType) {
        return switch (endpointType) {
            case CHAT_COMPLETIONS -> "chat_completions";
            case ANTHROPIC_MESSAGES -> "messages";
            case OPENAI_RESPONSES -> "responses";
            case OPENAI_VIDEOS -> "videos";
            case OPENAI_IMAGES -> "images";
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
        return new GatewayPrincipal(null, "anonymous", java.util.Set.of(), java.util.Set.of(), false);
    }

    /**
     * 检查异常链中是否包含客户端主动断开连接（AsyncRequestNotUsableException）。
     * 客户端断开是流式请求的正常现象，应降级为 DEBUG 日志并跳过错误写入。
     */
    private static boolean isClientDisconnect(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String cn = t.getClass().getName();
            // Spring 异步请求处理路径
            if ("org.springframework.web.context.request.async.AsyncRequestNotUsableException".equals(cn)) {
                return true;
            }
            // Tomcat NIO 同步 I/O 路径（ClientAbortException extends IOException）
            if ("org.apache.catalina.connector.ClientAbortException".equals(cn)) {
                return true;
            }
            // 通用网络断开（非 Tomcat 环境）
            if (t instanceof java.io.IOException ioe) {
                String msg = ioe.getMessage();
                if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset") || msg.contains("connection was forcibly closed") || msg.contains("closed"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 安全地写出流式错误事件，忽略因响应已提交或客户端断开导致的写入失败。
     */
    private void writeStreamErrorSafely(StreamResponseTransformer.WrappedStream wrappedStream,
                                         OutputStream outputStream, String message, String code, String type) {
        try {
            if (wrappedStream != null) {
                wrappedStream.writeErrorEvent(message);
            } else {
                writeOpenAiStreamError(outputStream, message, code, type);
            }
        } catch (Exception ignored) {
            // 响应已提交或客户端已断开，忽略写入失败
            log.trace("写入 SSE 错误事件失败（可能客户端已断开）：{}", ignored.getMessage());
        }
    }

    /**
     * 统计本次流式尝试是否已向客户端写出字节；未写出时上游失败可以安全切换到下一个渠道。
     */
    private static final class CountingOutputStream extends FilterOutputStream {
        private boolean written;

        private CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            written = true;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            if (len > 0) {
                written = true;
            }
        }

        private boolean hasWritten() {
            return written;
        }
    }
}
