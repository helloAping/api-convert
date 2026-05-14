package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.entity.RequestLogEntity;
import cn.ms08.apiconvert.dao.RequestLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 对话请求审计记录器，只保存脱敏后的路由、耗时、token 和错误信息。
 */
@Service
public class UsageRecorder {

    /**
     * 日志写入失败时只记录告警，不影响原始对话接口返回。
     */
    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    /**
     * 请求日志表 Mapper。
     */
    private final RequestLogMapper requestLogMapper;

    /**
     * 注入请求日志持久化依赖。
     */
    public UsageRecorder(RequestLogMapper requestLogMapper) {
        this.requestLogMapper = requestLogMapper;
    }

    /**
     * 记录成功完成的对话调用，包括实际渠道、供应商协议类型和上游 token 用量。
     */
    public void recordSuccess(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType, ModelRoute route,
                              boolean stream, int httpStatus, long latencyMs, UnifiedUsage usage) {
        RequestLogEntity entity = base(requestId, gatewayApiKeyId, sourceProtocol, requestType, route.publicModel(), stream, httpStatus, latencyMs);
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

    /**
     * 记录失败的对话调用；如果已经完成路由解析，会同时写入渠道和上游模型。
     */
    public void recordFailure(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType, ModelRoute route, String publicModel,
                              boolean stream, int httpStatus, long latencyMs, String errorCode, String errorMessage) {
        String resolvedPublicModel = route == null ? publicModel : route.publicModel();
        RequestLogEntity entity = base(requestId, gatewayApiKeyId, sourceProtocol, requestType, resolvedPublicModel, stream, httpStatus, latencyMs);
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

    /**
     * 构建成功和失败日志共有字段。
     */
    private RequestLogEntity base(String requestId, Long gatewayApiKeyId, String sourceProtocol, String requestType, String publicModel,
                                  boolean stream, int httpStatus, long latencyMs) {
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

    /**
     * 请求日志不能反向影响对话调用；此处不会写入任何密钥明文。
     */
    private void safeInsert(RequestLogEntity entity) {
        try {
            requestLogMapper.insert(entity);
        } catch (Exception exception) {
            log.warn("请求日志写入失败 requestId={} error={}", entity.getRequestId(), exception.getMessage());
        }
    }
}
