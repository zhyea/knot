package org.chobit.knot.admin.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 防爆破固定窗口计数器：在给定时间窗内累计某 key 的失败次数，超过阈值即视为「被封禁」，窗末整体过期。
 * 用于登录失败、消费者鉴权失败等场景的粗粒度限速；计数为节点级（per-node），仅在单 JVM 内有效。
 */
public class BruteForceGuard {

    private final Cache<String, AtomicLong> failures;
    private final long maxFailures;

    /**
     * @param maxFailures 时间窗内允许的最大失败次数，超过即封禁
     * @param window      时间窗长度（从最后一次写入起算自动过期）
     */
    public BruteForceGuard(long maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(100_000)
                .build();
    }

    /**
     * 该 key 是否在当前窗口内已达封禁阈值。
     */
    public boolean isBlocked(String key) {
        AtomicLong counter = failures.getIfPresent(key);
        return counter != null && counter.get() >= maxFailures;
    }

    /**
     * 记录一次失败（原子自增；窗口过期后自动归零）。
     */
    public void recordFailure(String key) {
        failures.get(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 成功后清除该 key 的失败计数（如登录成功清零）。
     */
    public void reset(@Nullable String key) {
        if (key != null) {
            failures.invalidate(key);
        }
    }
}
