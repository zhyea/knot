package org.chobit.knot.gateway.retry;

import org.chobit.knot.gateway.model.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：重试退避时长的指数增长、上限收敛与 Retry-After 处理。
 *
 * <p>测试一律关闭 jitter，保证可断言；开启 jitter 时结果落在 [0, delay) 内。</p>
 */
class BackoffCalculatorTest {

    /** 关闭抖动的固定策略：200ms 起、×2、上限 5s */
    private static RetryPolicy fixed() {
        return new RetryPolicy(true, 3, 200, 5_000, 2.0, false, false,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
    }

    @Test
    void growsExponentiallyFromBase() {
        RetryPolicy policy = fixed();
        assertEquals(200L, BackoffCalculator.delayMillis(1, policy, null));
        assertEquals(400L, BackoffCalculator.delayMillis(2, policy, null));
        assertEquals(800L, BackoffCalculator.delayMillis(3, policy, null));
    }

    @Test
    void neverExceedsConfiguredCap() {
        RetryPolicy policy = fixed();
        assertEquals(5_000L, BackoffCalculator.delayMillis(10, policy, null));
        assertEquals(5_000L, BackoffCalculator.delayMillis(50, policy, null));
    }

    @Test
    void retryAfterIsHonoredWhenEnabled() {
        RetryPolicy policy = new RetryPolicy(true, 3, 200, 5_000, 2.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("429"), RetryPolicy.MODE_SIMPLE);
        // 指数退避 200ms，但上游要求 3s —— 取较大者
        assertEquals(3_000L, BackoffCalculator.delayMillis(1, policy, 3_000L));
        // 上游要求 100ms，小于指数退避 —— 仍用 200ms
        assertEquals(200L, BackoffCalculator.delayMillis(1, policy, 100L));
    }

    @Test
    void retryAfterIsIgnoredWhenDisabled() {
        RetryPolicy policy = fixed();
        assertEquals(200L, BackoffCalculator.delayMillis(1, policy, 3_000L));
    }

    @Test
    void retryAfterIsStillCappedByMax() {
        RetryPolicy policy = new RetryPolicy(true, 3, 200, 5_000, 2.0, false, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("429"), RetryPolicy.MODE_SIMPLE);
        // 上游给了 1 小时，不能无上限转嫁给调用方
        assertEquals(5_000L, BackoffCalculator.delayMillis(1, policy, 3_600_000L));
    }

    @Test
    void jitterStaysWithinDelay() {
        RetryPolicy policy = new RetryPolicy(true, 5, 1_000, 5_000, 2.0, true, false,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
        for (int i = 0; i < 100; i++) {
            long delay = BackoffCalculator.delayMillis(1, policy, null);
            assertTrue(delay >= 0 && delay < 1_000, "jitter 结果应落在 [0,1000)，实际 " + delay);
        }
    }

    @Test
    void zeroBaseYieldsZeroDelay() {
        RetryPolicy policy = new RetryPolicy(true, 3, 0, 0, 2.0, false, false,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
        assertEquals(0L, BackoffCalculator.delayMillis(1, policy, null));
    }
}
