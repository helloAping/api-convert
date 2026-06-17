package cn.ms08.apiconvert.circuitbreaker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器单个维度（provider × model）的状态机。
 * <p>
 * 三态机：CLOSED → OPEN → HALF_OPEN → CLOSED / OPEN。
 * <ul>
 *   <li>CLOSED：所有请求正常放行；滑动窗口累计成功 / 失败</li>
 *   <li>OPEN：所有请求直接拒绝（短路失败）；冷却时间到后转入 HALF_OPEN</li>
 *   <li>HALF_OPEN：放行最多 {@code halfOpenMaxTrials} 个试探请求；全部成功 → CLOSED，任一失败 → OPEN</li>
 * </ul>
 * 线程安全：所有可变状态用原子变量 / 同步块保护；{@code onCallPermitted} 与 {@code recordSuccess/recordFailure}
 * 在多线程下安全并发。
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** 当前状态机阶段，所有维度复用。 */
    private final String providerCode;
    private final String providerModel;
    private final CircuitBreakerProperties properties;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong openedAtMs = new AtomicLong(0L);
    private final AtomicInteger halfOpenInFlight = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

    /** 滑动窗口：用定长 Deque 记录每次调用的成功 / 失败，新结果从队尾入队、超期结果从队头出队。 */
    private final Deque<Boolean> window;
    private final Object windowLock = new Object();

    public CircuitBreaker(String providerCode, String providerModel, CircuitBreakerProperties properties) {
        this.providerCode = providerCode;
        this.providerModel = providerModel;
        this.properties = properties;
        this.window = new ArrayDeque<>(properties.getWindowSize());
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerModel() {
        return providerModel;
    }

    public State state() {
        transitionIfExpired();
        return state.get();
    }

    /**
     * 请求前置判断：是否允许本次调用通过？HALF_OPEN 阶段按试探配额放行。
     */
    public boolean onCallPermitted() {
        transitionIfExpired();
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) return false;
        // HALF_OPEN：原子累加试探计数，未超阈值才放行
        int inFlight = halfOpenInFlight.incrementAndGet();
        return inFlight <= properties.getHalfOpenMaxTrials();
    }

    /**
     * 请求成功后通知：CLOSED 状态累计成功；HALF_OPEN 试探成功后累加。
     */
    public void recordSuccess() {
        synchronized (windowLock) {
            pushWindow(true);
        }
        if (state.get() == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            int trials = properties.getHalfOpenMaxTrials();
            if (successes >= trials) {
                close();
            }
        }
    }

    /**
     * 请求失败后通知：CLOSED 状态累计失败并在窗口满足阈值时触发 OPEN；HALF_OPEN 任一失败立即 OPEN。
     */
    public void recordFailure() {
        synchronized (windowLock) {
            pushWindow(false);
        }
        State current = state.get();
        if (current == State.HALF_OPEN) {
            open("half-open trial failed");
            return;
        }
        if (current == State.CLOSED && shouldTrip()) {
            open("failure rate exceeded threshold");
        }
    }

    /**
     * 强制重置为 CLOSED（管理端手动恢复）。
     */
    public void reset() {
        synchronized (windowLock) {
            window.clear();
        }
        halfOpenSuccesses.set(0);
        halfOpenInFlight.set(0);
        openedAtMs.set(0L);
        state.set(State.CLOSED);
    }

    public int failureCount() {
        synchronized (windowLock) {
            int n = 0;
            for (Boolean success : window) if (!success) n++;
            return n;
        }
    }

    public int totalCalls() {
        synchronized (windowLock) {
            return window.size();
        }
    }

    private void pushWindow(boolean success) {
        while (window.size() >= properties.getWindowSize()) {
            window.pollFirst();
        }
        window.offerLast(success);
    }

    private boolean shouldTrip() {
        int total = window.size();
        if (total < properties.getMinimumCalls()) return false;
        int failures = failureCount();
        return (double) failures / total >= properties.getFailureRateThreshold();
    }

    private void open(String reason) {
        openedAtMs.set(System.currentTimeMillis());
        halfOpenInFlight.set(0);
        halfOpenSuccesses.set(0);
        state.set(State.OPEN);
        // 触发 OPEN 时清空窗口，避免冷启动半开即再次熔断
        synchronized (windowLock) {
            window.clear();
        }
    }

    private void close() {
        halfOpenInFlight.set(0);
        halfOpenSuccesses.set(0);
        openedAtMs.set(0L);
        synchronized (windowLock) {
            window.clear();
        }
        state.set(State.CLOSED);
    }

    /**
     * 检查 OPEN 是否到期，到期转入 HALF_OPEN。
     */
    private void transitionIfExpired() {
        if (state.get() != State.OPEN) return;
        long openedAt = openedAtMs.get();
        if (openedAt == 0L) return;
        long elapsed = System.currentTimeMillis() - openedAt;
        if (elapsed >= properties.getOpenDurationSeconds() * 1000L) {
            // 仅当从 OPEN 转到 HALF_OPEN 时重置试探计数
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                halfOpenInFlight.set(0);
                halfOpenSuccesses.set(0);
            }
        }
    }
}
