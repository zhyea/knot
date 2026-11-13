package org.chobit.knot.gateway.outage;

import org.chobit.knot.common.model.EjectionPolicy;

/**
 * 摘除状态机：所有状态转换的纯函数实现，不碰任何存储。
 * 判定逻辑全部在这里，存储层只负责原子地「读-改-写」。
 */
public final class OutageStateMachine {

    private OutageStateMachine() {
    }

    /**
     * 记录一次异常并给出新状态。
     * 窗口已过期则开新窗口、计数从本次起算；探活中判为探活失败，释放租约、冷却按倍数增长（封顶）后重新摘除；其余计数 +1，达阈值即摘除（首次用策略基础冷却）。
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
     * 探活请求成功则整条状态清除（恢复健康）；普通成功只清零失败计数、保留慢计数，不动摘除状态（解除摘除只能靠冷却 + 探活）。
     * 慢计数的清除路径：窗口过期重开、探活成功整条清除、手动 {@code release}。
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
