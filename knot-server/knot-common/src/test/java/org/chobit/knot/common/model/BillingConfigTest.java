package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, BillingConfig.PricingContext.ofAmount(500_000), ZERO)));
        assertEquals(0, new BigDecimal("0.5").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, BillingConfig.PricingContext.ofAmount(1), ZERO)));
        assertEquals(0, new BigDecimal("0.5").compareTo(pricing.resolveDefaultPrice(BillingConfig.PricingContext.ofAmount(1), ZERO)));
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
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, BillingConfig.PricingContext.ofAmount(500_000), ZERO)));
        // 档位边界：from 与 to 均含
        assertEquals(0, new BigDecimal("1").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, BillingConfig.PricingContext.ofAmount(1_000_000), ZERO)));
        assertEquals(0, new BigDecimal("2").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, BillingConfig.PricingContext.ofAmount(1_000_001), ZERO)));
        // 第二档只配了 input，output 落回 basePrices
        assertEquals(0, new BigDecimal("20").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, BillingConfig.PricingContext.ofAmount(2_000_000), ZERO)));
        // 全部未命中 -> fallback
        assertEquals(0, new BigDecimal("9").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, BillingConfig.PricingContext.ofAmount(100), new BigDecimal("9"))));
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
                [{"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                  "phase":"PEAK","multiplier":1},
                 {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]
                """;
        // 合法结构通过
        assertNull(BillingConfig.parse(
                String.format(base, "MULTIPLIER", "UTC+0", validPhases)).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // rateMode 仅允许 MULTIPLIER 或 ABSOLUTE
        assertEquals("pricing.rateMode must be MULTIPLIER or ABSOLUTE",
                BillingConfig.parse(String.format(base, "FLAT", "UTC+0", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 仅接受 UTC 偏移格式，+08:00 与 IANA 名称必须被拒
        assertEquals("pricing.timezone must be a valid UTC offset",
                BillingConfig.parse(String.format(base, "MULTIPLIER", "+08:00", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.timezone must be a valid UTC offset",
                BillingConfig.parse(String.format(base, "MULTIPLIER", "Asia/Shanghai", validPhases))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertNull(BillingConfig.parse(String.format(base, "MULTIPLIER", "UTC+8", validPhases))
                .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases must be a non-empty array",
                BillingConfig.parse("{\"pricing\":{\"rateMode\":\"MULTIPLIER\",\"timezone\":\"UTC+0\",\"phases\":[]}}")
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
       // 首项必须是高峰
        assertEquals("pricing.phases first item must be PEAK",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 缺兜底低峰
        assertEquals("pricing.phases last item must be the DEFAULT off-peak fallback",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 倍率越界：>1 与 <=0
        assertEquals("pricing.phases[1].multiplier cannot be greater than 1",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":1.2}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases[1].multiplier must be greater than 0",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 空星期 / 空时段
        assertEquals("pricing.phases[0].condition.weekdays must not be empty",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"windows":[{"start":"01:00","end":"04:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        assertEquals("pricing.phases[0].condition.windows must not be empty",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1]},"phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 跨午夜时段必须拆成两段
        assertEquals("pricing.phases[0].condition.windows[0].start must be before end and cannot cross midnight",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],"windows":[{"start":"22:00","end":"02:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 同一 condition 内时段重叠
        assertEquals("pricing.phases[0].condition.windows[1] overlaps the previous window",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],
                                        "windows":[{"start":"01:00","end":"04:00"},
                                                   {"start":"03:00","end":"06:00"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    /** end=24:00 = 次日 0 点（数轴端点 1440）；start 不允许 24:00 */
    @Test
    void midnightEndIsAcceptedButMidnightStartIsNot() {
        String template = "{\"pricing\":{\"rateMode\":\"MULTIPLIER\",\"timezone\":\"UTC+0\",\"phases\":["
                + "{\"condition\":{\"weekdays\":[1],\"windows\":[{\"start\":\"%s\",\"end\":\"%s\"}]},"
                + "\"phase\":\"PEAK\",\"multiplier\":1},"
                + "{\"condition\":{\"type\":\"DEFAULT\"},\"phase\":\"OFF_PEAK\",\"multiplier\":0.8}]}}";
        // 覆盖到午夜：合法
        assertNull(BillingConfig.parse(String.format(template, "01:00", "24:00"))
                .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 全天 00:00-24:00：合法
        assertNull(BillingConfig.parse(String.format(template, "00:00", "24:00"))
                .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // start=24:00 不合法（数轴上与 end 不可比且语义无意义）
        assertEquals("pricing.phases[0].condition.windows[0] must use HH:mm clock format (end may also be 24:00)",
                BillingConfig.parse(String.format(template, "24:00", "24:00"))
                        .validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // end=24:00 之后的下一段不能与之重叠
        assertEquals("pricing.phases[0].condition.windows[1] overlaps the previous window",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],
                                        "windows":[{"start":"01:00","end":"24:00"},
                                                   {"start":"23:00","end":"23:30"}]},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 24:00 当作 start 与 24:00 结尾可衔接（相邻不算重叠）
        assertNull(BillingConfig.parse("""
                {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[1],
                                "windows":[{"start":"00:00","end":"08:00"},
                                           {"start":"08:00","end":"24:00"}]},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    /** 调休策略各条必须一致：判定器只读第一条，不一致必须拒绝保存而非静默忽略 */
    @Test
    void makeUpWorkdayPolicyMustBeIdenticalAcrossPeakRules() {
        // 不一致 -> 拒绝
        assertEquals("pricing.phases[1].condition.makeUpWorkdayPolicy must be identical across all peak rules",
                BillingConfig.parse("""
                        {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                          {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}],
                                        "makeUpWorkdayPolicy":"OFF_PEAK"},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"weekdays":[2],"windows":[{"start":"01:00","end":"04:00"}],
                                        "makeUpWorkdayPolicy":"FOLLOW_WEEKDAY_WINDOWS"},
                           "phase":"PEAK","multiplier":1},
                          {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 一致（显式相同）-> 通过
        assertNull(BillingConfig.parse("""
                {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}],
                                "makeUpWorkdayPolicy":"FOLLOW_WEEKDAY_WINDOWS"},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"weekdays":[2],"windows":[{"start":"01:00","end":"04:00"}],
                                "makeUpWorkdayPolicy":"FOLLOW_WEEKDAY_WINDOWS"},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
        // 一致（一条缺失按默认 OFF_PEAK，另一条显式 OFF_PEAK）-> 通过
        assertNull(BillingConfig.parse("""
                {"pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                  {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"weekdays":[2],"windows":[{"start":"01:00","end":"04:00"}],
                                "makeUpWorkdayPolicy":"OFF_PEAK"},
                   "phase":"PEAK","multiplier":1},
                  {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]}}
                """).validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    /** 24:00 端点必须真的覆盖到当日最后一刻，而不是把 23:59 之后的每分钟都漏给低峰 */
    @Test
    void midnightEndWindowCoversUntilEndOfDay() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":10},
                 "pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[3],
                                 "windows":[{"start":"01:00","end":"24:00"}]},
                    "phase":"PEAK","multiplier":1},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.5}]}}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.PEAK_OFF_PEAK);
        // 2026-09-30 周三：23:59Z 仍在高峰窗口内（左闭右开延伸到次日 0 点）
        assertEquals(0, new BigDecimal("10").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT,
                        new BillingConfig.PricingContext(100, instant(2026, 9, 30, 23, 59)), ZERO)));
        // 00:30Z 不在窗口内 -> 低峰 5
        assertEquals(0, new BigDecimal("5.0").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT,
                        new BillingConfig.PricingContext(100, instant(2026, 9, 30, 0, 30)), ZERO)));
    }

    @Test
    void peakOffPeakMultipliesModeBasePrice() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[1,3],
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
        // 无时间来源时不做调整，价格与 FIXED 一致
        assertEquals(0, new BigDecimal("4").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, BillingConfig.PricingContext.ofAmount(100), ZERO)));
    }

    @Test
    void peakOffPeakAbsoluteUsesPhaseOwnPrice() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "pricing":{"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[1,3],
                                 "windows":[{"start":"01:00","end":"04:00"}]},
                    "phase":"PEAK","unitPrices":{"input":10,"output":50}},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK",
                    "unitPrices":{"input":6,"output":30}}]}}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.PEAK_OFF_PEAK);
        // 周三 03:00Z 命中高峰窗口 -> 直接取峰值自身单价，与 basePrices 无关
        Instant peak = instant(2026, 9, 30, 3, 0);
        assertEquals(0, new BigDecimal("10").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, new BillingConfig.PricingContext(100, peak), ZERO)));
        assertEquals(0, new BigDecimal("50").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, new BillingConfig.PricingContext(100, peak), ZERO)));
        // 窗口外 -> 兜底低峰自身单价 6 / 30，不做任何乘法
        Instant off = instant(2026, 9, 30, 5, 0);
        assertEquals(0, new BigDecimal("6").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.INPUT, new BillingConfig.PricingContext(100, off), ZERO)));
        assertEquals(0, new BigDecimal("30").compareTo(
                pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, new BillingConfig.PricingContext(100, off), ZERO)));
    }

    @Test
    void peakOffPeakAbsoluteRejectsMissingUnitPrices() {
        // 缺 unitPrices 应通过方案级校验拦截
        BillingConfig missing = BillingConfig.parse("""
                {"pricing":{"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                    "phase":"PEAK"},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK"}]}}
                """);
        assertNotNull(missing.validate(PricingPlanEnum.PEAK_OFF_PEAK));

        // 带完整 unitPrices 应通过
        BillingConfig ok = BillingConfig.parse("""
                {"pricing":{"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                    "phase":"PEAK","unitPrices":{"input":10}},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","unitPrices":{"input":6}}]}}
                """);
        assertNull(ok.validate(PricingPlanEnum.PEAK_OFF_PEAK));
    }

    @Test
    void vendorMiniMaxRequiresComponentFields() {
        // MiniMax H3 识别特征 = 输入图片按张计费：缺 video.inputImagePerImage 一律拒存
        BillingConfig bare = BillingConfig.parse("""
                {"defaultUnitPrice":0.5,
                 "resolutionPrices":{"768P":0.5,"2K":0.8}}
                """);
        String error = bare.validate(PricingPlanEnum.VENDOR_MINIMAX);
        assertNotNull(error);
        assertTrue(error.contains("VENDOR_MINIMAX requires video.inputImagePerImage"));

        // 只有输入视频价、没有图片价 -> 仍拒存（图片计费是 MiniMax 的必需分量）
        BillingConfig videoOnly = BillingConfig.parse("""
                {"defaultUnitPrice":0.5,
                 "resolutionPrices":{"768P":0.5,"2K":0.8},
                 "video":{"inputVideoPerSecond":0.5}}
                """);
        assertNotNull(videoOnly.validate(PricingPlanEnum.VENDOR_MINIMAX));

        // 只有附加费、没有图片价 -> 仍拒存
        BillingConfig surchargeOnly = BillingConfig.parse("""
                {"defaultUnitPrice":0.5,
                 "resolutionPrices":{"768P":0.5,"2K":0.8},
                 "surcharges":[{"code":"AUDIO","trigger":"AUDIO","amount":0.3}]}
                """);
        assertNotNull(surchargeOnly.validate(PricingPlanEnum.VENDOR_MINIMAX));

        // 带图片每张价 -> 通过
        BillingConfig withInputImage = BillingConfig.parse("""
                {"defaultUnitPrice":0.5,
                 "resolutionPrices":{"768P":0.5,"2K":0.8},
                 "video":{"inputImagePerImage":0.2,"freeInputImages":5}}
                """);
        assertNull(withInputImage.validate(PricingPlanEnum.VENDOR_MINIMAX));
    }

    @Test
    void vendorMiniMaxResolvesLikeFixed() {
        // 取价语义与 FIXED 一致：单价直接来自 defaultUnitPrice / resolutionPrices，无阶梯/相位倍率
        BillingConfig config = BillingConfig.parse("""
                {"defaultUnitPrice":0.5,
                 "resolutionPrices":{"768P":0.5,"2K":0.8},
                 "video":{"inputVideoPerSecond":0.5}}
                """);
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.VENDOR_MINIMAX);
        assertEquals(PricingPlanEnum.VENDOR_MINIMAX, pricing.plan());
        assertEquals(0, new BigDecimal("0.5").compareTo(
                pricing.resolveDefaultPrice(BillingConfig.PricingContext.ofAmount(1), ZERO)));
        assertEquals(0, new BigDecimal("0.8").compareTo(
                pricing.resolveDimensionPrice(List.of("2K"), BillingConfig.PricingContext.ofAmount(1), ZERO)));
    }

    @Test
    void dimensionTieredRejectsMalformedDimensionKeys() {
        // 维度键是自由文本，拼错在 FIXED 下只会静默回落默认价——本方案把它变成拒绝保存
        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{"720P:SOUND":1.2}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("suffix must be"));

        // 顺序颠倒的键永远进不了「逐级去尾」的候选链，配了等于没配
        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{"720P:AUDIO:REFERENCE":1.2}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("suffix must be"));

        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{":AUDIO":1.2}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("must start with a tier name"));

        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{"   ":1.2}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("must not be blank"));

        // 一张档位表都没有 = 退化成固定价，不是维度分档
        assertTrue(BillingConfig.parse("{\"defaultUnitPrice\":1.0}")
                .validate(PricingPlanEnum.DIMENSION_TIERED).contains("requires resolutionPrices"));
        assertTrue(BillingConfig.parse("{\"defaultUnitPrice\":1.0,\"resolutionPrices\":{}}")
                .validate(PricingPlanEnum.DIMENSION_TIERED).contains("requires resolutionPrices"));

        // 只有输入侧分量键，没有输出档位键
        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{"INPUT_VIDEO:720P":0.5}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("requires at least one tier price"));
    }

    @Test
    void dimensionTieredRequiresDefaultUnitPrice() {
        // 厂商档位不可枚举，总有配不到的档；缺兜底价等于「识别不了的请求免费出账」
        assertTrue(BillingConfig.parse("""
                {"resolutionPrices":{"720P":1.0,"720P:AUDIO":1.2}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("requires defaultUnitPrice"));
    }

    @Test
    void dimensionTieredRejectsDoubleCountedInputSeconds() {
        // 输入秒既折进计费量、又按分量单独成价 = 同一批秒收两次
        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "resolutionPrices":{"720P":1.0,"720P:REFERENCE":0.6},
                 "video":{"quantityMode":"SUM","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("billed twice"));

        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "resolutionPrices":{"720P":1.0,"720P:REFERENCE":0.6},
                 "video":{"quantityMode":"OUTPUT_PLUS_MAX","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED).contains("billed twice"));

        // 口径为「仅输出」时输入秒不折进量，单独成价是合法的（MiniMax 那种结构）
        assertNull(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "resolutionPrices":{"720P":1.0,"720P:REFERENCE":0.6},
                 "video":{"quantityMode":"OUTPUT","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED));
    }

    @Test
    void fixedAlsoRejectsDoubleCountedInputSeconds() {
        // 这条校验此前只挂在 DIMENSION_TIERED 上：FIXED 下配 SUM + 独立输入秒单价，
        // 同一批输入秒会被收两次且保存不报错（静默资损）。
        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "video":{"quantityMode":"SUM","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.FIXED).contains("billed twice"));

        assertTrue(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "video":{"quantityMode":"OUTPUT_PLUS_MAX","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.FIXED).contains("billed twice"));

        // 仅输出 + 独立分量合法（MiniMax 那种「输入秒单独成价」的结构）
        assertNull(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "video":{"quantityMode":"OUTPUT","inputVideoPerSecond":0.5}}
                """).validate(PricingPlanEnum.FIXED));
    }

    @Test
    void tierModeIsRejectedForEveryNonTieredPlan() {
        // tierMode 原本只在 validate() 末尾检查，而 DIMENSION_TIERED / VENDOR_MINIMAX /
        // PEAK_OFF_PEAK 三个分支各自直接 return —— 僵尸字段能静默存进 config_json。
        // 前端只在 TIERED 下写它，所以命中者只会是手工改配置或历史迁移，
        // 但「配置意图与方案不一致」不该被静默接受。
        assertEquals("tierMode is only allowed for TIERED plan",
                BillingConfig.parse("""
                        {"defaultUnitPrice":1.0,
                         "resolutionPrices":{"720P":1.0},
                         "tierMode":"PROGRESSIVE"}
                        """).validate(PricingPlanEnum.DIMENSION_TIERED));

        assertEquals("tierMode is only allowed for TIERED plan",
                BillingConfig.parse("""
                        {"defaultUnitPrice":1.0,
                         "resolutionPrices":{"720P":1.0},
                         "video":{"inputImagePerImage":0.2},
                         "tierMode":"PROGRESSIVE"}
                        """).validate(PricingPlanEnum.VENDOR_MINIMAX));

        // pricing 本身合法，证明拦截来自 tierMode 而不是别的校验先命中
        assertEquals("tierMode is only allowed for TIERED plan",
                BillingConfig.parse("""
                        {"defaultUnitPrice":1.0,
                         "pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+8","phases":[
                           {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                            "phase":"PEAK","multiplier":1},
                           {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK","multiplier":0.8}]},
                         "tierMode":"PROGRESSIVE"}
                        """).validate(PricingPlanEnum.PEAK_OFF_PEAK));

        // FIXED 原本就走末尾检查，这里是回归保护
        assertEquals("tierMode is only allowed for TIERED plan",
                BillingConfig.parse("{\"defaultUnitPrice\":1.0,\"tierMode\":\"PROGRESSIVE\"}")
                        .validate(PricingPlanEnum.FIXED));

        // plan 缺省按 FIXED 处理，同一个僵尸字段照样拒绝
        assertEquals("tierMode is only allowed for TIERED plan",
                BillingConfig.parse("{\"defaultUnitPrice\":1.0,\"tierMode\":\"PER_REQUEST\"}")
                        .validate(null));

        // TIERED 下 PROGRESSIVE 是正常配置，不能被误伤
        assertNull(BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "tierMode":"PROGRESSIVE",
                 "tier":[{"condition":{"from":0},
                   "unitPrices":{"input":4,"output":20,"cacheRead":0.2,"cacheWrite":5,
                                 "cacheWrite5m":5,"cacheWrite1h":5}}]}
                """).validate(PricingPlanEnum.TIERED));
    }

    @Test
    void fixedPlanRejectsResolutionPrices() {
        // 固定价不呈现档位：界面上没有这张表，库里就不该有——
        // 否则取价按 {@code resolutionPrices} 命中，而没有任何界面能看见或改它。
        assertEquals("FIXED plan does not accept resolutionPrices; use DIMENSION_TIERED instead",
                BillingConfig.parse("""
                        {"defaultUnitPrice":1.0,"resolutionPrices":{"720P":1.0}}
                        """).validate(PricingPlanEnum.FIXED));

        // plan 缺省按 FIXED 处理（PricingPlanEnum.fromCode 同口径），一样拒绝
        assertEquals("FIXED plan does not accept resolutionPrices; use DIMENSION_TIERED instead",
                BillingConfig.parse("""
                        {"defaultUnitPrice":1.0,"resolutionPrices":{"720P":1.0}}
                        """).validate(null));

        // 维度分档价照旧接受同一张表（本方案的立身之本）
        assertNull(BillingConfig.parse("""
                {"defaultUnitPrice":1.0,"resolutionPrices":{"720P":1.0}}
                """).validate(PricingPlanEnum.DIMENSION_TIERED));

        // TIERED 仍可叠加属性档位（阶梯与属性分档是两根正交的轴），不能被这次收紧误伤
        assertNull(BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20},
                 "resolutionPrices":{"720P":1.0},
                 "tier":[{"condition":{"from":0},
                   "unitPrices":{"input":4,"output":20,"cacheRead":0.2,"cacheWrite":5,
                                 "cacheWrite5m":5,"cacheWrite1h":5}}]}
                """).validate(PricingPlanEnum.TIERED));
    }

    @Test
    void dimensionTieredAcceptsFullKeySpaceAndResolvesLikeFixed() {
        BillingConfig config = BillingConfig.parse("""
                {"defaultUnitPrice":1.0,
                 "resolutionPrices":{"720P":1.0,"720P:AUDIO":1.2,"720P:REFERENCE":0.6,
                                     "720P:REFERENCE:AUDIO":1.5,"INPUT_VIDEO:720P":0.8}}
                """);
        assertNull(config.validate(PricingPlanEnum.DIMENSION_TIERED));

        // 取价与 FIXED 完全一致（无阶梯、无相位倍率），增量只在保存期校验
        BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.DIMENSION_TIERED);
        assertEquals(PricingPlanEnum.DIMENSION_TIERED, pricing.plan());
        assertEquals(0, new BigDecimal("1.5").compareTo(
                pricing.resolveDimensionPrice(List.of("720P:REFERENCE:AUDIO", "720P:REFERENCE", "720P"),
                        BillingConfig.PricingContext.ofAmount(1), ZERO)));
        // 未命中任何维度键时回落默认价（不是 0）
        assertEquals(0, new BigDecimal("1.0").compareTo(
                pricing.resolveDimensionPrice(List.of("4K:REFERENCE:AUDIO", "4K"),
                        BillingConfig.PricingContext.ofAmount(1), ZERO)));
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
                config.pricingPlan(PricingPlanEnum.FIXED).resolveDefaultPrice(BillingConfig.PricingContext.ofAmount(1), ZERO)));
    }
}
