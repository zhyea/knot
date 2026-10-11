package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficRejectReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficRejectDetailTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void rateLimitDetailKeepsRawCountsAndWindowUnit() {
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision(
                TrafficRejectReason.RATE_LIMIT_RPM, "MODEL", "gpt-4o", 2L, 3L, NOW + 42_000L, null), NOW);

        assertEquals("MODEL", detail.resourceType());
        assertEquals("gpt-4o", detail.resourceCode());
        assertEquals("RATE_LIMIT_RPM", detail.reason());
        assertEquals("RPM", detail.dimension());
        assertEquals("requests/minute", detail.unit());
        assertEquals(0, BigDecimal.valueOf(2L).compareTo(detail.limit()));
        assertEquals(0, BigDecimal.valueOf(3L).compareTo(detail.used()));
        assertEquals(42L, detail.resetAfterSeconds());
        assertEquals("Rate limit exceeded for MODEL#gpt-4o: 3/2 requests/minute. Resets in 42s.", detail.describe());
    }

    @Test
    void costDetailConvertsNanoYuanBackToAmount() {
        // 内部按纳元计数：10.00 → 10^8，直接透给调用方是无法读的 1000000000
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision(
                TrafficRejectReason.QUOTA_COST, "PROVIDER", "account-code",
                1_000_000_000L, 1_200_000_000L, NOW + 3_600_000L, "USD"), NOW);

        assertEquals("USD", detail.unit(), "成本维度的单位就是策略币种");
        assertEquals(0, new BigDecimal("10.00000000").compareTo(detail.limit()));
        assertEquals(0, new BigDecimal("12.00000000").compareTo(detail.used()));
        assertEquals(3600L, detail.resetAfterSeconds());
        assertEquals("Quota exceeded for PROVIDER#account-code: 12/10 USD. Resets in 3600s.", detail.describe());
    }

    @Test
    void quotaTokensDetailUsesTokenUnit() {
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision(
                TrafficRejectReason.QUOTA_TOKENS, "APP", "app-code", 1000L, 1200L, NOW + 60_000L, null), NOW);

        assertEquals("TOKENS", detail.dimension());
        assertEquals("tokens", detail.unit());
        assertEquals("Quota exceeded for APP#app-code: 1200/1000 tokens. Resets in 60s.", detail.describe());
    }

    @Test
    void resetAfterSecondsNeverDropsToZero() {
        // 给 0 会让客户端立即重投，等于没退避
        TrafficRejectDetail past = TrafficRejectDetail.of(decision(
                TrafficRejectReason.RATE_LIMIT_TPM, "MODEL", "gpt-4o", 10L, 10L, NOW - 5_000L, null), NOW);
        assertEquals(1L, past.resetAfterSeconds());

        TrafficRejectDetail rounding = TrafficRejectDetail.of(decision(
                TrafficRejectReason.RATE_LIMIT_TPM, "MODEL", "gpt-4o", 10L, 10L, NOW + 1_400L, null), NOW);
        assertEquals(2L, rounding.resetAfterSeconds(), "向上取整，不截断");
    }

    private static TrafficDecision decision(TrafficRejectReason reason,
                                            String resourceType,
                                            String resourceCode,
                                            long limit,
                                            long used,
                                            long resetAtMillis,
                                            String currency) {
        return TrafficDecision.reject(reason, resourceType, resourceCode, limit, used, resetAtMillis, currency);
    }
}
