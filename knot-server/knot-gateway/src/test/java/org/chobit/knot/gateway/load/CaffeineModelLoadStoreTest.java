package org.chobit.knot.gateway.load;

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

class CaffeineModelLoadStoreTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String KEY = "knot:load:MODEL:gpt-4o";

    @Test
    void enterAndLeaveKeepsInflightBalanced() {
        CaffeineModelLoadStore store = new CaffeineModelLoadStore(100L, 300L);

        assertEquals(1L, store.enter(KEY));
        assertEquals(2L, store.enter(KEY));
        assertEquals(2L, store.inflight(KEY));
        assertEquals(1L, store.leave(KEY));
        assertEquals(0L, store.leave(KEY));
        // 归零即删条目：空闲模型不占堆
        assertEquals(0L, store.inflight(KEY));
    }

    @Test
    void leaveOnUnknownKeyNeverGoesNegative() {
        CaffeineModelLoadStore store = new CaffeineModelLoadStore(100L, 300L);

        // 从未 enter（或条目已过期）时的迟到递减：不得压出负在途，
        // 否则该模型会被当成「最空闲」吸走全部流量
        assertEquals(0L, store.leave(KEY));
        assertEquals(0L, store.inflight(KEY));

        store.enter(KEY);
        assertEquals(0L, store.leave(KEY));
        assertEquals(0L, store.leave(KEY));
        assertEquals(0L, store.inflight(KEY));
    }

    @Test
    void dropsInflightAfterTtl() {
        AtomicLong millis = new AtomicLong(T0);
        AtomicLong ticks = new AtomicLong(0L);
        CaffeineModelLoadStore store =
                new CaffeineModelLoadStore(100L, 10L, tickingClock(millis), (Ticker) ticks::get);

        store.enter(KEY);
        assertEquals(1L, store.inflight(KEY));

        // TTL 内仍未过期
        advance(millis, ticks, 9_000L);
        assertEquals(1L, store.inflight(KEY));

        // 越过 TTL：泄漏的计数随条目一起消失，不会永久化
        advance(millis, ticks, 2_000L);
        assertEquals(0L, store.inflight(KEY));
        assertEquals(0L, store.leave(KEY));
    }

    @Test
    void enterExtendsTtlButLeaveDoesNot() {
        AtomicLong millis = new AtomicLong(T0);
        AtomicLong ticks = new AtomicLong(0L);
        CaffeineModelLoadStore store =
                new CaffeineModelLoadStore(100L, 10L, tickingClock(millis), (Ticker) ticks::get);

        store.enter(KEY);
        advance(millis, ticks, 8_000L);
        // 后续 enter 续期：长请求（含长流式响应）不会中途把条目熬没
        store.enter(KEY);
        advance(millis, ticks, 8_000L);
        assertEquals(2L, store.inflight(KEY));

        // leave 不续期：再越 8s 后条目该过期（距上次 enter 已 16s > 10s）
        store.leave(KEY);
        advance(millis, ticks, 3_000L);
        assertEquals(0L, store.inflight(KEY));
    }

    @Test
    void reportsImplementationId() {
        assertEquals("caffeine", new CaffeineModelLoadStore(10L, 300L).id());
    }

    @Test
    void evictsWhenMaximumSizeExceeded() {
        CaffeineModelLoadStore store = new CaffeineModelLoadStore(2L, 300L);
        for (int i = 0; i < 200; i++) {
            store.enter("k" + i);
        }
        long alive = 0L;
        for (int i = 0; i < 200; i++) {
            if (store.inflight("k" + i) > 0L) {
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
