package org.chobit.knot.gateway.traffic;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.QuotaPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 限额维度：与 {@code kb_quota_policies} 的 {@code max_tokens} / {@code cost_limit} 一一对应。
 * 两者都按策略指定的 {@link org.chobit.knot.common.constants.enums.QuotaWindowEnum 统计窗口}累计、窗口结束清零，且只在请求成功后才记账——上游失败与 failover 重试不消耗额度。
 * 成本维度以 1e-12 为单位存成 {@code long} 整数计数；12 位缩放对应的金额上界约 9.2e6（920 万），超出由 {@link #toScaledAmount(BigDecimal)} 显式报错，不静默溢出。
 */
public enum QuotaDimension {

    TOKENS("TOKENS"),
    COST("COST");

    /**
     * 成本的计数单位：1 元 = 10^12 计数。
     * 这是计数器自己的截断口径，与对外金额字符串的小数位、与成本上限的配置精度（4 位小数）都无关。
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
     * 成本维度缺币种时视为未配置——不做汇率换算，币种不明就不累加。
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
     * 用 {@code movePointRight(COST_SCALE)} 乘 10^12，再 {@code setScale(0, HALF_UP)} 四舍五入收敛到整数计数，不用 {@code setScale(12, …)}（它只改小数位数）。
     * 超过 {@code long} 可表示范围（金额 > 约 920 万）抛 {@link BusinessException}，不静默溢出。
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
