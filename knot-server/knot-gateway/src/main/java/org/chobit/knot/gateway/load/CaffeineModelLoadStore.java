package org.chobit.knot.gateway.load;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 单节点在途计量：Caffeine。
 * 走 {@code cache.asMap().compute} 保证并发 {@code enter}/{@code leave} 原子性；用 {@link Expiry} 按条目携带的过期时刻逐键计算 TTL，{@code leave} 不推后过期时刻。
 * {@code leave} 减到 0 时移除条目；条目已过期时递减不做任何事并返回 0，不压出负在途。
 * 只统计本进程内请求，多节点下为近似负载；全局口径用 {@code RedisModelLoadStore}。
 */
public class CaffeineModelLoadStore implements ModelLoadStore {

    private final Cache<String, Gauge> cache;
    private final Clock clock;
    private final long ttlSeconds;

    /**
     * @param maximumSize 最大条目数
     * @param ttlSeconds  在途计数存活时间（秒）
     */
    public CaffeineModelLoadStore(long maximumSize, long ttlSeconds) {
        this(maximumSize, ttlSeconds, Clock.systemUTC(), Ticker.systemTicker());
    }

    /**
     * @param maximumSize 最大条目数
     * @param ttlSeconds  在途计数存活时间（秒）
     * @param clock       墙钟，测试可注入固定时钟
     * @param ticker      Caffeine 的时间源，测试可注入
     */
    public CaffeineModelLoadStore(long maximumSize, long ttlSeconds, Clock clock, Ticker ticker) {
        this.clock = clock;
        this.ttlSeconds = ttlSeconds > 0L ? ttlSeconds : 300L;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(ticker)
                // 写入后同步执行维护：在途写入频率等于请求频率，维护开销可以忽略，
                // 换来的是过期 / 淘汰时机确定——不丢给公共线程池排队，避免高负载下堆积
                .executor(Runnable::run)
                .expireAfter(new GaugeExpiry(this.clock))
                .build();
    }

    @Override
    public String id() {
        return "caffeine";
    }

    @Override
    public long enter(String key) {
        long expireAtMillis = clock.millis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
        return cache.asMap()
                .compute(key, (ignored, existing) -> {
                    Gauge gauge = existing == null ? new Gauge(expireAtMillis) : existing;
                    gauge.value += 1L;
                    gauge.extendTo(expireAtMillis);
                    return gauge;
                })
                .value;
    }

    @Override
    public long leave(String key) {
        Gauge remaining = cache.asMap().compute(key, (ignored, existing) -> {
            if (existing == null) {
                // 条目已过期 / 从未 enter：迟到递减，不动键空间
                return null;
            }
            existing.value -= 1L;
            return existing.value > 0L ? existing : null;
        });
        return remaining == null ? 0L : remaining.value;
    }

    @Override
    public long inflight(String key) {
        Gauge gauge = cache.getIfPresent(key);
        return gauge == null ? 0L : gauge.value;
    }

    @Override
    public Map<String, Long> inflightBatch(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>(keys.size());
        for (String key : keys) {
            result.put(key, inflight(key));
        }
        return result;
    }

    /** 可变 gauge 条目：存活期间只在其上加减。 */
    private static final class Gauge {

        private volatile long value;
        private volatile long expireAtMillis;

        private Gauge(long expireAtMillis) {
            this.expireAtMillis = expireAtMillis;
        }

        /**
         * 续期：仅允许往后推（enter 每次都续），不因时钟回拨缩短 TTL。
         */
        private void extendTo(long candidate) {
            if (candidate > expireAtMillis) {
                expireAtMillis = candidate;
            }
        }
    }

    /**
     * 逐键可变过期。
     */
    private static final class GaugeExpiry implements Expiry<String, Gauge> {

        private final Clock clock;

        private GaugeExpiry(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long expireAfterCreate(String key, Gauge value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, Gauge value, long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(String key, Gauge value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long remainingNanos(Gauge gauge) {
            long remainingMillis = gauge.expireAtMillis - clock.millis();
            if (remainingMillis <= 0L) {
                return TimeUnit.MILLISECONDS.toNanos(1L);
            }
            // TimeUnit 转换在溢出时饱和到 Long.MAX_VALUE，即 Caffeine 语义下的「不过期」
            return TimeUnit.MILLISECONDS.toNanos(remainingMillis);
        }
    }
}
