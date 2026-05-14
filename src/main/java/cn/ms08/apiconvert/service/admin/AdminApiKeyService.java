package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.GatewayApiKeyChannelMapper;
import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import cn.ms08.apiconvert.dto.admin.ApiKeyForm;
import cn.ms08.apiconvert.dto.admin.ApiKeyQuotaAddRequest;
import cn.ms08.apiconvert.dto.admin.ApiKeyUpdateForm;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.entity.GatewayApiKeyChannelEntity;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.security.ApiKeyHasher;
import cn.ms08.apiconvert.vo.admin.ApiKeyCreationVO;
import cn.ms08.apiconvert.vo.admin.ApiKeyVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理端网关密钥服务，负责创建外部工具调用 OpenAI/Anthropic 接口时携带的密钥。
 */
@Service
public class AdminApiKeyService {

    /**
     * 网关密钥主表 Mapper，保存明文密钥和哈希；日志与前端展示必须脱敏明文。
     */
    private final GatewayApiKeyMapper apiKeyMapper;
    /**
     * 密钥与渠道授权关系 Mapper。
     */
    private final GatewayApiKeyChannelMapper apiKeyChannelMapper;
    /**
     * 渠道 Mapper，用于校验授权渠道是否存在。
     */
    private final AiChannelMapper channelMapper;
    /**
     * 用于额度余额的原子追加，避免并发管理操作覆盖余额。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入密钥管理依赖。
     */
    public AdminApiKeyService(GatewayApiKeyMapper apiKeyMapper, GatewayApiKeyChannelMapper apiKeyChannelMapper,
                              AiChannelMapper channelMapper, JdbcTemplate jdbcTemplate) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyChannelMapper = apiKeyChannelMapper;
        this.channelMapper = channelMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有网关密钥，返回明文给管理端用于复制；前端展示时必须脱敏。
     */
    public List<ApiKeyVO> list() {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<GatewayApiKeyEntity>().orderByAsc(GatewayApiKeyEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 按 ID 查询网关密钥。
     */
    public ApiKeyVO getById(Long id) {
        var entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "API key not found");
        }
        return toVO(entity);
    }

    /**
     * 创建外部工具调用密钥；明文和哈希同时保存，哈希用于鉴权，明文用于管理端复制。
     */
    @Transactional
    public ApiKeyCreationVO create(ApiKeyForm form) {
        requireText(form.name(), "密钥名称不能为空");
        List<String> channelCodes = normalizeChannelCodes(form.channelCodes());
        String rawKey = "sk-" + UUID.randomUUID().toString().replace("-", "");
        String hash = ApiKeyHasher.hash(rawKey);
        var entity = new GatewayApiKeyEntity();
        entity.setName(form.name());
        entity.setRawKey(rawKey);
        entity.setApiKeyHash(hash);
        entity.setKeyPreview(maskRawKey(rawKey));
        entity.setStatus("ACTIVE");
        setInitialBalance(entity, form.quotaBalance());
        applyWindowConfig(entity, form.quotaLimit(), form.quotaWindowValue(), form.quotaWindowUnit());
        apiKeyMapper.insert(entity);
        replaceChannels(entity.getId(), channelCodes);
        return new ApiKeyCreationVO(entity.getId(), entity.getName(), rawKey, entity.getKeyPreview(), entity.getStatus(),
                entity.getQuotaBalance(), entity.getQuotaLimit(), entity.getQuotaWindowValue(), entity.getQuotaWindowUnit(), channelCodes);
    }

