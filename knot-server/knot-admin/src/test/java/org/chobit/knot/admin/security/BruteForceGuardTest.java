package org.chobit.knot.admin.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BruteForceGuardTest {

    @Test
    void blocksOnlyAfterThresholdAndResetsOnSuccess() {
        BruteForceGuard guard = new BruteForceGuard(3, Duration.ofSeconds(60));

        assertFalse(guard.isBlocked("alice"));

        guard.recordFailure("alice");
        guard.recordFailure("alice");
        assertFalse(guard.isBlocked("alice"));

        guard.recordFailure("alice");
        assertTrue(guard.isBlocked("alice"));

        guard.reset("alice");
        assertFalse(guard.isBlocked("alice"));
    }

    @Test
    void countersAreKeyScoped() {
        BruteForceGuard guard = new BruteForceGuard(1, Duration.ofSeconds(60));

        guard.recordFailure("ip-a");
        assertTrue(guard.isBlocked("ip-a"));
        assertFalse(guard.isBlocked("ip-b"));
    }
}
