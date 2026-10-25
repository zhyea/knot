package org.chobit.knot.gateway.adapter.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * payload Map 通用读写原语的契约测试。
 *
 * <p>锁定两条易腐化语义：{@code firstPresent} 认任意非 null（与 {@code MapNumberUtils.firstLong}
 * 只认正数刻意不同——前者回答「给没给」，后者回答「给了多少」）；{@code putLong} 负值钳 0。</p>
 */
class PayloadMapSupportTest {

    @Test
    void castShouldPassSameMapInstanceThrough() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("a", 1);
        assertSame(source, PayloadMapSupport.cast(source));
    }

    @Test
    void nestedShouldReturnInnerMapWhenBothLayersAreObjects() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("role", "assistant");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("output", Map.of("message", inner));

        assertEquals(inner, PayloadMapSupport.nested(body, "output", "message"));
    }

    @Test
    void nestedShouldReturnNullWhenAnyLayerMissingOrNotObject() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("output", "not-an-object");
        assertNull(PayloadMapSupport.nested(body, "output", "message"));
        assertNull(PayloadMapSupport.nested(body, "missing", "message"));
        assertNull(PayloadMapSupport.nested(new LinkedHashMap<>(), "output", "message"));
    }

    @Test
    void firstPresentShouldTakeFirstNonNullIncludingZeroAndEmpty() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("zero", 0);
        source.put("empty", List.of());
        source.put("later", "x");

        // 0 / 空集合都算「给了」——与 MapNumberUtils.firstLong 只认正数不同
        assertEquals(0, ((Number) PayloadMapSupport.firstPresent(source, "zero", "later")).intValue());
        assertEquals(List.of(), PayloadMapSupport.firstPresent(source, "missing", "empty"));
        assertNull(PayloadMapSupport.firstPresent(source, "missing", "alsoMissing"));
    }

    @Test
    void stringOfShouldMapNullToEmptyString() {
        assertEquals("", PayloadMapSupport.stringOf(null));
        assertEquals("v", PayloadMapSupport.stringOf("v"));
        assertEquals("12", PayloadMapSupport.stringOf(12));
    }

    @Test
    void textShouldReadTrimmedStringOrNull() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("region", "  us-east-1  ");
        source.put("blank", "   ");
        // null map / 空白键 / 缺失键 → null；取值 trim；空白值 → null（「空白 = 未配置」）
        assertNull(PayloadMapSupport.text(null, "region"));
        assertNull(PayloadMapSupport.text(source, "  "));
        assertNull(PayloadMapSupport.text(source, "missing"));
        assertNull(PayloadMapSupport.text(source, "blank"));
        assertEquals("us-east-1", PayloadMapSupport.text(source, "region"));
    }

    @Test
    void putLongShouldClampNegativeToZero() {
        Map<String, Object> target = new LinkedHashMap<>();
        PayloadMapSupport.putLong(target, "negative", -5);
        PayloadMapSupport.putLong(target, "normal", 7);
        assertEquals(0L, ((Number) target.get("negative")).longValue());
        assertEquals(7L, ((Number) target.get("normal")).longValue());
    }
}
