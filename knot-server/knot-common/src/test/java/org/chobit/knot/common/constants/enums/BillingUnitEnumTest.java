package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：BillingUnitEnum 的基数不变式——所有 {@code unitSize} 都是 10 的幂。
 *
 * <p>这是「金额 = 用量 × 单价 ÷ 基数 恒定除尽、不终止除法不可能出现」的前提。
 * PER_MINUTE（60 秒）曾是唯一的非 10 的幂基数，会逼出「秒 ÷ 60」的不终止除法，
 * 2026-10-17 退役（音频与视频统一按秒计费）。新增单位若引入非 10 的幂基数
 * （如按小时 = 3600），本测试立即失败，强制先落地「配置期折算成基础单位价」再放开，
 * 而不是让 60 / 3600 进金额公式。</p>
 */
class BillingUnitEnumTest {

    @Test
    void everyUnitSizeIsPowerOfTen() {
        for (BillingUnitEnum unit : BillingUnitEnum.values()) {
            int size = unit.unitSize();
            assertTrue(isPowerOfTen(size),
                    "unitSize must be a power of ten: " + unit.code() + " -> " + size);
        }
    }

    @Test
    void perMinuteIsRetired() {
        assertNull(BillingUnitEnum.fromCode("PER_MINUTE"), "PER_MINUTE 已于 2026-10-17 退役");
        assertThrows(BusinessException.class, () -> BillingUnitEnum.unitSizeOf("PER_MINUTE"));
        assertThrows(BusinessException.class,
                () -> BillingUnitEnum.requireCode("PER_MINUTE", "unsupported billing unit"));
    }

    private static boolean isPowerOfTen(int value) {
        int current = value;
        while (current > 1 && current % 10 == 0) {
            current /= 10;
        }
        return current == 1;
    }
}
