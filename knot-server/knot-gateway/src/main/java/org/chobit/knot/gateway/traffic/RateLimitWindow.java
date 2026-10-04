package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.Duration;
import java.util.List;

/**
 * 频控窗口：与 {@code kb_rate_limit_policies} 的 {@code per_second} / {@code per_minute} 一一对应。
 *
 * <p>采用固定窗口（fixed window）：窗口序号由 {@code epochMillis / 窗口长度} 得出，
 * 不依赖时区和本地时钟，多节点下天然一致。窗口过期即计数清零，
 * 因此存储层只要保证「窗口结束后条目被清除」即可，不需要滑动窗口的复杂结构。</p>
 */
public enum RateLimitWindow {

    SECOND("SECOND", Duration.ofSeconds(1)),
    MINUTE("MINUTE", Duration.ofMinutes(1));

    /**
     * 全部窗口，按从窄到宽排列：先判秒级可以最快拒绝突发流量。
     */
    public static final List<RateLimitWindow> ALL = List.of(SECOND, MINUTE);

    private final String code;
    private final long windowMillis;

    RateLimitWindow(String code, Duration window) {
        this.code = code;
        this.windowMillis = window.toMillis();
    }

    /**
     * 窗口标识，用于拼存储键。
     */
    public String code() {
        return code;
    }

    /**
     * 窗口长度（毫秒）。
     */
    public long windowMillis() {
        return windowMillis;
    }

    /**
     * 该窗口在给定策略下的阈值；{@code <= 0} 表示这一档不做限制。
     */
    public int limitOf(RateLimitPolicy policy) {
        if (policy == null) {
            return 0;
        }
        return this == SECOND ? policy.perSecond() : policy.perMinute();
    }

    /**
     * 当前时刻落在的固定窗口序号。
     */
    public String windowId(long nowMillis) {
        return Long.toString(nowMillis / windowMillis);
    }

    /**
     * 当前窗口的结束时刻（毫秒）。
     *
     * <p>额外留一个窗口长度的余量：计数条目比窗口晚一点再清除，
     * 可以避免节点间时钟微小偏差导致的「窗口刚翻页就被判超限」抖动。</p>
     */
    public long expireAtMillis(long nowMillis) {
        return (nowMillis / windowMillis + 2) * windowMillis;
    }
}