    /**
     * 更新密钥状态和渠道授权范围；空渠道列表表示不限制渠道。
     */
    @Transactional
    public ApiKeyVO updateStatus(Long id, ApiKeyUpdateForm form) {
        var entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "API key not found");
        }
        if (StringUtils.hasText(form.status())) {
            entity.setStatus(form.status());
        }
        applyWindowConfig(entity, form.quotaLimit(), form.quotaWindowValue(), form.quotaWindowUnit());
        apiKeyMapper.updateById(entity);
        replaceChannels(id, normalizeChannelCodes(form.channelCodes()));
        return toVO(entity);
    }

    /**
     * 给密钥追加可消费额度；追加后从数据库重新读取，返回最新余额。
     */
    @Transactional
    public ApiKeyVO addQuota(Long id, ApiKeyQuotaAddRequest request) {
        var entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "API key not found");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "追加额度必须大于 0");
        }
        jdbcTemplate.update("""
                update gateway_api_key
                set quota_balance = coalesce(quota_balance, 0) + ?, updated_at = ?
                where id = ?
                """, request.amount(), Timestamp.valueOf(LocalDateTime.now()), id);
        return toVO(apiKeyMapper.selectById(id));
    }

    /**
     * 删除网关密钥及其渠道授权关系。
     */
    @Transactional
    public void delete(Long id) {
        apiKeyChannelMapper.delete(new LambdaQueryWrapper<GatewayApiKeyChannelEntity>()
                .eq(GatewayApiKeyChannelEntity::getApiKeyId, id));
        apiKeyMapper.deleteById(id);
    }

    /**
     * 转换为管理端 VO；rawKey 是敏感字段，只能给管理端复制使用。
     */
    private ApiKeyVO toVO(GatewayApiKeyEntity entity) {
        String prefix = entity.getApiKeyHash().length() > 8 ? entity.getApiKeyHash().substring(0, 8) : entity.getApiKeyHash();
        return new ApiKeyVO(entity.getId(), entity.getName(), rawKey(entity), prefix, keyPreview(entity), entity.getStatus(),
                entity.getQuotaBalance(), entity.getQuotaLimit(), entity.getQuotaWindowValue(), entity.getQuotaWindowUnit(),
                findChannelCodes(entity.getId()));
    }

    /**
     * 设置初始余额；为空表示不限总额度，负数没有业务含义。
     */
    private void setInitialBalance(GatewayApiKeyEntity entity, BigDecimal quotaBalance) {
        if (quotaBalance == null) {
            entity.setQuotaBalance(null);
            return;
        }
        if (quotaBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "初始额度不能为负数");
        }
        entity.setQuotaBalance(quotaBalance);
    }

    /**
     * 应用密钥滑动窗口限制；quotaLimit 为空时清除周期限制。
     */
    private void applyWindowConfig(GatewayApiKeyEntity entity, BigDecimal quotaLimit, Integer windowValue, String windowUnit) {
        if (quotaLimit == null) {
            entity.setQuotaLimit(null);
            entity.setQuotaWindowValue(null);
            entity.setQuotaWindowUnit(null);
            return;
        }
        if (quotaLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "限制额度必须大于 0");
        }
        if (windowValue == null || windowValue <= 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "限制窗口必须大于 0");
        }
        String normalizedUnit = normalizeWindowUnit(windowUnit);
        entity.setQuotaLimit(quotaLimit);
        entity.setQuotaWindowValue(windowValue);
        entity.setQuotaWindowUnit(normalizedUnit);
    }

    /**
     * 统一窗口单位，管理端可以传入单复数或小写值。
     */
    private String normalizeWindowUnit(String unit) {
        if (!StringUtils.hasText(unit)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "限制窗口单位不能为空");
        }
        return switch (unit.trim().toUpperCase()) {
            case "HOUR", "HOURS" -> "HOUR";
            case "DAY", "DAYS" -> "DAY";
            case "MONTH", "MONTHS" -> "MONTH";
            default -> throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "不支持的限制窗口单位: " + unit);
        };
    }

    /**
     * 兼容历史数据缺少 raw_key 的情况，无法还原时返回空字符串。
     */
    private String rawKey(GatewayApiKeyEntity entity) {
        return StringUtils.hasText(entity.getRawKey()) ? entity.getRawKey() : "";
    }

    /**
     * 生成列表展示用脱敏密钥；前端也会基于 rawKey 再次脱敏展示。
     */
    private String maskRawKey(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return "sk-****";
        }
        String suffix = rawKey.length() > 4 ? rawKey.substring(rawKey.length() - 4) : rawKey;
        return "sk-****" + suffix;
    }

    /**
     * 兼容历史密钥没有 keyPreview 的情况，优先按 rawKey 生成脱敏值。
     */
    private String keyPreview(GatewayApiKeyEntity entity) {
        if (StringUtils.hasText(entity.getRawKey())) {
            return maskRawKey(entity.getRawKey());
        }
        if (StringUtils.hasText(entity.getKeyPreview())) {
            return entity.getKeyPreview();
        }
        String prefix = entity.getApiKeyHash().length() > 8 ? entity.getApiKeyHash().substring(0, 8) : entity.getApiKeyHash();
        return "sha256:" + prefix;
    }

    /**
     * 用新的渠道列表覆盖当前密钥授权范围。
     */
    private void replaceChannels(Long apiKeyId, List<String> channelCodes) {
        apiKeyChannelMapper.delete(new LambdaQueryWrapper<GatewayApiKeyChannelEntity>()
                .eq(GatewayApiKeyChannelEntity::getApiKeyId, apiKeyId));
        for (String channelCode : channelCodes) {
            GatewayApiKeyChannelEntity entity = new GatewayApiKeyChannelEntity();
            entity.setApiKeyId(apiKeyId);
            entity.setChannelCode(channelCode);
            apiKeyChannelMapper.insert(entity);
        }
    }

    /**
     * 查询密钥允许使用的渠道，空列表代表允许所有渠道。
     */
    private List<String> findChannelCodes(Long apiKeyId) {
        return apiKeyChannelMapper.selectList(new LambdaQueryWrapper<GatewayApiKeyChannelEntity>()
                        .eq(GatewayApiKeyChannelEntity::getApiKeyId, apiKeyId)
                        .orderByAsc(GatewayApiKeyChannelEntity::getChannelCode))
                .stream()
                .map(GatewayApiKeyChannelEntity::getChannelCode)
                .toList();
    }

    /**
     * 去重并校验渠道编码；空列表按允许所有渠道处理。
     */
    private List<String> normalizeChannelCodes(List<String> channelCodes) {
        if (channelCodes == null || channelCodes.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String channelCode : channelCodes) {
            if (!StringUtils.hasText(channelCode)) {
                continue;
            }
            String trimmed = channelCode.trim();
            AiChannelEntity channel = channelMapper.selectOne(new LambdaQueryWrapper<AiChannelEntity>()
                    .eq(AiChannelEntity::getCode, trimmed));
            if (channel == null) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "渠道不存在: " + trimmed);
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    /**
     * 必填文本缺失时抛出管理端校验错误。
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
        }
    }
}
