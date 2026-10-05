package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.time.Duration;
import java.util.List;

/**
 * 限流维度：与 {@code kb_rate_limit_policies} 的 {@code rpm} / {@code tpm} 一一对应，
 * 两者都是分钟级固定窗口（窗口序号 = {@code epochMillis / 60000}，与时区无关，多节点一致）。
 *
 * <p>两者的记账时机不同，这是由「请求前拿不到 token 数」决定的：</p>
 * <ul>
 *   <li>{@link #RPM}：请求进入即 +1，不管上游成败，超出即拒；</li>
 *   <li>{@link #TPM}：请求成功后按真实用量累加，超限只能在<b>后续请求</b>上拦住。</li>
 * </ul>
 */
public enum RateLimitDimension {

    RPM("RPM", true),
    TPM("TPM", false);

    /**
     * 窗口长度：两个维度都是一分钟。
     */
    public static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 全部限流维度，先判 RPM 可最快拒绝突发流量。
     */
    public static final List<RateLimitDimension> ALL = List.of(RPM, TPM);

    private static final long WINDOW_MILLIS = WINDOW.toMillis();

    private final String code;
    private final boolean requestBased;

    RateLimitDimension(String code, boolean requestBased) {
        this.code = code;
        this.requestBased = requestBased;
    }

    /**
     * 维度标识，用于拼存储键。
     */
    public String code() {
        return code;
    }

    /**
     * 是否按「请求数」计数；{@code false} 表示按 token 数、且只能事后累加。
     */
    public boolean requestBased() {
        return requestBased;
    }

    /**
     * 该维度在给定策略下的阈值；{@code <= 0} 表示这一维度不限。
     */
    public int limitOf(RateLimitPolicy policy) {
        if (policy == null) {
            return 0;
        }
        return this == RPM ? policy.rpm() : policy.tpm();
    }

    /**
     * 当前时刻落在的固定窗口序号。
     */
    public String windowId(long nowMillis) {
        return Long.toString(nowMillis / WINDOW_MILLIS);
    }

    /**
     * 当前窗口的结束时刻，多留一个窗口长度余量，避免节点间时钟偏差导致翻页抖动。
     */
    public long expireAtMillis(long nowMillis) {
        return (nowMillis / WINDOW_MILLIS + 2) * WINDOW_MILLIS;
    }

    /**
     * 该维度超限时对应的拒绝原因。
     */
    public TrafficRejectReason rejectReason() {
        return this == RPM ? TrafficRejectReason.RATE_LIMIT_RPM : TrafficRejectReason.RATE_LIMIT_TPM;
    }
}
