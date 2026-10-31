package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.Condition;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.chobit.knot.common.model.BillingConfig.TierRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 阶梯价方案的单元测试（2026-10-31 从 {@code BillingConfig} 的私有嵌套 record 拆出后新增）。
 *
 * <p>拆出前「残缺档位跳过」「PROGRESSIVE 逐单位回落」这些分支只能靠 {@code config_json} 间接探，
 * 而 JSON 里造不出 {@code condition} 为 null、数组里带 null 元素这种形态；现在可以直接构造档位列表。
 *
 * <p>与 {@code BillingConfigTest} 的分工：那边守「TIERED 方案必须配齐 unitPrices」这类保存期校验，
 * 这边守「已经落库的历史配置里存在残缺档位时，取价不能崩也不能错位」。
 */
class TieredPricingTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    @DisplayName("残缺档位一律跳过继续看下一档：null 元素 / 缺 condition / 缺 unitPrices / 缺 from")
    void malformedTierRulesAreSkippedNotAborted() {
        List<TierRule> tier = Arrays.asList(
                null,
                new TierRule(null, priceSet("1")),
                new TierRule(new Condition(BigDecimal.ZERO, null), null),
                new TierRule(new Condition(null, null), priceSet("2")),
                new TierRule(new Condition(BigDecimal.ZERO, null), priceSet("7")));
        TieredPricing pricing = new TieredPricing(null, new BigDecimal("0.5"), tier, false, null);

        // 前四档全残缺，第五档才合法；不能因为第一档是 null 就整条链中断
        assertPrice("7", pricing.resolvePrice(PriceKind.INPUT, context(100), ZERO));
        // 唯一合法档位没配 output -> 落回 defaultUnitPrice
        assertPrice("0.5", pricing.resolvePrice(PriceKind.OUTPUT, context(100), ZERO));
    }

    @Test
    @DisplayName("to 含端点、省略 to 表示上不封顶、from 含端点")
    void tierBoundariesAreInclusiveAndOpenEndedToIsUnbounded() {
        TieredPricing pricing = new TieredPricing(null, null, List.of(
                new TierRule(new Condition(BigDecimal.ZERO, new BigDecimal("1000")), priceSet("5")),
                new TierRule(new Condition(new BigDecimal("1001"), null), priceSet("3"))), false, null);

        assertPrice("5", pricing.resolvePrice(PriceKind.INPUT, context(1), ZERO));
        assertPrice("5", pricing.resolvePrice(PriceKind.INPUT, context(1_000), ZERO));
        assertPrice("3", pricing.resolvePrice(PriceKind.INPUT, context(1_001), ZERO));
        assertPrice("3", pricing.resolvePrice(PriceKind.INPUT, context(9_999_999), ZERO));
    }

    @Test
    @DisplayName("用量 <= 0 或无 time 来源的上下文不查阶梯，直接走基础价链")
    void nonPositiveAmountSkipsTierLookup() {
        TieredPricing pricing = new TieredPricing(priceSet("4"), new BigDecimal("0.5"), List.of(
                new TierRule(new Condition(BigDecimal.ZERO, null), priceSet("1"))), false, null);

        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, context(0), ZERO));
        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, context(-1), ZERO));
        // context 本身为 null 时按 0 处理，不 NPE
        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, null, ZERO));
    }

    @Test
    @DisplayName("TIERED 但没配档位（历史配置）时退化为固定价，不抛异常")
    void tierlessConfigDegradesToFixedPrice() {
        TieredPricing pricing = new TieredPricing(priceSet("4", "20"), new BigDecimal("0.5"), null, false, null);

        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, context(100), ZERO));
        // 该 kind 没配 -> defaultUnitPrice（不是 fallback）
        assertPrice("0.5", pricing.resolvePrice(PriceKind.CACHE_WRITE, context(100), ZERO));
        // defaultUnitPrice 也没配时才用 fallback
        assertPrice("9", new TieredPricing(null, null, null, false, null)
                .resolvePrice(PriceKind.CACHE_WRITE, context(100), new BigDecimal("9")));
    }

    @Test
    @DisplayName("PER_REQUEST：用整笔用量命中单档；progressive 关掉时逐单位累加返回 null")
    void perRequestModeMatchesWholeAmountOnly() {
        TieredPricing pricing = new TieredPricing(null, new BigDecimal("5"), List.of(
                new TierRule(new Condition(BigDecimal.ZERO, new BigDecimal("1000")), priceSet("5")),
                new TierRule(new Condition(new BigDecimal("1001"), null), priceSet("3"))), false, null);

        // 整笔 1500 落第二档，全量按 3 计价（不是分段切）
        assertPrice("3", pricing.resolvePrice(PriceKind.INPUT, context(1_500), ZERO));
        assertNull(pricing.resolveProgressiveSum(PriceKind.INPUT, 8, context(8), ZERO));
    }

    @Test
    @DisplayName("PROGRESSIVE：逐单位判定档位后累加（Vidu Q2 720P 8 秒 = 8 + 10 + 10×6 = 78）")
    void progressiveAccumulatesUnitPrices() {
        TieredPricing pricing = viduQ2();

        assertPrice("8", pricing.resolveProgressiveSum(PriceKind.INPUT, 1, context(1), ZERO));
        assertPrice("18", pricing.resolveProgressiveSum(PriceKind.INPUT, 2, context(2), ZERO));
        assertPrice("78", pricing.resolveProgressiveSum(PriceKind.INPUT, 8, context(8), ZERO));
        // amount <= 0 时没有「单价和」可言，返回 null 让调用方回退「量 × 单价」
        assertNull(pricing.resolveProgressiveSum(PriceKind.INPUT, 0, context(0), ZERO));
        // 未开 progressive 的阶梯方案不承担逐单位累加
        assertNull(new TieredPricing(null, BigDecimal.ONE, null, false, null)
                .resolveProgressiveSum(PriceKind.INPUT, 8, context(8), ZERO));
    }

    @Test
    @DisplayName("PROGRESSIVE 下某单位未命中任何档位时逐单位回落 defaultUnitPrice，而不是整体归零")
    void progressiveFallsBackPerUnitWhenTierMisses() {
        // 档位从第 3 个单位起才配：前 2 个单位无处可落
        TieredPricing pricing = new TieredPricing(null, new BigDecimal("2"), List.of(
                new TierRule(new Condition(new BigDecimal("3"), new BigDecimal("10")), priceSet("10"))), true, null);

        // 2 + 2 + 10 + 10
        assertPrice("24", pricing.resolveProgressiveSum(PriceKind.INPUT, 4, context(4), ZERO));
    }

    @Test
    @DisplayName("plan() 恒为 TIERED，与构造入参无关")
    void planIsAlwaysTiered() {
        assertEquals(PricingPlanEnum.TIERED, viduQ2().plan());
    }

    private static TieredPricing viduQ2() {
        return new TieredPricing(null, null, List.of(
                new TierRule(new Condition(BigDecimal.ONE, BigDecimal.ONE), priceSet("8")),
                new TierRule(new Condition(new BigDecimal("2"), new BigDecimal("2")), priceSet("10")),
                new TierRule(new Condition(new BigDecimal("3"), null), priceSet("10"))), true, null);
    }

    private static PricingContext context(long amount) {
        return PricingContext.ofAmount(amount);
    }

    /**
     * 单值 PriceSet：阶梯档位只关心单一价格种类，其余槽位留空。
     * 空槽位也是断言的一部分——没配的 kind 必须落回基础价而不是 0。
     */
    private static PriceSet priceSet(String input) {
        return new PriceSet(new BigDecimal(input), null, null, null, null, null);
    }

    private static PriceSet priceSet(String input, String output) {
        return new PriceSet(new BigDecimal(input), new BigDecimal(output), null, null, null, null);
    }

    private static void assertPrice(String expected, BigDecimal actual) {
        assertNotNull(actual, "价格解析成了 null——这里要比的是数值，不是缺失");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual.toPlainString());
    }
}
