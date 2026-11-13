package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.BillingConfig.PhaseRule;
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
 * 高低峰价：在模式层产出的基础单价上乘相位倍率，自身不持有价格；时间缺失时倍率为 1，退化为普通固定价。
 *
 * {@code pricing.rateMode} 决定两条互斥路径：{@code MULTIPLIER} 下基础价 / 维度分档价 / 分量价都先算出再统一乘倍率；
 * {@code ABSOLUTE} 下相位自带的 {@code unitPrices} 即最终价，不再叠维度分档价与分量默认价，
 * 简单模式的单单价统一收在 {@code unitPrices.input} 槽位。
 */
record PeakOffPeakPricePolicy(PriceSet basePrices,
                              BigDecimal defaultUnitPrice,
                              Pricing pricing,
                              HolidayCalendar calendar,
                              Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

    @Override
    public PricingPlanEnum plan() {
        return PricingPlanEnum.PEAK_OFF_PEAK;
    }

    @Override
    public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = decide(context);
        if (absolute()) {
            return absolutePrice(decision.matchedRule(), kind, fallback);
        }
        BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
        BigDecimal base = fromBase != null ? fromBase : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
        return base == null ? null : base.multiply(decision.multiplier());
    }

    @Override
    public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = decide(context);
        if (absolute()) {
            // 简单模式单单价：ABSOLUTE 相位价格统一收在 input 槽位
            return absolutePrice(decision.matchedRule(), PriceKind.INPUT, fallback);
        }
        BigDecimal base = defaultUnitPrice != null ? defaultUnitPrice : fallback;
        return base == null ? null : base.multiply(decision.multiplier());
    }

    /**
     * 维度价 × 相位倍率：MULTIPLIER 下维度价与普通单价一样乘倍率，回落顺序复用 {@link BillingConfig#dimensionPriceOrDefault}。
     * ABSOLUTE 下相位自带价即最终价，维度分档价不参与。
     */
    @Override
    public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
        PhaseDecision decision = decide(context);
        if (absolute()) {
            return absolutePrice(decision.matchedRule(), PriceKind.INPUT, fallback);
        }
        BigDecimal base = BillingConfig.dimensionPriceOrDefault(
                candidateKeys, dimensionPrices, defaultUnitPrice, fallback);
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
        PhaseDecision decision = decide(context);
        if (absolute()) {
            return absolutePrice(decision.matchedRule(), PriceKind.INPUT, fallback);
        }
        BigDecimal base = BillingConfig.componentPriceOrDefault(
                candidateKeys, dimensionPrices, componentDefault, defaultUnitPrice, fallback);
        return base == null ? null : base.multiply(decision.multiplier());
    }

    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        return null;
    }

    /**
     * 相位判定的唯一出口：四个取价入口都经此解析命中相位与倍率。
     */
    private PhaseDecision decide(PricingContext context) {
        return PeakOffPeakResolver.resolve(
                pricing, context == null ? null : context.occurredAt(), calendar);
    }

    /** 是否 ABSOLUTE 模式：只有显式写 ABSOLUTE 才走「相位自带价」，其余（含缺省）都是乘倍率 */
    private boolean absolute() {
        return BillingConfig.RATE_MODE_ABSOLUTE.equals(BillingConfig.blankToNull(pricing == null ? null : pricing.rateMode()));
    }

    /** ABSOLUTE 取相位自带单价；相位缺 {@code unitPrices} 或缺该 kind 时回落 fallback，不返回 0 */
    private BigDecimal absolutePrice(PhaseRule rule, PriceKind kind, BigDecimal fallback) {
        if (rule == null || rule.unitPrices() == null) {
            return fallback;
        }
        BigDecimal price = rule.unitPrices().valueOf(kind);
        return price != null ? price : fallback;
    }
    /**
     * 严格维度取价：档位表非空但未命中即抛，命中价照常乘相位倍率；
     * ABSOLUTE 模式取相位自带价，相位缺 {@code unitPrices} 同样按配置漏项抛。
     */
    @Override
    public BigDecimal resolveDimensionPriceStrict(List<String> candidateKeys, PricingContext context, String label) {
        PhaseDecision decision = decide(context);
        if (absolute()) {
            BigDecimal phasePrice = absolutePrice(decision.matchedRule(), PriceKind.INPUT, null);
            if (phasePrice == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "no ABSOLUTE unit price configured for the matched phase of " + label);
            }
            return phasePrice;
        }
        BigDecimal base = BillingConfig.requireDimensionPrice(candidateKeys, dimensionPrices, label);
        return base == null ? null : base.multiply(decision.multiplier());
    }

}
