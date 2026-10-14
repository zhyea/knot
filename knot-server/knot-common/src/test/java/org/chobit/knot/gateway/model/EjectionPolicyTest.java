package org.chobit.knot.gateway.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：异常摘除策略的默认值、容错解析、越界收敛与冷却增长。
 */
class EjectionPolicyTest {

    private static EjectionPolicy policy(Integer windowSec,
                                         Integer threshold,
                                         Integer slowFirstTokenMs,
                                         Integer cooldownMs) {
        return new EjectionPolicy(true, windowSec, threshold, true, slowFirstTokenMs, cooldownMs,
                true, 2.0, EjectionPolicy.MAX_COOLDOWN_MS_DEFAULT,
                EjectionPolicy.PROBE_TIMEOUT_MS_DEFAULT, true, EjectionPolicy.MODE_SIMPLE);
    }

    @Test
    void nullOrBlankFallsBackToDefault() {
        assertEquals(EjectionPolicy.DEFAULT, EjectionPolicy.parse(null));
        assertEquals(EjectionPolicy.DEFAULT, EjectionPolicy.parse(""));
        assertEquals(EjectionPolicy.DEFAULT, EjectionPolicy.parse("   "));
    }

    @Test
    void brokenJsonFallsBackToDefault() {
        assertEquals(EjectionPolicy.DEFAULT, EjectionPolicy.parse("{not a json"));
    }

    @Test
    void defaultIsDisabledSimpleWithSaneValues() {
        EjectionPolicy policy = EjectionPolicy.DEFAULT;
        assertFalse(policy.isEnabled());
        assertEquals(EjectionPolicy.MODE_SIMPLE, policy.mode());
        assertEquals(60, policy.windowSec());
        assertEquals(5, policy.threshold());
        assertEquals(15_000, policy.slowFirstTokenMs());
        assertEquals(60_000, policy.cooldownMs());
        assertTrue(policy.isCountFailures());
        assertTrue(policy.isCountSlow());
        assertTrue(policy.isFailOpenWhenAllEjected());
        assertEquals(600_000, policy.maxCooldownMs());
        assertEquals(30_000, policy.probeTimeoutMs());
        assertEquals(60_000L, policy.windowMillis());
    }

    @Test
    void nullFieldsAreFilledWithDefaults() {
        EjectionPolicy policy = new EjectionPolicy(null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertFalse(policy.isEnabled());
        assertEquals(EjectionPolicy.WINDOW_SEC_DEFAULT, policy.windowSec());
        assertEquals(EjectionPolicy.THRESHOLD_DEFAULT, policy.threshold());
        assertEquals(EjectionPolicy.SLOW_FIRST_TOKEN_MS_DEFAULT, policy.slowFirstTokenMs());
        assertEquals(EjectionPolicy.COOLDOWN_MS_DEFAULT, policy.cooldownMs());
        assertEquals(EjectionPolicy.MODE_SIMPLE, policy.mode());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        EjectionPolicy policy = policy(999_999, 999_999, 999_999_999, 0);
        assertEquals(EjectionPolicy.WINDOW_SEC_LIMIT, policy.windowSec());
        assertEquals(EjectionPolicy.THRESHOLD_LIMIT, policy.threshold());
        assertEquals(EjectionPolicy.SLOW_FIRST_TOKEN_MS_LIMIT, policy.slowFirstTokenMs());
        assertEquals(1_000, policy.cooldownMs());
    }

    @Test
    void maxCooldownNeverBelowFirstCooldown() {
        EjectionPolicy policy = new EjectionPolicy(true, 60, 5, true, 15_000, 120_000,
                true, 2.0, 30_000, 30_000, true, EjectionPolicy.MODE_PROFESSIONAL);
        assertEquals(120_000, policy.maxCooldownMs());
    }

    @Test
    void multiplierIsRoundedToOneDecimal() {
        EjectionPolicy policy = new EjectionPolicy(true, 60, 5, true, 15_000, 60_000,
                true, 2.67, 600_000, 30_000, true, EjectionPolicy.MODE_PROFESSIONAL);
        assertEquals(2.7, policy.cooldownMultiplier());
    }

    @Test
    void configModeIsNormalized() {
        assertEquals(EjectionPolicy.MODE_SIMPLE, policy(60, 5, 15_000, 60_000).mode());
        assertEquals(EjectionPolicy.MODE_PROFESSIONAL,
                new EjectionPolicy(true, 60, 5, true, 15_000, 60_000, true, 2.0, 600_000, 30_000,
                        true, " PROFESSIONAL ").mode());
        assertEquals(EjectionPolicy.MODE_SIMPLE,
                new EjectionPolicy(true, 60, 5, true, 15_000, 60_000, true, 2.0, 600_000, 30_000,
                        true, "whatever").mode());
    }

    @Test
    void cooldownGrowsByMultiplierAndCapsAtMax() {
        EjectionPolicy policy = policy(60, 5, 15_000, 60_000);
        assertEquals(120_000L, policy.nextCooldownMillis(60_000L));
        assertEquals(240_000L, policy.nextCooldownMillis(120_000L));
        assertEquals(600_000L, policy.nextCooldownMillis(500_000L));
        // 倍数 1.0 时不允许变短（ceil 后仍取 base）
        EjectionPolicy flat = new EjectionPolicy(true, 60, 5, true, 15_000, 60_000,
                true, 1.0, 600_000, 30_000, true, EjectionPolicy.MODE_PROFESSIONAL);
        assertEquals(60_000L, flat.nextCooldownMillis(60_000L));
    }

    @Test
    void serializeRoundTripKeepsValues() {
        EjectionPolicy policy = new EjectionPolicy(true, 30, 3, true, 8_000, 45_000,
                true, 1.5, 300_000, 20_000, false, EjectionPolicy.MODE_PROFESSIONAL);
        EjectionPolicy parsed = EjectionPolicy.parse(EjectionPolicy.serialize(policy));
        assertEquals(policy, parsed);
        assertTrue(parsed.isEnabled());
        assertFalse(parsed.isFailOpenWhenAllEjected());
    }

    @Test
    void serializeNullWritesNull() {
        assertEquals(null, EjectionPolicy.serialize(null));
    }
}
