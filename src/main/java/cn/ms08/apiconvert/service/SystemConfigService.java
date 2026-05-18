package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dao.GatewaySystemConfigMapper;
import cn.ms08.apiconvert.dto.RoutingConfig;
import cn.ms08.apiconvert.dto.RoutingMode;
import cn.ms08.apiconvert.dto.admin.RoutingConfigRequest;
import cn.ms08.apiconvert.entity.GatewaySystemConfigEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.vo.admin.RoutingConfigVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 系统配置服务，负责把数据库中的键值配置转换为网关运行期配置。
 */
@Service
public class SystemConfigService {

    private static final String KEY_ROUTING_MODE = "routing.mode";
    private static final String KEY_FAILURE_THRESHOLD = "routing.failure_threshold";
    private static final String KEY_FAILURE_COOLDOWN_MINUTES = "routing.failure_cooldown_minutes";
    private static final String KEY_STICKY_TTL_MINUTES = "routing.sticky_ttl_minutes";
    private static final long CACHE_TTL_MILLIS = 5_000L;

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            KEY_ROUTING_MODE, "路由模式：RANDOM、ROUND_ROBIN、WEIGHTED、SESSION_STICKY",
            KEY_FAILURE_THRESHOLD, "同一密钥+渠道+模型连续失败达到该次数后进入临时避让；0 表示关闭",
            KEY_FAILURE_COOLDOWN_MINUTES, "失败阈值触发后的避让分钟数；0 表示关闭",
            KEY_STICKY_TTL_MINUTES, "会话粘性绑定保留分钟数"
    );

    private final GatewaySystemConfigMapper configMapper;

    private volatile RoutingConfig cachedRoutingConfig;
    private volatile long cacheExpiresAt;

    /**
     * 注入系统配置 Mapper，所有配置项仍存放在业务数据库中以便管理端调整。
     */
    public SystemConfigService(GatewaySystemConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 返回路由服务使用的配置；短时间缓存用于避免每次请求都查询数据库。
     */
    public RoutingConfig routingConfig() {
        long now = System.currentTimeMillis();
        RoutingConfig cached = cachedRoutingConfig;
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            cached = cachedRoutingConfig;
            if (cached != null && now < cacheExpiresAt) {
                return cached;
            }
            RoutingConfig loaded = loadRoutingConfig();
            cachedRoutingConfig = loaded;
            cacheExpiresAt = now + CACHE_TTL_MILLIS;
            return loaded;
        }
    }

    /**
     * 管理端读取当前路由配置。
     */
    public RoutingConfigVO getRoutingConfig() {
        return toVO(routingConfig());
    }

    /**
     * 管理端更新路由配置，并立即使路由服务缓存失效。
     */
    @Transactional
    public RoutingConfigVO updateRoutingConfig(RoutingConfigRequest request) {
        if (request == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "request body is required");
        }
        RoutingMode mode = parseMode(request.mode());
        int failureThreshold = nonNegative(request.failureThreshold(), "failureThreshold");
        int failureCooldownMinutes = nonNegative(request.failureCooldownMinutes(), "failureCooldownMinutes");
        int stickyTtlMinutes = positive(request.stickyTtlMinutes(), "stickyTtlMinutes");

        upsert(KEY_ROUTING_MODE, mode.name());
        upsert(KEY_FAILURE_THRESHOLD, String.valueOf(failureThreshold));
        upsert(KEY_FAILURE_COOLDOWN_MINUTES, String.valueOf(failureCooldownMinutes));
        upsert(KEY_STICKY_TTL_MINUTES, String.valueOf(stickyTtlMinutes));
        invalidateCache();
        return getRoutingConfig();
    }

    private RoutingConfig loadRoutingConfig() {
        return new RoutingConfig(
                parseMode(value(KEY_ROUTING_MODE, RoutingMode.RANDOM.name())),
                integer(value(KEY_FAILURE_THRESHOLD, "0"), 0),
                integer(value(KEY_FAILURE_COOLDOWN_MINUTES, "0"), 0),
                Math.max(1, integer(value(KEY_STICKY_TTL_MINUTES, "1440"), 1440))
        );
    }

    private RoutingConfigVO toVO(RoutingConfig config) {
        return new RoutingConfigVO(config.mode().name(), config.failureThreshold(),
                config.failureCooldownMinutes(), config.stickyTtlMinutes());
    }

    private String value(String key, String defaultValue) {
        GatewaySystemConfigEntity entity = configMapper.selectById(key);
        if (entity == null || !StringUtils.hasText(entity.getConfigValue())) {
            return defaultValue;
        }
        return entity.getConfigValue();
    }

    private void upsert(String key, String value) {
        GatewaySystemConfigEntity entity = configMapper.selectById(key);
        if (entity == null) {
            entity = new GatewaySystemConfigEntity();
            entity.setConfigKey(key);
            entity.setConfigValue(value);
            entity.setDescription(DESCRIPTIONS.get(key));
            configMapper.insert(entity);
            return;
        }
        entity.setConfigValue(value);
        entity.setDescription(DESCRIPTIONS.get(key));
        configMapper.updateById(entity);
    }

    private RoutingMode parseMode(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim().toUpperCase() : RoutingMode.RANDOM.name();
        try {
            return RoutingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Unsupported routing mode: " + raw);
        }
    }

    private int integer(String raw, int defaultValue) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, fieldName + " must be >= 0");
        }
        return value;
    }

    private int positive(Integer value, String fieldName) {
        if (value == null) {
            return 1440;
        }
        if (value <= 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, fieldName + " must be > 0");
        }
        return value;
    }

    private void invalidateCache() {
        cachedRoutingConfig = null;
        cacheExpiresAt = 0;
    }
}
