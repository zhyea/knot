package org.chobit.knot.gateway.routing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一致性 hash 环：同键稳定、分布均匀、成员变更只影响环上相邻段。
 */
class ConsistentHashRingTest {

    private static List<String> candidates(int count) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add("model-" + i);
        }
        return result;
    }

    @Test
    void sameKeyIsStableAcrossCalls() {
        ConsistentHashRing ring = ConsistentHashRing.of(candidates(5));
        String first = ring.select("session-1");
        for (int i = 0; i < 50; i++) {
            assertEquals(first, ring.select("session-1"), "同一键必须恒定命中同一候选");
        }
    }

    @Test
    void selectedAlwaysBelongsToCandidates() {
        List<String> nodes = candidates(7);
        ConsistentHashRing ring = ConsistentHashRing.of(nodes);
        for (int i = 0; i < 500; i++) {
            assertTrue(nodes.contains(ring.select("key-" + i)));
        }
    }

    @Test
    void emptyRingSelectsNothing() {
        ConsistentHashRing ring = ConsistentHashRing.of(List.of());
        assertNull(ring.select("session-1"));
        assertNull(ConsistentHashRing.of(null).select("session-1"));
    }

    @Test
    void blankCandidatesAreSkipped() {
        // List.of 不接受 null，这里刻意传 null + 空白验证建环时被跳过
        ConsistentHashRing ring = ConsistentHashRing.of(Arrays.asList("  ", null, "model-a"));
        assertEquals("model-a", ring.select("any"));
    }

    @Test
    void singleCandidateAlwaysWins() {
        ConsistentHashRing ring = ConsistentHashRing.of(List.of("model-a"));
        assertEquals("model-a", ring.select("session-1"));
        assertEquals("model-a", ring.select("session-2"));
    }

    @Test
    void removingOneMemberKeepsMostKeysStable() {
        List<String> before = candidates(10);
        ConsistentHashRing first = ConsistentHashRing.of(before);

        Map<String, String> mapping = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "session-" + i;
            mapping.put(key, first.select(key));
        }

        List<String> after = new ArrayList<>(before);
        after.remove("model-3");
        ConsistentHashRing second = ConsistentHashRing.of(after);

        int unchanged = 0;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getValue().equals(second.select(entry.getKey()))) {
                unchanged++;
            }
        }
        // 一致性 hash 的核心收益：去掉 1/10 成员，绝大多数键的命中不变（hash % n 下会近乎全变）
        assertTrue(unchanged > 800, "成员变更后应保持大多数键命中不变，实际 " + unchanged + "/1000");
    }

    @Test
    void keysSpreadOverAllCandidates() {
        List<String> nodes = candidates(5);
        ConsistentHashRing ring = ConsistentHashRing.of(nodes);
        Map<String, Integer> hits = new HashMap<>();
        for (String node : nodes) {
            hits.put(node, 0);
        }
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            String node = ring.select("session-" + i);
            hits.merge(node, 1, Integer::sum);
        }
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, Integer> entry : hits.entrySet()) {
            double ratio = entry.getValue() / (double) total;
            assertTrue(ratio > 0.10 && ratio < 0.30,
                    "分布应大致均匀，实际 " + entry.getKey() + "=" + ratio);
            if (entry.getValue() > 0) {
                used.add(entry.getKey());
            }
        }
        assertEquals(nodes.size(), used.size(), "所有候选都应分到键");
    }

    @Test
    void md5LongIsDeterministicAndSigned() {
        assertEquals(ConsistentHashRing.md5Long("abc"), ConsistentHashRing.md5Long("abc"));
        assertTrue(ConsistentHashRing.md5Long("abc") != ConsistentHashRing.md5Long("abd"));
    }
}
