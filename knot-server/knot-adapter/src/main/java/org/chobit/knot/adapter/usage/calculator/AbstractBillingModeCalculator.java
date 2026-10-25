package org.chobit.knot.adapter.usage.calculator;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.BillingUnitEnum;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenUsageDetailKind;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;

import java.math.BigDecimal;
import java.math.MathContext;

public abstract class AbstractBillingModeCalculator implements BillingModeCalculator {

    /**
     * 金额除法的精度兜底：{@link BillingUnitEnum#unitSize()} 的基数目前全是 10 的幂
     * （1 / 1e3 / 1e6），「用量 × 单价 ÷ 基数」恒定除尽，本上下文不会实际参与计算。
     * 保留它是为了未来：一旦新增非 10 的幂基数（如按小时 = 3600），除法可能不终止，
     * 无精度上下文的 {@code divide} 会直接抛 {@link ArithmeticException} 打断计费链路。
     *
     * <p>取 128 位十进制：对任何真实单价与用量都等于不设限，远大于「6 位小数单价 ÷ 1e6」
     * 所需的 12 位。PER_MINUTE（60 秒）曾是唯一的非 10 的幂基数，已于 2026-10-17 退役——
     * 按分钟计费必须在配置期把单价折成每秒价，不允许 60 进金额公式。</p>
     */
    private static final MathContext MONEY_DIVISION = MathContext.DECIMAL128;

    protected BillingRuleRef rule(NormalizedUsageContext context) {
        return context.rule();
    }

    /**
     * 金额公式的分母：单位基数。
     *
     * <p>单位归一化一律经 {@link BillingModeEnum#normalizeUnit(String)}：
     * 基数是分母，1K_TOKENS(1000) 与 PER_SECOND(1) 差 1000 倍，
     * 「兜底成 1K_TOKENS」这种写法一旦脱离模式口径就是静默的 1000 倍错价。
     * 各计算器必须传自己的 {@link BillingModeEnum#mode()}，不要用规则列的空值兜底。</p>
     */
    protected int unitSize(BillingRuleRef rule, BillingModeEnum mode) {
        return BillingUnitEnum.unitSizeOf(mode.normalizeUnit(rule == null ? null : rule.unit()));
    }

    protected BigDecimal safeMoney(BigDecimal value) {
        return ObjectUtils.defaultIfNull(value, BigDecimal.ZERO);
    }

    /**
     * 单项金额 = 用量 × 单价 ÷ 单位基数。
     *
     * <p>不再定标 8 位小数：单价常见 6 位小数，除百万量级后需要 12 位以上才不归零，
     * 8 位会把小额真实成本抹成 0（如 1000 × 0.0000015 ÷ 1e6 = 1.5e-9）。</p>
     */
    protected BigDecimal cost(long amount, BigDecimal unitPrice, int unitSize) {
        if (amount <= 0 || unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount)
                .multiply(unitPrice)
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), MONEY_DIVISION);
    }

    protected NormalizedUsageDetail detail(String type,
                                           long tokens,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           int unitSize) {
        return new NormalizedUsageDetail(
                type,
                tokens,
                NormalizedBillingAmount.plainMoney(safeMoney(cost)),
                NormalizedBillingAmount.plainMoney(pricePerMillion(safeMoney(unitPrice), unitSize))
        );
    }

    /** 枚举版重载：明细类型以 {@link TokenUsageDetailKind} 声明，避免调用处散落魔法串 */
    protected NormalizedUsageDetail detail(TokenUsageDetailKind kind,
                                           long tokens,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           int unitSize) {
        return detail(kind.key(), tokens, cost, unitPrice, unitSize);
    }

    /**
     * 明细单价口径统一换算成「每百万单位单价」，与 {@link #cost(long, BigDecimal, int)} 同精度：
     * 单价本身常见 6 位小数，8 位定标同样会把更细的单价抹平，
     * 使自检层按「数量 × 单价 ÷ 1e6」复算时对不上真实成本。
     *
     * <p>返回 BigDecimal 是内部计算口径；对外文本由 {@code detail()} 经
     * {@link NormalizedBillingAmount#plainMoney} 一次收敛，与 cost 同一契约。</p>
     */
    protected BigDecimal pricePerMillion(BigDecimal unitPrice, int unitSize) {
        if (unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return unitPrice
                .multiply(BigDecimal.valueOf(1_000_000L))
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), MONEY_DIVISION);
    }

    protected String normalizeDetailType(String itemType, BillingModeEnum mode) {
        if (StringUtils.isNotBlank(itemType)) {
            return StringUtils.trim(itemType);
        }
        return mode == null ? BillingModeEnum.CUSTOM.code() : mode.code();
    }

    protected long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
    }
}
