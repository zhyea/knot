package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.util.JsonKit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 路由规则级「失败重试」策略。
 *
 * <p>重试位于<b>跨候选 failover 之前</b>：对同一路由目标（同一 {@code upstream_model}）原地重投，
 * 次数耗尽仍失败才切到规则内的下一个候选。三层降级顺序为「retry → failover → 规则 fallback」。</p>
 *
 * <p>各字段在<b>紧凑构造器中即归一化</b>——缺省值填充、越界收敛、状态集过滤非法项。
 * 因此任何 {@code RetryPolicy} 实例（含 Jackson 反序列化出来的）拿到的都是可直接使用的有效值，
 * 调用方不需要再做空值兜底。</p>
 *
 * <p>存储为 {@code kb_routing_rules.retry_policy}（TEXT/JSON）；该列为空表示未显式配置，
 * 运行时解析为 {@link #DEFAULT}（默认开启）。</p>
 */
public record RetryPolicy(
        /** 是否启用重试；关闭等价于「每个目标只试一次」，直接走 failover */
        Boolean enabled,
        /** 总尝试次数（含首次）；{@code <= 1} 表示不重试 */
        Integer maxAttempts,
        /** 首次退避基数（毫秒） */
        Integer backoffBaseMs,
        /** 单次退避上限（毫秒），同时作为总预算上限 */
        Integer backoffMaxMs,
        /** 指数退避基数 */
        Double multiplier,
        /** 是否在退避区间内随机取值，打散重试尖峰 */
        Boolean jitter,
        /** 是否服从上游 {@code Retry-After}（429/503） */
        Boolean respectRetryAfter,
        /** {@code allowlist}（命中才重试）/ {@code denylist}（命中才不重试） */
        String retryOnMode,
        /** 参与判定的上游 HTTP 状态码集合（字符串形式，便于 JSON 与前端传输） */
        List<String> retryOn
) {

    public static final int MAX_ATTEMPTS_DEFAULT = 3;
    public static final int MAX_ATTEMPTS_LIMIT = 10;
    public static final int BACKOFF_BASE_MS_DEFAULT = 200;
    public static final int BACKOFF_MAX_MS_DEFAULT = 5_000;
    public static final int BACKOFF_LIMIT_MS = 60_000;
    public static final double MULTIPLIER_DEFAULT = 2.0;
    public static final double MULTIPLIER_LIMIT = 10.0;
    public static final String MODE_ALLOWLIST = "allowlist";
    public static final String MODE_DENYLIST = "denylist";

    /**
     * 默认重试状态集。单独抽成常量而非引用 {@link #DEFAULT}：紧凑构造器在 {@code DEFAULT}
     * 静态初始化期间就会执行，此时 {@code DEFAULT} 仍为 null，回退分支读它会 NPE。
     */
    private static final List<String> DEFAULT_RETRY_ON = List.of("500", "502", "503", "504", "429");

    /**
     * 默认策略：默认开启，总尝试 3 次，指数退避（200ms 起、×2、上限 5s）+ 抖动，
     * 重试 5xx 与 429，服从上游 Retry-After。
     */
    public static final RetryPolicy DEFAULT = new RetryPolicy(
            Boolean.TRUE,
            MAX_ATTEMPTS_DEFAULT,
            BACKOFF_BASE_MS_DEFAULT,
            BACKOFF_MAX_MS_DEFAULT,
            MULTIPLIER_DEFAULT,
            Boolean.TRUE,
            Boolean.TRUE,
            MODE_ALLOWLIST,
            DEFAULT_RETRY_ON
    );

    /**
     * 归一化：缺省值填充、越界收敛、状态集过滤非法项。
     *
     * <p>写成紧凑构造器而非静态工厂，是为了让 Jackson 反序列化出的实例同样是归一化的，
     * 避免出现「字段为 null 的策略实例」流到运行时。</p>
     */
    public RetryPolicy {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        maxAttempts = clampInt(maxAttempts, MAX_ATTEMPTS_DEFAULT, 1, MAX_ATTEMPTS_LIMIT);
        backoffBaseMs = clampInt(backoffBaseMs, BACKOFF_BASE_MS_DEFAULT, 0, BACKOFF_LIMIT_MS);
        backoffMaxMs = clampInt(backoffMaxMs, BACKOFF_MAX_MS_DEFAULT, 0, BACKOFF_LIMIT_MS);
        multiplier = clampDouble(multiplier, MULTIPLIER_DEFAULT, 1.0, MULTIPLIER_LIMIT);
        jitter = jitter == null ? Boolean.TRUE : jitter;
        respectRetryAfter = respectRetryAfter == null ? Boolean.TRUE : respectRetryAfter;
        retryOnMode = normalizeMode(retryOnMode);
        retryOn = normalizeRetryOn(retryOn);
    }

    /**
     * 解析持久化的 JSON；空值或非法 JSON 一律退回 {@link #DEFAULT}，不因配置损坏中断请求。
     */
    public static RetryPolicy parse(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT;
        }
        RetryPolicy parsed = JsonKit.fromJson(json, RetryPolicy.class);
        return parsed == null ? DEFAULT : parsed;
    }

    /**
     * 序列化为持久化 JSON；null 策略写出 null（表示该行未配置，运行时走默认）。
     */
    public static String serialize(RetryPolicy policy) {
        return policy == null ? null : JsonKit.toJson(policy);
    }

    /**
     * 上游状态码是否命中重试条件。
     */
    public boolean matchesStatus(int status) {
        boolean hit = statusSet().contains(status);
        return MODE_DENYLIST.equals(retryOnMode) != hit;
    }

    /**
     * 参与判定的状态码集合；非法项在构造时已过滤。
     */
    public Set<Integer> statusSet() {
        Set<Integer> set = new LinkedHashSet<>();
        if (retryOn == null) {
            return set;
        }
        for (String item : retryOn) {
            Integer parsed = parseStatus(item);
            if (parsed != null) {
                set.add(parsed);
            }
        }
        return set;
    }

    /**
     * 是否实际会产生重试：开启且总尝试次数大于 1。
     */
    public boolean retryable() {
        return Boolean.TRUE.equals(enabled) && maxAttempts != null && maxAttempts > 1;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_ALLOWLIST;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return MODE_DENYLIST.equals(normalized) ? MODE_DENYLIST : MODE_ALLOWLIST;
    }

    private static List<String> normalizeRetryOn(List<String> raw) {
        Set<Integer> parsed = new LinkedHashSet<>();
        if (raw != null) {
            for (String item : raw) {
                Integer status = parseStatus(item);
                if (status != null) {
                    parsed.add(status);
                }
            }
        }
        if (parsed.isEmpty()) {
            return DEFAULT_RETRY_ON;
        }
        return parsed.stream().map(String::valueOf).toList();
    }

    private static Integer parseStatus(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clampInt(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    private static double clampDouble(Double value, double fallback, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }
}
