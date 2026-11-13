package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.chobit.knot.common.model.BillingConfig.PricingPlan;
import org.chobit.knot.common.model.BillingConfig.TierRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 阶梯价：按用量命中档位单价，未命中回退基础价格 */
record TieredPricePolicy(PriceSet basePrices,
                         BigDecimal defaultUnitPrice,
                         List<TierRule> tier,
                         boolean progressive,
                         Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

    @Override
    public PricingPlanEnum plan() {
        return PricingPlanEnum.TIERED;
    }

    @Override
    public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
        BigDecimal fromTier = resolveFromTier(kind, context == null ? 0L : context.usageAmount());
        if (fromTier != null) {
            return fromTier;
        }
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
     * 维度分档价：查表与回落顺序在 {@link BillingConfig#dimensionPriceOrDefault}，本类不重复实现。
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

    /**
     * PROGRESSIVE：把用量拆成 {@code amount} 个单位，逐单位判定档位后累加单价。
     * 与 {@link #resolvePrice} 共用 {@link #resolveFromTier}，区别是代入单位序号 i 而非本笔总用量。
     */
    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        if (!progressive || amount <= 0) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (long unit = 1; unit <= amount; unit++) {
            BigDecimal price = resolveFromTier(kind, unit);
            if (price == null) {
                price = resolveDefaultPrice(context, fallback);
            }
            if (price != null) {
                total = total.add(price);
            }
        }
        return total;
    }

    /**
     * 按数值命中档位单价：{@code from} 含、{@code to} 含（{@code null} 表示上不封顶）。
     * {@code usageAmount <= 0} 或未配档位时返回 {@code null}，回落方向由调用方决定。
     */
    private BigDecimal resolveFromTier(PriceKind kind, long usageAmount) {
        if (usageAmount <= 0 || tier == null) {
            return null;
        }
        BigDecimal amount = BigDecimal.valueOf(usageAmount);
        for (TierRule rule : tier) {
            if (rule == null || rule.condition() == null || rule.unitPrices() == null) {
                continue;
            }
            BigDecimal from = rule.condition().from();
            BigDecimal to = rule.condition().to();
            if (from == null || from.compareTo(amount) > 0) {
                continue;
            }
            if (to != null && to.compareTo(amount) < 0) {
                continue;
            }
            BigDecimal matched = rule.unitPrices().valueOf(kind);
            if (matched != null) {
                return matched;
            }
        }
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
