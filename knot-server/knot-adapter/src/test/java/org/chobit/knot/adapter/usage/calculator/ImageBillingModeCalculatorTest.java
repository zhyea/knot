package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IMAGE 模式计算器：像素档落档、默认单价回落、输入图片分量与明细复算式。
 *
 * 金额断言用字符串全等，避免 {@code compareTo} 放过精度回退。
 */
class ImageBillingModeCalculatorTest {

    /** 截图价表形状：两档有界 + 一档上不封顶 */
    private static final String TIERED_CONFIG = """
            {"defaultUnitPrice":0.008,
             "image":{"tiers":[{"maxPixels":2601123,"price":0.02},
                               {"maxPixels":4624220,"price":0.035},
                               {"price":0.05}],
                      "inputImagePerImage":0.003,
                      "freeInputImages":1}}
            """;

    /** 无像素档、无输入分量的规则：退化为「张数 × 默认单价」 */
    private static final String PLAIN_CONFIG = "{\"defaultUnitPrice\":0.5}";

    private final ImageBillingModeCalculator calculator = new ImageBillingModeCalculator();

    @Test
    void shouldBillByPixelTier() {
        // 1024×1024 = 1,048,576 → 第一档
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(1L, 1_048_576L, 0L)));

        assertEquals("0.02", amount.totalCost());
        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("IMAGE", detail.type());
        assertEquals(1L, detail.quantity());
        assertEquals("PER_IMAGE", detail.unit());
    }

    @Test
    void shouldPickMiddleTierAtBoundary() {
        // 上界含本档
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(1L, 4_624_220L, 0L)));

        assertEquals("0.035", amount.totalCost());
    }

    @Test
    void shouldFallBackToOpenEndedTier() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(1L, 5_000_000L, 0L)));

        assertEquals("0.05", amount.totalCost());
    }

    @Test
    void shouldUseDefaultPriceWhenPixelsUnknown() {
        // 上游未上报尺寸、请求也没带 size：没有可比对的档位，按默认单价出账
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(2L, 0L, 0L)));

        assertEquals("0.016", amount.totalCost(), "2 张 × 0.008");
    }

    @Test
    void shouldRejectPixelsOutsideTierTable() {
        String bounded = """
                {"defaultUnitPrice":0.008,
                 "image":{"tiers":[{"maxPixels":2601123,"price":0.02}]}}
                """;
        BusinessException ex = assertThrows(BusinessException.class, () -> calculator.calculate(context(
                rule("DIMENSION_TIERED", bounded), usage(1L, 5_000_000L, 0L))));
        assertTrue(ex.getMessage().contains("5000000"), ex.getMessage());
    }

    @Test
    void shouldBillInputImagesAfterFreeQuota() {
        // 出 1 张（第一档 0.02）+ 输入 3 张扣 1 张免费 = 2 × 0.003
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(1L, 1_048_576L, 3L)));

        assertEquals("0.026", amount.totalCost());
        assertEquals(2, amount.details().size());
        NormalizedUsageDetail inputDetail = amount.details().get(1);
        assertEquals("IMAGE_INPUT_IMAGE", inputDetail.type());
        assertEquals(2L, inputDetail.quantity());
        assertEquals("0.003", inputDetail.price());
    }

    @Test
    void shouldSkipInputImagesWithinFreeQuota() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(1L, 1_048_576L, 1L)));

        assertEquals("0.02", amount.totalCost());
        assertEquals(1, amount.details().size());
    }

    @Test
    void shouldBillPlainRuleWithoutTierOrInputComponent() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", PLAIN_CONFIG), usage(3L, 0L, 2L)));

        assertEquals("1.5", amount.totalCost(), "3 张 × 0.5；未配输入分量则输入图不计费");
        assertEquals(1, amount.details().size());
    }

    @Test
    void shouldBillOneImageWhenCountMissing() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", PLAIN_CONFIG), ImageBillingUsage.empty()));

        assertEquals("0.5", amount.totalCost());
    }

    @Test
    void detailsShouldCloseOnQuantityTimesPrice() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("DIMENSION_TIERED", TIERED_CONFIG), usage(2L, 5_000_000L, 3L)));

        for (NormalizedUsageDetail detail : amount.details()) {
            BigDecimal expected = BigDecimal.valueOf(detail.quantity())
                    .multiply(new BigDecimal(detail.price()));
            assertEquals(0, expected.compareTo(new BigDecimal(detail.cost())),
                    detail.type() + " 复算式闭合：" + detail.quantity() + " × " + detail.price());
        }
    }

    private static ImageBillingUsage usage(long count, long pixels, long inputImages) {
        long side = pixels > 0 ? (long) Math.sqrt(pixels) : 0L;
        return new ImageBillingUsage(TokenBillingUsage.empty(), count, pixels, side, side, inputImages);
    }

    private static NormalizedUsageContext context(BillingRuleRef rule, BillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, Map.of(), Instant.parse("2026-11-20T02:30:00Z"));
    }

    private static BillingRuleRef rule(String plan, String config) {
        return new BillingRuleRef("IMAGE", config, "USD", plan, "PER_IMAGE", "v1");
    }
}
