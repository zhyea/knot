package org.chobit.knot.gateway.outage;

import org.chobit.knot.common.model.EjectionPolicy;

/**
 * 摘除状态机：所有状态转换的纯函数实现，不碰任何存储。
 *
 * <p>抽成纯函数是为了让 Caffeine 与 Redis 两种实现共享同一套语义，且可以脱离存储做单测——
 * 存储层只负责「原子地读-改-写」，判定逻辑全部在这里。</p>
 */
public final class OutageStateMachine {

    private OutageStateMachine() {
    }

    /**
     * 记录一次异常并给出新状态。
     *
     * <p>口径：</p>
     * <ul>
     *   <li>窗口已过期 → 开新窗口，计数从本次重新起算；</li>
     *   <li>处于探活中 → 判为探活失败：租约释放，冷却按倍数增长（封顶）后重新摘除；</li>
     *   <li>其余 → 计数 +1，达阈值即摘除（首次摘除用策略的基础冷却）。</li>
     * </ul>
     */
    public static OutageState recordAnomaly(OutageState current, EjectionPolicy policy, AnomalyKind kind, long nowMillis) {
        OutageState state = current == null ? OutageState.HEALTHY : current;
        boolean windowActive = state.windowActive(nowMillis);
        long windowEndAt = windowActive ? state.windowEndAt() : nowMillis + policy.windowMillis();
        int failures = windowActive ? state.failures() : 0;
        int slows = windowActive ? state.slows() : 0;
        if (AnomalyKind.SLOW == kind) {
            slows += 1;
        } else {
            failures += 1;
        }
        int anomalies = failures + slows;
        String reason = kind == null ? AnomalyKind.FAILURE.name() : kind.name();

        if (state.probing(nowMillis)) {
            long nextCooldown = policy.nextCooldownMillis(state.cooldownMs());
            return new OutageState(failures, slows, windowEndAt, nowMillis + nextCooldown, 0L, nextCooldown, reason);
        }
        if (state.ejected(nowMillis)) {
            // 冷却期内又发生异常：不叠加摘除次数，只把现有摘除延长到「至少再一个基础冷却」
            long extended = Math.max(state.ejectedUntil(), nowMillis + policy.cooldownMs());
            return new OutageState(failures, slows, windowEndAt, extended, state.probeUntil(), state.cooldownMs(), state.reason());
        }
        if (anomalies < policy.threshold()) {
            return new OutageState(failures, slows, windowEndAt, 0L, 0L, 0L, "");
        }
        long cooldown = policy.cooldownMs();
        return new OutageState(failures, slows, windowEndAt, nowMillis + cooldown, 0L, cooldown, reason);
    }

    /**
     * 记录一次成功。
     *
     * <p>探活请求成功 → 整条状态清除（恢复健康）；普通成功 → 只清零<b>失败</b>计数，
     * <b>保留慢计数</b>，不动摘除状态（解除摘除只能靠冷却 + 探活）。</p>
     *
     * <p>为什么不清慢计数：成功只能证明「这次没失败」，不能证明「不慢」——流式请求的
     * 慢信号（{@code recordSlow}）在首 token 回调里产生，天然晚于请求线程的
     * {@code recordSuccess}；若成功同时清零慢计数，慢信号会在「清零 → +1」循环里
     * 永远无法累积到阈值。慢计数的清除路径：窗口过期自然重开、探活成功整条清除、
     * 手动 release。</p>
     */
    public static OutageState recordSuccess(OutageState current, boolean probing, long nowMillis) {
        OutageState state = current == null ? OutageState.HEALTHY : current;
        if (probing) {
            return OutageState.HEALTHY;
        }
        if (!state.windowActive(nowMillis)) {
            return OutageState.HEALTHY;
        }
        return new OutageState(0, state.slows(), state.windowEndAt(), state.ejectedUntil(),
                state.probeUntil(), state.cooldownMs(), state.reason());
    }

    /**
     * 抢占探活租约：冷却已过且无他人在探活时才允许放行，租约期内同一时刻只有一个探活请求。
     */
    public static OutageState acquireProbe(OutageState current, long probeTimeoutMs, long nowMillis) {
        OutageState state = current == null ? OutageState.HEALTHY : current;
        return new OutageState(state.failures(), state.slows(), state.windowEndAt(),
                state.ejectedUntil(), nowMillis + probeTimeoutMs, state.cooldownMs(), state.reason());
    }

    /**
     * 手动恢复：清除整条状态。
     */
    public static OutageState release() {
        return OutageState.HEALTHY;
    }

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     */
    public static OutageState forceEject(long cooldownMs, long nowMillis) {
        return new OutageState(0, 0, nowMillis + cooldownMs, nowMillis + cooldownMs, 0L, cooldownMs,
                OutageState.REASON_MANUAL);
    }
}
