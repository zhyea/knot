package org.chobit.knot.gateway.traffic;

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
 * <p>成本维度以「纳元」（1e-8）为单位存成整数计数：与计费侧口径对齐——
 * {@code AbstractBillingModeCalculator.cost} 产出的成本就是 {@code setScale(8, HALF_UP)}，
 * 缩放因子必须一致，否则小额成本（单价 1e-6 / 1e-8 量级）会被截断成 0，
 * 表现为「明明花超了却一直不超限」。用 {@code long} 缩放值累加既避开浮点误差，
 * 也与 Redis {@code INCRBY} 的语义天然契合。</p>
 *
 * <p><b>配置精度与计数精度是两回事，别一起改</b>：用户配置成本上限只要求 4 位小数
 * （{@code cost_limit DECIMAL(18,4)}、前端 {@code precision=4}），那是「预算能配到多细」的诉求；
 * 但内部计数必须保留 8 位——若把本缩放因子降到 4，单次 1e-8 的成本会截断成 0，
 * 额度永远不累加，直接退回「明明花超了却一直不超限」。</p>
 *
 * <p>取值上界：{@code long} 计数约 9.2e18，对应金额上限约 9.2e10（百亿），
 * 远超任何配额场景，无需额外保护。</p>
 */
public enum QuotaDimension {

    TOKENS("TOKENS"),
    COST("COST");

    /**
     * 成本的计数单位：1 元 = 10^8 计数（纳元），与计费侧 {@code setScale(8, HALF_UP)} 对齐。
     *
     * <p><b>不要因为成本上限只配 4 位小数就把它降到 4</b>——那样单次 1e-8 的成本
     * 会截断成 0，额度永远不累加。</p>
     */
    public static final int COST_SCALE = 8;

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
     * 该维度在给定策略下的限额（成本维度已换算成纳元计数）；{@code <= 0} 表示这一维度不限。
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
     * 金额 → 纳元计数（金额 × 10^8）。
     *
     * <p>注意是<b>乘</b> 10^8 而不是 {@code setScale(8, …)}：{@code setScale} 只改小数位数，
     * {@code new BigDecimal("10.00").setScale(8, HALF_UP)} 仍是 {@code 10.00000000}（{@code longValue}=10），
     * 不是 10^8 —— 那样写会把 6.00 这种正常金额当成 6 纳元，额度完全失效。</p>
     *
     * <p>{@code setScale(0, HALF_UP)} 收尾是为了让超过 8 位小数的输入按四舍五入收敛到整数计数，
     * 而不是静默截断（截断会让「已超限」判定偏松）。</p>
     */
    public static long toScaledAmount(BigDecimal amount) {
        if (amount == null) {
            return 0L;
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
