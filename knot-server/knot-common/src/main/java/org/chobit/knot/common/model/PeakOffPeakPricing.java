package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.Pricing;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.chobit.knot.common.model.BillingConfig.PricingPlan;
import org.chobit.knot.common.pricing.HolidayCalendar;
import org.chobit.knot.common.pricing.PeakOffPeakResolver;
import org.chobit.knot.common.pricing.PhaseDecision;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 高低峰价：在模式层产出的基础单价上乘相位倍率，自身不持有价格。
 *
 * <p>价格本体仍完全由 {@link FixedPricing} 的解析链给出（basePrices -&gt; defaultUnitPrice -&gt; fallback），
 * 这里只按 {@link PeakOffPeakResolver} 判定的相位乘系数，因此对所有计费模式是同一套代码；
 * 时间缺失时倍率为 1，结果退化为普通固定价。
 */
record PeakOffPeakPricing(PriceSet basePrices,
                            BigDecimal defaultUnitPrice,
                            Pricing pricing,
                            HolidayCalendar calendar,
                            Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

    private boolean absolute() {
        return BillingConfig.RATE_MODE_ABSOLUTE.equals(BillingConfig.blankToNull(pricing == null ? null : pricing.rateMode()));
    }

    @Override
    public PricingPlanEnum plan() {
        return PricingPlanEnum.PEAK_OFF_PEAK;
    }

    @Override
    public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = PeakOffPeakResolver.resolve(
                pricing, context == null ? null : context.occurredAt(), calendar);
        if (absolute()) {
            return absolutePrice(decision.matchedRule(), kind, fallback);
        }
        BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
        BigDecimal base = fromBase != null ? fromBase : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
        return base == null ? null : base.multiply(decision.multiplier());
    }

    @Override
    public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = PeakOffPeakResolver.resolve(
                pricing, context == null ? null : context.occurredAt(), calendar);
        if (absolute()) {
            // 简单模式单单价：ABSOLUTE 相位价格统一收在 input 槽位
            BillingConfig.PhaseRule rule = decision.matchedRule();
            BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
            return price != null ? price : fallback;
        }
        BigDecimal base = defaultUnitPrice != null ? defaultUnitPrice : fallback;
        return base == null ? null : base.multiply(decision.multiplier());
    }

    private BigDecimal absolutePrice(BillingConfig.PhaseRule rule, PriceKind kind, BigDecimal fallback) {
        if (rule == null || rule.unitPrices() == null) {
            return fallback;
        }
        BigDecimal price = rule.unitPrices().valueOf(kind);
        return price != null ? price : fallback;
    }

    /**
     * 维度价 × 相位倍率。
     *
     * <p>ABSOLUTE 模式下相位自带单价，维度分档价<b>不</b>参与（相位价已是最终价，
     * 再叠维度价会双重计价）；MULTIPLIER 模式下维度价与普通单价一样乘倍率。</p>
     */
    @Override
    public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = PeakOffPeakResolver.resolve(
                pricing, context == null ? null : context.occurredAt(), calendar);
        if (absolute()) {
            BillingConfig.PhaseRule rule = decision.matchedRule();
            BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
            return price != null ? price : fallback;
        }
        BigDecimal matched = BillingConfig.dimensionPrice(candidateKeys, dimensionPrices);
        BigDecimal base = matched != null ? matched : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
        return base == null ? null : base.multiply(decision.multiplier());
    }

    /**
     * 分量价 × 相位倍率；ABSOLUTE 模式下取相位自带价（叠加分量默认价会双重计价）。
     */
    @Override
    public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                            BigDecimal componentDefault,
                                            PricingContext context,
                                            BigDecimal fallback) {
        PhaseDecision decision = PeakOffPeakResolver.resolve(
                pricing, context == null ? null : context.occurredAt(), calendar);
        if (absolute()) {
            BillingConfig.PhaseRule rule = decision.matchedRule();
            BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
            return price != null ? price : fallback;
        }
        BigDecimal matched = BillingConfig.dimensionPrice(candidateKeys, dimensionPrices);
        BigDecimal base = matched != null ? matched
                : (componentDefault != null ? componentDefault
                : (defaultUnitPrice != null ? defaultUnitPrice : fallback));
        return base == null ? null : base.multiply(decision.multiplier());
    }

    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        return null;
    }
}
