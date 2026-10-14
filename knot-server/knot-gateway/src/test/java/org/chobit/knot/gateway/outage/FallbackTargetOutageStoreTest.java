package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.model.EjectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：降级适配器的「Redis 优先 → 故障降级 → 恢复回灌」行为。
 *
 * <p>用可控的假存储替代真实 Redis：{@code broken} 开关驱动 primary 抛异常，
 * 探针可用性独立控制，节流与回灌行为均可断言。</p>
 */
class FallbackTargetOutageStoreTest {

    private static final EjectionPolicy POLICY = new EjectionPolicy(
            true, 60, 3, true, 15_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

    private static final String KEY = "knot:outage:RULE:rule-a:MODEL:gpt-4o";
    private static final long NOW = System.currentTimeMillis();

    private FakeStore primary;
    private FakeStore fallback;
    private OutageKeys keys;
    private AtomicInteger probes;
    private boolean primaryReachable;
    private FallbackTargetOutageStore store;

    /** 可控假存储：broken 时全部操作抛异常，模拟 Redis 抖动。 */
    private static final class FakeStore implements TargetOutageStore {

        private final String name;
        private volatile boolean broken;

        private FakeStore(String name) {
            this.name = name;
        }

        @Override
        public String id() {
            return name;
        }

        private void guard() {
            if (broken) {
                throw new IllegalStateException("simulated redis failure");
            }
        }

        @Override
        public OutageState state(String key, long nowMillis) {
            guard();
            return states.getOrDefault(key, OutageState.HEALTHY);
        }

        @Override
        public OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long nowMillis) {
            guard();
            states.put(key, OutageStateMachine.recordAnomaly(states.get(key), policy, kind, nowMillis));
            return states.get(key);
        }

        @Override
        public void recordSuccess(String key, boolean probing, long nowMillis) {
            guard();
            states.put(key, OutageStateMachine.recordSuccess(states.get(key), probing, nowMillis));
        }

        @Override
        public boolean tryAcquireProbe(String key, long probeTimeoutMs, long nowMillis) {
            guard();
            OutageState state = states.getOrDefault(key, OutageState.HEALTHY);
            if (state.ejected(nowMillis) || state.probing(nowMillis)) {
                return false;
            }
            states.put(key, OutageStateMachine.acquireProbe(state, probeTimeoutMs, nowMillis));
            return true;
        }

        @Override
        public void release(String key) {
            guard();
            states.remove(key);
        }

        @Override
        public void forceEject(String key, long cooldownMs, long nowMillis) {
            guard();
            states.put(key, OutageStateMachine.forceEject(cooldownMs, nowMillis));
        }

        @Override
        public List<OutageEntry> list(long nowMillis) {
            guard();
            List<OutageEntry> result = new ArrayList<>();
            states.forEach((key, state) -> {
                if (state.ejected(nowMillis) || state.probing(nowMillis)) {
                    result.add(new OutageEntry("rule-a", "gpt-4o", state));
                }
            });
            return result;
        }

        private final java.util.Map<String, OutageState> states = new java.util.concurrent.ConcurrentHashMap<>();
    }

    @BeforeEach
    void setUp() {
        primary = new FakeStore("redis");
        fallback = new FakeStore("caffeine");
        keys = new OutageKeys("knot:outage");
        probes = new AtomicInteger();
        primaryReachable = true;
        store = new FallbackTargetOutageStore(primary, fallback, keys, 10_000L,
                () -> {
                    probes.incrementAndGet();
                    if (!primaryReachable) {
                        throw new IllegalStateException("ping failed");
                    }
                });
    }

    @Test
    @DisplayName("健康时读写全走 primary")
    void routesToPrimaryWhenHealthy() {
        OutageState state = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        assertEquals(1, state.failures());
        assertTrue(primary.states.containsKey(KEY));
        assertFalse(fallback.states.containsKey(KEY));
        assertEquals("redis", store.id());
        assertFalse(store.degraded());
    }

