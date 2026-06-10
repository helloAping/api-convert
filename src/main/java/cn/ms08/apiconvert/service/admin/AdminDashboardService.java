package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import cn.ms08.apiconvert.dao.RequestLogMapper;
import cn.ms08.apiconvert.dto.admin.DashboardStatsParam;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.entity.RequestLogEntity;
import cn.ms08.apiconvert.vo.admin.DashboardDimensionUsageVO;
import cn.ms08.apiconvert.vo.admin.DashboardSeriesPointVO;
import cn.ms08.apiconvert.vo.admin.DashboardSeriesVO;
import cn.ms08.apiconvert.vo.admin.DashboardStatsVO;
import cn.ms08.apiconvert.vo.admin.DashboardSummaryVO;
import cn.ms08.apiconvert.vo.admin.DashboardTokenPointVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端控制台仪表盘统计服务，基于 request_log 做跨数据库内存聚合。
 */
@Service
public class AdminDashboardService {

    private static final int DEFAULT_TOP_N = 6;
    private static final int MAX_TOP_N = 20;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:00");

    private final RequestLogMapper requestLogMapper;
    private final GatewayApiKeyMapper gatewayApiKeyMapper;
    private final ZoneId projectZoneId;

    public AdminDashboardService(RequestLogMapper requestLogMapper, GatewayApiKeyMapper gatewayApiKeyMapper,
                                 ZoneId projectZoneId) {
        this.requestLogMapper = requestLogMapper;
        this.gatewayApiKeyMapper = gatewayApiKeyMapper;
        this.projectZoneId = projectZoneId;
    }

    /**
     * 获取仪表盘统计数据。根据 range 参数自动决定按天或按小时聚合。
     * <ul>
     *   <li>24h / 48h / 72h → 按小时聚合</li>
     *   <li>7d / 14d / 30d / 90d → 按天聚合</li>
     * </ul>
     */
    public DashboardStatsVO stats(DashboardStatsParam param) {
        int topN = bounded(param.topN(), DEFAULT_TOP_N, 1, MAX_TOP_N);
        RangeConfig range = parseRange(param.range());

        LocalDateTime now = LocalDateTime.now(projectZoneId);
        LocalDateTime start = range.daily
                ? now.toLocalDate().minusDays(range.value - 1L).atStartOfDay()
                : now.minusHours(range.value - 1L).withMinute(0).withSecond(0).withNano(0);

        List<RequestLogEntity> logs = requestLogMapper.selectList(new LambdaQueryWrapper<RequestLogEntity>()
                .eq(RequestLogEntity::getSuccess, true)
                .ge(RequestLogEntity::getCreatedAt, start)
                .le(RequestLogEntity::getCreatedAt, now)
                .orderByAsc(RequestLogEntity::getCreatedAt));

        List<String> labels = range.daily
                ? dayLabels(now.toLocalDate().minusDays(range.value - 1L), range.value)
                : hourLabels(start, range.value);

        Map<Long, String> apiKeyNames = apiKeyNames(logs);
        List<DashboardDimensionUsageVO> modelDistribution = topDimensions(logs, Dimension.MODEL, topN, apiKeyNames);
        List<DashboardDimensionUsageVO> channelDistribution = topDimensions(logs, Dimension.CHANNEL, topN, apiKeyNames);
        List<DashboardDimensionUsageVO> apiKeyDistribution = topDimensions(logs, Dimension.API_KEY, topN, apiKeyNames);

        return new DashboardStatsVO(
                summary(logs),
                tokenPoints(logs, labels, range.daily),
                modelDistribution,
                channelDistribution,
                apiKeyDistribution,
                dimensionSeries(logs, Dimension.MODEL, modelDistribution, labels, range.daily),
                dimensionSeries(logs, Dimension.CHANNEL, channelDistribution, labels, range.daily),
                dimensionSeries(logs, Dimension.API_KEY, apiKeyDistribution, labels, range.daily)
        );
    }

