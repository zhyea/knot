package org.chobit.knot.common.model;

/**
 * 策略配置的数值归一：null 取缺省值、越界钳位、小数位收敛。
 *
 * <p>⚠ <b>为什么单独一个类</b>：{@link EjectionPolicy} 与 {@link RetryPolicy} 的紧凑构造器里
 * 曾各存一份<b>逐字相同</b>的 {@code clampInt} / {@code clampDouble} / {@code roundToOneDecimal}。
 * 跨类逐字重复的私有实现是<b>归属错误</b>的信号——正确改法是两边一起改，而各存一份时
 * 改一边就是静默分叉（典型后果：摘除策略与重试策略对「倍率 1.0」的下限口径悄悄不一致）。</p>
 *
 * <p>⚠ <b>只包内可见</b>：三个方法都只在 {@code common.model} 的策略 record 里用，
 * 不开成对外契约。别为了「将来可能有别人要用」加 {@code public}——那会把实现细节
 * 提成别人可以依赖的接口，之后再想内联就动不了了。</p>
 *
 * <p>⚠ <b>不叫 {@code NumberUtils}</b>：§4.4 禁止 {@code Util} 一类泛化主干名，
 * 且本类只服务「策略配置取值」这一件事，不承接任意数值计算。</p>
 */
final class PolicyNumberSupport {

    private PolicyNumberSupport() {
    }

    /**
     * 整型归一：null 取缺省值，其余钳到 {@code [min, max]}。
     *
     * <p>注意 {@code min} 可以是<b>运行期算出来的</b>（如摘除策略里 {@code maxCooldownMs}
     * 的下限取归一后的 {@code cooldownMs}），不一定是编译期常量。</p>
     */
    static int clampInt(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 浮点归一：null / NaN / 无穷取缺省值，其余钳到 {@code [min, max]}。
     *
     * <p>⭐ NaN 与无穷必须单独判：{@code Math.min/max} 遇 NaN 会<b>把 NaN 原样传下去</b>，
     * 钳位看着执行了、结果仍是 NaN，落库后表现为「配置了但策略不生效」。
     * JSON 里写 {@code 1e999} 就能造出 Infinity。</p>
     */
    static double clampDouble(Double value, double fallback, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 小数位收敛：四舍五入到 1 位，避免 {@code 2.67} 这类过长小数污染配置与回显。
     */
    static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
