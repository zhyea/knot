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
 * 固定价：基础价格直出（无阶梯、无相位倍率）。
 *
 * <p>{@code plan} 由构建入口按版本列传入，只作<b>分类标签与校验锚点</b>。取价语义与
 * {@link PricingPlanEnum#FIXED} 完全一致，故所有「语义等同固定价」的方案共用这一份实现——
 * MiniMax 与 Kling 两家的多分量编排都在计算器侧，方案值不作为独立的价格算法。
 * 此前 MiniMax 单独复制了一份逐字重复的 record，2026-10-26 加 Kling 时收敛掉。</p>
 */
record FixedPricing(PricingPlanEnum plan,
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

    @Override
    public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
        BigDecimal matched = BillingConfig.dimensionPrice(candidateKeys, dimensionPrices);
        return matched != null ? matched : resolveDefaultPrice(context, fallback);
    }

    @Override
    public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                            BigDecimal componentDefault,
                                            PricingContext context,
                                            BigDecimal fallback) {
        BigDecimal matched = BillingConfig.dimensionPrice(candidateKeys, dimensionPrices);
        if (matched != null) {
            return matched;
        }
        return componentDefault != null ? componentDefault : resolveDefaultPrice(context, fallback);
    }

    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        return null;
    }
}
