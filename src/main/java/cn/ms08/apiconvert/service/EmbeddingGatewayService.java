package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiEmbeddingRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.metrics.GatewayMetrics;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.security.GatewayPrincipal;
import cn.ms08.apiconvert.vo.OpenAiEmbeddingResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 嵌入网关服务，复用 API Key、模型路由、额度扣减和请求日志能力，向 OpenAI 兼容供应商透传请求。
 * 与图片/视频网关服务相比，嵌入按 token 计费（prompt_tokens）并写入请求日志。
 */
@Service
public class EmbeddingGatewayService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGatewayService.class);
    private static final String SOURCE_PROTOCOL = "openai";
    private static final String REQUEST_TYPE = "embeddings";

    private final RoutingService routingService;
    private final ProviderClientRegistry providerClientRegistry;
    private final UsageRecorder usageRecorder;
    private final ApiKeyQuotaService apiKeyQuotaService;
    private final GatewayMetrics metrics;

    public EmbeddingGatewayService(RoutingService routingService, ProviderClientRegistry providerClientRegistry,
                                   UsageRecorder usageRecorder, ApiKeyQuotaService apiKeyQuotaService,
                                   GatewayMetrics metrics) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
        this.metrics = metrics;
    }

    /**
     * 执行 OpenAI Embeddings API 调用；按 prompt_tokens 计费，不读取 cache。
     */
    public OpenAiEmbeddingResponse embed(OpenAiEmbeddingRequest request, HttpServletRequest servletRequest) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        GatewayPrincipal principal = principal(servletRequest);
        ModelRoute route = null;
        try {
            validate(request);
            route = routingService.resolveModel(request.getModel(), principal.apiKeyId(),
                    principal.allowedChannelCodes(), principal.allowedModelNames(), EndpointType.OPENAI_EMBEDDINGS);
            apiKeyQuotaService.recordRequest(principal.apiKeyId());
            long upstreamStart = System.nanoTime();
            int upstreamStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            try {
                OpenAiEmbeddingResponse response = providerClientRegistry.get(route.providerType())
                        .embed(route, request);
                upstreamStatus = HttpStatus.OK.value();
                routingService.recordSuccess(principal.apiKeyId(), route);
                int promptTokens = response.getUsage() != null && response.getUsage().getPromptTokens() != null
                        ? response.getUsage().getPromptTokens() : 0;
                apiKeyQuotaService.deductEmbeddings(principal.apiKeyId(), route, promptTokens);
                usageRecorder.recordSuccess(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE,
                        EndpointType.OPENAI_EMBEDDINGS.name(), EndpointType.OPENAI_EMBEDDINGS.name(),
                        route, false, HttpStatus.OK.value(), System.currentTimeMillis() - start, null);
                return response;
            } finally {
                metrics.recordUpstream(EndpointType.OPENAI_EMBEDDINGS, route.providerType().name(),
                        upstreamStatus, System.nanoTime() - upstreamStart);
            }
        } catch (ProviderException exception) {
            routingService.recordFailure(principal.apiKeyId(), null, route, null);
            metrics.recordError(EndpointType.OPENAI_EMBEDDINGS, exception.code().name());
            log.warn("嵌入上游调用失败：model={} channel={} error={}",
                    request != null ? request.getModel() : null,
                    route != null ? route.providerCode() : null,
                    exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (GatewayException exception) {
            metrics.recordError(EndpointType.OPENAI_EMBEDDINGS, exception.code().name());
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            metrics.recordError(EndpointType.OPENAI_EMBEDDINGS, ErrorCode.INTERNAL_ERROR.name());
            log.error("嵌入网关异常：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE,
                    EndpointType.OPENAI_EMBEDDINGS.name(), EndpointType.OPENAI_EMBEDDINGS.name(),
                    route,
                    request != null ? request.getModel() : null, false, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    System.currentTimeMillis() - start, ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            throw exception;
        }
    }

    private void validate(OpenAiEmbeddingRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (request.getInput() == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input is required");
        }
        List<String> inputs = request.normalizedInput();
        if (inputs.isEmpty()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input must not be empty");
        }
    }

    private GatewayPrincipal principal(HttpServletRequest request) {
        Object principal = request.getAttribute(GatewayApiKeyFilter.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof GatewayPrincipal gatewayPrincipal) {
            return gatewayPrincipal;
        }
        return new GatewayPrincipal(null, "anonymous", Set.of(), Set.of(), false);
    }
}
