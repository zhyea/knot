package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.model.EjectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：Caffeine 存储的原子读写、TTL 回收、手动操作与管理面列举。
 */
class CaffeineTargetOutageStoreTest {

    private static final EjectionPolicy POLICY = new EjectionPolicy(
            true, 60, 3, true, 15_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_PROFESSIONAL);

    private final CaffeineTargetOutageStore store =
            new CaffeineTargetOutageStore(1_000L, 60_000L);

    /** 基准时刻取真实当前时间：Caffeine 逐键过期按真实墙钟计算，假时间戳会让条目立即过期 */
    private static final long NOW = System.currentTimeMillis();
    private static final String KEY = "knot:outage:RULE:rule-a:MODEL:gpt-4o";

    private OutageState fail(int times, long now) {
        OutageState state = store.state(KEY, now);
        for (int i = 0; i < times; i++) {
            state = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, now);
        }
        return state;
    }

    @Test
    @DisplayName("阈值内不摘除，达阈值即摘除")
    void thresholdBehavior() {
        assertFalse(fail(2, NOW).ejected(NOW));
        assertTrue(fail(1, NOW).ejected(NOW));
    }

    @Test
    @DisplayName("探活租约抢占：冷却内不抢占，冷却后仅一次")
    void probeLeaseIsSingleShot() {
        fail(3, NOW);
        // 第三次失败发生在 NOW，摘除到 NOW+60s；取更晚时刻确保冷却已结束
        long afterCooldown = NOW + 60_010;
        assertTrue(store.tryAcquireProbe(KEY, POLICY.probeTimeoutMs(), afterCooldown));
        // 租约期内第二次抢占失败
        assertFalse(store.tryAcquireProbe(KEY, POLICY.probeTimeoutMs(), afterCooldown + 10));
        // 冷却期内不抢占
        assertFalse(store.tryAcquireProbe(KEY, POLICY.probeTimeoutMs(), NOW + 5));
    }

    @Test
    @DisplayName("手动恢复与手动摘除")
    void releaseAndForceEject() {
        fail(3, NOW);
        assertTrue(store.state(KEY, NOW).ejected(NOW));
        store.release(KEY);
        assertFalse(store.state(KEY, NOW).ejected(NOW));

        store.forceEject(KEY, 90_000L, NOW);
        OutageState state = store.state(KEY, NOW);
        assertTrue(state.ejected(NOW));
        assertEquals(OutageState.REASON_MANUAL, state.reason());
    }

    @Test
    @DisplayName("普通成功清零窗口计数")
    void plainSuccessClearsCounters() {
        fail(2, NOW);
        store.recordSuccess(KEY, false, NOW + 1);
        assertEquals(0, store.state(KEY, NOW + 1).anomalies());
    }

    @Test
    @DisplayName("列举只返回当前非健康条目，且反解出规则与模型码")
    void listReturnsUnhealthyOnly() {
        String other = "knot:outage:RULE:rule-b:MODEL:claude";
        fail(3, NOW);
        // 未达阈值的条目（慢信号 1 次 < 3）不应出现在列举里
        store.recordAnomaly(other, POLICY, AnomalyKind.SLOW, NOW);
        store.recordAnomaly(other, POLICY, AnomalyKind.SLOW, NOW + 1);
        List<OutageEntry> onlyA = store.list(NOW + 2);
        assertEquals(1, onlyA.size());

        store.recordAnomaly(other, POLICY, AnomalyKind.SLOW, NOW + 2);
        List<OutageEntry> entries = store.list(NOW + 3);
        assertEquals(2, entries.size());
        for (OutageEntry entry : entries) {
            assertTrue("rule-a".equals(entry.ruleCode()) || "rule-b".equals(entry.ruleCode()));
            assertTrue(entry.state().ejected(NOW + 3));
        }
        OutageEntry a = entries.stream()
                .filter(entry -> "rule-a".equals(entry.ruleCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("gpt-4o", a.modelCode());
    }

    @Test
    @DisplayName("探活成功后状态彻底消失")
    void probeSuccessRemovesEntry() {
        fail(3, NOW);
        store.tryAcquireProbe(KEY, POLICY.probeTimeoutMs(), NOW + 60_010);
        store.recordSuccess(KEY, true, NOW + 60_100);
        assertEquals(OutageState.HEALTHY, store.state(KEY, NOW + 60_100));
        assertTrue(store.list(NOW + 60_100).isEmpty());
    }

    @Test
    @DisplayName("id 标识")
    void idIsCaffeine() {
        assertEquals("caffeine", store.id());
    }
}
