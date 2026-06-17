package cn.ms08.apiconvert.circuitbreaker;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器注册表：按 (providerCode, providerModel) 维度持有 {@link CircuitBreaker} 实例，
 * 在 RoutingService 路由前过滤 OPEN 维度，上游调用结果后回调 recordSuccess/recordFailure。
 * <p>
 * 注册表是 gateway 单例，breakers 是无状态的可复用对象，使用 {@link ConcurrentMap} 延迟创建。
 */
@Component
public class CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final CircuitBreakerProperties properties;
    private final MeterRegistry meterRegistry;
    /** 各 breaker 的 (failureRate * 100) 整数值，暴露为 Prometheus gauge。 */
    private final ConcurrentMap<String, AtomicInteger> failureRateGauges = new ConcurrentHashMap<>();

    public CircuitBreakerRegistry(CircuitBreakerProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public CircuitBreaker forKey(String providerCode, String providerModel) {
        String key = key(providerCode, providerModel);
        return breakers.computeIfAbsent(key, k -> {
            CircuitBreaker breaker = new CircuitBreaker(providerCode, providerModel, properties);
            registerMetrics(breaker);
            return breaker;
        });
    }

    public Collection<CircuitBreaker> all() {
        return breakers.values();
    }

    public CircuitBreakerProperties properties() {
        return properties;
    }

    private void registerMetrics(CircuitBreaker breaker) {
        Tags tags = Tags.of("provider", breaker.providerCode(), "model", breaker.providerModel());
        Gauge.builder("gateway_circuit_state", breaker, b -> {
                    switch (b.state()) {
                        case CLOSED: return 0.0;
                        case OPEN: return 2.0;
                        case HALF_OPEN: return 1.0;
                        default: return -1.0;
                    }
                })
                .tags(tags)
                .description("Circuit breaker state: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                .register(meterRegistry);
        AtomicInteger rate = new AtomicInteger(0);
        failureRateGauges.put(key(breaker.providerCode(), breaker.providerModel()), rate);
        Gauge.builder("gateway_circuit_failure_rate", rate, AtomicInteger::get)
                .tags(tags)
                .description("Current failure rate in sliding window (0-100)")
                .register(meterRegistry);
    }

    public void updateFailureRate(String providerCode, String providerModel, int total, int failures) {
        AtomicInteger gauge = failureRateGauges.get(key(providerCode, providerModel));
        if (gauge == null) return;
        int pct = total <= 0 ? 0 : (int) Math.round(100.0 * failures / total);
        gauge.set(pct);
    }

    private static String key(String providerCode, String providerModel) {
        return providerCode + "|" + (providerModel == null ? "" : providerModel);
    }
}
