package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.model.EjectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘除门面的慢信号契约：{@link OutageTracker#recordSlow}。
 *
 * <p>用真实 {@link CaffeineTargetOutageStore}（不用假存储）：慢信号的落点就是窗口计数器，
 * 与失败类异常共用同一套状态机，真假存储的行为差异应由store 自己的用例覆盖。</p>
 */
class OutageTrackerTest {

    private static final String RULE = "rule-a";
    private static final String MODEL = "gpt-4o";

    /** 阈值 3、慢阈值 1000ms、窗口 60s：三个慢信号即摘除。 */
    private static final EjectionPolicy POLICY = new EjectionPolicy(
            true, 60, 3, true, 1_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

    private TargetOutageStore store;
    private OutageTracker tracker;
    private long now;

    @BeforeEach
    void setUp() {
        store = new CaffeineTargetOutageStore(1_000L, 60_000L);
        tracker = new OutageTracker(store, new OutageKeys("knot:outage"));
        now = System.currentTimeMillis();
    }

    @Test
    @DisplayName("慢信号计入 slows，与失败共用阈值")
    void slowCountsTowardThreshold() {
        tracker.recordSlow(POLICY, RULE, MODEL, 1_500, now);
        OutageState first = store.state(key(), now);
        assertEquals(0, first.failures());
        assertEquals(1, first.slows());

        tracker.recordSlow(POLICY, RULE, MODEL, 2_000, now + 1);
        tracker.recordSlow(POLICY, RULE, MODEL, 3_000, now + 2);

        OutageState third = store.state(key(), now + 2);
        assertEquals(3, third.anomalies());
        assertTrue(third.ejected(now + 2), "阈值达成必须摘除");
    }

    @Test
    @DisplayName("快于慢阈值的不计；达到阈值即计")
    void fastFirstTokenIsIgnored() {
        tracker.recordSlow(POLICY, RULE, MODEL, 999, now);
        assertEquals(0, store.state(key(), now).anomalies(), "低于慢阈值必须不计");

        // 口径为「达到即计」：等于慢阈值的一次慢信号照记
        tracker.recordSlow(POLICY, RULE, MODEL, 1_000, now + 1);
        assertEquals(1, store.state(key(), now + 1).anomalies());
    }

    @Test
    @DisplayName("countSlow 关闭时不计慢信号")
    void countSlowDisabledIgnoresSlow() {
        EjectionPolicy policy = new EjectionPolicy(
                true, 60, 3, false, 1_000, 60_000,
                true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

        tracker.recordSlow(policy, RULE, MODEL, 5_000, now);

        assertEquals(0, store.state(key(), now).anomalies());
        assertFalse(store.state(key(), now).ejected(now));
    }

    @Test
    @DisplayName("策略未启用（含 null）时不计")
    void disabledPolicyIgnoresSlow() {
        tracker.recordSlow(EjectionPolicy.DEFAULT, RULE, MODEL, 5_000, now);
        tracker.recordSlow(null, RULE, MODEL, 5_000, now);
        assertEquals(0, store.state(key(), now).anomalies());
    }

    @Test
    @DisplayName("慢信号摘除后进入冷却，与失败摘除同一条冷却时间线")
    void slowEjectionEntersCooldown() {
        tracker.recordSlow(POLICY, RULE, MODEL, 2_000, now);
        tracker.recordSlow(POLICY, RULE, MODEL, 2_000, now + 1);
        tracker.recordSlow(POLICY, RULE, MODEL, 2_000, now + 2);

        OutageState ejected = store.state(key(), now + 2);
        assertTrue(ejected.ejected(now + 2));
        assertEquals(now + 2 + 60_000, ejected.ejectedUntil());
        assertEquals(AnomalyKind.SLOW.name(), ejected.reason());
    }

    private String key() {
        return new OutageKeys("knot:outage").of(RULE, MODEL);
    }
}
