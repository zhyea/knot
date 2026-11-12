package org.chobit.knot.common.constants.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⭐ 错误类型是<b>对外可见的词表</b>：HTTP 响应体的 {@code error.type} 与数仓消息的 {@code error.type}
 * 同源（都取 {@link GatewayErrorTypeEnum#code()}），所以 code 一旦上线就是契约。
 * 这里把 9 个值逐个钉死——改任何一个都等于改对外口径，必须当成一次接口变更来评审。
 *
 * <p>为什么连 {@code fromCode} 一起钉：数仓出口靠它把 {@code type} 投影成 {@code status}，
 * 反查必须与 {@link #code()} 双向一致。只测正向（常量 → code）会漏掉「两个常量写了同一个 code」，
 * 因为正向断言永远自洽。</p>
 */
class GatewayErrorTypeEnumTest {

    @Test
    @DisplayName("⭐ wire code 逐个钉死（改任何一个 = 改对外词表）")
    void codesArePinned() {
        assertEquals("auth_error", GatewayErrorTypeEnum.AUTH_ERROR.code());
        assertEquals("invalid_request_error", GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code());
        assertEquals("rate_limit_error", GatewayErrorTypeEnum.RATE_LIMIT_ERROR.code());
        assertEquals("quota_error", GatewayErrorTypeEnum.QUOTA_ERROR.code());
        assertEquals("moderation_error", GatewayErrorTypeEnum.MODERATION_ERROR.code());
        assertEquals("storage_unavailable", GatewayErrorTypeEnum.STORAGE_UNAVAILABLE.code());
        assertEquals("upstream_error", GatewayErrorTypeEnum.UPSTREAM_ERROR.code());
        assertEquals("timeout_error", GatewayErrorTypeEnum.TIMEOUT_ERROR.code());
        // 2026-11-12 新增：兜底 500 不再复用 upstream_error
        assertEquals("internal_error", GatewayErrorTypeEnum.INTERNAL_ERROR.code());
    }

    @Test
    @DisplayName("code 不得重复（重复会让 fromCode 静默只返回第一个）")
    void codesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (GatewayErrorTypeEnum value : GatewayErrorTypeEnum.values()) {
            assertTrue(seen.add(value.code()), "重复的 code: " + value.code());
        }
        assertEquals(GatewayErrorTypeEnum.values().length, seen.size());
    }

    @Test
    @DisplayName("fromCode 与 code() 双向一致：每个值都必须能查回自己")
    void fromCodeRoundTrips() {
        for (GatewayErrorTypeEnum value : GatewayErrorTypeEnum.values()) {
            assertSame(value, GatewayErrorTypeEnum.fromCode(value.code()), "反查失败: " + value.code());
        }
    }

    @Test
    @DisplayName("未知 code 与 null 返回 null，由调用方决定兜底语义")
    void unknownAndNullReturnNull() {
        assertNull(GatewayErrorTypeEnum.fromCode(null));
        assertNull(GatewayErrorTypeEnum.fromCode(""));
        assertNull(GatewayErrorTypeEnum.fromCode("unknown_error"));
        // 大小写不做宽容：wire 上只认小写，宽松匹配会让「拼错」被静默吞掉
        assertNull(GatewayErrorTypeEnum.fromCode("UPSTREAM_ERROR"));
    }
}
