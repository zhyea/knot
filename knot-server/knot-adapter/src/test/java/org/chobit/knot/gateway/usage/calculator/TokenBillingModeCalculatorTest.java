package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.usage.BillingRuleRef;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * TOKEN 计算器按定价方案分发的计费行为：固定价 / 阶梯价 / 高低峰各自一条计费路径，
 * 共用出账组装（分项单价 -> 算钱 -> 明细）。
 *
 * <p>方案经 {@link NormalizedUsageContext#pricingPlan()} 下发（规则列解析，空或未知回落 FIXED），
 * 单价解析委托 {@code BillingConfig.PricingPlan}，本测试只盯分发与出账口径。</p>
 *
 * <p>金额断言一律用**字符串全等**：对外契约是十进制普通字符串且不设小数位上限，
 * {@code compareTo} 会把 {@code 2} 与 {@code 2.00} 判成相等，恰好放过精度回退。</p>
 */
class TokenBillingModeCalculatorTest {

    private static final String BASE_PRICES =
            "{\"basePrices\":{\"input\":2,\"output\":8,\"cacheRead\":0.2,"
                    + "\"cacheWrite\":2.5,\"cacheWrite5m\":2.5,\"cacheWrite1h\":5}}";

    /** 2026-09-28 是周一，02:30Z 落在 01:00-04:00 高峰窗口内 */
    private static final Instant PEAK = Instant.parse("2026-09-28T02:30:00Z");

    /** 2026-09-30 是周三，无高峰规则命中 -> 兜底低峰 */
    private static final Instant OFF_PEAK = Instant.parse("2026-09-30T02:30:00Z");

    private final TokenBillingModeCalculator calculator = new TokenBillingModeCalculator();

    @Test
    void fixedPlanBillsBasePricesPerKind() {
        NormalizedBillingAmount amount = calculate(rule(BASE_PRICES, "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);

        assertEquals("3.6", amount.totalCost(), "1000×2/1000 + 200×8/1000");
        assertEquals(List.of("uncachedInput", "cachedRead", "cachedWrite", "output"), types(amount));
        assertEquals("2000", detail(amount, "uncachedInput").price(), "每百万单价");
        assertEquals("8000", detail(amount, "output").price());
    }

    @Test
    void fixedPlanFallsBackToDefaultUnitPrice() {
        NormalizedBillingAmount amount = calculate(rule("{\"defaultUnitPrice\":0.01}", "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);

        assertEquals("0.012", amount.totalCost(), "全部回落 defaultUnitPrice");
    }

    @Test
    void cacheWriteFallsBackToInputPriceWhenNotConfigured() {
        NormalizedBillingAmount amount = calculate(rule("{\"basePrices\":{\"input\":2,\"output\":8}}", "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 500L, 0L, 0L), PEAK);

        // 缓存写未配分项价 -> 回落 input 价 2：500×2/1000 = 1
        assertEquals("1", detail(amount, "cachedWrite").cost());
        // 未缓存输入 = 1000 - 0 - 500 = 500 -> 1.0；output 1.6；合计 3.6
        assertEquals("3.6", amount.totalCost());
    }

    @Test
    void tieredPlanHitsTierByTotalTokens() {
        String config = """
                {"basePrices":{"input":4,"output":20},
                 "tier":[{"condition":{"from":0,"to":1000000},"unitPrices":{"input":1}},
                         {"condition":{"from":1000001},"unitPrices":{"input":2}}]}
                """;

        // 阶梯基准是请求总 token（1_100_000 命中第二档），不是 input（400_000 会命中第一档）
        NormalizedBillingAmount second = calculate(rule(config, "TIERED"),
                new TokenBillingUsage(400_000L, 700_000L, 1_100_000L, 0L, 0L, 0L, 0L), PEAK);
        assertEquals("800", detail(second, "uncachedInput").cost(), "400_000×2/1000");
        // 第二档未配 output -> 回落 basePrices 20
        assertEquals("14000", detail(second, "output").cost(), "700_000×20/1000");
        assertEquals("14800", second.totalCost());

        // 档位边界：to 含，总量恰好 1_000_000 仍命中第一档（价 1）
        NormalizedBillingAmount first = calculate(rule(config, "TIERED"),
                new TokenBillingUsage(1_000_000L, 0L, 1_000_000L, 0L, 0L, 0L, 0L), PEAK);
        assertEquals("1000", detail(first, "uncachedInput").cost(), "1_000_000×1/1000");
    }

    @Test
    void tieredPlanWithoutTierConfigFallsBackToBasePrices() {
        NormalizedBillingAmount amount = calculate(rule("{\"basePrices\":{\"input\":4,\"output\":20}}", "TIERED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);

        assertEquals("8", amount.totalCost(), "无 tier 数组 -> 基础价直出");
    }

    @Test
    void peakOffPeakMultiplierBillsPeakAndOffPeakDifferently() {
        String config = """
                {"defaultUnitPrice":0.01,
                 "basePrices":{"input":2,"output":8,"cacheRead":0.2,"cacheWrite":2.5,
                               "cacheWrite5m":2.5,"cacheWrite1h":5},
                 "pricing":{"rateMode":"MULTIPLIER","timezone":"UTC+0","phases":[
                   {"phase":"PEAK","multiplier":1.5,
                    "condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]}},
                   {"phase":"OFF_PEAK","multiplier":0.5,"condition":{"type":"DEFAULT"}}]}}
                """;

        NormalizedBillingAmount peak = calculate(rule(config, "PEAK_OFF_PEAK"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);
        assertEquals("5.4", peak.totalCost(), "高峰 = 基础价 × 1.5");
        // 单价 2 × 1.5 = 3.0（scale 1），每百万换算保留被除数自然位数
        assertEquals("3000.0", detail(peak, "uncachedInput").price());

        NormalizedBillingAmount offPeak = calculate(rule(config, "PEAK_OFF_PEAK"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), OFF_PEAK);
        assertEquals("1.8", offPeak.totalCost(), "低峰 = 基础价 × 0.5");
        assertEquals("1000.0", detail(offPeak, "uncachedInput").price());
    }

    @Test
    void peakOffPeakAbsoluteUsesPhaseOwnPrices() {
        String config = """
                {"basePrices":{"input":4,"output":20},
                 "pricing":{"rateMode":"ABSOLUTE","timezone":"UTC+0","phases":[
                   {"condition":{"weekdays":[1],"windows":[{"start":"01:00","end":"04:00"}]},
                    "phase":"PEAK","unitPrices":{"input":10,"output":50}},
                   {"condition":{"type":"DEFAULT"},"phase":"OFF_PEAK",
                    "unitPrices":{"input":6,"output":30}}]}}
                """;

        // ABSOLUTE 取相位自身单价，与 basePrices 无关，也不做乘法
        NormalizedBillingAmount peak = calculate(rule(config, "PEAK_OFF_PEAK"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);
        assertEquals("20", peak.totalCost(), "1000×10/1000 + 200×50/1000");

        NormalizedBillingAmount offPeak = calculate(rule(config, "PEAK_OFF_PEAK"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), OFF_PEAK);
        assertEquals("12", offPeak.totalCost(), "1000×6/1000 + 200×30/1000");
    }

    @Test
    void ttlSplitBillsCacheWrite5mAnd1hSeparately() {
        // input 1000 / cacheRead 100 / 5m 200 / 1h 300 -> 未缓存输入 400
        NormalizedBillingAmount amount = calculate(rule(BASE_PRICES, "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_400L, 100L, 0L, 200L, 300L), PEAK);

        assertEquals(List.of("uncachedInput", "cachedRead", "cachedWrite5m", "cachedWrite1h", "output"), types(amount));
        assertEquals("0.8", detail(amount, "uncachedInput").cost(), "400×2/1000");
        assertEquals("0.02", detail(amount, "cachedRead").cost(), "100×0.2/1000");
        assertEquals("0.5", detail(amount, "cachedWrite5m").cost(), "200×2.5/1000");
        assertEquals("1.5", detail(amount, "cachedWrite1h").cost(), "300×5/1000");
        assertEquals("4.42", amount.totalCost());
    }

    @Test
    void standardCacheWriteBillsSingleField() {
        NormalizedBillingAmount amount = calculate(rule(BASE_PRICES, "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 100L, 500L, 0L, 0L), PEAK);

        assertEquals(List.of("uncachedInput", "cachedRead", "cachedWrite", "output"), types(amount));
        assertEquals("1.25", detail(amount, "cachedWrite").cost(), "500×2.5/1000");
        assertEquals("3.67", amount.totalCost(), "0.8 + 0.02 + 1.25 + 1.6");
    }

    @Test
    void blankOrUnknownPlanFallsBackToFixed() {
        TokenBillingUsage usage = new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L);

        NormalizedBillingAmount blank = calculate(rule(BASE_PRICES, null), usage, PEAK);
        assertEquals("3.6", blank.totalCost(), "plan 空值 -> FIXED");

        NormalizedBillingAmount unknown = calculate(rule(BASE_PRICES, "GARBAGE"), usage, PEAK);
        assertEquals("3.6", unknown.totalCost(), "未知编码 -> FIXED");
    }

    @Test
    void nullConfigBillsZero() {
        NormalizedBillingAmount amount = calculate(rule(null, "FIXED"),
                new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L), PEAK);

        assertEquals("0", amount.totalCost(), "无配置按 0 出账");
        assertEquals(4, amount.details().size(), "明细仍按标准口径出具");
    }

    @Test
    void tinyCostIsPlainDecimalNotScientificNotation() {
        // 1 token × 0.00001 / 1000 = 0.00000001：BigDecimal.toString() 是 "1E-8"
        // （调整指数 -8 < -6，Jackson 默认沿用 toString 原样写出），String 契约必须落成普通十进制
        NormalizedBillingAmount amount = calculate(rule("{\"basePrices\":{\"input\":0.00001}}", "FIXED"),
                new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L), PEAK);

        String cost = detail(amount, "uncachedInput").cost();
        assertEquals("0.00000001", cost);
        assertFalse(cost.contains("E"), "不得出现科学计数法: " + cost);
    }

    @Test
    void sixDecimalUnitPriceIsNotFlattenedToZero() {
        // 计费单价常见值已是 6 位小数，再除百万量级需要 10 位以上：
        // 1000 × 0.0000015 ÷ 1e6 = 0.0000000015。旧的 8 位定标会把它抹成 0.00000000，
        // 小额真实成本静默归零——这正是取消小数位上限的原因，此处钉住不回退。
        NormalizedBillingAmount amount = calculate(
                rule("{\"basePrices\":{\"input\":0.0000015}}", "FIXED", "1M_TOKENS"),
                new TokenBillingUsage(1_000L, 0L, 1_000L, 0L, 0L, 0L, 0L), PEAK);

        assertEquals("0.0000000015", detail(amount, "uncachedInput").cost());
        assertEquals("0.0000000015", amount.totalCost());
        // 单价换算成每百万口径后仍是 1.5e-6，同样不得被 8 位抹平
        assertEquals("0.0000015", detail(amount, "uncachedInput").price());
    }

    @Test
    void contextResolvesPlanFromRuleColumn() {
        assertEquals(PricingPlanEnum.TIERED,
                context(rule("{}", "TIERED"), new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L)).pricingPlan());
        assertEquals(PricingPlanEnum.FIXED,
                context(rule("{}", null), new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L)).pricingPlan());
        assertEquals(PricingPlanEnum.FIXED,
                context(rule("{}", ""), new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L)).pricingPlan());
        assertEquals(PricingPlanEnum.FIXED,
                context(rule("{}", "GARBAGE"), new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L)).pricingPlan());
        assertEquals(PricingPlanEnum.PEAK_OFF_PEAK,
                context(rule("{}", "peak_off_peak"), new TokenBillingUsage(1L, 0L, 1L, 0L, 0L, 0L, 0L)).pricingPlan());
    }

    private NormalizedBillingAmount calculate(BillingRuleRef rule, TokenBillingUsage usage, Instant occurredAt) {
        return calculator.calculate(context(rule, usage, occurredAt));
    }

    private NormalizedUsageContext context(BillingRuleRef rule, TokenBillingUsage usage, Instant occurredAt) {
        return new NormalizedUsageContext(rule, usage, java.util.Map.of(), occurredAt);
    }

    private NormalizedUsageContext context(BillingRuleRef rule, TokenBillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, java.util.Map.of(), PEAK);
    }

    private static BillingRuleRef rule(String configJson, String pricingPlan) {
        return rule(configJson, pricingPlan, "1K_TOKENS");
    }

    private static BillingRuleRef rule(String configJson, String pricingPlan, String unit) {
        return new BillingRuleRef("TOKEN", configJson, "USD", pricingPlan, unit, "v1");
    }

    private static List<String> types(NormalizedBillingAmount amount) {
        return amount.details().stream().map(NormalizedUsageDetail::type).toList();
    }

    private static NormalizedUsageDetail detail(NormalizedBillingAmount amount, String type) {
        return amount.details().stream()
                .filter(item -> type.equals(item.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing detail: " + type));
    }
}
