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
        assertEquals(9, values.length);
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
            assertNotNull(mode.defaultItemType(), "no default item type for " + mode.code());
            assertFalse(mode.defaultItemType().isBlank(), "blank default item type for " + mode.code());
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
    void fromCodeIsCaseInsensitiveAndNullSafe() {
        assertEquals(BillingModeEnum.TOKEN, BillingModeEnum.fromCode(" token "));
        assertEquals(BillingModeEnum.IMAGE, BillingModeEnum.fromCode("IMAGE"));
        assertEquals(null, BillingModeEnum.fromCode(null));
        assertEquals(null, BillingModeEnum.fromCode("UNKNOWN"));
    }
}
