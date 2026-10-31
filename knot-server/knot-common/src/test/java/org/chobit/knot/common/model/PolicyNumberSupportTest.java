package org.chobit.knot.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 契约测试：策略配置数值归一的三个分支。
 *
 * <p>⚠ 这些用例在收口前<b>够不着</b>——原实现是 {@code EjectionPolicy} / {@code RetryPolicy}
 * 各自的 {@code private static}，只能通过策略构造器间接观察，而既有策略测试里
 * <b>一个 NaN / Infinity 用例都没有</b>。收口把实现变成同包可见的类，才第一次能直测。</p>
 */
class PolicyNumberSupportTest {

    @Test
    @DisplayName("整型：null 取缺省值，越界钳到边界，区间内原样")
    void clampIntFillsFallbackAndBounds() {
        assertEquals(3, PolicyNumberSupport.clampInt(null, 3, 1, 5));
        assertEquals(1, PolicyNumberSupport.clampInt(0, 3, 1, 5));
        assertEquals(5, PolicyNumberSupport.clampInt(9, 3, 1, 5));
        assertEquals(4, PolicyNumberSupport.clampInt(4, 3, 1, 5));
    }

    @Test
    @DisplayName("整型：下限可以是运行期算出的变量（摘除策略里 maxCooldownMs 的下限）")
    void clampIntAcceptsRuntimeLowerBound() {
        int cooldownMs = 60_000;
        assertEquals(cooldownMs, PolicyNumberSupport.clampInt(1_000, 300_000, cooldownMs, 600_000));
    }

    @Test
    @DisplayName("浮点：NaN 与正负无穷回落缺省值，不能穿到钳位结果里")
    void clampDoubleRejectsNaNAndInfinity() {
        assertEquals(1.5, PolicyNumberSupport.clampDouble(null, 1.5, 1.0, 2.0));
        assertEquals(1.5, PolicyNumberSupport.clampDouble(Double.NaN, 1.5, 1.0, 2.0));
        assertEquals(1.5, PolicyNumberSupport.clampDouble(Double.POSITIVE_INFINITY, 1.5, 1.0, 2.0));
        assertEquals(1.5, PolicyNumberSupport.clampDouble(Double.NEGATIVE_INFINITY, 1.5, 1.0, 2.0));
    }

    @Test
    @DisplayName("浮点：正常值按区间钳位，区间内原样")
    void clampDoubleBoundsNormalValues() {
        assertEquals(2.0, PolicyNumberSupport.clampDouble(3.0, 1.5, 1.0, 2.0));
        assertEquals(1.0, PolicyNumberSupport.clampDouble(0.5, 1.5, 1.0, 2.0));
        assertEquals(1.8, PolicyNumberSupport.clampDouble(1.8, 1.5, 1.0, 2.0));
    }

    @Test
    @DisplayName("小数位收敛：四舍五入到 1 位，2 位小数不再落库")
    void roundsToOneDecimal() {
        assertEquals(2.7, PolicyNumberSupport.roundToOneDecimal(2.67));
        assertEquals(2.7, PolicyNumberSupport.roundToOneDecimal(2.66));
        assertEquals(1.0, PolicyNumberSupport.roundToOneDecimal(1.0));
    }
}
