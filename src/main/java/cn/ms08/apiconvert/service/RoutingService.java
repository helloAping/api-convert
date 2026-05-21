package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.RoutingConfig;
import cn.ms08.apiconvert.dto.RoutingMode;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 路由解析服务，根据请求模型、密钥授权、模型能力和运行期路由策略选择上游渠道。
 */
@Service
public class RoutingService {

    private final AiChannelModelMapper modelMapper;
    private final AiChannelMapper channelMapper;
    private final SystemConfigService systemConfigService;

    /**
     * 轮询模式的游标，按候选集合签名隔离，避免不同模型互相影响。
     */
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    /**
     * 平滑加权轮询状态，按候选集合签名隔离。
     */
    private final Map<String, WeightedState> weightedStates = new ConcurrentHashMap<>();
    /**
     * 会话粘性绑定，只保存在内存中，服务重启后自然失效。
     */
    private final Map<StickyKey, StickyBinding> stickyBindings = new ConcurrentHashMap<>();
    /**
     * 同一密钥+渠道+模型的连续失败和临时避让状态。
     */
    private final Map<FailureKey, FailureState> failureStates = new ConcurrentHashMap<>();

    /**
     * 注入路由所需的模型/渠道 Mapper 和系统配置服务。
     */
    public RoutingService(AiChannelModelMapper modelMapper, AiChannelMapper channelMapper,
                          SystemConfigService systemConfigService) {
        this.modelMapper = modelMapper;
        this.channelMapper = channelMapper;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 按模型名解析路由，保留旧调用方使用的默认随机行为入口。
     */
    public ModelRoute resolve(String requestedModel) {
        return resolve(requestedModel, Set.of(), Set.of());
    }

    /**
     * 按模型名和密钥渠道授权范围解析路由。
     */
    public ModelRoute resolve(String requestedModel, Set<String> allowedChannelCodes) {
        return resolve(requestedModel, allowedChannelCodes, Set.of());
    }

    /**
     * 按模型名、密钥渠道授权范围和模型授权范围解析路由。
     */
    public ModelRoute resolve(String requestedModel, Set<String> allowedChannelCodes, Set<String> allowedModelNames) {
        return resolve(requestedModel, null, allowedChannelCodes, allowedModelNames, false, null);
    }

    /**
     * 按统一请求解析路由，自动识别工具调用请求。
     */
    public ModelRoute resolve(UnifiedChatRequest request, Set<String> allowedChannelCodes) {
        return resolve(request, null, allowedChannelCodes, Set.of(), null);
    }

    /**
     * 按统一请求、密钥 ID 和会话标识解析路由，供网关主链路使用。
     */
    public ModelRoute resolve(UnifiedChatRequest request, Long apiKeyId, Set<String> allowedChannelCodes, String sessionKey) {
        return resolve(request, apiKeyId, allowedChannelCodes, Set.of(), sessionKey);
    }

    /**
     * 按统一请求、密钥 ID、渠道授权、模型授权和会话标识解析路由，供网关主链路使用。
     */
    public ModelRoute resolve(UnifiedChatRequest request, Long apiKeyId, Set<String> allowedChannelCodes,
                              Set<String> allowedModelNames, String sessionKey) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request is required");
        }
        return resolve(request.model(), apiKeyId, allowedChannelCodes, allowedModelNames, hasTools(request.rawOptions()), sessionKey);
    }

    /**
     * 上游调用成功后清理该密钥在此渠道模型上的失败状态。
     */
    public void recordSuccess(Long apiKeyId, ModelRoute route) {
        if (route == null) {
            return;
        }
        failureStates.remove(failureKey(apiKeyId, route));
    }

    /**
     * 上游调用失败后累计连续失败次数，达到配置阈值时进入临时避让并清理相关会话粘性绑定。
     */
    public void recordFailure(Long apiKeyId, UnifiedChatRequest request, ModelRoute route, String sessionKey) {
        if (route == null) {
            return;
        }
        RoutingConfig config = systemConfigService.routingConfig();
        StickyKey stickyKey = stickyKey(apiKeyId, request != null ? request.model() : route.publicModel(), sessionKey);
        if (stickyKey != null) {
            stickyBindings.remove(stickyKey);
        }
        if (!config.failureCooldownEnabled()) {
            return;
        }
        FailureKey key = failureKey(apiKeyId, route);
        FailureState state = failureStates.computeIfAbsent(key, ignored -> new FailureState());
        synchronized (state) {
            state.failureCount++;
            if (state.failureCount >= config.failureThreshold()) {
                state.blockedUntilMillis = System.currentTimeMillis() + config.failureCooldownMinutes() * 60_000L;
                state.failureCount = 0;
            }
        }
    }

    private ModelRoute resolve(String requestedModel, Long apiKeyId, Set<String> allowedChannelCodes,
                               Set<String> allowedModelNames, boolean requiresTools, String sessionKey) {
        if (!StringUtils.hasText(requestedModel)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "model is required");
        }
        Set<String> allowed = allowedChannelCodes == null ? Set.of() : allowedChannelCodes;
        Set<String> allowedModels = allowedModelNames == null ? Set.of() : allowedModelNames;
        List<RouteCandidate> candidates = resolveCandidates(requestedModel, allowed, allowedModels);
        if (candidates.isEmpty()) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND, HttpStatus.BAD_REQUEST,
                    "Model not found or no active channel: " + requestedModel);
        }
        RoutingConfig config = systemConfigService.routingConfig();
        List<RouteCandidate> available = filterTemporarilyBlocked(candidates, apiKeyId, config);
        if (available.isEmpty()) {
            throw new GatewayException(ErrorCode.ROUTE_NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE,
                    "No available route for model because all candidates are temporarily blocked: " + requestedModel);
        }
        available = preferToolCapableCandidates(available, requiresTools);
        RouteCandidate selected = selectCandidate(requestedModel, apiKeyId, sessionKey, available, config);
        return toRoute(selected);
    }

    /**
     * 支持按对外模型名解析，也支持 channel/model 形式直接指定渠道和上游模型。
     */
    private List<RouteCandidate> resolveCandidates(String requestedModel, Set<String> allowedChannelCodes,
                                                   Set<String> allowedModelNames) {
        int separator = requestedModel.indexOf('/');
        if (separator > 0 && separator < requestedModel.length() - 1) {
            String channelCode = requestedModel.substring(0, separator);
            String providerModel = requestedModel.substring(separator + 1);
            List<AiChannelModelEntity> directModels = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                    .eq(AiChannelModelEntity::getChannelCode, channelCode)
                    .eq(AiChannelModelEntity::getProviderModel, providerModel)
                    .eq(AiChannelModelEntity::getEnabled, true));
            List<RouteCandidate> directCandidates = activeCandidates(directModels, allowedChannelCodes, allowedModelNames);
            if (!directCandidates.isEmpty()) {
                return directCandidates;
            }
        }
        List<AiChannelModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getPublicName, requestedModel)
                .eq(AiChannelModelEntity::getEnabled, true));
        return activeCandidates(models, allowedChannelCodes, allowedModelNames);
    }

    /**
     * 过滤出启用、ACTIVE 且配置了上游密钥的渠道。
     */
    private List<RouteCandidate> activeCandidates(List<AiChannelModelEntity> models, Set<String> allowedChannelCodes,
                                                  Set<String> allowedModelNames) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (AiChannelModelEntity model : models) {
            if (!allowedChannelCodes.isEmpty() && !allowedChannelCodes.contains(model.getChannelCode())) {
                continue;
            }
            if (!allowedModelNames.isEmpty() && !allowedModelNames.contains(model.getPublicName())) {
                continue;
            }
            AiChannelEntity channel = channelMapper.selectOne(new LambdaQueryWrapper<AiChannelEntity>()
                    .eq(AiChannelEntity::getCode, model.getChannelCode()));
            if (channel != null
                    && Boolean.TRUE.equals(channel.getEnabled())
                    && "ACTIVE".equals(channel.getStatus())
                    && hasUsableCredential(channel)) {
                candidates.add(new RouteCandidate(model, channel));
            }
        }
        return sorted(candidates);
    }

    private List<RouteCandidate> preferToolCapableCandidates(List<RouteCandidate> candidates, boolean requiresTools) {
        if (!requiresTools) {
            return candidates;
        }
        List<RouteCandidate> toolCapable = candidates.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.model().getToolsSupport()))
                .toList();
        return toolCapable.isEmpty() ? candidates : toolCapable;
    }

    private List<RouteCandidate> filterTemporarilyBlocked(List<RouteCandidate> candidates, Long apiKeyId, RoutingConfig config) {
        if (!config.failureCooldownEnabled()) {
            return candidates;
        }
        long now = System.currentTimeMillis();
        List<RouteCandidate> available = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            FailureState state = failureStates.get(failureKey(apiKeyId, candidate));
            if (state == null || state.blockedUntilMillis <= now) {
                if (state != null && state.blockedUntilMillis > 0) {
                    failureStates.remove(failureKey(apiKeyId, candidate));
                }
                available.add(candidate);
            }
        }
        return available;
    }

    private RouteCandidate selectCandidate(String requestedModel, Long apiKeyId, String sessionKey,
                                           List<RouteCandidate> candidates, RoutingConfig config) {
        if (candidates.size() == 1) {
            RouteCandidate only = candidates.getFirst();
            rememberSticky(requestedModel, apiKeyId, sessionKey, only, config);
            return only;
        }
        if (config.mode() == RoutingMode.SESSION_STICKY) {
            RouteCandidate sticky = findStickyCandidate(requestedModel, apiKeyId, sessionKey, candidates);
            if (sticky != null) {
                return sticky;
            }
        }

        RouteCandidate selected = switch (config.mode()) {
            case ROUND_ROBIN -> selectRoundRobin(requestedModel, candidates);
            case WEIGHTED -> selectWeighted(requestedModel, candidates);
            case RANDOM, SESSION_STICKY -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        };
        rememberSticky(requestedModel, apiKeyId, sessionKey, selected, config);
        return selected;
    }

    private RouteCandidate selectRoundRobin(String requestedModel, List<RouteCandidate> candidates) {
        String key = routeStateKey(requestedModel, candidates);
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, ignored -> new AtomicInteger());
        int index = Math.floorMod(counter.getAndIncrement(), candidates.size());
        return candidates.get(index);
    }

    private RouteCandidate selectWeighted(String requestedModel, List<RouteCandidate> candidates) {
        String key = routeStateKey(requestedModel, candidates);
        return weightedStates.computeIfAbsent(key, ignored -> new WeightedState()).select(candidates);
    }

    private RouteCandidate findStickyCandidate(String requestedModel, Long apiKeyId, String sessionKey,
                                               List<RouteCandidate> candidates) {
        StickyKey key = stickyKey(apiKeyId, requestedModel, sessionKey);
        if (key == null) {
            return null;
        }
        StickyBinding binding = stickyBindings.get(key);
        long now = System.currentTimeMillis();
        if (binding == null) {
            return null;
        }
        if (binding.expiresAtMillis() <= now) {
            stickyBindings.remove(key);
            return null;
        }
        for (RouteCandidate candidate : candidates) {
            if (binding.matches(candidate)) {
                return candidate;
            }
        }
        stickyBindings.remove(key);
        return null;
    }

    private void rememberSticky(String requestedModel, Long apiKeyId, String sessionKey,
                                RouteCandidate selected, RoutingConfig config) {
        if (config.mode() != RoutingMode.SESSION_STICKY) {
            return;
        }
        StickyKey key = stickyKey(apiKeyId, requestedModel, sessionKey);
        if (key == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + config.stickyTtlMinutes() * 60_000L;
        stickyBindings.put(key, new StickyBinding(selected.channel().getCode(), selected.model().getProviderModel(), expiresAt));
    }

    private boolean hasTools(Map<String, Object> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return false;
        }
        Object tools = rawOptions.get("tools");
        if (tools instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (tools instanceof Object[] array) {
            return array.length > 0;
        }
        return tools != null;
    }

    private List<RouteCandidate> sorted(List<RouteCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing((RouteCandidate candidate) -> candidate.channel().getCode())
                        .thenComparing(candidate -> candidate.model().getProviderModel()))
                .toList();
    }

    private ModelRoute toRoute(RouteCandidate selected) {
        AiChannelModelEntity model = selected.model();
        AiChannelEntity channel = selected.channel();
        return new ModelRoute(model.getPublicName(), channel.getCode(), ProviderType.valueOf(channel.getType()),
                model.getProviderModel(), channel.getBaseUrl(), channel.getChatPath(), channel.getApiKey(),
                channel.getAuthMode(), channel.getAuthFilePath(),
                model.getInputQuotaPerMillion(), model.getOutputQuotaPerMillion(), model.getCacheReadQuotaPerMillion());
    }

    private boolean hasUsableCredential(AiChannelEntity channel) {
        ProviderType type = ProviderType.valueOf(channel.getType());
        if (type == ProviderType.GPT_AUTH || type == ProviderType.CLAUDE_AUTH) {
            return "AUTHORIZED".equals(channel.getAuthStatus()) && StringUtils.hasText(channel.getAuthFilePath());
        }
        return StringUtils.hasText(channel.getApiKey());
    }

    private String routeStateKey(String requestedModel, List<RouteCandidate> candidates) {
        StringBuilder builder = new StringBuilder(requestedModel).append('|');
        for (RouteCandidate candidate : candidates) {
            builder.append(candidate.identity()).append(';');
        }
        return builder.toString();
    }

    private FailureKey failureKey(Long apiKeyId, ModelRoute route) {
        return new FailureKey(apiKeyId, route.providerCode(), route.providerModel());
    }

    private FailureKey failureKey(Long apiKeyId, RouteCandidate candidate) {
        return new FailureKey(apiKeyId, candidate.channel().getCode(), candidate.model().getProviderModel());
    }

    private StickyKey stickyKey(Long apiKeyId, String requestedModel, String sessionKey) {
        if (!StringUtils.hasText(sessionKey) || !StringUtils.hasText(requestedModel)) {
            return null;
        }
        return new StickyKey(apiKeyId, requestedModel, sessionKey);
    }

    private static int weight(RouteCandidate candidate) {
        Integer priority = candidate.channel().getPriority();
        return priority == null || priority <= 0 ? 1 : priority;
    }

    private record RouteCandidate(AiChannelModelEntity model, AiChannelEntity channel) {
        private String identity() {
            return channel.getCode() + "/" + model.getProviderModel();
        }
    }

    private record StickyKey(Long apiKeyId, String requestedModel, String sessionKey) {
    }

    private record StickyBinding(String providerCode, String providerModel, long expiresAtMillis) {
        private boolean matches(RouteCandidate candidate) {
            return providerCode.equals(candidate.channel().getCode())
                    && providerModel.equals(candidate.model().getProviderModel());
        }
    }

    private record FailureKey(Long apiKeyId, String providerCode, String providerModel) {
    }

    private static class FailureState {
        private int failureCount;
        private long blockedUntilMillis;
    }

    /**
     * 平滑加权轮询状态，使用渠道 priority 作为权重，避免简单随机导致短时间分布抖动。
     */
    private static class WeightedState {
        private final Map<String, Integer> currentWeights = new HashMap<>();

        private synchronized RouteCandidate select(List<RouteCandidate> candidates) {
            Map<String, RouteCandidate> valid = new LinkedHashMap<>();
            for (RouteCandidate candidate : candidates) {
                valid.put(candidate.identity(), candidate);
            }
            currentWeights.keySet().removeIf(key -> !valid.containsKey(key));

            int totalWeight = 0;
            String selectedKey = null;
            int selectedWeight = Integer.MIN_VALUE;
            for (RouteCandidate candidate : candidates) {
                String key = candidate.identity();
                int weight = weight(candidate);
                totalWeight += weight;
                int current = currentWeights.getOrDefault(key, 0) + weight;
                currentWeights.put(key, current);
                if (current > selectedWeight) {
                    selectedWeight = current;
                    selectedKey = key;
                }
            }
            currentWeights.put(selectedKey, currentWeights.get(selectedKey) - totalWeight);
            return valid.get(selectedKey);
        }
    }
}
