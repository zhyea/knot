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
 *
 * <p>{@code context}（用量 / 发生时间）在本实现里<b>刻意不用</b>：固定价既不看阶梯也不看相位。
 * 形参由接口约定必须收，不代表每个实现都要消费它——消费方是 {@link TieredPricePolicy}（用量）
 * 与 {@link PeakOffPeakPricePolicy}（发生时间）。</p>
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
     * 维度分档价：查表与回落顺序在 {@link BillingConfig#dimensionPriceOrDefault}。
     *
     * <p>⚠ 别在这里再抄一份「命中 -&gt; 默认价 -&gt; fallback」。它与 {@link TieredPricePolicy}
     * 的同名方法曾经逐字重复（diff 差 0 行），重复的代价是同一个口径要改两次。</p>
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
