package org.chobit.knot.common.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定价方案的单元测试（2026-10-31 从 {@code BillingConfig} 的私有嵌套 record 拆出后新增）。
 *
 * <p>为什么现在才写：拆出前它是 {@code private record}，同包甚至同类测试都够不着，只能借
 * {@code config_json} 解析后经 {@link BillingConfig#pricingPlan} 间接观察结果值；拆成包私有类后
 * 可以直接构造，这才第一次能把「维度键大小写宽容度」「分量价与规则默认价的先后」这类
 * <b>分支顺序</b>单独钉住。
 *
 * <p>与 {@code BillingConfigTest} 的分工：那边守 JSON 契约、校验与端到端取价链，这边只守本类内部的
 * 分支与回落顺序，不重复断言同一条链路的结果值。
 */
class FixedPricePolicyTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    @DisplayName("plan() 原样回传构造入参：DIMENSION_TIERED / VENDOR_MINIMAX 共用本实现，但不能被同化成 FIXED")
    void planLabelFollowsConstructorArgument() {
        assertEquals(PricingPlanEnum.DIMENSION_TIERED, fixed(PricingPlanEnum.DIMENSION_TIERED).plan());
        assertEquals(PricingPlanEnum.VENDOR_MINIMAX, fixed(PricingPlanEnum.VENDOR_MINIMAX).plan());
        // 只有 plan 入参为 null 才回落到 FIXED：调用方拿 plan() 做展示与校验锚点，标签不能被实现顺手改掉
        assertEquals(PricingPlanEnum.FIXED, fixed(null).plan());
    }

    @Test
    @DisplayName("取价链：basePrices[kind] -> defaultUnitPrice -> fallback，缺哪层落哪层")
    void resolvePriceWalksBaseThenDefaultThenFallback() {
        FixedPricePolicy pricing = new FixedPricePolicy(PricingPlanEnum.FIXED,
                priceSet("4", "20"), new BigDecimal("0.5"), null);
        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, context(100), ZERO));
        // 该 kind 未配置时落 defaultUnitPrice，不能越过它直接用 fallback
        assertPrice("0.5", pricing.resolvePrice(PriceKind.CACHE_WRITE, context(100), new BigDecimal("9")));
        // 只有 defaultUnitPrice 也没配时才用 fallback
        assertPrice("9", new FixedPricePolicy(PricingPlanEnum.FIXED, null, null, null)
                .resolvePrice(PriceKind.INPUT, context(100), new BigDecimal("9")));
    }

    @Test
    @DisplayName("维度键查表两侧都按大写比对：配置侧写 720p / Input_Video:768p 也能被配置侧大写请求命中")
    void dimensionLookupIsCaseInsensitiveOnBothSides() {
        Map<String, BigDecimal> prices = Map.of(
                "720P", new BigDecimal("1.5"),
                "INPUT_VIDEO:768P", new BigDecimal("0.8"));
        FixedPricePolicy pricing = new FixedPricePolicy(PricingPlanEnum.FIXED, null, new BigDecimal("1.0"), prices);

        // ① 两侧写法一致：原样命中
        assertPrice("1.5", pricing.resolveDimensionPrice(List.of("720P"), context(1), ZERO));
        assertPrice("0.8", pricing.resolveDimensionPrice(List.of("INPUT_VIDEO:768P"), context(1), ZERO));
        // ② 请求侧写成小写（上游给的分辨率写法不可控，这里必须宽容）
        assertPrice("1.5", pricing.resolveDimensionPrice(List.of("720p"), context(1), ZERO));
        assertPrice("0.8", pricing.resolveDimensionPrice(List.of("input_video:768p"), context(1), ZERO));
        // ③ 配置侧小写
        FixedPricePolicy lowerCased = new FixedPricePolicy(PricingPlanEnum.FIXED, null, ZERO,
                Map.of("1080p", new BigDecimal("2")));
        assertPrice("2", lowerCased.resolveDimensionPrice(List.of("1080P"), context(1), ZERO));
        // ④ ⭐ 配置侧**混合**大小写：旧实现只对查询键试三种写法，这类键三种全落空、静默回落默认价
        FixedPricePolicy mixedCased = new FixedPricePolicy(PricingPlanEnum.FIXED, null, ZERO,
                Map.of("Input_Video:768p", new BigDecimal("0.9")));
        assertPrice("0.9", mixedCased.resolveDimensionPrice(List.of("INPUT_VIDEO:768P"), context(1), ZERO));
        // 一个都没命中 -> 回落 defaultUnitPrice（不是 0，也不能是 null）
        assertPrice("1.0", pricing.resolveDimensionPrice(List.of("4K"), context(1), ZERO));
    }

    @Test
    @DisplayName("严格取价：档位表非空但未命中 -> 抛；档位表为空 -> 返回 null 交调用方用默认价")
    void strictDimensionPriceRejectsUnconfiguredTier() {
        FixedPricePolicy pricing = new FixedPricePolicy(PricingPlanEnum.FIXED, null, new BigDecimal("1.0"),
                Map.of("720P", new BigDecimal("1.5")));

        assertPrice("1.5", pricing.resolveDimensionPriceStrict(List.of("720P"), context(1), "output video"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricing.resolveDimensionPriceStrict(List.of("1080P"), context(1), "output video"));
        assertTrue(ex.getMessage().contains("1080P"), ex.getMessage());

        // 没配档位表 = 不分档的合法形态，不是「未命中」，绝不抛
        assertNull(new FixedPricePolicy(PricingPlanEnum.FIXED, null, new BigDecimal("1.0"), null)
                .resolveDimensionPriceStrict(List.of("720P"), context(1), "output video"));
        // 没声明档位名（如视频未带 resolution）同样不算未命中
        assertNull(pricing.resolveDimensionPriceStrict(List.of(), context(1), "output video"));
    }

    @Test
    @DisplayName("维度键为空/列表为 null 时不查表，直接走默认价链")
    void dimensionLookupToleratesMissingInputs() {
        FixedPricePolicy pricing = new FixedPricePolicy(PricingPlanEnum.FIXED, null, new BigDecimal("1.0"),
                Map.of("720P", new BigDecimal("1.5")));
        assertPrice("1.0", pricing.resolveDimensionPrice(null, context(1), ZERO));
        assertPrice("1.0", pricing.resolveDimensionPrice(List.of(), context(1), ZERO));
        // 列表里带 null 元素时跳过它继续看下一个
        assertPrice("1.5", pricing.resolveDimensionPrice(Arrays.asList(null, "720P"), context(1), ZERO));
    }

    @Test
    @DisplayName("分量价：维度键 -> 分量自带默认价 -> 规则默认价 -> fallback（分量默认价不能被规则默认价抢先）")
    void componentPricePrefersOwnDefaultOverRuleDefault() {
        FixedPricePolicy pricing = new FixedPricePolicy(PricingPlanEnum.FIXED, null, new BigDecimal("0.5"),
                Map.of("INPUT_IMAGE:720P", new BigDecimal("0.2")));

        assertPrice("0.2", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:720P"), new BigDecimal("0.3"), context(1), ZERO));
        // ⭐ 维度未命中时必须用分量自带价 0.3，不能落成规则默认价 0.5——
        // 那会把「输入图片 0.3/张」静默算成「输出视频 0.5/秒」的价
        assertPrice("0.3", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:1080P"), new BigDecimal("0.3"), context(1), ZERO));
        // 分量没配默认价才用规则默认价
        assertPrice("0.5", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:1080P"), null, context(1), ZERO));
        // 规则默认价也没配 -> fallback
        assertPrice("9", new FixedPricePolicy(PricingPlanEnum.FIXED, null, null, null)
                .resolveComponentPrice(List.of("INPUT_IMAGE:1080P"), null, context(1), new BigDecimal("9")));
    }

    @Test
    @DisplayName("固定价没有阶梯，逐单位累加恒为 null（调用方据此回退「量 × 单价」）")
    void progressiveSumIsAlwaysNull() {
        assertNull(fixed(PricingPlanEnum.FIXED).resolveProgressiveSum(PriceKind.INPUT, 10, context(10), ZERO));
    }

    private static FixedPricePolicy fixed(PricingPlanEnum plan) {
        return new FixedPricePolicy(plan, priceSet("4", "20"), new BigDecimal("0.5"), null);
    }

    private static PricingContext context(long amount) {
        return PricingContext.ofAmount(amount);
    }

    /** 六个具名价格按位置传，这里只关心 input / output，其余留空 */
    private static PriceSet priceSet(String input, String output) {
        return new PriceSet(new BigDecimal(input), new BigDecimal(output), null, null, null, null);
    }

    /** BigDecimal 断言一律走 compareTo：1.0 与 1 数值相同但 equals 为假 */
    private static void assertPrice(String expected, BigDecimal actual) {
        assertNotNull(actual, "价格解析成了 null——这里要比的是数值，不是缺失");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual.toPlainString());
    }
}
