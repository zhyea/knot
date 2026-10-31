package org.chobit.knot.common.model;

import org.chobit.knot.common.util.JsonKit;

import java.util.Locale;

/**
 * 路由规则级「异常摘除」策略：把窗口内反复异常的供应商模型从候选链里临时剔除。
 *
 * <p>与 {@link RetryPolicy} 的关系是<b>正交的两层降级</b>：重试在<b>同一目标内部</b>原地重投，
 * 摘除作用于<b>目标之间</b>——被摘除的目标在后续请求里根本不进入候选链，省掉「明知是坏的目标
 * 还要先试一遍」的延迟与上游压力。降级顺序为「retry → failover → eject」。</p>
 *
 * <p>异常信号有两类，共用一个窗口计数器，任一达阈值即摘除：</p>
 * <ul>
 *   <li>{@code FAILURE}：上游故障类失败（5xx、上游 429、网络超时/连接错误）；</li>
 *   <li>{@code SLOW}：请求成功但首 token 延迟超过 {@code slowFirstTokenMs}。</li>
 * </ul>
 *
 * <p>各字段在<b>紧凑构造器中即归一化</b>——缺省值填充、越界收敛、模式归一。
 * 因此任何 {@code EjectionPolicy} 实例（含 Jackson 反序列化出来的）拿到的都是可直接使用的有效值。</p>
 *
 * <p>配置分两种模式：{@code simple}（仅启用/窗口/阈值/慢阈值/冷却）与
 * {@code professional}（计数开关、冷却增长、探活租约、全摘 fail-open 全可调）。默认 {@code simple}。</p>
 *
 * <p>存储为 {@code kb_routing_rules.ejection_policy}（TEXT/JSON）；该列为空表示未显式配置，
 * 运行时解析为 {@link #DEFAULT}（<b>默认关闭</b>——摘除会主动减少可用容量，误判代价高于漏判）。</p>
 */
