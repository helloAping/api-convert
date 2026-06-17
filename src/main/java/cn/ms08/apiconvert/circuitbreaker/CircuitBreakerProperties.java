package cn.ms08.apiconvert.circuitbreaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 熔断器配置：滑动窗口大小、失败率阈值、OPEN 状态冷却时间、HALF_OPEN 试探次数。
 * 默认 20 次窗口 / 50% 失败率 / 60 秒冷却 / 3 次试探，适合大多数 AI 上游；高频低延迟场景可调小窗口，
 * 慢响应场景可调高阈值。
 */
@ConfigurationProperties(prefix = "api-convert.circuit-breaker")
public class CircuitBreakerProperties {

    /** 滑动窗口大小（每次 CB 维度最多记录的最近调用数）。 */
    private int windowSize = 20;
    /** 失败率阈值（0.0 - 1.0），窗口内失败数 / 总数 超过阈值即 OPEN。 */
    private double failureRateThreshold = 0.5;
    /** 最小窗口样本数，样本不足时不触发熔断（避免偶发错误拉低比例）。 */
    private int minimumCalls = 10;
    /** OPEN 状态持续时间（秒），到期转入 HALF_OPEN。 */
    private int openDurationSeconds = 60;
    /** HALF_OPEN 状态允许放过的最大试探次数，超出后任意失败立即 OPEN。 */
    private int halfOpenMaxTrials = 3;

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public double getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(double failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public int getMinimumCalls() {
        return minimumCalls;
    }

    public void setMinimumCalls(int minimumCalls) {
        this.minimumCalls = minimumCalls;
    }

    public int getOpenDurationSeconds() {
        return openDurationSeconds;
    }

    public void setOpenDurationSeconds(int openDurationSeconds) {
        this.openDurationSeconds = openDurationSeconds;
    }

    public int getHalfOpenMaxTrials() {
        return halfOpenMaxTrials;
    }

    public void setHalfOpenMaxTrials(int halfOpenMaxTrials) {
        this.halfOpenMaxTrials = halfOpenMaxTrials;
    }
}
