package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.provider.ProviderType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 路由解析服务，根据请求模型名找到启用的渠道和上游模型配置。
 */
@Service
public class RoutingService {

    /**
     * 读取渠道模型映射。
     */
    private final AiChannelModelMapper modelMapper;
    /**
     * 读取渠道主配置，包含 Base URL、路径和上游 API Key。
     */
    private final AiChannelMapper channelMapper;

    /**
     * 注入路由所需的渠道和模型 Mapper。
     */
    public RoutingService(AiChannelModelMapper modelMapper, AiChannelMapper channelMapper) {
        this.modelMapper = modelMapper;
        this.channelMapper = channelMapper;
    }

    /**
     * 将客户端请求的模型名解析为可调用上游的完整路由；同一模型存在多个可用渠道时随机选择。
     */
    public ModelRoute resolve(String requestedModel) {
        return resolve(requestedModel, Set.of());
    }

    /**
     * 将客户端请求的模型名解析为可调用上游的完整路由；allowedChannelCodes 为空表示不限制渠道。
     */
    public ModelRoute resolve(String requestedModel, Set<String> allowedChannelCodes) {
        if (!StringUtils.hasText(requestedModel)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        List<RouteCandidate> candidates = resolveCandidates(requestedModel, allowedChannelCodes == null ? Set.of() : allowedChannelCodes);
        if (candidates.isEmpty()) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND, HttpStatus.BAD_REQUEST, "Model not found or no active channel: " + requestedModel);
        }
        RouteCandidate selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        AiChannelModelEntity model = selected.model();
        AiChannelEntity channel = selected.channel();
        return new ModelRoute(model.getPublicName(), channel.getCode(), ProviderType.valueOf(channel.getType()),
                model.getProviderModel(), channel.getBaseUrl(), channel.getChatPath(), channel.getApiKey(),
                model.getInputQuotaPerMillion(), model.getOutputQuotaPerMillion(), model.getCacheReadQuotaPerMillion());
    }

    /**
     * 支持按对外模型名解析，也支持 channel/model 形式直接指定渠道和上游模型。
     */
    private List<RouteCandidate> resolveCandidates(String requestedModel, Set<String> allowedChannelCodes) {
        int separator = requestedModel.indexOf('/');
        if (separator > 0 && separator < requestedModel.length() - 1) {
            String channelCode = requestedModel.substring(0, separator);
            String providerModel = requestedModel.substring(separator + 1);
            List<AiChannelModelEntity> directModels = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                    .eq(AiChannelModelEntity::getChannelCode, channelCode)
                    .eq(AiChannelModelEntity::getProviderModel, providerModel)
                    .eq(AiChannelModelEntity::getEnabled, true));
            List<RouteCandidate> directCandidates = activeCandidates(directModels, allowedChannelCodes);
            if (!directCandidates.isEmpty()) {
                return directCandidates;
            }
        }
        List<AiChannelModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getPublicName, requestedModel)
                .eq(AiChannelModelEntity::getEnabled, true));
        return activeCandidates(models, allowedChannelCodes);
    }

    /**
     * 过滤出启用、ACTIVE 且配置了上游密钥的渠道，避免随机命中不可用渠道。
     */
    private List<RouteCandidate> activeCandidates(List<AiChannelModelEntity> models, Set<String> allowedChannelCodes) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (AiChannelModelEntity model : models) {
            if (!allowedChannelCodes.isEmpty() && !allowedChannelCodes.contains(model.getChannelCode())) {
                continue;
            }
            AiChannelEntity channel = channelMapper.selectOne(new LambdaQueryWrapper<AiChannelEntity>()
                    .eq(AiChannelEntity::getCode, model.getChannelCode()));
            if (channel != null
                    && Boolean.TRUE.equals(channel.getEnabled())
                    && "ACTIVE".equals(channel.getStatus())
                    && StringUtils.hasText(channel.getApiKey())) {
                candidates.add(new RouteCandidate(model, channel));
            }
        }
        return candidates;
    }

    /**
     * 路由候选项，把模型映射和渠道主配置放在一起，便于随机选择后构建 ModelRoute。
     */
    private record RouteCandidate(AiChannelModelEntity model, AiChannelEntity channel) {
    }
}
