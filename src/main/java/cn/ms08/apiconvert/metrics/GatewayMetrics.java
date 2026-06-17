package cn.ms08.apiconvert.metrics;

import cn.ms08.apiconvert.endpoint.EndpointType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 网关指标门面，统一管理 Micrometer Counter / Timer 的命名、tag 维度和缓存。
 * <p>
 * 指标命名遵循 Prometheus 规范：{@code gateway_<area>_<metric>_<unit>}，tag 维度收敛到
 * {@code endpoint / provider / status / error_code} 四个核心字段，避免高基数（不允许把
 * api_key_id、model、request_id 等放进 tag）。
 * <p>
 * Counter/Timer 按 (name, tags) 缓存复用，避免每次请求都新建 Meter 实例。
 */
@Component
public class GatewayMetrics {

    public static final String M_REQUESTS_TOTAL = "gateway_requests_total";
    public static final String M_REQUEST_DURATION = "gateway_request_duration_seconds";
    public static final String M_UPSTREAM_TOTAL = "gateway_upstream_calls_total";
    public static final String M_UPSTREAM_DURATION = "gateway_upstream_duration_seconds";
    public static final String M_FAILOVER_TOTAL = "gateway_failover_attempts_total";
    public static final String M_ERROR_TOTAL = "gateway_errors_total";
    public static final String M_INFLIGHT = "gateway_active_requests";

    private static final String TAG_ENDPOINT = "endpoint";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ERROR_CODE = "error_code";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次网关入口请求开始，返回 Sample 供结束时调用 {@link #stopRequest}。
     */
    public Timer.Sample startRequest(EndpointType endpoint) {
        Timer.Sample sample = Timer.start(registry);
        registry.counter(M_INFLIGHT, TAG_ENDPOINT, endpoint.name()).increment();
        return sample;
    }

    /**
     * 结束请求计时，并按 HTTP 状态码累加 Counter。
     */
    public void stopRequest(Timer.Sample sample, EndpointType endpoint, int httpStatus) {
        registry.counter(M_INFLIGHT, TAG_ENDPOINT, endpoint.name()).increment(-1);
        String tagValue = String.valueOf(httpStatus);
        sample.stop(cachedTimer(M_REQUEST_DURATION,
                Tags.of(TAG_ENDPOINT, endpoint.name(), TAG_STATUS, tagValue)));
        counter(M_REQUESTS_TOTAL, Tags.of(TAG_ENDPOINT, endpoint.name(), TAG_STATUS, tagValue)).increment();
    }

    /**
     * 记录一次上游调用完成（含成功与失败），按 provider + status 打点。
     */
    public void recordUpstream(EndpointType endpoint, String provider, int httpStatus, long elapsedNanos) {
        String status = String.valueOf(httpStatus);
        Tags tags = Tags.of(TAG_ENDPOINT, endpoint.name(),
                TAG_PROVIDER, provider == null ? "unknown" : provider,
                TAG_STATUS, status);
        counter(M_UPSTREAM_TOTAL, tags).increment();
        cachedTimer(M_UPSTREAM_DURATION, tags).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录一次失败切换（failover）尝试。
     */
    public void recordFailoverAttempt(EndpointType endpoint, String fromProvider) {
        counter(M_FAILOVER_TOTAL, Tags.of(TAG_ENDPOINT, endpoint.name(),
                TAG_PROVIDER, fromProvider == null ? "unknown" : fromProvider)).increment();
    }

    /**
     * 记录一次错误事件，按错误码聚合。
     */
    public void recordError(EndpointType endpoint, String errorCode) {
        counter(M_ERROR_TOTAL, Tags.of(TAG_ENDPOINT, endpoint.name(),
                TAG_ERROR_CODE, errorCode == null ? "UNKNOWN" : errorCode)).increment();
    }

    private Counter counter(String name, Tags tags) {
        String key = name + '|' + tags;
        return counterCache.computeIfAbsent(key, k -> Counter.builder(name).tags(tags).register(registry));
    }

    private Timer cachedTimer(String name, Tags tags) {
        String key = name + '|' + tags;
        return timerCache.computeIfAbsent(key, k -> Timer.builder(name).tags(tags).register(registry));
    }
}