    @Test
    @DisplayName("primary 抛异常即降级，读写改走 fallback")
    void degradesOnPrimaryFailure() {
        primary.broken = true;
        OutageState state = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        assertEquals(1, state.failures());
        assertTrue(store.degraded());
        assertEquals("caffeine (fallback)", store.id());
        assertTrue(fallback.states.containsKey(KEY));

        // 降级期间继续计数，直到阈值摘除
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 1);
        OutageState third = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 2);
        assertTrue(third.ejected(NOW + 2));
    }

    @Test
    @DisplayName("恢复探测按间隔节流，不是每次读写都探")
    void recoveryProbeIsThrottled() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        assertTrue(store.degraded());
        assertEquals(0, probes.get());

        // 同窗口内的连续读写不再触发探针
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 100);
        store.state(KEY, NOW + 200);
        assertEquals(0, probes.get());

        // 越过间隔才探一次（NOW 是类加载时刻，与真实墙钟有毫秒级差，取大余量）
        store.state(KEY, NOW + 60_000);
        assertEquals(1, probes.get());
    }

    @Test
    @DisplayName("探针失败保持降级，探针成功则回灌并切回 primary")
    void recoversAndRehydrates() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 1);
        OutageState ejected = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 2);
        assertTrue(ejected.ejected(NOW + 2));

        // primary 仍不可写：探针虽通，回灌 forceEject 抛异常 → 维持降级
        store.state(KEY, NOW + 20_000);
        assertTrue(store.degraded());

        // Redis 恢复（探针可达 + primary 不再抛），下次探测通过 → 回灌本地摘除条目
        primaryReachable = true;
        primary.broken = false;
        long recoveredAt = NOW + 30_000;
        store.state(KEY, recoveredAt);
        assertFalse(store.degraded());
        assertEquals("redis", store.id());

        // 回灌：primary 里该目标处于摘除中，本地已清空
        OutageState rehydrated = primary.states.get(KEY);
        assertTrue(rehydrated != null && rehydrated.ejected(recoveredAt), "primary 应保留剩余冷却");
        assertFalse(fallback.states.containsKey(KEY));

        // 恢复后读写重新走 primary
        primary.broken = false;
        OutageState after = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, recoveredAt + 1);
        assertTrue(primary.states.containsKey(KEY));
        assertEquals(after.failures(), primary.states.get(KEY).failures());
    }

    @Test
    @DisplayName("探针通过但主存储操作仍失败：不得假恢复，保持降级")
    void probeOkButOpsStillFailStaysDegraded() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        assertTrue(store.degraded());

        // 探针可达（如只读副本 ping 通）但真实读写仍抛：恢复探测通过后当次走 primary 又炸
        primaryReachable = true;
        OutageState second = store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 30_000);
        assertTrue(store.degraded(), "主存储操作失败必须重新降级");
        assertEquals("caffeine (fallback)", store.id());
        // 计数继续落在本地，窗口未重置（30s < 60s 窗口）
        assertEquals(2, second.failures());
        assertEquals(2, fallback.states.get(KEY).failures());
    }

    @Test
    @DisplayName("已过期的本地摘除条目不回灌")
    void expiredEntriesAreNotRehydrated() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 1);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 2);
        // 冷却 60s，跳到 5 分钟后：本地条目已过冷却期（真实 Caffeine 由 TTL 回收）
        long wayLater = NOW + 300_000;
        primaryReachable = true;
        primary.broken = false;
        store.state(KEY, wayLater);
        assertFalse(store.degraded());
        assertFalse(primary.states.containsKey(KEY), "过期条目不应回灌");
        // 假存储不过期，条目可能残留，但必须已脱离摘除态
        OutageState leftover = fallback.states.get(KEY);
        assertTrue(leftover == null || !leftover.ejected(wayLater), "过期条目即便残留也不再处于摘除");
    }

    @Test
    @DisplayName("降级期间运维动作双写：恢复后旧摘除不复活")
    void releaseDuringDegradeWritesBoth() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 1);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 2);

        // 降级期间手动恢复：本地清掉；primary 此时不可用，降级标记保持
        store.release(KEY);
        assertFalse(fallback.states.containsKey(KEY));
        assertTrue(store.degraded());

        // primary 恢复：探针通过 → 切回；primary 里残留的旧摘除状态需被清掉
        primaryReachable = true;
        primary.broken = false;
        store.state(KEY, NOW + 20_000);
        assertFalse(store.degraded());
        assertFalse(primary.states.containsKey(KEY), "primary 残留旧状态必须已清");
    }

    @Test
    @DisplayName("降级期间强制摘除双写两侧")
    void forceEjectWritesBoth() {
        primary.broken = true;
        store.forceEject(KEY, 120_000L, NOW);
        assertTrue(fallback.states.get(KEY).ejected(NOW));
        assertTrue(store.degraded());

        primaryReachable = true;
        primary.broken = false;
        store.forceEject(KEY, 120_000L, NOW + 20_000);
        assertTrue(primary.states.get(KEY).ejected(NOW + 20_000));
        assertFalse(store.degraded());
    }

    @Test
    @DisplayName("list 降级时走 fallback，恢复后走 primary")
    void listFollowsActiveStore() {
        primary.broken = true;
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 1);
        store.recordAnomaly(KEY, POLICY, AnomalyKind.FAILURE, NOW + 2);
        assertEquals(1, store.list(NOW + 3).size());

        // Redis 恢复（探针可达 + primary 不再抛）：回灌本地摘除条目后切回 primary
        primaryReachable = true;
        primary.broken = false;
        store.list(NOW + 20_000);
        assertFalse(store.degraded());
        // 回灌后 primary 可见（剩余冷却 42s 内）
        assertEquals(1, store.list(NOW + 20_001).size());
    }
}
