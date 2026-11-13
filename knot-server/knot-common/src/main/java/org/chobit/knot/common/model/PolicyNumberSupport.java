package org.chobit.knot.common.model;

/**
 * 策略配置的数值归一：{@code null} 取缺省值、越界钳位、小数位收敛。
 *
 * 只包内可见，仅服务 {@code common.model} 下策略 record 的取值，不做通用数值工具。
 */
final class PolicyNumberSupport {

    private PolicyNumberSupport() {
    }

    /**
     * 整型归一：{@code null} 取缺省值，其余钳到 {@code [min, max]}。{@code min} 可为运行期算出的值，不一定是编译期常量。
     */
    static int clampInt(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 浮点归一：{@code null} / NaN / 无穷取缺省值，其余钳到 {@code [min, max]}；
     * NaN 与无穷单独判定，不经 {@code Math.min/max} 传递。
     */
    static double clampDouble(Double value, double fallback, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 小数位收敛：四舍五入到 1 位小数。
     */
    static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
