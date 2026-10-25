package org.chobit.knot.common.constants.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RouteTargetTypeEnum} 单测。
 *
 * <p>该枚举本来就存在并已通过 {@code /api/enum-options} 下发前端（gateway 侧 {@code RoutingResolver}
 * 早已在用），缺的是管理面写入口的归一。本次补的三个静态方法钉住同一件事：
 * <b>「空白/未知」的处置必须由调用方显式表态</b>，枚举自己既不静默兜底、也不抛错。</p>
 */
class RouteTargetTypeEnumTest {

    @Test
    @DisplayName("fromCode：大小写不敏感，空白与未知一律 null")
    void fromCodeIsCaseInsensitiveAndNullOnUnknown() {
        assertSame(RouteTargetTypeEnum.MODEL, RouteTargetTypeEnum.fromCode("MODEL"));
        assertSame(RouteTargetTypeEnum.MODEL, RouteTargetTypeEnum.fromCode("  model  "));
        assertSame(RouteTargetTypeEnum.MODEL_POOL, RouteTargetTypeEnum.fromCode("model_pool"));
        assertNull(RouteTargetTypeEnum.fromCode(null));
        assertNull(RouteTargetTypeEnum.fromCode(""));
        assertNull(RouteTargetTypeEnum.fromCode("   "));
        assertNull(RouteTargetTypeEnum.fromCode("PROVIDER"));
    }

    @Test
    @DisplayName("ofOrDefault：未知/空白回退到调用方指定的类型，而不是枚举内定默认")
    void ofOrDefaultFallsBackToCallerChoice() {
        assertSame(RouteTargetTypeEnum.MODEL_POOL,
                RouteTargetTypeEnum.ofOrDefault("MODEL_POOL", RouteTargetTypeEnum.MODEL));
        assertSame(RouteTargetTypeEnum.MODEL,
                RouteTargetTypeEnum.ofOrDefault(null, RouteTargetTypeEnum.MODEL));
        // 同一个未知值，不同调用方可以选不同兜底：写入口宽松（MODEL），别处也能要求严格
        assertSame(RouteTargetTypeEnum.MODEL_POOL,
                RouteTargetTypeEnum.ofOrDefault("PROVIDER", RouteTargetTypeEnum.MODEL_POOL));
    }

    @Test
    @DisplayName("matches：只比 code，不认 null，也不做前缀/模糊匹配")
    void matchesComparesCodeOnly() {
        assertTrue(RouteTargetTypeEnum.MODEL_POOL.matches("model_pool"));
        assertTrue(RouteTargetTypeEnum.MODEL.matches(" MODEL "));
        assertFalse(RouteTargetTypeEnum.MODEL.matches(null));
        assertFalse(RouteTargetTypeEnum.MODEL.matches("MODEL_POOL"));
        assertFalse(RouteTargetTypeEnum.MODEL.matches("MOD"));
    }

    @Test
    @DisplayName("code() 与 label() 是落库值与展示名，两者不可互换")
    void codeAndLabelAreDistinct() {
        assertEquals("MODEL", RouteTargetTypeEnum.MODEL.code());
        assertEquals("模型", RouteTargetTypeEnum.MODEL.label());
        assertEquals("MODEL_POOL", RouteTargetTypeEnum.MODEL_POOL.code());
        assertEquals("模型池", RouteTargetTypeEnum.MODEL_POOL.label());
    }
}
