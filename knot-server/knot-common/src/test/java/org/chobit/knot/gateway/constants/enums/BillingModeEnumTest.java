package org.chobit.knot.gateway.constants.enums;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：BillingModeEnum 是「模式 -> 单位/价格项」的单一来源。
 * 前端 unitsByMode / defaultsByMode 已删除，后端 validateModeAndUnit 依赖这里的定义，
 * 改动取值必须同步考虑前端与既有数据。
 */
class BillingModeEnumTest {

    @Test
    void containsNineModesInStableOrder() {
        BillingModeEnum[] values = BillingModeEnum.values();
        assertEquals(8, values.length);
        assertEquals("TOKEN", values[0].code());
        assertEquals("CUSTOM", values[values.length - 1].code());

        Set<String> codes = new HashSet<>();
        for (BillingModeEnum mode : values) {
            assertTrue(codes.add(mode.code()), "duplicated billing mode code: " + mode.code());
        }
    }

    @Test
    void everyModeHasNonEmptyUnitsAndConsistentDefaults() {
        for (BillingModeEnum mode : BillingModeEnum.values()) {
            assertFalse(mode.supportedUnits().isEmpty(), "no supported units for " + mode.code());
            assertTrue(mode.supportedUnits().contains(mode.defaultUnit()),
                    "default unit not in supported units for " + mode.code());
        }
    }

    @Test
    void supportsUnitMatchesDeclaredUnits() {
        assertTrue(BillingModeEnum.TOKEN.supportsUnit("1K_TOKENS"));
        assertTrue(BillingModeEnum.TOKEN.supportsUnit("PER_TOKEN"));
        assertFalse(BillingModeEnum.TOKEN.supportsUnit("PER_IMAGE"));
        assertFalse(BillingModeEnum.REQUEST.supportsUnit("1K_TOKENS"));
        assertTrue(BillingModeEnum.CUSTOM.supportsUnit("PER_SECOND"));
    }

    @Test
    void audioBillsPerSecondAfterPerMinuteRetirement() {
        // PER_MINUTE（唯一的非 10 的幂基数）于 2026-10-17 退役，音频与视频统一按秒计费。
        // 若未来恢复按分钟，须先在配置期把单价折成每秒价，详见 BillingUnitEnumTest 的不变式。
        assertEquals(BillingUnitEnum.PER_SECOND, BillingModeEnum.AUDIO.defaultUnit());
        assertEquals(1, BillingModeEnum.AUDIO.supportedUnits().size());
        assertFalse(BillingModeEnum.AUDIO.supportsUnit("PER_MINUTE"));
    }

    @Test
    void fromCodeIsCaseInsensitiveAndNullSafe() {
        assertEquals(BillingModeEnum.TOKEN, BillingModeEnum.fromCode(" token "));
        assertEquals(BillingModeEnum.IMAGE, BillingModeEnum.fromCode("IMAGE"));
        assertEquals(null, BillingModeEnum.fromCode(null));
        assertEquals(null, BillingModeEnum.fromCode("UNKNOWN"));
    }
}
