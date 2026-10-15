package org.chobit.knot.gateway.outage.admin;

import org.chobit.knot.gateway.outage.OutageEntry;
import org.chobit.knot.gateway.outage.OutageState;

/**
 * 管理面视图的一条摘除条目：键 + 展平后的状态快照。
 *
 * <p>相对 {@link OutageEntry} 的嵌套 {@code state} 展平一层——管理端面板直接按字段渲染
 * （模型 / 状态 / 原因 / 窗口计数 / 剩余冷却），不必再解一层嵌套。</p>
 *
 * @param ruleCode     路由规则业务码
 * @param modelCode    供应商模型业务码
 * @param failures     窗口内失败类异常计数
 * @param slows        窗口内慢响应计数
 * @param windowEndAt  当前计数窗口结束时刻（epoch 毫秒）
 * @param ejectedUntil 摘除到期时刻（epoch 毫秒；大于当前时刻即摘除中）
 * @param probeUntil   探活租约到期时刻（epoch 毫秒；大于当前时刻即有探活在途）
 * @param cooldownMs   当前生效的冷却时长（探活失败按倍数增长后的值）
 * @param reason       最近一次摘除原因（FAILURE / SLOW / MANUAL）
 */
public record OutageAdminItem(
        String ruleCode,
        String modelCode,
        int failures,
        int slows,
        long windowEndAt,
        long ejectedUntil,
        long probeUntil,
        long cooldownMs,
        String reason
) {

    /**
     * 从存储条目展平为管理面视图。
     */
    public static OutageAdminItem of(OutageEntry entry) {
        OutageState state = entry.state();
        return new OutageAdminItem(
                entry.ruleCode(),
                entry.modelCode(),
                state.failures(),
                state.slows(),
                state.windowEndAt(),
                state.ejectedUntil(),
                state.probeUntil(),
                state.cooldownMs(),
                state.reason()
        );
    }

    /**
     * 给定时刻是否处于摘除中（含探活中——探活期间同样不计入常规候选）。
     */
    public boolean ejected(long nowMillis) {
        return ejectedUntil > nowMillis || probeUntil > nowMillis;
    }

    /**
     * 给定时刻的剩余冷却毫秒数；未摘除时为 0。
     */
    public long remainingCooldownMs(long nowMillis) {
        return ejectedUntil > nowMillis ? ejectedUntil - nowMillis : 0L;
    }
}
