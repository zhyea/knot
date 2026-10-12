package org.chobit.knot.gateway.routing;

import org.chobit.knot.gateway.model.StickyPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 粘性路由：默认关闭、键归一化、同键稳定命中、单候选不适用。
 */
class StickyRoutingTest {

    private static final List<String> CANDIDATES = List.of("m1", "m2", "m3", "m4", "m5");

    private final StickyRouting routing = new StickyRouting();

    private static StickyPolicy enabled() {
        return new StickyPolicy(Boolean.TRUE, StickyPolicy.HEADER_NAME_DEFAULT);
    }

    @Test
    void disabledByDefaultSoPreferredIsNull() {
        assertNull(routing.preferred(CANDIDATES, null, "session-1"));
        assertNull(routing.preferred(CANDIDATES, StickyPolicy.DEFAULT, "session-1"));
        assertNull(routing.preferred(CANDIDATES, new StickyPolicy(Boolean.FALSE, null), "session-1"));
    }

    @Test
    void blankOrTooLongKeySkipsSticky() {
        assertNull(routing.preferred(CANDIDATES, enabled(), null));
        assertNull(routing.preferred(CANDIDATES, enabled(), "   "));
        assertNull(routing.preferred(CANDIDATES, enabled(), "k".repeat(StickyRouting.MAX_KEY_LENGTH + 1)));
    }

    @Test
    void whitespaceAroundKeyIsTrimmed() {
        assertEquals(routing.preferred(CANDIDATES, enabled(), "session-1"),
                routing.preferred(CANDIDATES, enabled(), "  session-1  "));
    }

    @Test
    void sameKeyKeepsSamePreferredModel() {
        String first = routing.preferred(CANDIDATES, enabled(), "session-1");
        assertTrue(CANDIDATES.contains(first));
        for (int i = 0; i < 20; i++) {
            assertEquals(first, routing.preferred(CANDIDATES, enabled(), "session-1"));
        }
    }

    @Test
    void differentKeysSpreadAcrossModels() {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            used.add(routing.preferred(CANDIDATES, enabled(), "session-" + i));
        }
        assertTrue(used.size() >= 3, "多个不同键应分散到不同模型，实际命中 " + used.size() + " 个");
        assertTrue(CANDIDATES.containsAll(used));
    }

    @Test
    void singleCandidateHasNothingToStick() {
        assertNull(routing.preferred(List.of("only-one"), enabled(), "session-1"));
        assertNull(routing.preferred(List.of(), enabled(), "session-1"));
    }

    @Test
    void candidateOrderDoesNotChangeResult() {
        List<String> reversed = new ArrayList<>(CANDIDATES);
        java.util.Collections.reverse(reversed);
        assertEquals(routing.preferred(CANDIDATES, enabled(), "session-7"),
                routing.preferred(reversed, enabled(), "session-7"));
    }

    @Test
    void normalizeKeyRejectsBlankAndTooLong() {
        assertNull(routing.normalizeKey(null));
        assertNull(routing.normalizeKey(""));
        assertNull(routing.normalizeKey("   "));
        assertEquals("abc", routing.normalizeKey(" abc "));
    }
}
