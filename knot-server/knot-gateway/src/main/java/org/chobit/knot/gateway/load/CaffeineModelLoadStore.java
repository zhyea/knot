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
 *
 * <p>三条实现要点：</p>
 * <ol>
 *   <li><b>原子性</b>：走 {@code cache.asMap().compute}，Caffeine 对同一 key 的计算是排他的，
 *       并发 enter/leave 不会丢计数。</li>
 *   <li><b>逐键过期</b>：用 {@link Expiry} 按条目自身携带的过期时刻计算 TTL；leave 不推后过期时刻，
 *       泄漏的计数最多存活一个 TTL。</li>
 *   <li><b>归零即删</b>：leave 把在途减到 0 时直接移除条目，避免零值条目长期占堆；
 *       递减时条目已不存在（过期）则不做任何事，返回 0——迟到递减不得压出负在途。</li>
 * </ol>
 *
 * <p>局限：只统计本进程内的在途请求，多节点部署下各节点各算一份，是<b>近似</b>负载；
 * 需要全局口径切到 {@code RedisModelLoadStore}。</p>
 */
public class CaffeineModelLoadStore implements ModelLoadStore {

    private final Cache<String, Gauge> cache;
    private final Clock clock;
    private final long ttlSeconds;

    /**
     * Constructs a new instance.
     *
     * @param maximumSize 最大条目数
     * @param ttlSeconds  在途计数存活时间（秒）
     */
    public CaffeineModelLoadStore(long maximumSize, long ttlSeconds) {
        this(maximumSize, ttlSeconds, Clock.systemUTC(), Ticker.systemTicker());
    }

    /**
     * Constructs a new instance.
     *
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

    /**
     * 可变 gauge 条目：存活期间只在其上加减，避免频繁替换缓存条目。
     */
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
