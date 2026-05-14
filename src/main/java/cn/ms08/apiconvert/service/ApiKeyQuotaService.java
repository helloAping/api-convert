package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedContentPart;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关密钥额度服务，负责请求前额度预检、成功后的余额扣减和密钥级滑动窗口限制。
 */
@Service
public class ApiKeyQuotaService {

    /**
     * 单价字段统一按每 100 万 token 消耗多少额度配置。
     */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    /**
     * 读取密钥余额和窗口限制配置。
     */
    private final GatewayApiKeyMapper apiKeyMapper;
    /**
     * 用原子 SQL 扣减余额，避免并发请求把同一个密钥余额扣成负数。
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * 进程内滑动窗口缓存；窗口内事件会按密钥配置的过期边界清理。
     */
    private final Map<Long, WindowState> windows = new ConcurrentHashMap<>();

    /**
     * 注入额度服务依赖。
     */
    public ApiKeyQuotaService(GatewayApiKeyMapper apiKeyMapper, JdbcTemplate jdbcTemplate) {
        this.apiKeyMapper = apiKeyMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据请求体粗略估算本次调用可能消耗的额度；输出 token 只在请求显式传入 maxTokens 时纳入预检。
     */
    public UnifiedUsage estimateUsage(UnifiedChatRequest request) {
        int inputTokens = estimateInputTokens(request);
        Integer outputTokens = request.maxTokens() == null || request.maxTokens() <= 0 ? 0 : request.maxTokens();
        return new UnifiedUsage(inputTokens, outputTokens, inputTokens + outputTokens, 0);
    }

    /**
     * 请求上游之前先按估算用量检查总余额和滑动窗口，明显不足时直接拒绝请求。
     */
    public void assertEnough(Long apiKeyId, ModelRoute route, UnifiedUsage estimatedUsage) {
        BigDecimal cost = calculateCost(route, estimatedUsage);
        if (apiKeyId == null || cost.signum() <= 0) {
            return;
        }
        GatewayApiKeyEntity key = apiKey(apiKeyId);
        assertBalanceEnough(key, cost);
        assertWindowEnough(key, cost);
    }

    /**
     * 上游成功返回后按实际 usage 扣减余额并写入滑动窗口；上游缺少 usage 时退回使用请求前估算值。
     */
    public void deduct(Long apiKeyId, ModelRoute route, UnifiedUsage actualUsage, UnifiedUsage fallbackUsage) {
        UnifiedUsage usage = actualUsage == null ? fallbackUsage : actualUsage;
        BigDecimal cost = calculateCost(route, usage);
        if (apiKeyId == null || cost.signum() <= 0) {
            return;
        }
        WindowState state = windows.computeIfAbsent(apiKeyId, ignored -> new WindowState());
        synchronized (state) {
            GatewayApiKeyEntity key = apiKey(apiKeyId);
            assertBalanceEnough(key, cost);
            assertWindowEnough(key, cost, state);
            deductBalance(key, cost);
            addWindowEvent(key, cost, state);
        }
    }

    /**
     * 计算指定模型、指定 token 用量应消耗的额度。
     */
    public BigDecimal calculateCost(ModelRoute route, UnifiedUsage usage) {
        if (route == null || usage == null) {
            return BigDecimal.ZERO;
        }
        int inputTokens = positive(usage.inputTokens());
        int outputTokens = positive(usage.outputTokens());
        int cacheReadTokens = Math.min(inputTokens, positive(usage.cacheReadInputTokens()));
        BigDecimal inputCost;
        if (cacheReadTokens > 0 && route.cacheReadQuotaPerMillion() != null) {
            inputCost = tokenCost(inputTokens - cacheReadTokens, route.inputQuotaPerMillion())
                    .add(tokenCost(cacheReadTokens, route.cacheReadQuotaPerMillion()));
        } else {
            inputCost = tokenCost(inputTokens, route.inputQuotaPerMillion());
        }
        return inputCost.add(tokenCost(outputTokens, route.outputQuotaPerMillion()))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 估算输入 token：没有引入 tokenizer 时用文本长度/4 做保守近似，并给每条消息增加少量结构开销。
     */
    private int estimateInputTokens(UnifiedChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (var message : request.messages()) {
            int chars = textLength(message.content()) + textLength(message.role()) + textLength(message.name());
            tokens += Math.max(1, (chars + 3) / 4) + 4;
        }
        return tokens;
    }

    /**
     * 尽量从多模态或结构化内容中提取文本长度，避免直接 JSON 序列化带来额外依赖和敏感内容日志风险。
     */
    private int textLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof String text) {
            return text.length();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value).length();
        }
        if (value instanceof UnifiedContentPart part) {
            return textLength(part.value());
        }
        if (value instanceof List<?> list) {
            return list.stream().mapToInt(this::textLength).sum();
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().mapToInt(this::textLength).sum();
        }
        return String.valueOf(value).length();
    }

    /**
     * 查找密钥配置；如果鉴权和扣费之间密钥被删除，按额度不足拒绝当前请求。
     */
    private GatewayApiKeyEntity apiKey(Long apiKeyId) {
        GatewayApiKeyEntity key = apiKeyMapper.selectById(apiKeyId);
        if (key == null) {
            throw insufficient(HttpStatus.PAYMENT_REQUIRED, "API key quota is unavailable");
        }
        return key;
    }

    /**
     * 检查密钥总余额；余额为空代表不限总额度。
     */
    private void assertBalanceEnough(GatewayApiKeyEntity key, BigDecimal cost) {
        if (key.getQuotaBalance() != null && key.getQuotaBalance().compareTo(cost) < 0) {
            throw insufficient(HttpStatus.PAYMENT_REQUIRED, "API key quota is insufficient");
        }
    }

    /**
     * 检查滑动窗口限制；窗口配置不完整时不启用周期限制。
     */
    private void assertWindowEnough(GatewayApiKeyEntity key, BigDecimal cost) {
        WindowState state = windows.computeIfAbsent(key.getId(), ignored -> new WindowState());
        synchronized (state) {
            assertWindowEnough(key, cost, state);
        }
    }

    /**
     * 在调用方已经持有窗口锁时检查密钥窗口额度。
     */
    private void assertWindowEnough(GatewayApiKeyEntity key, BigDecimal cost, WindowState state) {
        if (!windowEnabled(key)) {
            state.events.clear();
            return;
        }
        purgeExpired(key, state);
        if (state.total.add(cost).compareTo(key.getQuotaLimit()) > 0) {
            throw insufficient(HttpStatus.TOO_MANY_REQUESTS, "API key sliding window quota is insufficient");
        }
    }

    /**
     * 总余额使用数据库原子条件更新扣减，防止并发请求突破剩余额度。
     */
    private void deductBalance(GatewayApiKeyEntity key, BigDecimal cost) {
        if (key.getQuotaBalance() == null) {
            return;
        }
        int updated = jdbcTemplate.update("""
                update gateway_api_key
                set quota_balance = quota_balance - ?, updated_at = ?
                where id = ? and quota_balance >= ?
                """, cost, Timestamp.valueOf(LocalDateTime.now()), key.getId(), cost);
        if (updated == 0) {
            throw insufficient(HttpStatus.PAYMENT_REQUIRED, "API key quota is insufficient");
        }
    }

    /**
     * 写入滑动窗口事件；事件过期边界由密钥的窗口配置决定。
     */
    private void addWindowEvent(GatewayApiKeyEntity key, BigDecimal cost, WindowState state) {
        if (!windowEnabled(key)) {
            return;
        }
        long now = System.currentTimeMillis();
        state.events.addLast(new WindowEvent(now, cost));
        state.total = state.total.add(cost);
        purgeExpired(key, state);
    }

    /**
     * 按当前密钥窗口配置清理已过期的额度事件。
     */
    private void purgeExpired(GatewayApiKeyEntity key, WindowState state) {
        long boundary = windowBoundaryMillis(key);
        while (!state.events.isEmpty() && state.events.peekFirst().createdAtMillis() < boundary) {
            WindowEvent event = state.events.removeFirst();
            state.total = state.total.subtract(event.cost());
        }
        if (state.total.signum() < 0) {
            state.total = BigDecimal.ZERO;
        }
    }

    /**
     * 判断窗口限制配置是否完整且额度为正数。
     */
    private boolean windowEnabled(GatewayApiKeyEntity key) {
        return key.getQuotaLimit() != null
                && key.getQuotaLimit().signum() > 0
                && key.getQuotaWindowValue() != null
                && key.getQuotaWindowValue() > 0
                && key.getQuotaWindowUnit() != null;
    }

    /**
     * 计算滑动窗口起点，MONTH 使用自然月回退以符合“n 月内”的业务语义。
     */
    private long windowBoundaryMillis(GatewayApiKeyEntity key) {
        int value = key.getQuotaWindowValue();
        String unit = key.getQuotaWindowUnit().trim().toUpperCase();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime boundary = switch (unit) {
            case "HOUR", "HOURS" -> now.minusHours(value);
            case "DAY", "DAYS" -> now.minusDays(value);
            case "MONTH", "MONTHS" -> now.minusMonths(value);
            default -> throw insufficient(HttpStatus.BAD_REQUEST, "Unsupported quota window unit: " + key.getQuotaWindowUnit());
        };
        return boundary.toInstant().toEpochMilli();
    }

    /**
     * 计算某一类 token 的额度消耗。
     */
    private BigDecimal tokenCost(int tokens, BigDecimal quotaPerMillion) {
        if (tokens <= 0 || quotaPerMillion == null || quotaPerMillion.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).multiply(quotaPerMillion).divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /**
     * 将空 token 或负数 token 统一按 0 处理，避免异常供应商 usage 影响额度计算。
     */
    private int positive(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    /**
     * 构建对 OpenAI/Anthropic 兼容客户端可识别的额度不足错误。
     */
    private GatewayException insufficient(HttpStatus status, String message) {
        return new GatewayException(ErrorCode.QUOTA_INSUFFICIENT, status, message);
    }

    /**
     * 单个密钥的滑动窗口状态。
     */
    private static final class WindowState {
        private final ArrayDeque<WindowEvent> events = new ArrayDeque<>();
        private BigDecimal total = BigDecimal.ZERO;
    }

    /**
     * 滑动窗口中的一次成功扣费事件。
     */
    private record WindowEvent(long createdAtMillis, BigDecimal cost) {
    }
}
