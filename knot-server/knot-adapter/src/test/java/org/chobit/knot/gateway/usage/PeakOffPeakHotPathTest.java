package org.chobit.knot.gateway.usage;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 高低峰在**网关热路径**是否真的生效（B7 接线回归）。
 *
 * <p>链路：{@code UsageNormalizationSupport.normalize} -> {@code NormalizedUsageContext.occurredAt}
 * -> {@code TokenBillingModeCalculator} -> {@code PricingContext} -> {@code PeakOffPeakPricing}。
 * 任一段漏传时间，倍率都会退化成 1、金额回到基础价；这里同时盯住高峰与低峰两个时点，
 * 断言它们必须算出**不同**的钱。
 */
class PeakOffPeakHotPathTest {

    private static final String CONFIG = """
            {
              "defaultUnitPrice": 0.01,
              "basePrices": {"input": 2, "output": 8, "cacheRead": 0.2, "cacheWrite": 2.5,
                             "cacheWrite5m": 2.5, "cacheWrite1h": 5},
              "pricing": {
                "rateMode": "MULTIPLIER",
                "timezone": "UTC",
                "phases": [
                  {"phase": "PEAK", "multiplier": 1.5,
                   "condition": {"type": "WEEKDAY_WINDOW", "weekdays": ["MONDAY"],
                                 "windows": [{"start": "01:00", "end": "04:00"}]}},
                  {"phase": "OFF_PEAK", "multiplier": 0.5, "condition": {"type": "DEFAULT"}}
                ]
              }
            }
            """;

    /** 2026-09-28 是周一，02:30Z 落在高峰窗口内 */
    private static final Instant PEAK = Instant.parse("2026-09-28T02:30:00Z");

    /** 2026-09-30 是周三，没有 PEAK 规则命中 -> 兜底低峰 0.5 倍 */
    private static final Instant OFF_PEAK = Instant.parse("2026-09-30T02:30:00Z");

    /**
     * 基础账（1000 input / 200 output，单位 1K）：
     * input = 1000×2/1000 = 2，output = 200×8/1000 = 1.6，合计 3.6。
     */
    @Test
    void shouldApplyPeakMultiplierOnHotPath() {
        NormalizedUsage peak = normalize(PEAK, "PEAK_OFF_PEAK");
        NormalizedUsage offPeak = normalize(OFF_PEAK, "PEAK_OFF_PEAK");

        assertNotNull(peak);
        assertNotNull(offPeak);

        assertEquals(0, new BigDecimal("5.4").compareTo(peak.totalCost()), "peak cost = base x 1.5");
        assertEquals(0, new BigDecimal("1.8").compareTo(offPeak.totalCost()), "off-peak cost = base x 0.5");

        // 明细单价也要带上倍率（已换算成每百万）：input 2 -> 3 / 1
        assertEquals(0, new BigDecimal("3000").compareTo(detailPrice(peak, "uncachedInput")));
        assertEquals(0, new BigDecimal("1000").compareTo(detailPrice(offPeak, "uncachedInput")));
    }

    /** FIXED 方案与时点无关：同一时刻改回固定价，金额必须是未打折的 3.6 */
    @Test
    void shouldKeepFixedPlanAmountUnchanged() {
        NormalizedUsage usage = normalize(PEAK, "FIXED");
        assertNotNull(usage);
        assertEquals(0, new BigDecimal("3.6").compareTo(usage.totalCost()), "fixed cost");
        assertEquals(0, new BigDecimal("2000").compareTo(detailPrice(usage, "uncachedInput")));
    }

    private NormalizedUsage normalize(Instant occurredAt, String pricingPlan) {
        BillingRuleEntity rule = rule();
        rule.setPricingPlan(pricingPlan);
        return UsageNormalizationSupport.normalize(
                usage(), rule, null, occurredAt, new TokenBillingModeCalculator());
    }

    private BigDecimal detailPrice(NormalizedUsage usage, String type) {
        return usage.detail().stream()
                .filter(item -> type.equals(item.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing detail: " + type))
                .price();
    }

    private BillingUsage usage() {
        return new BillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L);
    }

    private BillingRuleEntity rule() {
        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setBillingMode("TOKEN");
        rule.setUnit("ONE_K_TOKENS");
        rule.setCurrency("USD");
        rule.setVersionCode("v1");
        rule.setConfigJson(CONFIG);
        return rule;
    }
}
