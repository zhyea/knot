package org.chobit.knot.gateway.traffic;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineTrafficCounterStoreTest {

    private static final long T0 = 1_700_000_000_000L;

    @Test
    void accumulatesOnTheSameKey() {
        CaffeineTrafficCounterStore store = new CaffeineTrafficCounterStore(100L);

        long expireAt = T0 + 60_000L;
        assertEquals(1L, store.addAndGet("rl:MODEL:7:MINUTE:1", 1L, expireAt));
        assertEquals(3L, store.addAndGet("rl:MODEL:7:MINUTE:1", 2L, expireAt));
        assertEquals(3L, store.get("rl:MODEL:7:MINUTE:1"));
        assertEquals(0L, store.get("rl:MODEL:7:MINUTE:2"));
    }

    @Test
    void dropsCounterAfterWindowEnd() {
        AtomicLong millis = new AtomicLong(T0);
        AtomicLong ticks = new AtomicLong(0L);
        CaffeineTrafficCounterStore store =
                new CaffeineTrafficCounterStore(100L, tickingClock(millis), (Ticker) ticks::get);

        store.addAndGet("rl:MODEL:7:SECOND:1", 1L, T0 + 1_000L);
        assertEquals(1L, store.get("rl:MODEL:7:SECOND:1"));

        advance(millis, ticks, 2_000L);
        assertEquals(0L, store.get("rl:MODEL:7:SECOND:1"));
    }

    @Test
    void extendsTtlOnSubsequentWrites() {
        AtomicLong millis = new AtomicLong(T0);
        AtomicLong ticks = new AtomicLong(0L);
        CaffeineTrafficCounterStore store =
                new CaffeineTrafficCounterStore(100L, tickingClock(millis), (Ticker) ticks::get);

        String key = "qt:APP:3:TOTAL_TOKENS:ALL";
        store.addAndGet(key, 100L, T0 + 10_000L);
        advance(millis, ticks, 8_000L);
        // 续期后再累加：窗口未过，计数继续累积而不是从 0 重新开始
        assertEquals(150L, store.addAndGet(key, 50L, T0 + 18_000L));
    }

    @Test
    void evictsWhenMaximumSizeExceeded() {
        CaffeineTrafficCounterStore store = new CaffeineTrafficCounterStore(2L);
        long expireAt = T0 + 60_000L;
        for (int i = 0; i < 200; i++) {
            store.addAndGet("k" + i, 1L, expireAt);
        }

        long alive = 0L;
        for (int i = 0; i < 200; i++) {
            if (store.get("k" + i) > 0L) {
                alive++;
            }
        }
        assertTrue(alive > 0L && alive <= 2L, "存活条目应被 maximumSize 限制在 2 以内，实际 " + alive);
    }

    private static void advance(AtomicLong millis, AtomicLong ticks, long deltaMillis) {
        millis.addAndGet(deltaMillis);
        ticks.addAndGet(TimeUnit.MILLISECONDS.toNanos(deltaMillis));
    }

    private static Clock tickingClock(AtomicLong millis) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }
        };
    }
}
