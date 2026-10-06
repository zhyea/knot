package org.chobit.knot.gateway.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：失败重试策略的默认值、容错解析、越界收敛与状态码判定。
 */
class RetryPolicyTest {

    @Test
    void nullOrBlankFallsBackToDefault() {
        assertEquals(RetryPolicy.DEFAULT, RetryPolicy.parse(null));
        assertEquals(RetryPolicy.DEFAULT, RetryPolicy.parse(""));
        assertEquals(RetryPolicy.DEFAULT, RetryPolicy.parse("   "));
    }

    @Test
    void brokenJsonFallsBackToDefault() {
        assertEquals(RetryPolicy.DEFAULT, RetryPolicy.parse("{not a json"));
    }

    @Test
    void defaultIsEnabledSimpleWithSaneValues() {
        RetryPolicy policy = RetryPolicy.DEFAULT;
        assertTrue(policy.enabled());
        assertTrue(policy.retryable());
        assertEquals(RetryPolicy.MODE_SIMPLE, policy.mode());
        assertEquals(2, policy.maxAttempts());
        assertEquals(200, policy.backoffBaseMs());
        assertEquals(5_000, policy.backoffMaxMs());
        assertEquals(1.0, policy.multiplier());
        assertFalse(policy.jitter());
        assertTrue(policy.respectRetryAfter());
        assertEquals(RetryPolicy.MODE_ALLOWLIST, policy.retryOnMode());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        RetryPolicy policy = new RetryPolicy(true, 999, -5, 999_999, 100.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
        assertEquals(RetryPolicy.MAX_ATTEMPTS_LIMIT, policy.maxAttempts());
        assertEquals(0, policy.backoffBaseMs());
        assertEquals(RetryPolicy.BACKOFF_LIMIT_MS, policy.backoffMaxMs());
        assertEquals(10.0, policy.multiplier());
    }

    @Test
    void multiplierIsRoundedToOneDecimal() {
        assertEquals(2.7, new RetryPolicy(true, 2, 200, 5_000, 2.67, true, false,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE).multiplier());
        assertEquals(1.2, new RetryPolicy(true, 2, 200, 5_000, 1.234, true, false,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE).multiplier());
    }

    @Test
    void invalidStatusesAreFilteredAndEmptyFallsBackToDefault() {
        RetryPolicy filtered = new RetryPolicy(true, 2, 200, 5_000, 2.0, true, false,
                RetryPolicy.MODE_ALLOWLIST, Arrays.asList("500", "abc", "", null), RetryPolicy.MODE_SIMPLE);
        assertEquals(List.of("500"), filtered.retryOn());

        RetryPolicy empty = new RetryPolicy(true, 2, 200, 5_000, 2.0, true, false,
                RetryPolicy.MODE_ALLOWLIST, List.of(), RetryPolicy.MODE_SIMPLE);
        assertEquals(RetryPolicy.DEFAULT.retryOn(), empty.retryOn());
    }

    @Test
    void allowlistRetriesOnlyListedStatuses() {
        // 显式白名单：只重试列出的精确码，未列出的 4xx 不重试
        RetryPolicy policy = new RetryPolicy(true, 2, 200, 5_000, 1.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500", "429"), RetryPolicy.MODE_SIMPLE);
        assertTrue(policy.matchesStatus(500));
        assertTrue(policy.matchesStatus(429));
        assertFalse(policy.matchesStatus(400));
        assertFalse(policy.matchesStatus(404));
    }

    @Test
    void denylistSkipsOnlyListedStatuses() {
        RetryPolicy policy = new RetryPolicy(true, 2, 200, 5_000, 2.0, false, false,
                RetryPolicy.MODE_DENYLIST, List.of("400", "401"), RetryPolicy.MODE_SIMPLE);
        assertFalse(policy.matchesStatus(400));
        assertTrue(policy.matchesStatus(500));
    }

    @Test
    void singleAttemptOrDisabledMeansNoRetry() {
        assertFalse(new RetryPolicy(true, 1, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE).retryable());
        assertFalse(new RetryPolicy(false, 2, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE).retryable());
    }

    @Test
    void serializeAndParseRoundTripKeepsDisabledState() {
        RetryPolicy disabled = new RetryPolicy(false, 5, 100, 1_000, 2.5, false, false,
                RetryPolicy.MODE_DENYLIST, List.of("400"), RetryPolicy.MODE_PROFESSIONAL);
        String json = RetryPolicy.serialize(disabled);
        assertNotNull(json);

        RetryPolicy parsed = RetryPolicy.parse(json);
        assertFalse(parsed.enabled());
        assertFalse(parsed.retryable());
        assertEquals(5, parsed.maxAttempts());
        assertEquals(100, parsed.backoffBaseMs());
        assertEquals(1_000, parsed.backoffMaxMs());
        assertEquals(2.5, parsed.multiplier());
        assertFalse(parsed.jitter());
        assertFalse(parsed.respectRetryAfter());
        assertEquals(RetryPolicy.MODE_DENYLIST, parsed.retryOnMode());
        assertEquals(RetryPolicy.MODE_PROFESSIONAL, parsed.mode());
    }

    @Test
    void configModeNormalized() {
        RetryPolicy professional = new RetryPolicy(true, 2, 200, 5_000, 2.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), "professional");
        assertEquals(RetryPolicy.MODE_PROFESSIONAL, professional.mode());

        RetryPolicy garbage = new RetryPolicy(true, 2, 200, 5_000, 2.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), "whatever");
        assertEquals(RetryPolicy.MODE_SIMPLE, garbage.mode());

        RetryPolicy blank = new RetryPolicy(true, 2, 200, 5_000, 2.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), null);
        assertEquals(RetryPolicy.MODE_SIMPLE, blank.mode());
    }

    @Test
    void defaultUsesWildcardClasses() {
        assertEquals(List.of("4xx", "5xx"), RetryPolicy.DEFAULT.retryOn());
        // 默认集合为通配符 4xx+5xx：4xx/5xx 均命中，1xx/2xx/3xx 不命中
        assertTrue(RetryPolicy.DEFAULT.matchesStatus(404));
        assertTrue(RetryPolicy.DEFAULT.matchesStatus(503));
        assertFalse(RetryPolicy.DEFAULT.matchesStatus(301));
    }

    @Test
    void wildcardMatchesWholeClass() {
        RetryPolicy only5xx = new RetryPolicy(true, 2, 200, 5_000, 1.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
        assertTrue(only5xx.matchesStatus(500));
        assertTrue(only5xx.matchesStatus(599));
        assertFalse(only5xx.matchesStatus(404));

        RetryPolicy only4xx = new RetryPolicy(true, 2, 200, 5_000, 1.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("4xx"), RetryPolicy.MODE_SIMPLE);
        assertTrue(only4xx.matchesStatus(400));
        assertTrue(only4xx.matchesStatus(499));
        assertFalse(only4xx.matchesStatus(503));
    }

    @Test
    void wildcardMixedWithExactCodes() {
        RetryPolicy policy = new RetryPolicy(true, 2, 200, 5_000, 1.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx", "429"), RetryPolicy.MODE_SIMPLE);
        assertTrue(policy.matchesStatus(503));
        assertTrue(policy.matchesStatus(429));
        assertFalse(policy.matchesStatus(404));
    }
}
