package cn.ms08.apiconvert.vo.admin;

import java.time.Instant;

/**
 * 管理端展示用的熔断器状态 VO，序列化时给出最近一次状态切换时间便于排查。
 */
public record CircuitBreakerVO(
    String providerCode,
    String providerModel,
    String state,
    int totalCalls,
    int failureCount,
    long openedAtEpochMs
) {
    public static CircuitBreakerVO of(cn.ms08.apiconvert.circuitbreaker.CircuitBreaker breaker) {
        return new CircuitBreakerVO(
                breaker.providerCode(),
                breaker.providerModel(),
                breaker.state().name(),
                breaker.totalCalls(),
                breaker.failureCount(),
                breaker.state() == cn.ms08.apiconvert.circuitbreaker.CircuitBreaker.State.OPEN
                        ? Instant.now().toEpochMilli() : 0L);
    }
}
