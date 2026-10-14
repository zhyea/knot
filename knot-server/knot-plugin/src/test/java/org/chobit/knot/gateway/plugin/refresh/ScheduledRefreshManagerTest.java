package org.chobit.knot.gateway.plugin.refresh;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledRefreshManagerTest {

    private static final class CountingRefreshable implements Refreshable {

        private final AtomicInteger count = new AtomicInteger();
        private final Duration interval;
        private final boolean throwing;

        CountingRefreshable(Duration interval, boolean throwing) {
            this.interval = interval;
            this.throwing = throwing;
        }

        @Override
        public void refresh() {
            count.incrementAndGet();
            if (throwing) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        public Duration refreshInterval() {
            return interval;
        }
    }

    @Test
    void schedulesRefreshableAndToleratesFailures() throws Exception {
        CountingRefreshable good = new CountingRefreshable(Duration.ofMillis(30), false);
        CountingRefreshable bad = new CountingRefreshable(Duration.ofMillis(30), true);
        ScheduledRefreshManager manager = new ScheduledRefreshManager(List.of(good, bad));
        manager.start();
        try {
            Thread.sleep(200);
            assertTrue(good.count.get() >= 3, "good refreshable should be scheduled repeatedly");
            assertTrue(bad.count.get() >= 3, "throwing refreshable should keep being scheduled despite failures");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void skipsRefreshableWithNonPositiveInterval() throws Exception {
        CountingRefreshable never = new CountingRefreshable(Duration.ZERO, false);
        ScheduledRefreshManager manager = new ScheduledRefreshManager(List.of(never));
        manager.start();
        try {
            Thread.sleep(150);
            assertEquals(0, never.count.get(), "interval <= 0 should not be scheduled");
        } finally {
            manager.shutdown();
        }
    }
}