public record EjectionPolicy(
        /** 是否启用摘除；关闭表示候选链不做任何摘除过滤 */
        Boolean enabled,
        /** 异常计数窗口（秒） */
        Integer windowSec,
        /** 窗口内异常次数阈值（failures + slows） */
        Integer threshold,
        /** 是否统计「首 token 过慢」 */
        Boolean countSlow,
        /** 首 token 超时阈值（毫秒） */
        Integer slowFirstTokenMs,
        /** 首次摘除的冷却时长（毫秒） */
        Integer cooldownMs,
        /** 是否统计失败类异常（{@code false} 时只看慢信号） */
        Boolean countFailures,
        /** 探活失败后的冷却增长倍数（1 位小数浮点） */
        Double cooldownMultiplier,
        /** 冷却时长上限（毫秒） */
        Integer maxCooldownMs,
        /** 半开探活请求的租约时长（毫秒）：同一时刻只放行一个探活请求 */
        Integer probeTimeoutMs,
        /** 候选被全部摘除时是否忽略摘除、放行原候选链（避免整体 503） */
        Boolean failOpenWhenAllEjected,
        /** 配置模式：{@code simple} 只暴露主参数 / {@code professional} 全字段 */
        String mode
) {

    public static final int WINDOW_SEC_DEFAULT = 60;
    public static final int WINDOW_SEC_LIMIT = 3600;
    public static final int THRESHOLD_DEFAULT = 5;
    public static final int THRESHOLD_LIMIT = 1000;
    public static final int SLOW_FIRST_TOKEN_MS_DEFAULT = 15_000;
    public static final int SLOW_FIRST_TOKEN_MS_LIMIT = 600_000;
    public static final int COOLDOWN_MS_DEFAULT = 60_000;
    public static final int COOLDOWN_MS_LIMIT = 3_600_000;
    public static final double COOLDOWN_MULTIPLIER_DEFAULT = 2.0;
    public static final double COOLDOWN_MULTIPLIER_LIMIT = 10.0;
    public static final int MAX_COOLDOWN_MS_DEFAULT = 600_000;
    public static final int PROBE_TIMEOUT_MS_DEFAULT = 30_000;
    public static final int PROBE_TIMEOUT_MS_LIMIT = 300_000;
    public static final String MODE_SIMPLE = "simple";
    public static final String MODE_PROFESSIONAL = "professional";

    /**
     * 默认策略：默认关闭、简单模式，窗口 60s、阈值 5 次、慢阈值 15s、冷却 60s。
     *
     * <p>单独抽常量而非引用 {@link #DEFAULT}：紧凑构造器在 {@code DEFAULT} 静态初始化期间
     * 就会执行，此时 {@code DEFAULT} 仍为 null，回退分支读它会 NPE。</p>
     */
    public static final EjectionPolicy DEFAULT = new EjectionPolicy(
            Boolean.FALSE,
            WINDOW_SEC_DEFAULT,
            THRESHOLD_DEFAULT,
            Boolean.TRUE,
            SLOW_FIRST_TOKEN_MS_DEFAULT,
            COOLDOWN_MS_DEFAULT,
            Boolean.TRUE,
            COOLDOWN_MULTIPLIER_DEFAULT,
            MAX_COOLDOWN_MS_DEFAULT,
            PROBE_TIMEOUT_MS_DEFAULT,
            Boolean.TRUE,
            MODE_SIMPLE
    );

    /**
     * 归一化：缺省值填充、越界收敛、模式归一、冷却上限不低于首次冷却。
     */
    public EjectionPolicy {
        enabled = enabled == null ? Boolean.FALSE : enabled;
        windowSec = PolicyNumberSupport.clampInt(windowSec, WINDOW_SEC_DEFAULT, 1, WINDOW_SEC_LIMIT);
        threshold = PolicyNumberSupport.clampInt(threshold, THRESHOLD_DEFAULT, 1, THRESHOLD_LIMIT);
        countSlow = countSlow == null ? Boolean.TRUE : countSlow;
        slowFirstTokenMs = PolicyNumberSupport.clampInt(slowFirstTokenMs, SLOW_FIRST_TOKEN_MS_DEFAULT, 1, SLOW_FIRST_TOKEN_MS_LIMIT);
        cooldownMs = PolicyNumberSupport.clampInt(cooldownMs, COOLDOWN_MS_DEFAULT, 1_000, COOLDOWN_MS_LIMIT);
        countFailures = countFailures == null ? Boolean.TRUE : countFailures;
        cooldownMultiplier = PolicyNumberSupport.roundToOneDecimal(
                PolicyNumberSupport.clampDouble(cooldownMultiplier, COOLDOWN_MULTIPLIER_DEFAULT, 1.0, COOLDOWN_MULTIPLIER_LIMIT));
        maxCooldownMs = PolicyNumberSupport.clampInt(maxCooldownMs, MAX_COOLDOWN_MS_DEFAULT, cooldownMs, COOLDOWN_MS_LIMIT);
        probeTimeoutMs = PolicyNumberSupport.clampInt(probeTimeoutMs, PROBE_TIMEOUT_MS_DEFAULT, 1_000, PROBE_TIMEOUT_MS_LIMIT);
        failOpenWhenAllEjected = failOpenWhenAllEjected == null ? Boolean.TRUE : failOpenWhenAllEjected;
        mode = normalizeConfigMode(mode);
    }

    /**
     * 解析持久化的 JSON；空值或非法 JSON 一律退回 {@link #DEFAULT}，不因配置损坏中断请求。
     */
    public static EjectionPolicy parse(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT;
        }
        EjectionPolicy parsed = JsonKit.fromJson(json, EjectionPolicy.class);
        return parsed == null ? DEFAULT : parsed;
    }

    /**
     * 序列化为持久化 JSON；null 策略写出 null（表示该行未配置，运行时走默认）。
     */
    public static String serialize(EjectionPolicy policy) {
        return policy == null ? null : JsonKit.toJson(policy);
    }

    /**
     * 是否启用摘除。
     *
     * <p>不叫 {@code enabled()}：record 的显式存取方法必须与组件同名同类型，
     * 而组件是 {@link Boolean}（可空、需要区分「未配置」与「显式关闭」）。</p>
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 是否统计失败类异常。
     */
    public boolean isCountFailures() {
        return Boolean.TRUE.equals(countFailures);
    }

    /**
     * 是否统计首 token 过慢。
     */
    public boolean isCountSlow() {
        return Boolean.TRUE.equals(countSlow);
    }

    /**
     * 候选被全部摘除时是否放行原候选链。
     */
    public boolean isFailOpenWhenAllEjected() {
        return Boolean.TRUE.equals(failOpenWhenAllEjected);
    }

    /**
     * 异常计数的窗口长度（毫秒）。
     */
    public long windowMillis() {
        return windowSec * 1000L;
    }

    /**
     * 探活失败后的下一次冷却时长：按倍数增长并封顶在 {@code maxCooldownMs}。
     *
     * @param currentCooldownMs 本次使用的冷却时长
     */
    public long nextCooldownMillis(long currentCooldownMs) {
        long base = currentCooldownMs <= 0L ? cooldownMs : currentCooldownMs;
        long grown = (long) Math.ceil(base * cooldownMultiplier);
        if (grown <= base) {
            grown = base;
        }
        return Math.min(grown, maxCooldownMs);
    }

    private static String normalizeConfigMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_SIMPLE;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return MODE_PROFESSIONAL.equals(normalized) ? MODE_PROFESSIONAL : MODE_SIMPLE;
    }
}
