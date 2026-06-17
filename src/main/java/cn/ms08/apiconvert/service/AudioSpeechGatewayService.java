package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiAudioBinaryResponse;
import cn.ms08.apiconvert.dto.OpenAiAudioSpeechRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.metrics.GatewayMetrics;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.security.GatewayPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

/**
 * 语音合成网关服务，复用 API Key、模型路由和请求日志能力，向 OpenAI 兼容供应商透传请求；
 * 暂不按 token 计费（音频单价与文本 token 单价不同），只记请求数和审计日志。
 */
@Service
public class AudioSpeechGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AudioSpeechGatewayService.class);
    private static final String SOURCE_PROTOCOL = "openai";
    private static final String REQUEST_TYPE = "audio_speech";

    private final RoutingService routingService;
    private final ProviderClientRegistry providerClientRegistry;
    private final UsageRecorder usageRecorder;
    private final ApiKeyQuotaService apiKeyQuotaService;
    private final GatewayMetrics metrics;

    public AudioSpeechGatewayService(RoutingService routingService, ProviderClientRegistry providerClientRegistry,
                                     UsageRecorder usageRecorder, ApiKeyQuotaService apiKeyQuotaService,
                                     GatewayMetrics metrics) {
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.usageRecorder = usageRecorder;
        this.apiKeyQuotaService = apiKeyQuotaService;
        this.metrics = metrics;
    }

    /**
     * 执行 OpenAI Audio Speech API 调用；返回二进制音频字节 + Content-Type，由 endpoint handler 写出。
     */
    public OpenAiAudioBinaryResponse speech(OpenAiAudioSpeechRequest request, HttpServletRequest servletRequest) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        GatewayPrincipal principal = principal(servletRequest);
        ModelRoute route = null;
        try {
            validate(request);
            route = routingService.resolveModel(request.getModel(), principal.apiKeyId(),
                    principal.allowedChannelCodes(), principal.allowedModelNames(), EndpointType.AUDIO_SPEECH);
            apiKeyQuotaService.recordRequest(principal.apiKeyId());
            long upstreamStart = System.nanoTime();
            int upstreamStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            try {
                OpenAiAudioBinaryResponse response = providerClientRegistry.get(route.providerType())
                        .speech(route, request);
                upstreamStatus = HttpStatus.OK.value();
                routingService.recordSuccess(principal.apiKeyId(), route);
                usageRecorder.recordSuccess(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE,
                        EndpointType.AUDIO_SPEECH.name(), EndpointType.AUDIO_SPEECH.name(),
                        route, false, HttpStatus.OK.value(), System.currentTimeMillis() - start, null);
                return response;
            } finally {
                metrics.recordUpstream(EndpointType.AUDIO_SPEECH, route.providerType().name(),
                        upstreamStatus, System.nanoTime() - upstreamStart);
            }
        } catch (ProviderException exception) {
            routingService.recordFailure(principal.apiKeyId(), null, route, null);
            metrics.recordError(EndpointType.AUDIO_SPEECH, exception.code().name());
            log.warn("语音合成上游调用失败：model={} channel={} error={}",
                    request != null ? request.getModel() : null,
                    route != null ? route.providerCode() : null,
                    exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (GatewayException exception) {
            metrics.recordError(EndpointType.AUDIO_SPEECH, exception.code().name());
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE, route,
                    request != null ? request.getModel() : null, false, exception.status().value(),
                    System.currentTimeMillis() - start, exception.code().name(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            metrics.recordError(EndpointType.AUDIO_SPEECH, ErrorCode.INTERNAL_ERROR.name());
            log.error("语音合成网关异常：{}", exception.getMessage(), exception);
            usageRecorder.recordFailure(requestId, principal.apiKeyId(), SOURCE_PROTOCOL, REQUEST_TYPE,
                    EndpointType.AUDIO_SPEECH.name(), EndpointType.AUDIO_SPEECH.name(),
                    route,
                    request != null ? request.getModel() : null, false, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    System.currentTimeMillis() - start, ErrorCode.INTERNAL_ERROR.name(), "Internal server error");
            throw exception;
        }
    }

    private void validate(OpenAiAudioSpeechRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        if (!StringUtils.hasText(request.getInput())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "input is required");
        }
        if (request.getInput().length() > 4096) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                    "input exceeds 4096 characters");
        }
        if (!StringUtils.hasText(request.getVoice())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "voice is required");
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
