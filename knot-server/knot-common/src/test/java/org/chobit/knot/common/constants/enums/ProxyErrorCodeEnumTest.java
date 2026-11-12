package org.chobit.knot.common.constants.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 契约测试：上游错误码是内部编码，{@code name()} 与 {@code code()} 必须一致。
 *
 * <p>一致是 {@code RetryableClassifier} 敢用 {@code switch (枚举)} 的前提——它按枚举分支，
 * 但线上抛的是 {@code code()} 字符串，两者一旦分叉，重试判定就会整体错位。</p>
 */
class ProxyErrorCodeEnumTest {

    @Test
    void codesArePinned() {
        // 逐个钉死：改 code 等于改重试判定的输入，必须让测试先红
        assertEquals(List.of(
                "MISSING_MODEL",
                "MODEL_NOT_FOUND",
                "PROVIDER_NOT_FOUND",
                "API_PROTOCOL_NOT_CONFIGURED",
                "UPSTREAM_ENDPOINT_NOT_CONFIGURED",
                "RATE_LIMIT_EXCEEDED",
                "QUOTA_EXCEEDED",
                "NO_ROUTING_TARGET",
                "UPSTREAM_ERROR",
                "UPSTREAM_EMPTY_RESPONSE"
        ), Arrays.stream(values()).map(ProxyErrorCodeEnum::code).toList());
    }

    @Test
    void nameAndCodeAreIdentical() {
        for (ProxyErrorCodeEnum value : values()) {
            assertEquals(value.name(), value.code(), value.name());
        }
    }

    @Test
    void codesAreUnique() {
        Set<String> unique = new HashSet<>(Arrays.stream(values()).map(ProxyErrorCodeEnum::code).toList());
        assertEquals(values().length, unique.size());
    }

    @Test
    void fromCodeRoundTrips() {
        for (ProxyErrorCodeEnum value : values()) {
            assertSame(value, ProxyErrorCodeEnum.fromCode(value.code()), value.name());
        }
    }

    @Test
    void unknownAndNullReturnNull() {
        assertNull(ProxyErrorCodeEnum.fromCode(null));
        assertNull(ProxyErrorCodeEnum.fromCode(""));
        assertNull(ProxyErrorCodeEnum.fromCode("NOT_A_CODE"));
        // 大小写敏感：code 全大写，小写进来不认（否则等于隐含放宽了一条线上契约）
        assertNull(ProxyErrorCodeEnum.fromCode("upstream_error"));
    }

    private static ProxyErrorCodeEnum[] values() {
        return ProxyErrorCodeEnum.values();
    }
}
