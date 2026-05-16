package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import cn.ms08.apiconvert.dao.RequestLogMapper;
import cn.ms08.apiconvert.dto.admin.RequestLogSearchParam;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.entity.RequestLogEntity;
import cn.ms08.apiconvert.vo.PageResult;
import cn.ms08.apiconvert.vo.admin.RequestLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端请求日志查询服务，按条件分页读取对话调用审计记录。
 */
@Service
public class AdminRequestLogService {

    /**
     * 请求日志表 Mapper。
     */
    private final RequestLogMapper requestLogMapper;

    /**
     * 网关密钥 Mapper，用于把日志中的密钥 ID 解析为管理端展示名称。
     */
    private final GatewayApiKeyMapper gatewayApiKeyMapper;

    /**
     * 注入请求日志 Mapper 和密钥 Mapper。
     */
    public AdminRequestLogService(RequestLogMapper requestLogMapper, GatewayApiKeyMapper gatewayApiKeyMapper) {
        this.requestLogMapper = requestLogMapper;
        this.gatewayApiKeyMapper = gatewayApiKeyMapper;
    }

    /**
     * 按请求编号、协议、接口类型、渠道、模型、结果和时间范围分页查询请求日志。
     */
    public PageResult<RequestLogVO> search(RequestLogSearchParam param) {
        int page = param.page() != null ? param.page() : 1;
        int pageSize = param.pageSize() != null ? param.pageSize() : 20;
        long total = requestLogMapper.selectCount(buildQueryWrapper(param));

        var wrapper = buildQueryWrapper(param).orderByDesc(RequestLogEntity::getId);
        var mpPage = new Page<RequestLogEntity>(page, pageSize, false);
        var result = requestLogMapper.selectPage(mpPage, wrapper);

        Map<Long, GatewayApiKeyEntity> apiKeys = apiKeysById(result.getRecords());
        var records = result.getRecords().stream()
                .map(entity -> toVO(entity, apiKeys))
                .toList();
        return new PageResult<>(records, total, page, pageSize);
    }

    /**
     * 按日志主键查询单条请求日志。
     */
    public RequestLogVO getById(Long id) {
        var entity = requestLogMapper.selectById(id);
        if (entity == null) return null;
        return toVO(entity, apiKeysById(List.of(entity)));
    }

    /**
     * 将实体转换为管理端视图。
     */
    private RequestLogVO toVO(RequestLogEntity entity, Map<Long, GatewayApiKeyEntity> apiKeys) {
        GatewayApiKeyEntity apiKey = entity.getGatewayApiKeyId() == null ? null : apiKeys.get(entity.getGatewayApiKeyId());
        return new RequestLogVO(entity.getId(), entity.getRequestId(), entity.getGatewayApiKeyId(),
                apiKeyName(entity.getGatewayApiKeyId(), apiKey), apiKey == null ? null : apiKey.getKeyPreview(),
                entity.getSourceProtocol(), entity.getRequestType(), entity.getProviderCode(), entity.getProviderType(),
                entity.getPublicModel(), entity.getProviderModel(),
                entity.getStream(), entity.getSuccess(), entity.getHttpStatus(), entity.getLatencyMs(),
                entity.getInputTokens(), entity.getCacheReadInputTokens(), entity.getOutputTokens(), entity.getTotalTokens(),
                entity.getErrorCode(), entity.getErrorMessage(), entity.getCreatedAt());
    }

    private Map<Long, GatewayApiKeyEntity> apiKeysById(List<RequestLogEntity> logs) {
        List<Long> ids = logs.stream()
                .map(RequestLogEntity::getGatewayApiKeyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return gatewayApiKeyMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GatewayApiKeyEntity::getId, Function.identity()));
    }

    private String apiKeyName(Long apiKeyId, GatewayApiKeyEntity apiKey) {
        if (apiKeyId == null) {
            return null;
        }
        if (apiKey != null && StringUtils.hasText(apiKey.getName())) {
            return apiKey.getName();
        }
        return "Key #" + apiKeyId;
    }

    /**
     * 构建请求日志查询条件，分页列表和总数统计共用同一套过滤逻辑。
     */
    private LambdaQueryWrapper<RequestLogEntity> buildQueryWrapper(RequestLogSearchParam param) {
        var wrapper = new LambdaQueryWrapper<RequestLogEntity>();
        if (StringUtils.hasText(param.requestId())) wrapper.eq(RequestLogEntity::getRequestId, param.requestId());
        if (param.gatewayApiKeyId() != null) wrapper.eq(RequestLogEntity::getGatewayApiKeyId, param.gatewayApiKeyId());
        if (StringUtils.hasText(param.sourceProtocol())) wrapper.eq(RequestLogEntity::getSourceProtocol, param.sourceProtocol());
        if (StringUtils.hasText(param.requestType())) wrapper.eq(RequestLogEntity::getRequestType, param.requestType());
        if (StringUtils.hasText(param.providerCode())) wrapper.eq(RequestLogEntity::getProviderCode, param.providerCode());
        if (StringUtils.hasText(param.providerType())) wrapper.eq(RequestLogEntity::getProviderType, param.providerType());
        if (StringUtils.hasText(param.publicModel())) wrapper.eq(RequestLogEntity::getPublicModel, param.publicModel());
        if (param.success() != null) wrapper.eq(RequestLogEntity::getSuccess, param.success());
        if (param.startTime() != null) wrapper.ge(RequestLogEntity::getCreatedAt, param.startTime());
        if (param.endTime() != null) wrapper.le(RequestLogEntity::getCreatedAt, param.endTime());
        return wrapper;
    }
}
