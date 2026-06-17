package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.entity.RequestLogEntity;
import cn.ms08.apiconvert.dao.RequestLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);
    private final RequestLogMapper requestLogMapper;

    public UsageRecorder(RequestLogMapper requestLogMapper) {
        this.requestLogMapper = requestLogMapper;
    }

    public void recordSuccess(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType,
                              ModelRoute route, boolean stream, int httpStatus, long latencyMs, UnifiedUsage usage) {
        recordSuccess(requestId, gatewayApiKeyId, sourceProtocol, requestType, null, null, route, stream, httpStatus, latencyMs, usage);
    }

    public void recordSuccess(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType,
                              String sourceEndpointType, String upstreamEndpointType,
                              ModelRoute route, boolean stream, int httpStatus, long latencyMs, UnifiedUsage usage) {
        RequestLogEntity entity = base(requestId, gatewayApiKeyId, sourceProtocol, requestType, route.publicModel(), stream, httpStatus, latencyMs);
        entity.setSourceEndpointType(sourceEndpointType);
        entity.setUpstreamEndpointType(upstreamEndpointType);
        entity.setProviderCode(route.providerCode());
        entity.setProviderType(route.providerType().name());
        entity.setProviderModel(route.providerModel());
        entity.setSuccess(true);
        if (usage != null) {
            entity.setInputTokens(usage.inputTokens());
            entity.setCacheReadInputTokens(usage.cacheReadInputTokens());
            entity.setOutputTokens(usage.outputTokens());
            entity.setTotalTokens(usage.totalTokens());
        }
        safeInsert(entity);
    }

    public void recordFailure(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType,
                              ModelRoute route, String publicModel,
                              boolean stream, int httpStatus, long latencyMs, String errorCode, String errorMessage) {
        recordFailure(requestId, gatewayApiKeyId, sourceProtocol, requestType, null, null, route, publicModel, stream, httpStatus, latencyMs, errorCode, errorMessage);
    }

    public void recordFailure(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType,
                              String sourceEndpointType, String upstreamEndpointType,
                              ModelRoute route, String publicModel,
                              boolean stream, int httpStatus, long latencyMs, String errorCode, String errorMessage) {
        String resolvedPublicModel = route == null ? publicModel : route.publicModel();
        RequestLogEntity entity = base(requestId, gatewayApiKeyId, sourceProtocol, requestType, resolvedPublicModel, stream, httpStatus, latencyMs);
        entity.setSourceEndpointType(sourceEndpointType);
        entity.setUpstreamEndpointType(upstreamEndpointType);
        if (route != null) {
            entity.setProviderCode(route.providerCode());
            entity.setProviderType(route.providerType().name());
            entity.setProviderModel(route.providerModel());
        }
        entity.setSuccess(false);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        safeInsert(entity);
    }

    private RequestLogEntity base(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType,
                                  String publicModel, boolean stream, int httpStatus, long latencyMs) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setGatewayApiKeyId(gatewayApiKeyId);
        entity.setSourceProtocol(sourceProtocol);
        entity.setRequestType(requestType);
        entity.setPublicModel(publicModel);
        entity.setStream(stream);
        entity.setHttpStatus(httpStatus);
        entity.setLatencyMs(latencyMs);
        return entity;
    }

    private void safeInsert(RequestLogEntity entity) {
        try {
            requestLogMapper.insert(entity);
        } catch (Exception exception) {
            log.warn("请求日志写入失败 requestId={} error={}", entity.getRequestId(), exception.getMessage());
        }
    }
}
