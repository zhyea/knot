package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.ImageTokenUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.adapter.usage.extractor.ImageUsageExtractor;
import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    /** 按 token 计价的模态价表：文本 / 图像输入分价、图像输出、缓存输入各一档 */
    private static final String TOKEN_CONFIG = """
            {"image":{"tokenPrices":{"inputText":5,"inputImage":8,
                                     "cacheReadText":1.25,"cacheReadImage":2,
                                     "outputImage":30}}}
            """;

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

    // ---------- 按 token 量纲：文本 / 图像模态分价 ----------

    @Test
    void shouldBillEveryModalityByItsOwnTokenPrice() {
        // 100 文本 + 1000 图像输入 × 各自输入价，1756 图像输出 × 30，基准为每百万 Token
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"),
                tokenUsage(100L, 1_000L, 0L, 0L, 1_756L)));

        assertEquals("0.06118", amount.totalCost());
        assertEquals(3, amount.details().size());
        assertEquals("inputText", amount.details().get(0).type());
        assertEquals("1M_TOKENS", amount.details().get(0).unit());
        assertEquals("outputImage", amount.details().get(2).type());
    }

    @Test
    void shouldDeductCachedTokensPerModality() {
        // 文本 100 含 40 缓存、图像 1000 含 200 缓存：未缓存量按输入价、缓存量按缓存价
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"),
                tokenUsage(100L, 1_000L, 40L, 200L, 1_756L)));

        assertEquals("0.05983", amount.totalCost());
        assertEquals(5, amount.details().size());
        assertEquals("cachedInputText", amount.details().get(2).type());
        assertEquals("cachedInputImage", amount.details().get(3).type());
    }

    @Test
    void shouldFallBackToInputPriceWhenCachePriceMissing() {
        String noCachePrice = """
                {"image":{"tokenPrices":{"inputText":5,"inputImage":8,"outputImage":30}}}
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", noCachePrice, "1M_TOKENS"),
                tokenUsage(100L, 0L, 40L, 0L, 0L)));

        // 60 × 5 + 40 × 5（缓存价未配 → 按文本输入价）；无图像输入与输出
        assertEquals("0.0005", amount.totalCost());
        assertEquals("5", amount.details().get(1).price());
    }

    @Test
    void shouldRejectTokenRuleWithoutModalityPrice() {
        String missingText = """
                {"image":{"tokenPrices":{"inputImage":8,"outputImage":30}}}
                """;
        BusinessException ex = assertThrows(BusinessException.class, () -> calculator.calculate(context(
                rule("FIXED", missingText, "1M_TOKENS"),
                tokenUsage(100L, 0L, 0L, 0L, 10L))));
        assertTrue(ex.getMessage().contains("inputText"), ex.getMessage());
    }

    @Test
    void shouldRejectTokenRuleWithoutPriceTable() {
        BusinessException ex = assertThrows(BusinessException.class, () -> calculator.calculate(context(
                rule("FIXED", PLAIN_CONFIG, "1M_TOKENS"),
                tokenUsage(100L, 0L, 0L, 0L, 10L))));
        assertTrue(ex.getMessage().contains("tokenPrices"), ex.getMessage());
    }

    @Test
    void shouldEmitOnlyOutputLineWhenNoInputTokens() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"),
                tokenUsage(0L, 0L, 0L, 0L, 1_756L)));

        assertEquals(1, amount.details().size());
        assertEquals("outputImage", amount.details().get(0).type());
        assertEquals(1_756L, amount.details().get(0).quantity());
    }

    @Test
    void shouldBillExtractedResponseEndToEnd() {
        // 接线：上游响应 -> 提取器模态拆分 -> token 计价，避免只验手搓用量
        ImageUsageExtractor extractor = new ImageUsageExtractor(calculator);
        Map<String, Object> body = Map.of(
                "usage", Map.of("total_tokens", 2_856, "input_tokens", 1_100, "output_tokens", 1_756,
                        "input_tokens_details", Map.of("text_tokens", 100, "image_tokens", 1_000)),
                "data", List.of(Map.of("b64_json", "x")));

        ImageBillingUsage usage = extractor.extractUsage(body, Map.of("image", "ref")).billing();
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"), usage));

        assertEquals("0.06118", amount.totalCost());
    }

    @Test
    void shouldNotChargeWhenTokenUsageMissing() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"),
                tokenUsage(0L, 0L, 0L, 0L, 0L)));

        assertEquals("0", amount.totalCost());
        assertTrue(amount.details().isEmpty(), "无 token 用量时不出明细行");
    }

    @Test
    void tokenDetailsShouldCloseOnQuantityTimesPricePerMillion() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("FIXED", TOKEN_CONFIG, "1M_TOKENS"),
                tokenUsage(100L, 1_000L, 40L, 200L, 1_756L)));

        for (NormalizedUsageDetail detail : amount.details()) {
            BigDecimal expected = BigDecimal.valueOf(detail.quantity())
                    .multiply(new BigDecimal(detail.price()))
                    .divide(BigDecimal.valueOf(1_000_000));
            assertEquals(0, expected.compareTo(new BigDecimal(detail.cost())),
                    detail.type() + " 复算式闭合：" + detail.quantity() + " × " + detail.price());
        }
    }

    private static ImageBillingUsage usage(long count, long pixels, long inputImages) {
        long side = pixels > 0 ? (long) Math.sqrt(pixels) : 0L;
        return new ImageBillingUsage(TokenBillingUsage.empty(), ImageTokenUsage.empty(),
                count, pixels, side, side, inputImages);
    }

    /** 按 token 量纲的用量：总量由模态拆分加总，缓存量计入 cacheRead */
    private static ImageBillingUsage tokenUsage(long textInput, long imageInput,
                                                long cachedText, long cachedImage, long output) {
        long input = textInput + imageInput;
        TokenBillingUsage tokens = new TokenBillingUsage(
                input, output, input + output, cachedText + cachedImage, 0L, 0L, 0L);
        ImageTokenUsage split = new ImageTokenUsage(textInput, imageInput, cachedText, cachedImage, output);
        return new ImageBillingUsage(tokens, split, 1L, 0L, 0L, 0L, 0L);
    }

    private static NormalizedUsageContext context(BillingRuleRef rule, BillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, Map.of(), Instant.parse("2026-11-20T02:30:00Z"));
    }

    private static BillingRuleRef rule(String plan, String config) {
        return rule(plan, config, "PER_IMAGE");
    }

    private static BillingRuleRef rule(String plan, String config, String unit) {
        return new BillingRuleRef("IMAGE", config, "USD", plan, unit, "v1");
    }
}
