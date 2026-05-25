package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.security.GatewayPrincipal;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

/**
 * 视频生成网关服务，复用 API Key、模型路由和请求日志能力，向支持视频的供应商透传请求。
 */
@Service
public class VideoGatewayService {

    private static final Logger log = LoggerFactory.getLogger(VideoGatewayService.class);
    private static final String SOURCE_PROTOCOL = "openai";
    private static final String REQUEST_TYPE = "videos";

    private final RoutingService routingService;
    private final ProviderClientRegistry providerClientRegistry;
    private final UsageRecorder usageRecorder;
    private final ApiKeyQuotaService apiKeyQuotaService;

    public VideoGatewayService(RoutingService routingService, ProviderClientRegistry providerClientRegistry,
                               UsageRecorder usageRecorder, ApiKeyQuotaService apiKeyQuotaService) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
    }

    /**
     * 执行 OpenAI Videos API 生成调用；视频暂不按 token 计费，只记录请求数和审计日志。
     */
    public OpenAiVideoResponse generate(OpenAiVideoRequest request, HttpServletRequest servletRequest) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        GatewayPrincipal principal = principal(servletRequest);
        ModelRoute route = null;
        try {
            validate(request);
            route = routingService.resolveModel(request.getModel(), principal.apiKeyId(),
                    principal.allowedChannelCodes(), principal.allowedModelNames());
            apiKeyQuotaService.recordRequest(principal.apiKeyId());
            OpenAiVideoResponse response = providerClientRegistry.get(route.providerType())
                    .generateVideo(route, request);
            routingService.recordSuccess(principal.apiKeyId(), route);
            usageRecorder.recordSuccess(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE,
                    route, false, HttpStatus.OK.value(), System.currentTimeMillis() - start, null);
            return response;
        } catch (ProviderException exception) {
            routingService.recordFailure(principal.apiKeyId(), null, route, null);
            log.warn("视频生成上游调用失败：model={} channel={} error={}",
                    request != null ? request.getModel() : null,
                    route != null ? route.providerCode() : null,
                    exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (GatewayException exception) {
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            log.error("视频生成网关异常：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    System.currentTimeMillis() - start, ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            throw exception;
        }
    }

    private void validate(OpenAiVideoRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "prompt is required");
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
