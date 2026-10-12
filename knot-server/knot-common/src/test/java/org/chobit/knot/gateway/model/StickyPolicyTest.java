package org.chobit.knot.gateway.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：池粘性路由策略的默认值（默认关闭）、容错解析与头名归一化。
 */
class StickyPolicyTest {

    @Test
    void nullOrBlankFallsBackToDefault() {
        assertEquals(StickyPolicy.DEFAULT, StickyPolicy.parse(null));
        assertEquals(StickyPolicy.DEFAULT, StickyPolicy.parse(""));
        assertEquals(StickyPolicy.DEFAULT, StickyPolicy.parse("   "));
    }

    @Test
    void brokenJsonFallsBackToDefault() {
        assertEquals(StickyPolicy.DEFAULT, StickyPolicy.parse("{not a json"));
    }

    @Test
    void defaultIsDisabledWithDefaultHeader() {
        StickyPolicy policy = StickyPolicy.DEFAULT;
        assertFalse(policy.stickied());
        assertEquals(StickyPolicy.HEADER_NAME_DEFAULT, policy.headerName());
        assertEquals("X-Knot-Sticky-Key", policy.headerName());
    }

    @Test
    void compactConstructorFillsDefaultsAndNormalizesHeader() {
        assertEquals(StickyPolicy.HEADER_NAME_DEFAULT, new StickyPolicy(null, null).headerName());
        assertFalse(new StickyPolicy(null, null).stickied());
        assertEquals(StickyPolicy.HEADER_NAME_DEFAULT, new StickyPolicy(true, "   ").headerName());
        assertEquals("X-Session-Id", new StickyPolicy(true, "  X-Session-Id  ").headerName());
        assertTrue(new StickyPolicy(true, "X-Session-Id").stickied());
    }

    @Test
    void tooLongHeaderFallsBackToDefault() {
        String tooLong = "H".repeat(StickyPolicy.HEADER_NAME_LIMIT + 1);
        assertEquals(StickyPolicy.HEADER_NAME_DEFAULT, new StickyPolicy(true, tooLong).headerName());
        // 恰好等于上限是合法输入，原样保留
        String atLimit = "H".repeat(StickyPolicy.HEADER_NAME_LIMIT);
        assertEquals(atLimit, new StickyPolicy(true, atLimit).headerName());
    }

    @Test
    void jsonRoundTripKeepsNormalizedValues() {
        StickyPolicy policy = new StickyPolicy(true, "X-Session-Id");
        String json = StickyPolicy.serialize(policy);
        StickyPolicy parsed = StickyPolicy.parse(json);
        assertTrue(parsed.stickied());
        assertEquals("X-Session-Id", parsed.headerName());
    }

    @Test
    void serializeNullWritesNull() {
        assertNull(StickyPolicy.serialize(null));
    }
}
