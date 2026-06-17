package cn.ms08.apiconvert.circuitbreaker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private CircuitBreakerProperties properties() {
        CircuitBreakerProperties p = new CircuitBreakerProperties();
        p.setWindowSize(10);
        p.setMinimumCalls(5);
        p.setFailureRateThreshold(0.5);
        p.setOpenDurationSeconds(60); // 默认 60s 冷却，测试用 sleep(10) 模拟到期
        p.setHalfOpenMaxTrials(2);
        return p;
    }

    private CircuitBreakerProperties shortCooldownProperties() {
        CircuitBreakerProperties p = properties();
        p.setOpenDurationSeconds(0); // 触发后立即允许 HALF_OPEN
        p.setHalfOpenMaxTrials(2);
        return p;
    }

    @Test
    void closedWhenBelowMinimumCalls() {
        var cb = new CircuitBreaker("c", "m", properties());
        // 4 次失败，window 还没达到 minimumCalls=5
        for (int i = 0; i < 4; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void tripsOpenWhenFailureRateExceedsThreshold() {
        var cb = new CircuitBreaker("c", "m", properties());
        for (int i = 0; i < 6; i++) cb.recordFailure();
        for (int i = 0; i < 4; i++) cb.recordSuccess();
        // failures=6, total=10, rate=60% > 50%
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.onCallPermitted()).isFalse();
    }

    @Test
    void staysClosedWhenFailureRateBelowThreshold() {
        var cb = new CircuitBreaker("c", "m", properties());
        for (int i = 0; i < 3; i++) cb.recordFailure();
        for (int i = 0; i < 7; i++) cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.onCallPermitted()).isTrue();
    }

    @Test
    void openTransitionsToHalfOpenAfterCoolDown() throws InterruptedException {
        var cb = new CircuitBreaker("c", "m", shortCooldownProperties());
        for (int i = 0; i < 10; i++) cb.recordFailure();
        // 短路调用后立即从 OPEN → HALF_OPEN（cooldown=0）
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(cb.onCallPermitted()).isTrue();
    }

    @Test
    void halfOpenClosesAfterSuccessfulTrials() {
        var cb = new CircuitBreaker("c", "m", shortCooldownProperties());
        for (int i = 0; i < 10; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        // 触发两次试探都成功 → 关闭
        cb.onCallPermitted();
        cb.onCallPermitted();
        cb.recordSuccess();
        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void halfOpenReopensOnAnyFailure() {
        var p = shortCooldownProperties();
        p.setOpenDurationSeconds(1);
        var cb = new CircuitBreaker("c", "m", p);
        for (int i = 0; i < 10; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        // 冷却时间到，进入 HALF_OPEN
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        cb.onCallPermitted();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void halfOpenLimitsInFlightToMaxTrials() {
        var cb = new CircuitBreaker("c", "m", shortCooldownProperties());
        for (int i = 0; i < 10; i++) cb.recordFailure();
        // 触发 HALF_OPEN 后只放过 2 个试探，第 3 个被拒
        assertThat(cb.onCallPermitted()).isTrue();
        assertThat(cb.onCallPermitted()).isTrue();
        assertThat(cb.onCallPermitted()).isFalse();
    }

    @Test
    void resetClearsState() {
        var cb = new CircuitBreaker("c", "m", properties());
        for (int i = 0; i < 10; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);
        cb.reset();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.totalCalls()).isZero();
    }
}
