package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.model.EjectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：摘除状态机的窗口累计、阈值摘除、冷却、半开探测与手动操作。
 */
class OutageStateMachineTest {

    private static final EjectionPolicy POLICY = new EjectionPolicy(
            true, 60, 3, true, 15_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_PROFESSIONAL);

    /** 基准时刻取真实当前时间：Caffeine 逐键过期按真实墙钟计算，假时间戳会让条目立即过期 */
    private static final long NOW = System.currentTimeMillis();

    @Test
    @DisplayName("窗口内累计达阈值才摘除，未达不摘")
    void accumulatesToThreshold() {
        OutageState state = OutageState.HEALTHY;
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW);
        assertFalse(state.ejected(NOW));
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 10);
        assertFalse(state.ejected(NOW + 10));
        assertEquals(2, state.failures());
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 20);
        assertTrue(state.ejected(NOW + 20));
        assertEquals(NOW + 20 + 60_000L, state.ejectedUntil());
    }

    @Test
    @DisplayName("窗口过期后计数重新起算")
    void windowExpiryResetsCounters() {
        OutageState state = OutageStateMachine.recordAnomaly(OutageState.HEALTHY, POLICY, AnomalyKind.FAILURE, NOW);
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 1);
        // 跳过整个窗口（60s）后，窗口失效，计数归零
        OutageState after = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 61_000);
        assertEquals(1, after.anomalies());
        assertFalse(after.ejected(NOW + 61_000));
    }

    @Test
    @DisplayName("冷却中再失败只延长摘除，不叠加新摘除")
    void failureDuringCooldownExtends() {
        OutageState state = OutageState.HEALTHY;
        for (int i = 0; i < 3; i++) {
            state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + i);
        }
        assertTrue(state.ejected(NOW + 3));
        OutageState again = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 10);
        assertEquals(Math.max(state.ejectedUntil(), NOW + 10 + 60_000L), again.ejectedUntil());
        // 延长冷却不改变原因（仍是导致首次摘除的那个信号）
        assertEquals(state.reason(), again.reason());
    }

    @Test
    @DisplayName("探活失败后冷却按倍数增长并封顶")
    void probeFailureGrowsCooldown() {
        long now = NOW;
        OutageState state = OutageState.HEALTHY;
        for (int i = 0; i < 3; i++) {
            state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, now + i);
        }
        // 最后一次失败发生在 now+2，摘除到 now+2+60s；取更晚的时刻确保冷却已结束
        long afterCooldown = now + 60_010;
        state = OutageStateMachine.acquireProbe(state, POLICY.probeTimeoutMs(), afterCooldown);
        assertTrue(state.probing(afterCooldown));
        assertFalse(state.ejected(afterCooldown));
        // 探活请求仍然失败
        OutageState failed = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, afterCooldown + 10);
        assertTrue(failed.ejected(afterCooldown + 10));
        assertEquals(120_000L, failed.cooldownMs());
        assertEquals(afterCooldown + 10 + 120_000L, failed.ejectedUntil());
        assertFalse(failed.probing(afterCooldown + 10));
    }

    @Test
    @DisplayName("探活成功整条恢复健康")
    void probeSuccessClearsState() {
        long now = NOW;
        OutageState state = OutageState.HEALTHY;
        for (int i = 0; i < 3; i++) {
            state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, now + i);
        }
        state = OutageStateMachine.acquireProbe(state, POLICY.probeTimeoutMs(), now + 60_010);
        OutageState recovered = OutageStateMachine.recordSuccess(state, true, now + 60_100);
        assertEquals(OutageState.HEALTHY, recovered);
    }

    @Test
    @DisplayName("普通成功只清零窗口计数，不动摘除状态")
    void plainSuccessOnlyClearsCounters() {
        OutageState state = OutageState.HEALTHY;
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW);
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW + 1);
        OutageState cleared = OutageStateMachine.recordSuccess(state, false, NOW + 2);
        assertEquals(0, cleared.anomalies());
        assertFalse(cleared.ejected(NOW + 2));
        // 计数已清零，需重新累计满 3 次才再摘除（2 次不够）
        cleared = OutageStateMachine.recordAnomaly(cleared, POLICY, AnomalyKind.FAILURE, NOW + 3);
        cleared = OutageStateMachine.recordAnomaly(cleared, POLICY, AnomalyKind.FAILURE, NOW + 4);
        assertFalse(cleared.ejected(NOW + 4));
        cleared = OutageStateMachine.recordAnomaly(cleared, POLICY, AnomalyKind.FAILURE, NOW + 5);
        assertTrue(cleared.ejected(NOW + 5));
    }

    @Test
    @DisplayName("普通成功清零失败计数但保留慢计数（成功只能证明没失败，不能证明不慢）")
    void successKeepsSlowCounters() {
        OutageState state = OutageState.HEALTHY;
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW);
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.SLOW, NOW + 1);
        OutageState after = OutageStateMachine.recordSuccess(state, false, NOW + 2);
        assertEquals(0, after.failures());
        assertEquals(1, after.slows());
    }

    @Test
    @DisplayName("慢信号跨多次成功请求累积达阈值（流式时序：请求线程成功先于异步慢信号）")
    void slowAccumulatesAcrossSuccesses() {
        OutageState state = OutageState.HEALTHY;
        // 复刻流式请求的真实时序：请求线程先 recordSuccess，异步首 token 回调再 recordSlow
        for (int i = 0; i < 3; i++) {
            long t = NOW + i * 1000L;
            state = OutageStateMachine.recordSuccess(state, false, t);
            state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.SLOW, t + 2000);
        }
        assertTrue(state.ejected(NOW + 4000));
        assertEquals(3, state.slows());
    }

    @Test
    @DisplayName("SLOW 与 FAILURE 共用同一个计数器")
    void slowCountsTowardSameThreshold() {
        OutageState state = OutageState.HEALTHY;
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.FAILURE, NOW);
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.SLOW, NOW + 1);
        assertEquals(1, state.slows());
        assertEquals(1, state.failures());
        state = OutageStateMachine.recordAnomaly(state, POLICY, AnomalyKind.SLOW, NOW + 2);
        assertTrue(state.ejected(NOW + 2));
        assertEquals("SLOW", state.reason());
    }

    @Test
    @DisplayName("手动摘除与恢复不依赖窗口计数")
    void forceEjectAndRelease() {
        OutageState ejected = OutageStateMachine.forceEject(120_000L, NOW);
        assertTrue(ejected.ejected(NOW));
        assertEquals(OutageState.REASON_MANUAL, ejected.reason());
        assertFalse(OutageStateMachine.release().ejected(NOW));
    }

    @Test
    @DisplayName("过期宽限决定条目回收时刻")
    void expireAtUsesLatestDeadline() {
        OutageState state = OutageStateMachine.recordAnomaly(OutageState.HEALTHY, POLICY, AnomalyKind.FAILURE, NOW);
        long expected = Math.max(state.windowEndAt(), Math.max(state.ejectedUntil(), state.probeUntil())) + 60_000L;
        assertEquals(expected, state.expireAtMillis(60_000L));
    }

    @Test
    @DisplayName("HEALTHY 的回收时刻为 0（不落库）")
    void healthyHasNoExpiry() {
        assertEquals(0L, OutageState.HEALTHY.expireAtMillis(60_000L));
    }
}
