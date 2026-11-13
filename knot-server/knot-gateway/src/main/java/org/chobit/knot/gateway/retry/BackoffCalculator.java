package org.chobit.knot.gateway.retry;

import org.chobit.knot.common.model.RetryPolicy;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试退避时长的唯一出口。
 * 默认指数退避 + 抖动：{@code delay = min(cap, base * multiplier^(n-1))}，开启 jitter 时在 {@code [0, delay)} 内随机取值。
 * 策略开启 {@code respectRetryAfter} 且上游给了 {@code Retry-After} 时取两者较大值，仍受 {@code backoffMaxMs} 约束。
 * 本类只计算毫秒数，等待由调用方以 {@code Mono.delay} 承载（不占用线程）；{@code backoffMaxMs} 约束单次重试的最大退避。
 */
public final class BackoffCalculator {

    private BackoffCalculator() {
    }

    /**
     * 计算第 {@code failedAttempts} 次失败之后的退避时长（毫秒）。
     *
     * @param failedAttempts 已失败的次数（第一次失败传 1），退避指数从 0 起算
     * @param policy         规则级重试策略，null 视为默认策略
     * @param retryAfterMs   上游 {@code Retry-After} 换算的毫秒数，无则传 null
     * @return 退避毫秒数，恒 {@code >= 0}
     */
    public static long delayMillis(int failedAttempts, RetryPolicy policy, Long retryAfterMs) {
        RetryPolicy effective = policy == null ? RetryPolicy.DEFAULT : policy;
        long base = effective.backoffBaseMs();
        long cap = effective.backoffMaxMs();
        double multiplier = effective.multiplier();
        int exponent = Math.max(0, failedAttempts - 1);

        long delay = cap <= 0 ? 0 : Math.min(cap, (long) (base * Math.pow(multiplier, exponent)));
        if (effective.respectRetryAfter() && retryAfterMs != null && retryAfterMs > 0) {
            delay = Math.min(cap, Math.max(delay, retryAfterMs));
        }
        if (delay <= 0) {
            return 0;
        }
        return effective.jitter() ? ThreadLocalRandom.current().nextLong(delay) : delay;
    }
}
