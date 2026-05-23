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

    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_HOURS = 24;
    private static final int DEFAULT_TOP_N = 6;
    private static final int MAX_DAYS = 90;
    private static final int MAX_HOURS = 168;
    private static final int MAX_TOP_N = 20;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:00");

    private final RequestLogMapper requestLogMapper;
    private final GatewayApiKeyMapper gatewayApiKeyMapper;
    private final ZoneId projectZoneId;

    /**
     * 注入请求日志 Mapper、密钥 Mapper 和项目统一时区。
     */
    public AdminDashboardService(RequestLogMapper requestLogMapper, GatewayApiKeyMapper gatewayApiKeyMapper,
                                 ZoneId projectZoneId) {
        this.requestLogMapper = requestLogMapper;
        this.gatewayApiKeyMapper = gatewayApiKeyMapper;
        this.projectZoneId = projectZoneId;
    }

    /**
     * 获取仪表盘统计数据。按天、按小时和维度分组都在同一个时间窗口内完成，避免重复查询。
     */
    public DashboardStatsVO stats(DashboardStatsParam param) {
        int days = bounded(param.days(), DEFAULT_DAYS, 1, MAX_DAYS);
        int hours = bounded(param.hours(), DEFAULT_HOURS, 1, MAX_HOURS);
        int topN = bounded(param.topN(), DEFAULT_TOP_N, 1, MAX_TOP_N);

        LocalDateTime now = LocalDateTime.now(projectZoneId);
        LocalDate dailyStartDay = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime dailyStart = dailyStartDay.atStartOfDay();
        LocalDateTime hourlyStart = now.minusHours(hours - 1L).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime queryStart = dailyStart.isBefore(hourlyStart) ? dailyStart : hourlyStart;

        List<RequestLogEntity> logs = requestLogMapper.selectList(new LambdaQueryWrapper<RequestLogEntity>()
                .eq(RequestLogEntity::getSuccess, true)
                .ge(RequestLogEntity::getCreatedAt, queryStart)
                .le(RequestLogEntity::getCreatedAt, now)
                .orderByAsc(RequestLogEntity::getCreatedAt));
        List<RequestLogEntity> dailyLogs = logs.stream()
                .filter(log -> log.getCreatedAt() != null && !log.getCreatedAt().isBefore(dailyStart))
                .toList();
        List<RequestLogEntity> hourlyLogs = logs.stream()
                .filter(log -> log.getCreatedAt() != null && !log.getCreatedAt().isBefore(hourlyStart))
                .toList();

        List<String> dayLabels = dayLabels(dailyStartDay, days);
        List<String> hourLabels = hourLabels(hourlyStart, hours);
        Map<Long, String> apiKeyNames = apiKeyNames(dailyLogs);
        List<DashboardDimensionUsageVO> modelDistribution = topDimensions(dailyLogs, Dimension.MODEL, topN, apiKeyNames);
        List<DashboardDimensionUsageVO> channelDistribution = topDimensions(dailyLogs, Dimension.CHANNEL, topN, apiKeyNames);
        List<DashboardDimensionUsageVO> apiKeyDistribution = topDimensions(dailyLogs, Dimension.API_KEY, topN, apiKeyNames);

        return new DashboardStatsVO(
                summary(dailyLogs),
                tokenPointsByDay(dailyLogs, dayLabels),
                tokenPointsByHour(hourlyLogs, hourLabels),
                modelDistribution,
                channelDistribution,
                apiKeyDistribution,
                dimensionSeries(dailyLogs, Dimension.MODEL, modelDistribution, dayLabels),
                dimensionSeries(dailyLogs, Dimension.CHANNEL, channelDistribution, dayLabels),
                dimensionSeries(dailyLogs, Dimension.API_KEY, apiKeyDistribution, dayLabels)
        );
    }

    private DashboardSummaryVO summary(List<RequestLogEntity> logs) {
        Accumulator accumulator = new Accumulator();
        logs.forEach(accumulator::add);
        return accumulator.toSummary();
    }

    private List<DashboardTokenPointVO> tokenPointsByDay(List<RequestLogEntity> logs, List<String> labels) {
        Map<String, Accumulator> buckets = emptyBuckets(labels);
        for (RequestLogEntity log : logs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            String label = log.getCreatedAt().toLocalDate().format(DAY_FORMATTER);
            Accumulator accumulator = buckets.get(label);
            if (accumulator != null) {
                accumulator.add(log);
            }
        }
        return toTokenPoints(buckets);
    }

    private List<DashboardTokenPointVO> tokenPointsByHour(List<RequestLogEntity> logs, List<String> labels) {
        Map<String, Accumulator> buckets = emptyBuckets(labels);
        for (RequestLogEntity log : logs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            String label = log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).format(HOUR_FORMATTER);
            Accumulator accumulator = buckets.get(label);
            if (accumulator != null) {
                accumulator.add(log);
            }
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
                .sorted(Comparator.comparingLong((DimensionAccumulator accumulator) -> accumulator.totalTokens).reversed()
                        .thenComparing(accumulator -> accumulator.name))
                .limit(topN)
                .map(DimensionAccumulator::toVO)
                .toList();
    }

    private List<DashboardSeriesVO> dimensionSeries(List<RequestLogEntity> logs, Dimension dimension,
                                                    List<DashboardDimensionUsageVO> topDimensions, List<String> labels) {
        List<DashboardSeriesVO> series = new ArrayList<>();
        for (DashboardDimensionUsageVO item : topDimensions) {
            Map<String, Long> buckets = new LinkedHashMap<>();
            labels.forEach(label -> buckets.put(label, 0L));
            for (RequestLogEntity log : logs) {
                if (log.getCreatedAt() == null || !item.key().equals(dimension.key(log))) {
                    continue;
                }
                String label = log.getCreatedAt().toLocalDate().format(DAY_FORMATTER);
                if (buckets.containsKey(label)) {
                    buckets.put(label, buckets.get(label) + tokenTotal(log));
                }
            }
            List<DashboardSeriesPointVO> points = buckets.entrySet().stream()
                    .map(entry -> new DashboardSeriesPointVO(entry.getKey(), entry.getValue()))
                    .toList();
            series.add(new DashboardSeriesVO(item.key(), item.name(), points));
        }
        return series;
    }

    private Map<Long, String> apiKeyNames(List<RequestLogEntity> logs) {
        List<Long> apiKeyIds = logs.stream()
                .map(RequestLogEntity::getGatewayApiKeyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (apiKeyIds.isEmpty()) {
            return Map.of();
        }
        return gatewayApiKeyMapper.selectBatchIds(apiKeyIds).stream()
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
                .map(entry -> entry.getValue().toTokenPoint(entry.getKey()))
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
        int normalized = value == null ? defaultValue : value;
        if (normalized < min) {
            return min;
        }
        return Math.min(normalized, max);
    }

    private static long longValue(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static long tokenTotal(RequestLogEntity log) {
        if (log.getTotalTokens() != null) {
            return log.getTotalTokens().longValue();
        }
        return longValue(log.getInputTokens()) + longValue(log.getOutputTokens());
    }

    private enum Dimension {
        MODEL {
            @Override
            String key(RequestLogEntity log) {
                return textOrDefault(log.getPublicModel(), "unknown-model");
            }

            @Override
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) {
                return textOrDefault(log.getPublicModel(), "未记录模型");
            }
        },
        CHANNEL {
            @Override
            String key(RequestLogEntity log) {
                return textOrDefault(log.getProviderCode(), "unrouted");
            }

            @Override
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) {
                return textOrDefault(log.getProviderCode(), "未路由");
            }
        },
        API_KEY {
            @Override
            String key(RequestLogEntity log) {
                return log.getGatewayApiKeyId() == null ? "anonymous" : String.valueOf(log.getGatewayApiKeyId());
            }

            @Override
            String name(RequestLogEntity log, Map<Long, String> apiKeyNames) {
                if (log.getGatewayApiKeyId() == null) {
                    return "未鉴权";
                }
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
        protected long requestCount;
        protected long successCount;
        protected long failureCount;
        protected long inputTokens;
        protected long cacheReadInputTokens;
        protected long outputTokens;
        protected long totalTokens;

        protected void add(RequestLogEntity log) {
            requestCount++;
            if (Boolean.TRUE.equals(log.getSuccess())) {
                successCount++;
            } else {
                failureCount++;
            }
            inputTokens += longValue(log.getInputTokens());
            cacheReadInputTokens += longValue(log.getCacheReadInputTokens());
            outputTokens += longValue(log.getOutputTokens());
            totalTokens += tokenTotal(log);
        }

        private DashboardSummaryVO toSummary() {
            return new DashboardSummaryVO(requestCount, successCount, failureCount, inputTokens,
                    cacheReadInputTokens, outputTokens, totalTokens);
        }

        private DashboardTokenPointVO toTokenPoint(String label) {
            return new DashboardTokenPointVO(label, requestCount, inputTokens, cacheReadInputTokens, outputTokens, totalTokens);
        }
    }

    private static class DimensionAccumulator extends Accumulator {
        private final String key;
        private final String name;

        private DimensionAccumulator(String key, String name) {
            this.key = key;
            this.name = name;
        }

        private DashboardDimensionUsageVO toVO() {
            return new DashboardDimensionUsageVO(key, name, requestCount, successCount, failureCount,
                    inputTokens, cacheReadInputTokens, outputTokens, totalTokens);
        }
    }
}
