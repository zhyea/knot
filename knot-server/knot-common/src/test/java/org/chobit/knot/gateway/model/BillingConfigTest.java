package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 契约测试：config_json 描述类的解析、结构/方案级校验与价格解析链
 * （tier 命中 -> basePrices -> defaultUnitPrice -> fallback）。
 */
class BillingConfigTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void parseHandlesBlankInvalidAndObject() {
        assertNull(BillingConfig.parse(null));
        assertNull(BillingConfig.parse("  "));
        assertNull(BillingConfig.fromJsonOrNull("not-json"));
        assertThrows(IllegalArgumentException.class, () -> BillingConfig.parse("not-json"));
        assertThrows(IllegalArgumentException.class, () -> BillingConfig.parse("[1,2]"));
        assertNotNull(BillingConfig.parse("{}"));
    }

    @Test
    void fixedPlanResolvesBasePricesThenFallback() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20,"cacheRead":0.2},
                 "defaultUnitPrice":0.5}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.FIXED);
        assertEquals(0, new BigDecimal("4").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, 500_000, ZERO)));
        assertEquals(0, new BigDecimal("0.5").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, 1, ZERO)));
        assertEquals(0, new BigDecimal("0.5").compareTo(pricing.resolveDefaultPrice(ZERO)));
    }

    @Test
    void tieredPlanMatchesTierThenFallsBack() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20,"cacheRead":0.2},
                 "tier":[
                   {"condition":{"from":0,"to":1000000},"unitPrices":{"input":1,"output":5}},
                   {"condition":{"from":1000001},"unitPrices":{"input":2}}
                 ]}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.TIERED);
        // 命中第一档
        assertEquals(0, new BigDecimal("1").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, 500_000, ZERO)));
        // 档位边界：from 与 to 均含
        assertEquals(0, new BigDecimal("1").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, 1_000_000, ZERO)));
        assertEquals(0, new BigDecimal("2").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, 1_000_001, ZERO)));
        // 第二档只配了 input，output 落回 basePrices
        assertEquals(0, new BigDecimal("20").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, 2_000_000, ZERO)));
        // 全部未命中 -> fallback
        assertEquals(0, new BigDecimal("9").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, 100, new BigDecimal("9"))));
    }

    @Test
    void validateRejectsNegativePricesAndBadTier() {
        assertNull(BillingConfig.parse("{\"defaultUnitPrice\":0.5}").validate());
        assertEquals("defaultUnitPrice cannot be negative",
                BillingConfig.parse("{\"defaultUnitPrice\":-1}").validate());
        assertEquals("base price cannot be negative: input",
                BillingConfig.parse("{\"basePrices\":{\"input\":-0.1}}").validate());
        assertEquals("resolution price cannot be negative: 720P",
                BillingConfig.parse("{\"resolutionPrices\":{\"720P\":-1}}").validate());
        assertEquals("tier price cannot be negative: input",
                BillingConfig.parse("{\"tier\":[{\"condition\":{\"from\":0},\"unitPrices\":{\"input\":-1}}]}").validate());
        assertEquals("tier item.condition.from is required",
                BillingConfig.parse("{\"tier\":[{\"condition\":{\"to\":10}}]}").validate());
        assertEquals("tier item.condition.from cannot be greater than to",
                BillingConfig.parse("{\"tier\":[{\"condition\":{\"from\":10,\"to\":1}}]}").validate());
        assertEquals("tier intervals overlap",
                BillingConfig.parse("""
                        {"tier":[
                          {"condition":{"from":0,"to":100}},
                          {"condition":{"from":100,"to":200}}
                        ]}
                        """).validate());
        assertEquals("tier intervals overlap: an open-ended interval must be the last one",
                BillingConfig.parse("""
                        {"tier":[
                          {"condition":{"from":0}},
                          {"condition":{"from":10}}
                        ]}
                        """).validate());
    }

    @Test
    void tieredPlanRequiresCompleteUnitPrices() {
        // tier 缺失
        assertEquals("tier must be a non-empty array for TIERED plan",
                BillingConfig.parse("{\"basePrices\":{\"input\":1}}").validate(PricingPlanEnum.TIERED));
        // unitPrices 缺字段（cacheWrite5m 缺失即不完整）
        assertEquals("tier item.unitPrices must contain complete price fields, missing: cacheWrite5m",
                BillingConfig.parse("""
                        {"tier":[{"condition":{"from":0},
                          "unitPrices":{"input":1,"output":1,"cacheRead":1,"cacheWrite":1,"cacheWrite1h":1}}]}
                        """).validate(PricingPlanEnum.TIERED));
        // 完整结构通过
        assertNull(BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "tier":[{"condition":{"from":0},
                   "unitPrices":{"input":4,"output":20,"cacheRead":0.2,"cacheWrite":5,
                                 "cacheWrite5m":5,"cacheWrite1h":5}}]}
                """).validate(PricingPlanEnum.TIERED));
        // PEAK_OFF_PEAK 已随阶段三开放：方案是否可用不再由 plan 维度拒绝，改为校验 pricing 契约
        assertEquals("pricing is required for PEAK_OFF_PEAK plan",
                BillingConfig.parse("{}").validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    @Test
    void validateRejectsBadPricing() {
        String base = "{\"pricing\":{\"rateMode\":\"%s\",\"timezone\":\"%s\",\"phases\":%s}}";
        String validPhases = """
                [{"condition":{"weekdays":["MONDAY"],"windows":[{"start":"01:00","end":"04:00"}]},
                  "phase":"PEAK","multiplier":1},
                 {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]
                """;
        // 合法结构通过
        assertNull(BillingConfig.parse(
                String.format(base, "MULTIPLIER", "UTC", validPhases)).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // rateMode 首期只允许 MULTIPLIER
        assertEquals("pricing.rateMode must be MULTIPLIER",
                BillingConfig.parse(String.format(base, "FLAT", "UTC", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // timezone 白名单只有 UTC，+08:00 这类固定偏移必须被拒
        assertEquals("pricing.timezone must be one of [UTC]",
                BillingConfig.parse(String.format(base, "MULTIPLIER", "+08:00", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.timezone must be one of [UTC]",
                BillingConfig.parse(String.format(base, "MULTIPLIER", "Asia/Shanghai", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases must be a non-empty array",
                BillingConfig.parse("{\"pricing\":{\"rateMode\":\"MULTIPLIER\",\"timezone\":\"UTC\",\"phases\":[]}}")
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 首项必须是高峰
        assertEquals("pricing.phases first item must be PEAK",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 缺兜底低峰
        assertEquals("pricing.phases last item must be the DEFAULT off-peak fallback",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 倍率越界：>1 与 <=0
        assertEquals("pricing.phases[1].multiplier cannot be greater than 1",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":1.2}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases[1].multiplier must be greater than 0",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 空星期 / 空时段
        assertEquals("pricing.phases[0].condition.weekdays must not be empty",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases[0].condition.windows must not be empty",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"]},"phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 跨午夜时段必须拆成两段
        assertEquals("pricing.phases[0].condition.windows[0].start must be before end and cannot cross midnight",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"],"windows":[{"start":"22:00","end":"02:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 同一 condition 内时段重叠
        assertEquals("pricing.phases[0].condition.windows[1] overlaps the previous window",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                          {"condition":{"weekdays":["MONDAY"],
                                        "windows":[{"start":"01:00","end":"04:00"},
                                                   {"start":"03:00","end":"06:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    @Test
    void peakOffPeakMultipliesModeBasePrice() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "pricing":{"rateMode":"MULTIPLIER","timezone":"UTC","phases":[
                   {"condition":{"weekdays":["MONDAY","WEDNESDAY"],
                                 "windows":[{"start":"01:00","end":"04:00"}]},
                    "phase":"PEAK","multiplier":1},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.PEAK_OFF_PEAK);
        // 2026-09-30 是周三，03:00Z 命中高峰窗口 -> 倍率 1，价格不变
        Instant peak = instant(2026, 9, 30, 3, 0);
        assertEquals(0, new BigDecimal("4").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, new BillingConfig.PricingContext(100, peak), ZERO)));
        // 窗口外 -> 兜底低峰，4 × 0.8 = 3.2
        assertEquals(0, new BigDecimal("3.2").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, new BillingConfig.PricingContext(100, instant(2026, 9, 30, 5, 0)), ZERO)));
        assertEquals(0, new BigDecimal("16.0").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, new BillingConfig.PricingContext(100, instant(2026, 9, 30, 5, 0)), ZERO)));
        // 无时间来源（旧签名）时不做调整，价格与 FIXED 一致
        assertEquals(0, new BigDecimal("4").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, 100, ZERO)));
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }

    @Test
    void resolutionPricesAndUnknownFields() {
        BillingConfig config = BillingConfig.parse("""
                {"defaultUnitPrice":0.05,
                 "resolutionPrices":{"720P":0.1,"1080P":0.2},
                 "imageResolution":"1024x1024"}
                """);
        assertEquals(0, new BigDecimal("0.1").compareTo(config.resolutionPrice("720P")));
        assertNull(config.resolutionPrice("480P"));
        Map<String, BigDecimal> prices = config.resolutionPrices();
        assertEquals(2, prices.size());
        assertEquals(0, new BigDecimal("0.05").compareTo(
                config.pricingPlan(PricingPlanEnum.FIXED).resolveDefaultPrice(ZERO)));
    }
}
