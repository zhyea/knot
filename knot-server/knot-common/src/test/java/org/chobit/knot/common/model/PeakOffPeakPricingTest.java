package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.Pricing;
import org.chobit.knot.common.model.BillingConfig.PricingContext;
import org.chobit.knot.common.pricing.HolidayCalendar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 高低峰价方案的单元测试（2026-10-31 从 {@code BillingConfig} 的私有嵌套 record 拆出后新增）。
 *
 * <p>本类的核心守卫是<b>两条正交的价</b>：<br>
 * ① 维度分档价在 MULTIPLIER 下必须与基础价过同一条倍率链（漏乘 = 「配了错峰却不生效」）；<br>
 * ② 在 ABSOLUTE 下相位自带价已是最终价，维度分档价与分量默认价都不得再叠（叠 = 双重计价）。
 *
 * <p>这两条此前只能靠 {@code VideoBillingModeCalculatorTest} 间接覆盖，错在方案层却报在计算器层，
 * 定位方向是反的。拆出后可以对着方案层直接断言。
 */
class PeakOffPeakPricingTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 2026-09-30 是周三，03:00Z 落在「周三 01:00-04:00」高峰窗口内 */
    private static final Instant PEAK = instant(2026, 9, 30, 3, 0);

    /** 同一个周三的 05:00Z 在窗口外，落 DEFAULT 低峰 */
    private static final Instant OFF_PEAK = instant(2026, 9, 30, 5, 0);

    @Test
    @DisplayName("时间缺失（occurredAt = null）时倍率恒为 1，价格与固定价逐字相同")
    void missingOccurredAtLeavesPriceUntouched() {
        PeakOffPeakPricing pricing = multiplier(new BigDecimal("0.5"), null);

        assertPrice("4", pricing.resolvePrice(PriceKind.INPUT, PricingContext.ofAmount(100), ZERO));
        assertPrice("0.5", pricing.resolveDefaultPrice(PricingContext.ofAmount(100), ZERO));
        assertPrice("0.5", pricing.resolveDimensionPrice(List.of("4K"), PricingContext.ofAmount(100), ZERO));
    }

    @Test
    @DisplayName("MULTIPLIER：基础价、默认价、维度分档价三者都乘同一个倍率")
    void multiplierAppliesToEveryPriceSource() {
        PeakOffPeakPricing pricing = multiplier(new BigDecimal("0.5"), Map.of("720P", new BigDecimal("2")));

        assertPrice("3.2", pricing.resolvePrice(PriceKind.INPUT, new PricingContext(100, OFF_PEAK), ZERO));
        assertPrice("0.4", pricing.resolveDefaultPrice(new PricingContext(100, OFF_PEAK), ZERO));
        // ⭐ 维度分档价不能绕开倍率链：历史缺陷正是「命中 resolutionPrices 后直接 return」，
        // 导致视频配了错峰却不生效
        assertPrice("1.6", pricing.resolveDimensionPrice(List.of("720P"), new PricingContext(100, OFF_PEAK), ZERO));
        // 高峰相位倍率 1：价格原样
        assertPrice("2", pricing.resolveDimensionPrice(List.of("720P"), new PricingContext(100, PEAK), ZERO));
    }

    @Test
    @DisplayName("MULTIPLIER：分量价回落链 维度键 -> 分量自带价 -> 规则默认价 -> fallback，再统一乘倍率")
    void multiplierComponentPriceKeepsFallbackOrder() {
        PeakOffPeakPricing pricing = multiplier(new BigDecimal("0.5"),
                Map.of("INPUT_IMAGE:720P", new BigDecimal("1")));

        // 维度命中 -> 1 × 0.8
        assertPrice("0.8", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:720P"), new BigDecimal("5"), new PricingContext(100, OFF_PEAK), ZERO));
        // 维度未命中 -> 分量自带价 5 × 0.8（不能被规则默认价 0.5 抢先）
        assertPrice("4.0", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:1080P"), new BigDecimal("5"), new PricingContext(100, OFF_PEAK), ZERO));
        // 分量也没配 -> 规则默认价 0.5 × 0.8
        assertPrice("0.4", pricing.resolveComponentPrice(
                List.of("INPUT_IMAGE:1080P"), null, new PricingContext(100, OFF_PEAK), ZERO));
    }

    @Test
    @DisplayName("ABSOLUTE：取相位自带单价，维度分档价与分量默认价一律不参与")
    void absoluteTakesPhasePriceAndIgnoresDimensionAndComponentPrices() {
        PeakOffPeakPricing pricing = new PeakOffPeakPricing(priceSet("4", "20"), new BigDecimal("0.5"),
                absolute(true), HolidayCalendar.EMPTY, Map.of("720P", new BigDecimal("99")));

        assertPrice("10", pricing.resolvePrice(PriceKind.INPUT, new PricingContext(100, PEAK), ZERO));
        assertPrice("50", pricing.resolvePrice(PriceKind.OUTPUT, new PricingContext(100, PEAK), ZERO));
        // ⭐ 相位价已是最终价：再叠维度分档价 99 就是双重计价
        assertPrice("10", pricing.resolveDimensionPrice(List.of("720P"), new PricingContext(100, PEAK), ZERO));
        assertPrice("10", pricing.resolveComponentPrice(
                List.of("720P"), new BigDecimal("77"), new PricingContext(100, PEAK), ZERO));
        // 简单模式单单价统一收在 input 槽位
        assertPrice("10", pricing.resolveDefaultPrice(new PricingContext(100, PEAK), ZERO));
        // 低峰相位自带价 6 / 30
        assertPrice("6", pricing.resolvePrice(PriceKind.INPUT, new PricingContext(100, OFF_PEAK), ZERO));
    }

    @Test
    @DisplayName("ABSOLUTE 相位缺 unitPrices（历史配置）时回落 fallback，不抛异常也不返回 0")
    void absoluteWithoutUnitPricesFallsBackToFallback() {
        PeakOffPeakPricing pricing = new PeakOffPeakPricing(null, null, absolute(false),
                HolidayCalendar.EMPTY, Map.of("720P", new BigDecimal("99")));

        assertPrice("9", pricing.resolvePrice(PriceKind.INPUT, new PricingContext(100, PEAK), new BigDecimal("9")));
        assertPrice("9", pricing.resolveDefaultPrice(new PricingContext(100, PEAK), new BigDecimal("9")));
        assertPrice("9", pricing.resolveDimensionPrice(List.of("720P"), new PricingContext(100, PEAK), new BigDecimal("9")));
    }

    @Test
    @DisplayName("ABSOLUTE 相位配了 unitPrices 但缺该 kind 时，该 kind 回落 fallback（不能拿 0 充数）")
    void absoluteMissingKindFallsBackPerKind() {
        // 相位只配了 input，output 在 ABSOLUTE 下没有来源
        PeakOffPeakPricing pricing = new PeakOffPeakPricing(null, null, absoluteInputOnly(),
                HolidayCalendar.EMPTY, null);

        assertPrice("10", pricing.resolvePrice(PriceKind.INPUT, new PricingContext(100, PEAK), ZERO));
        assertPrice("9", pricing.resolvePrice(PriceKind.OUTPUT, new PricingContext(100, PEAK), new BigDecimal("9")));
    }

    @Test
    @DisplayName("高低峰方案不承担逐单位累加，恒返回 null")
    void progressiveSumIsAlwaysNull() {
        assertNull(multiplier(new BigDecimal("0.5"), null)
                .resolveProgressiveSum(PriceKind.INPUT, 8, new PricingContext(8, PEAK), ZERO));
    }

    @Test
    @DisplayName("plan() 恒为 PEAK_OFF_PEAK")
    void planIsAlwaysPeakOffPeak() {
        assertEquals(PricingPlanEnum.PEAK_OFF_PEAK, multiplier(BigDecimal.ONE, null).plan());
    }

    // ==================== 夹具 ====================

    private static PeakOffPeakPricing multiplier(BigDecimal defaultUnitPrice, Map<String, BigDecimal> dimensionPrices) {
        return new PeakOffPeakPricing(priceSet("4", "20"), defaultUnitPrice, multiplierPricing(),
                HolidayCalendar.EMPTY, dimensionPrices);
    }

    /** MULTIPLIER：周三 01:00-04:00 高峰倍率 1，其余时间低峰 0.8 */
    private static Pricing multiplierPricing() {
        return pricing("""
                {"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[3],"windows":[{"start":"01:00","end":"04:00"}]},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}""");
    }

    /**
     * ABSOLUTE 配置。{@code withUnitPrices=false} 造的是「历史配置缺 unitPrices」那种形态——
     * 保存期校验会拦，但已经落库的数据仍会被读到，方案层必须自己兜住。
     */
    private static Pricing absolute(boolean withUnitPrices) {
        String peakPrices = withUnitPrices ? ",\"unitPrices\":{\"input\":10,\"output\":50}" : "";
        String offPrices = withUnitPrices ? ",\"unitPrices\":{\"input\":6,\"output\":30}" : "";
        return pricing("""
                {"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[3],"windows":[{"start":"01:00","end":"04:00"}]},
                   "phase":"PEAK"%s},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK"%s}]}""".formatted(peakPrices, offPrices));
    }

    /** ABSOLUTE 只配 input 槽位：用于验证「缺该 kind 时按 kind 回落」 */
    private static Pricing absoluteInputOnly() {
        return pricing("""
                {"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[3],"windows":[{"start":"01:00","end":"04:00"}]},
                   "phase":"PEAK","unitPrices":{"input":10}},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","unitPrices":{"input":6}}]}""");
    }

    /** 借 parse 造嵌套的 Pricing 对象：四层嵌套手工 new 出来只会让测试读不懂 */    private static Pricing pricing(String json) {
        return BillingConfig.parse("{\"pricing\":" + json + "}").pricing();
    }

    private static PriceSet priceSet(String input, String output) {
        return new PriceSet(new BigDecimal(input), new BigDecimal(output), null, null, null, null);
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }

    private static void assertPrice(String expected, BigDecimal actual) {
        assertNotNull(actual, "价格解析成了 null——这里要比的是数值，不是缺失");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual.toPlainString());
    }
}
