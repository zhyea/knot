package org.chobit.knot.gateway.outage;

/**
 * 单个「规则 × 供应商模型」的异常摘除状态快照。
 *
 * <p>不可变值对象，所有状态转换都产出新实例（见 {@link OutageStateMachine}），
 * 便于 Caffeine 的 {@code compute} 与 Redis Lua 各自保证原子性。</p>
 *
 * <p>状态语义：</p>
 * <ul>
 *   <li><b>健康</b>：{@code ejectedUntil <= now} 且窗口内异常数未达阈值；</li>
 *   <li><b>摘除中</b>：{@code ejectedUntil > now}；</li>
 *   <li><b>探活中</b>：{@code ejectedUntil <= now && probeUntil > now}——冷却已过，
 *       已放行一个探活请求，其结果决定恢复还是再次冷却。</li>
 * </ul>
 */
public record OutageState(
        /** 窗口内失败类异常计数 */
        int failures,
        /** 窗口内慢响应计数 */
        int slows,
        /** 当前计数窗口的结束时刻；{@code now >= windowEndAt} 视为窗口已过期 */
        long windowEndAt,
        /** 摘除到期时刻；大于 now 表示该目标当前被摘除 */
        long ejectedUntil,
        /** 探活租约到期时刻；大于 now 表示已有探活请求在途 */
        long probeUntil,
        /** 当前生效的冷却时长，探活失败时按策略倍数增长 */
        long cooldownMs,
        /** 最近一次摘除的原因（{@link AnomalyKind} 的 name 或 {@code MANUAL}） */
        String reason
) {

    /** 无条目 / 已完全恢复的状态：等同于「健康」。 */
    public static final OutageState HEALTHY = new OutageState(0, 0, 0L, 0L, 0L, 0L, "");

    /** 手动摘除的原因标识（与自动摘除的 FAILURE / SLOW 区分）。 */
    public static final String REASON_MANUAL = "MANUAL";

    /**
     * 当前是否处于摘除中。
     */
    public boolean ejected(long nowMillis) {
        return ejectedUntil > nowMillis;
    }

    /**
     * 当前是否有探活请求在途（租约未过期）。
     */
    public boolean probing(long nowMillis) {
        return probeUntil > nowMillis;
    }

    /**
     * 计数窗口是否仍然有效。
     */
    public boolean windowActive(long nowMillis) {
        return windowEndAt > nowMillis;
    }

    /**
     * 窗口内异常总数（失败 + 慢），与策略阈值比较用。
     */
    public int anomalies() {
        return failures + slows;
    }

    /**
     * 条目的自然回收时刻：窗口、摘除、探活三者中最晚者 + 宽限。
     *
     * <p>用作缓存 TTL——窗口过期后无人续期即自动消失，无需清理任务。</p>
     */
    public long expireAtMillis(long graceMillis) {
        long latest = Math.max(windowEndAt, Math.max(ejectedUntil, probeUntil));
        return latest <= 0L ? 0L : latest + graceMillis;
    }
}
