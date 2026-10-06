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
    void defaultIsEnabledWithSaneValues() {
        RetryPolicy policy = RetryPolicy.DEFAULT;
        assertTrue(policy.enabled());
        assertTrue(policy.retryable());
        assertEquals(3, policy.maxAttempts());
        assertEquals(200, policy.backoffBaseMs());
        assertEquals(5_000, policy.backoffMaxMs());
        assertEquals(2.0, policy.multiplier());
        assertTrue(policy.jitter());
        assertTrue(policy.respectRetryAfter());
        assertEquals(RetryPolicy.MODE_ALLOWLIST, policy.retryOnMode());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        RetryPolicy policy = new RetryPolicy(true, 999, -5, 999_999, 100.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"));
        assertEquals(RetryPolicy.MAX_ATTEMPTS_LIMIT, policy.maxAttempts());
        assertEquals(0, policy.backoffBaseMs());
        assertEquals(RetryPolicy.BACKOFF_LIMIT_MS, policy.backoffMaxMs());
        assertEquals(RetryPolicy.MULTIPLIER_LIMIT, policy.multiplier());
    }

    @Test
    void invalidStatusesAreFilteredAndEmptyFallsBackToDefault() {
        RetryPolicy filtered = new RetryPolicy(true, 3, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, Arrays.asList("500", "abc", "", null));
        assertEquals(List.of("500"), filtered.retryOn());

        RetryPolicy empty = new RetryPolicy(true, 3, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of());
        assertEquals(RetryPolicy.DEFAULT.retryOn(), empty.retryOn());
    }

    @Test
    void allowlistRetriesOnlyListedStatuses() {
        RetryPolicy policy = RetryPolicy.DEFAULT;
        assertTrue(policy.matchesStatus(500));
        assertTrue(policy.matchesStatus(429));
        assertFalse(policy.matchesStatus(400));
        assertFalse(policy.matchesStatus(401));
    }

    @Test
    void denylistSkipsOnlyListedStatuses() {
        RetryPolicy policy = new RetryPolicy(true, 3, 200, 5_000, 2.0, false, false,
                RetryPolicy.MODE_DENYLIST, List.of("400", "401"));
        assertFalse(policy.matchesStatus(400));
        assertTrue(policy.matchesStatus(500));
    }

    @Test
    void singleAttemptOrDisabledMeansNoRetry() {
        assertFalse(new RetryPolicy(true, 1, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500")).retryable());
        assertFalse(new RetryPolicy(false, 3, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500")).retryable());
    }

    @Test
    void serializeAndParseRoundTripKeepsDisabledState() {
        RetryPolicy disabled = new RetryPolicy(false, 5, 100, 1_000, 1.5, false, false,
                RetryPolicy.MODE_DENYLIST, List.of("400"));
        String json = RetryPolicy.serialize(disabled);
        assertNotNull(json);

        RetryPolicy parsed = RetryPolicy.parse(json);
        assertFalse(parsed.enabled());
        assertFalse(parsed.retryable());
        assertEquals(5, parsed.maxAttempts());
        assertEquals(100, parsed.backoffBaseMs());
        assertEquals(1_000, parsed.backoffMaxMs());
        assertEquals(1.5, parsed.multiplier());
        assertFalse(parsed.jitter());
        assertFalse(parsed.respectRetryAfter());
        assertEquals(RetryPolicy.MODE_DENYLIST, parsed.retryOnMode());
    }
}
