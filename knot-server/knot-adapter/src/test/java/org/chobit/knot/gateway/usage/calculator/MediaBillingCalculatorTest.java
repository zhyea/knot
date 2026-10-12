package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.AudioBillingUsage;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.ImageBillingUsage;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.VideoBillingUsage;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 媒体用量记录进入计费计算器的行为：按模式摄取（张数 / 秒数）、
 * 视频分辨率价键由宽高推导、错配时零出账不抛异常。
 */
class MediaBillingCalculatorTest {

    private static final String SIMPLE_CONFIG = "{\"defaultUnitPrice\": 0.5}";
    private static final String VIDEO_CONFIG =
            "{\"defaultUnitPrice\": 0.02, \"resolutionPrices\": {\"720P\": 0.05, \"1080P\": 0.1}}";

    private final SimpleBillingModeCalculator simple = new SimpleBillingModeCalculator();
    private final VideoBillingModeCalculator videoCalculator = new VideoBillingModeCalculator();
    private final TokenBillingModeCalculator tokenCalculator = new TokenBillingModeCalculator();

    @Test
    void shouldBillImageByCount() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("IMAGE", "PER_IMAGE", SIMPLE_CONFIG),
                new ImageBillingUsage(TokenBillingUsage.empty(), 3L, 0L, 0L, 0L)));

        assertEquals(0, new BigDecimal("1.5").compareTo(amount.totalCost()), "3 张 × 0.5");
        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("IMAGE", detail.type());
        assertEquals(3L, detail.tokens());
    }

    @Test
    void shouldBillAtLeastOneImageWhenCountMissing() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("IMAGE", "PER_IMAGE", SIMPLE_CONFIG),
                ImageBillingUsage.empty()));

        assertEquals(0, new BigDecimal("0.5").compareTo(amount.totalCost()), "上游未上报张数按 1 张兜底");
    }

    @Test
    void shouldBillAudioBySecondsPerMinute() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("AUDIO", "PER_MINUTE", SIMPLE_CONFIG),
                new AudioBillingUsage(TokenBillingUsage.empty(), 120L)));

        assertEquals(0, new BigDecimal("1.0").compareTo(amount.totalCost()), "120 秒 / 60 × 0.5");
        assertEquals("AUDIO", amount.details().get(0).type());
        assertEquals(120L, amount.details().get(0).tokens());
    }

    @Test
    void shouldBillVideoBySecondsFromVideoUsage() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("VIDEO", "PER_SECOND", SIMPLE_CONFIG),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L, 0L, false, false)));

        assertEquals(0, new BigDecimal("5.0").compareTo(amount.totalCost()), "10 秒 × 0.5");
        assertEquals("VIDEO", amount.details().get(0).type());
    }

    @Test
    void shouldBillAtLeastOneSecondWhenTokenUsageMeetsVideoMode() {
        // 类型错配：token 用量没有秒数口径，按 1 秒兜底（atLeastOne）
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("VIDEO", "PER_SECOND", SIMPLE_CONFIG),
                new TokenBillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L)));

        assertEquals(0, new BigDecimal("0.5").compareTo(amount.totalCost()), "1 秒 × 0.5");
    }

    @Test
    void shouldUseResolutionPriceDerivedFromVideoSize() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 921_600L, 1280L, 720L, false, false)));

        assertEquals(0, new BigDecimal("0.5").compareTo(amount.totalCost()), "10 秒 × 720P 价 0.05");
        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("VIDEO:720P", detail.type());
    }

    @Test
    void shouldUse1080PPriceForFullHdVideo() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 2_073_600L, 1920L, 1080L, true, true)));

        assertEquals(0, new BigDecimal("1.0").compareTo(amount.totalCost()), "10 秒 × 1080P 价 0.1");
        assertEquals("VIDEO:1080P", amount.details().get(0).type());
    }

    @Test
    void shouldFallBackToDefaultPriceForUnknownResolution() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 409_920L, 854L, 480L, false, false)));

        assertEquals(0, new BigDecimal("0.2").compareTo(amount.totalCost()), "未配置分辨率 → 默认价 0.02");
        assertEquals("VIDEO:854X480", amount.details().get(0).type());
    }

    @Test
    void shouldUseDefaultPriceWhenSizeMissing() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L, 0L, false, false)));

        assertEquals(0, new BigDecimal("0.2").compareTo(amount.totalCost()));
        assertEquals("VIDEO", amount.details().get(0).type(), "无分辨率时 detail 不带后缀");
    }

    @Test
    void shouldReportZeroWhenTokenCalculatorGetsMediaUsage() {
        // 提取器与规则模式错配：热路径不抛异常，按零用量出账
        NormalizedBillingAmount amount = tokenCalculator.calculate(context(
                rule("TOKEN", "1K_TOKENS", SIMPLE_CONFIG),
                new ImageBillingUsage(TokenBillingUsage.empty(), 3L, 0L, 0L, 0L)));

        assertEquals(0, BigDecimal.ZERO.compareTo(amount.totalCost()));
        assertTrue(amount.details().isEmpty());
    }

    private NormalizedUsageContext context(BillingRuleEntity rule, BillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, Map.of(), Instant.parse("2026-10-12T02:30:00Z"));
    }

    private BillingRuleEntity rule(String mode, String unit, String config) {
        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setBillingMode(mode);
        rule.setUnit(unit);
        rule.setCurrency("USD");
        rule.setVersionCode("v1");
        rule.setPricingPlan("FIXED");
        rule.setConfigJson(config);
        return rule;
    }
}
