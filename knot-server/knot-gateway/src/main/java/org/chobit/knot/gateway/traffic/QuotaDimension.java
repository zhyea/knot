package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.QuotaPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 限额维度：与 {@code kb_quota_policies} 的 {@code max_tokens} / {@code cost_limit} 一一对应。
 *
 * <p>两者都按策略指定的
 * {@link org.chobit.knot.gateway.constants.enums.QuotaWindowEnum 统计窗口}累计、窗口结束清零，
 * 且都在请求成功后才记账——上游失败与 failover 重试不消耗额度。</p>
 *
 * <p>成本维度以「1e-12」为单位存成整数计数：计费侧的成本字符串已不再限定小数位
 * （见 {@code NormalizedBillingAmount}），计数侧若仍卡在 8 位，6 位小数单价下的极限小额
 * （1 token × 0.000001 ÷ 1e6 = 1e-12）会被抹成 0，表现为「明明花超了却一直不超限」。
 * 用 {@code long} 缩放值累加既避开浮点误差，也与 Redis {@code INCRBY} 的语义天然契合。</p>
 *
 * <p><b>配置精度与计数精度是两回事，别一起改</b>：用户配置成本上限只要求 4 位小数
 * （{@code cost_limit DECIMAL(18,4)}、前端 {@code precision=4}），那是「预算能配到多细」的诉求；
 * 内部计数精度由单次成本的最小量级决定，与配置精度无关。</p>
 *
 * <p>取值上界：{@code long} 计数约 9.2e18，12 位缩放对应金额上限约 9.2e6（920 万）。
 * 超出这个范围由 {@link #toScaledAmount(BigDecimal)} 明确报错，不静默溢出。</p>
 */
public enum QuotaDimension {

    TOKENS("TOKENS"),
    COST("COST");

    /**
     * 成本的计数单位：1 元 = 10^12 计数。
     *
     * <p>这是**计数器自己的**截断口径，与对外金额字符串的小数位无关（后者已不设上限）。
     * 取 12 位是为了兜住计费侧的最小单次成本：6 位小数单价 × 1 token ÷ 1e6 = 1e-12，
     * 8 位缩放会把它抹成 0，配额永远不累加。</p>
     *
     * <p><b>不要因为成本上限只配 4 位小数就把它降到 4</b>——那是配置精度，不是计数精度。</p>
     */
    public static final int COST_SCALE = 12;

    /** 12 位缩放在 long 下可表示的金额上界（约 922 万），超出即拒绝而不是静默溢出 */
    private static final BigDecimal MAX_SCALED_AMOUNT = new BigDecimal("9223372.036854775807");

    /**
     * 全部限额维度。
     */
    public static final List<QuotaDimension> ALL = List.of(TOKENS, COST);

    private final String code;

    QuotaDimension(String code) {
        this.code = code;
    }

    /**
     * 维度标识，用于拼存储键。
     */
    public String code() {
        return code;
    }

    /**
     * 该维度在给定策略下的限额（成本维度已换算成 1e-12 计数）；{@code <= 0} 表示这一维度不限。
     *
     * <p>成本维度缺币种时视为未配置——不做汇率换算，币种不明就不累加。</p>
     */
    public long limitOf(QuotaPolicy policy) {
        if (policy == null) {
            return 0L;
        }
        if (this == TOKENS) {
            return policy.maxTokens();
        }
        if (policy.costLimit() == null || policy.currency() == null || policy.currency().isBlank()) {
            return 0L;
        }
        return toScaledAmount(policy.costLimit());
    }

    /**
     * 金额 → 计数（金额 × 10^12）。
     *
     * <p>注意是<b>乘</b> 10^12 而不是 {@code setScale(12, …)}：{@code setScale} 只改小数位数，
     * {@code new BigDecimal("10.00").setScale(12, HALF_UP)} 仍是 {@code 10.000000000000}（{@code longValue}=10），
     * 不是 10^12 —— 那样写会把 6.00 这种正常金额当成 6 个计数单位，额度完全失效。</p>
     *
     * <p>{@code setScale(0, HALF_UP)} 收尾是为了让超过 12 位小数的输入按四舍五入收敛到整数计数，
     * 而不是静默截断（截断会让「已超限」判定偏松）。</p>
     *
     * <p>超过 long 可表示范围（金额 > 约 920 万）时明确报错：这是配置错误，
     * 静默截断会让配额形同虚设，而 {@code longValueExact()} 裸抛的
     * {@link ArithmeticException} 在热路径上只会变成 500，看不出是配额配大了。</p>
     */
    public static long toScaledAmount(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        if (amount.abs().compareTo(MAX_SCALED_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "cost amount exceeds the countable range of quota dimension: " + amount.toPlainString());
        }
        return amount.movePointRight(COST_SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * 该维度超限时对应的拒绝原因。
     */
    public TrafficRejectReason rejectReason() {
        return this == TOKENS ? TrafficRejectReason.QUOTA_TOKENS : TrafficRejectReason.QUOTA_COST;
    }
}
