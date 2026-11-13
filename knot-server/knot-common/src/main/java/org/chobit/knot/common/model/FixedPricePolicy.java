package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.chobit.knot.common.model.BillingConfig.PricingPlan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 固定价：基础价格直出，无阶梯、无相位倍率。{@code plan} 由构建入口按版本列传入，只作分类标签与校验锚点。
 *
 * 语义等同固定价的方案共用本实现；{@code context}（用量 / 发生时间）在本实现不消费，
 * 消费方是 {@link TieredPricePolicy} 与 {@link PeakOffPeakPricePolicy}。
 */
record FixedPricePolicy(PricingPlanEnum plan,
                        PriceSet basePrices,
                        BigDecimal defaultUnitPrice,
                        Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

    @Override
    public PricingPlanEnum plan() {
        return plan == null ? PricingPlanEnum.FIXED : plan;
    }

    @Override
    public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
        BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
        if (fromBase != null) {
            return fromBase;
        }
        return defaultUnitPrice != null ? defaultUnitPrice : fallback;
    }

    @Override
    public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
        return defaultUnitPrice != null ? defaultUnitPrice : fallback;
    }

    /**
     * 维度分档价：查表与回落顺序在 {@link BillingConfig#dimensionPriceOrDefault}，此处不重复实现。
     */
    @Override
    public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
        return BillingConfig.dimensionPriceOrDefault(candidateKeys, dimensionPrices, defaultUnitPrice, fallback);
    }

    /** 分量取价：回落顺序见 {@link BillingConfig#componentPriceOrDefault}（分量自带价优先于规则默认价） */
    @Override
    public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                            BigDecimal componentDefault,
                                            PricingContext context,
                                            BigDecimal fallback) {
        return BillingConfig.componentPriceOrDefault(
                candidateKeys, dimensionPrices, componentDefault, defaultUnitPrice, fallback);
    }

    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        return null;
    }
    /**
     * 严格维度取价：档位表非空但未命中即抛；固定价没有相位倍率，命中价原样返回。
     */
    @Override
    public BigDecimal resolveDimensionPriceStrict(List<String> candidateKeys, PricingContext context, String label) {
        return BillingConfig.requireDimensionPrice(candidateKeys, dimensionPrices, label);
    }

}