    private DashboardSummaryVO summary(List<RequestLogEntity> logs) {
        Accumulator acc = new Accumulator();
        logs.forEach(acc::add);
        return acc.toSummary();
    }

    private List<DashboardTokenPointVO> tokenPoints(List<RequestLogEntity> logs, List<String> labels, boolean daily) {
        Map<String, Accumulator> buckets = emptyBuckets(labels);
        for (RequestLogEntity log : logs) {
            if (log.getCreatedAt() == null) continue;
            String label = daily
                    ? log.getCreatedAt().toLocalDate().format(DAY_FORMATTER)
                    : log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).format(HOUR_FORMATTER);
            Accumulator acc = buckets.get(label);
            if (acc != null) acc.add(log);
        }
        return toTokenPoints(buckets);
    }

    private List<DashboardDimensionUsageVO> topDimensions(List<RequestLogEntity> logs, Dimension dimension, int topN,
                                                          Map<Long, String> apiKeyNames) {
        Map<String, DimensionAccumulator> grouped = new LinkedHashMap<>();
        for (RequestLogEntity log : logs) {
            String key = dimension.key(log);
            String name = dimension.name(log, apiKeyNames);
            grouped.computeIfAbsent(key, ignored -> new DimensionAccumulator(key, name)).add(log);
        }
        return grouped.values().stream()
                .sorted(Comparator.comparingLong((DimensionAccumulator a) -> a.totalTokens).reversed()
                        .thenComparing(a -> a.name))
                .limit(topN)
                .map(DimensionAccumulator::toVO)
                .toList();
    }

    private List<DashboardSeriesVO> dimensionSeries(List<RequestLogEntity> logs, Dimension dimension,
                                                    List<DashboardDimensionUsageVO> topItems,
                                                    List<String> labels, boolean daily) {
        List<DashboardSeriesVO> series = new ArrayList<>();
        for (DashboardDimensionUsageVO item : topItems) {
            Map<String, Long> buckets = new LinkedHashMap<>();
            labels.forEach(label -> buckets.put(label, 0L));
            for (RequestLogEntity log : logs) {
                if (log.getCreatedAt() == null || !item.key().equals(dimension.key(log))) continue;
                String label = daily
                        ? log.getCreatedAt().toLocalDate().format(DAY_FORMATTER)
                        : log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).format(HOUR_FORMATTER);
                Long prev = buckets.get(label);
                if (prev != null) {
                    buckets.put(label, prev + tokenTotal(log));
                }
            }
            List<DashboardSeriesPointVO> points = buckets.entrySet().stream()
                    .map(e -> new DashboardSeriesPointVO(e.getKey(), e.getValue()))
                    .toList();
            series.add(new DashboardSeriesVO(item.key(), item.name(), points));
        }
        return series;
    }

    private Map<Long, String> apiKeyNames(List<RequestLogEntity> logs) {
        List<Long> ids = logs.stream()
                .map(RequestLogEntity::getGatewayApiKeyId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return gatewayApiKeyMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GatewayApiKeyEntity::getId, this::apiKeyDisplayName));
    }

    private String apiKeyDisplayName(GatewayApiKeyEntity apiKey) {
        return StringUtils.hasText(apiKey.getName()) ? apiKey.getName() : "Key #" + apiKey.getId();
    }

    private Map<String, Accumulator> emptyBuckets(List<String> labels) {
        Map<String, Accumulator> buckets = new LinkedHashMap<>();
        labels.forEach(label -> buckets.put(label, new Accumulator()));
        return buckets;
    }

    private List<DashboardTokenPointVO> toTokenPoints(Map<String, Accumulator> buckets) {
        return buckets.entrySet().stream()
                .map(e -> e.getValue().toTokenPoint(e.getKey()))
                .toList();
    }

    private List<String> dayLabels(LocalDate start, int days) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            labels.add(start.plusDays(i).format(DAY_FORMATTER));
        }
        return labels;
    }

    private List<String> hourLabels(LocalDateTime start, int hours) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < hours; i++) {
            labels.add(start.plusHours(i).format(HOUR_FORMATTER));
        }
        return labels;
    }

    private int bounded(Integer value, int defaultValue, int min, int max) {
        int v = value == null ? defaultValue : value;
        return Math.max(min, Math.min(v, max));
    }

    private static long longValue(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static long tokenTotal(RequestLogEntity log) {
        if (log.getTotalTokens() != null) return log.getTotalTokens().longValue();
        return longValue(log.getInputTokens()) + longValue(log.getOutputTokens());
    }

    /**
     * 解析 range 参数为聚合配置：小时级范围用按小时聚合，天级范围用按天聚合。
     */
    private static RangeConfig parseRange(String range) {
        if (range == null) range = "";
        return switch (range) {
            case "24h" -> new RangeConfig(24, false);
            case "48h" -> new RangeConfig(48, false);
            case "72h" -> new RangeConfig(72, false);
            case "14d" -> new RangeConfig(14, true);
            case "30d" -> new RangeConfig(30, true);
            case "90d" -> new RangeConfig(90, true);
            default -> new RangeConfig(7, true);
        };
    }

    private record RangeConfig(int value, boolean daily) {}

    private enum Dimension {
        MODEL {
            String key(RequestLogEntity log) { return textOrDefault(log.getPublicModel(), "unknown-model"); }
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) { return textOrDefault(log.getPublicModel(), "未记录模型"); }
        },
        CHANNEL {
            String key(RequestLogEntity log) { return textOrDefault(log.getProviderCode(), "unrouted"); }
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) { return textOrDefault(log.getProviderCode(), "未路由"); }
        },
        API_KEY {
            String key(RequestLogEntity log) { return log.getGatewayApiKeyId() == null ? "anonymous" : String.valueOf(log.getGatewayApiKeyId()); }
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) {
                if (log.getGatewayApiKeyId() == null) return "未鉴权";
                return apiKeyNames.getOrDefault(log.getGatewayApiKeyId(), "Key #" + log.getGatewayApiKeyId());
            }
        };

        abstract String key(RequestLogEntity log);
        abstract String name(RequestLogEntity log, Map<Long, String> apiKeyNames);

        static String textOrDefault(String value, String defaultValue) {
            return StringUtils.hasText(value) ? value : defaultValue;
        }
    }

    private static class Accumulator {
        long requestCount, successCount, failureCount;
        long inputTokens, cacheReadInputTokens, outputTokens, totalTokens;

        void add(RequestLogEntity log) {
            requestCount++;
            if (Boolean.TRUE.equals(log.getSuccess())) successCount++; else failureCount++;
            inputTokens += longValue(log.getInputTokens());
            cacheReadInputTokens += longValue(log.getCacheReadInputTokens());
            outputTokens += longValue(log.getOutputTokens());
            totalTokens += tokenTotal(log);
        }

        DashboardSummaryVO toSummary() {
            return new DashboardSummaryVO(requestCount, successCount, failureCount,
                    inputTokens, cacheReadInputTokens, outputTokens, totalTokens);
        }

        DashboardTokenPointVO toTokenPoint(String label) {
            return new DashboardTokenPointVO(label, requestCount, inputTokens,
                    cacheReadInputTokens, outputTokens, totalTokens);
        }
    }

    private static class DimensionAccumulator extends Accumulator {
        final String key;
        final String name;

        DimensionAccumulator(String key, String name) {
            this.key = key;
            this.name = name;
        }

        DashboardDimensionUsageVO toVO() {
            return new DashboardDimensionUsageVO(key, name, requestCount, successCount, failureCount,
                    inputTokens, cacheReadInputTokens, outputTokens, totalTokens);
        }
    }
}
