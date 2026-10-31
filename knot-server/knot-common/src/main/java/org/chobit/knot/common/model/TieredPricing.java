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
record TieredPricing(PriceSet basePrices,
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

    @Override
    public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
        BigDecimal matched = BillingConfig.dimensionPrice(candidateKeys, dimensionPrices);
        return matched != null ? matched : resolveDefaultPrice(context, fallback);
    }

    /**
     * PROGRESSIVE：把用量拆成 {@code amount} 个单位，逐单位判定档位后累加单价。
     *
     * <p>与 {@link #resolveFromTier} 共用一个「按数值命中档位」的判定，
     * 区别只在代入的是<b>单位序号 i</b> 而不是<b>本笔总用量</b>：</p>
     * <pre>
     * Vidu Q2-turbo 720P（第1秒 8，第2秒 10，第3秒起 +10），8 秒：
     *   8 + 10 + 10×6 = 78
     * </pre>
     */
    @Override
    public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
        if (!progressive || amount <= 0) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (long unit = 1; unit <= amount; unit++) {
            BigDecimal price = tierPriceAt(kind, unit);
            if (price == null) {
                price = resolveDefaultPrice(context, fallback);
            }
            if (price != null) {
                total = total.add(price);
            }
        }
        return total;
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

    private BigDecimal tierPriceAt(PriceKind kind, long unitIndex) {
        if (tier == null) {
            return null;
        }
        return resolveFromTier(kind, unitIndex);
    }

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
}
