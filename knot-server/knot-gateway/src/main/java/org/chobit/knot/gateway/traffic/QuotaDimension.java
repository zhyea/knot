package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.model.QuotaPolicy;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 额度维度：与 {@code kb_quota_policies} 的三个限额字段一一对应。
 *
 * <p>口径（由 robin 拍板）：</p>
 * <ul>
 *   <li>{@code daily_limit} —— 自然日窗口内的<b>请求数</b>上限；</li>
 *   <li>{@code monthly_limit} —— 自然月窗口内的<b>请求数</b>上限；</li>
 *   <li>{@code token_limit} —— <b>累计 token</b> 上限，不按窗口清零。</li>
 * </ul>
 *
 * <p>日 / 月窗口的起止按配置的时区换算（默认 {@code Asia/Shanghai}）；
 * 累计 token 没有自然边界，采用「滑动续期」：每次累加都把 TTL 往后推
 * {@link #TOTAL_SLIDING_TTL}，长期无流量的资源会被存储自动淘汰，不会无限堆积。</p>
 */
public enum QuotaDimension {

    DAILY_REQUESTS("DAILY_REQUESTS", true),
    MONTHLY_REQUESTS("MONTHLY_REQUESTS", true),
    TOTAL_TOKENS("TOTAL_TOKENS", false);

    /**
     * 累计 token 维度的滑动续期时长。
     */
    public static final Duration TOTAL_SLIDING_TTL = Duration.ofDays(30);

    /**
     * 全部额度维度。
     */
    public static final List<QuotaDimension> ALL = List.of(DAILY_REQUESTS, MONTHLY_REQUESTS, TOTAL_TOKENS);

    private final String code;
    private final boolean requestBased;

    QuotaDimension(String code, boolean requestBased) {
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
     * 是否按「请求数」计数；{@code false} 表示按 token 数计数。
     */
    public boolean requestBased() {
        return requestBased;
    }

    /**
     * 该维度在给定策略下的限额；{@code <= 0} 表示这一维度不做限制。
     */
    public long limitOf(QuotaPolicy policy) {
        if (policy == null) {
            return 0L;
        }
        return switch (this) {
            case DAILY_REQUESTS -> policy.dailyLimit();
            case MONTHLY_REQUESTS -> policy.monthlyLimit();
            case TOTAL_TOKENS -> policy.tokenLimit();
        };
    }

    /**
     * 该维度超限时对应的拒绝原因。
     */
    public TrafficRejectReason rejectReason() {
        return switch (this) {
            case DAILY_REQUESTS -> TrafficRejectReason.QUOTA_DAILY;
            case MONTHLY_REQUESTS -> TrafficRejectReason.QUOTA_MONTHLY;
            case TOTAL_TOKENS -> TrafficRejectReason.QUOTA_TOKEN;
        };
    }

    /**
     * 当前时刻所在的窗口标识：自然日 / 自然月 / 固定值 {@code ALL}。
     */
    public String windowId(ZonedDateTime now) {
        return switch (this) {
            case DAILY_REQUESTS -> now.toLocalDate().toString();
            case MONTHLY_REQUESTS -> YearMonth.from(now).toString();
            case TOTAL_TOKENS -> "ALL";
        };
    }

    /**
     * 当前窗口的结束时刻（毫秒）。
     */
    public long expireAtMillis(ZonedDateTime now) {
        return switch (this) {
            case DAILY_REQUESTS -> now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
            case MONTHLY_REQUESTS -> YearMonth.from(now).plusMonths(1)
                    .atDay(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
            case TOTAL_TOKENS -> now.plus(TOTAL_SLIDING_TTL).toInstant().toEpochMilli();
        };
    }
}
