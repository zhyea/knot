package org.chobit.knot.gateway.traffic;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * 单节点计数存储：Caffeine。
 * 计数走 {@code cache.asMap().compute}（同一 key 排他，不会丢计数）；TTL 由 {@link Expiry} 按条目自身的过期时刻逐键计算；{@code maximumSize} 兜底。
 * 计数只在本进程内有效，多节点部署各节点各算各的，需要全局一致时切到 {@code RedisTrafficCounterStore}。
 */
public class CaffeineTrafficCounterStore implements TrafficCounterStore {

    private final Cache<String, Counter> cache;

    /**
     * @param maximumSize 最大条目数
     */
    public CaffeineTrafficCounterStore(long maximumSize) {
        this(maximumSize, Clock.systemUTC(), Ticker.systemTicker());
    }

    /**
     * @param maximumSize 最大条目数
     * @param clock       墙钟，测试可注入固定时钟
     * @param ticker      Caffeine 的时间源，测试可注入
     */
    public CaffeineTrafficCounterStore(long maximumSize, Clock clock, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(ticker)
                // 写入后同步执行维护：计数写入频率等于请求频率，维护开销可以忽略，
                // 换来的是过期 / 淘汰时机确定——不丢给公共线程池排队，避免高负载下堆积
                .executor(Runnable::run)
                .expireAfter(new CounterExpiry(clock))
                .build();
    }

    @Override
    public String id() {
        return "caffeine";
    }

    @Override
    public long addAndGet(String key, long delta, long expireAtMillis) {
        return cache.asMap()
                .compute(key, (ignored, existing) -> {
                    Counter counter = existing == null ? new Counter(expireAtMillis) : existing;
                    counter.add(delta);
                    counter.extendTo(expireAtMillis);
                    return counter;
                })
                .value();
    }

    @Override
    public long get(String key) {
        Counter counter = cache.getIfPresent(key);
        return counter == null ? 0L : counter.value();
    }

    /**
     * 可变计数条目：存活期间只在其上加值。
     */
    private static final class Counter {

        private volatile long value;
        private volatile long expireAtMillis;

        private Counter(long expireAtMillis) {
            this.expireAtMillis = expireAtMillis;
        }

        private void add(long delta) {
            value += delta;
        }

        private long value() {
            return value;
        }

        /**
         * 续期：仅允许往后推（累计 token 维度每次累加都续），不因时钟回拨缩短 TTL。
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
    private static final class CounterExpiry implements Expiry<String, Counter> {

        private final Clock clock;

        private CounterExpiry(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long expireAfterCreate(String key, Counter value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, Counter value, long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(String key, Counter value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long remainingNanos(Counter counter) {
            long remainingMillis = counter.expireAtMillis - clock.millis();
            if (remainingMillis <= 0L) {
                return TimeUnit.MILLISECONDS.toNanos(1L);
            }
            // TimeUnit 转换在溢出时饱和到 Long.MAX_VALUE，即 Caffeine 语义下的「不过期」
            return TimeUnit.MILLISECONDS.toNanos(remainingMillis);
        }
    }
}
