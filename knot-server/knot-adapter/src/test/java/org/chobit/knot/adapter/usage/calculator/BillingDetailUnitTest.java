package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.TokenUsageDetailKind;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.common.constants.enums.BillingUnitEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 明细行量纲守门（2026-11-07）。
 *
 * <p>起因：Kling 3.0 Turbo 的 Kafka 事件里 {@code billing.detail[0].tokens = 6} 其实是 6 秒，
 * 字段名骗人、量纲只存在于计算过程的局部变量。改名 {@code quantity} 并强制同行带 {@code unit} 后，
 * 本测试钉住「任何计算器的任何一行明细都不能只给量不给单位」，且复算式仍闭合。</p>
 *
 * <p>反向探针价值：若有人把 {@code detail()} 里的 unit 写死成 {@code 1M_TOKENS}，
 * {@link #unitShouldFollowRuleUnitNotBeHardcoded()} 会立刻变红。</p>
 */
class BillingDetailUnitTest {

    private static final Instant AT = Instant.parse("2026-11-07T10:00:00Z");
    private static final String CONFIG = """
            {
              "defaultUnitPrice": 0.8,
              "video": {"inputVideoPerSecond": 0.5, "inputImagePerImage": 0.1, "freeInputImages": 1},
              "surcharges": [{"code": "AUDIO", "trigger": "AUDIO", "amount": 0.2}]
            }
            """;

    private static final String TOKEN_CONFIG = """
            {"basePrices":{"input":2,"output":8,"cacheRead":0.2,"cacheWrite":2.5,
                           "cacheWrite5m":2.5,"cacheWrite1h":5}}
            """;

    private final VideoBillingModeCalculator calculator = new VideoBillingModeCalculator();
    private final TokenBillingModeCalculator tokenCalculator = new TokenBillingModeCalculator();

    @Test
    void everyRowShouldCarryUnitAndCloseAmountFormula() {
        NormalizedBillingAmount amount = calculate("PER_SECOND");

        assertEquals(4, amount.details().size(), "输出 + 输入视频 + 输入图片 + 附加费");
        for (NormalizedUsageDetail row : amount.details()) {
            assertNotNull(row.unit(), "每行必须自带量纲: " + row.type());
            assertNotNull(BillingUnitEnum.fromCode(row.unit()),
                    "unit 必须是 BillingUnitEnum 编码: " + row.unit());
            assertNotNull(row.quantity(), "每行必须给出计费量: " + row.type());
            BigDecimal recalculated = BigDecimal.valueOf(row.quantity())
                    .multiply(new BigDecimal(row.price()))
                    .divide(BigDecimal.valueOf(BillingUnitEnum.unitSizeOf(row.unit())), MathContext.DECIMAL128);
            assertEquals(0, recalculated.compareTo(new BigDecimal(row.cost())),
                    row.type() + " 复算不闭合：quantity × price ÷ unitSize(unit)");
        }

        NormalizedUsageDetail outputRow = amount.details().get(0);
        assertEquals(6L, outputRow.quantity(), "Kling 6 秒产物 -> 计费量 6 秒");
        assertEquals("PER_SECOND", outputRow.unit());
        assertEquals("4.8", outputRow.cost());
        amount.details().stream()
                .filter(row -> "VIDEO_SURCHARGE".equals(row.type()))
                .forEach(row -> assertEquals("PER_REQUEST", row.unit(),
                        "附加费是与用量无关的常数项，单位基数恒为 1"));
    }

    @Test
    void unitShouldFollowRuleUnitNotBeHardcoded() {
        // 同一计算器换规则单位，输出分量的 unit 必须跟着变；附加费行是常数项，不随规则量纲变。
        NormalizedBillingAmount tokenPriced = calculate("1M_TOKENS");
        assertTrue(tokenPriced.details().size() >= 1);
        assertEquals("1M_TOKENS", tokenPriced.details().get(0).unit(),
                "量纲必须来自规则 unit，不许写死");
    }

    @Test
    void nonTokenUnitShouldNotUseTokenDetailKinds() {
        boolean tokenKindUsed = Arrays.stream(TokenUsageDetailKind.values())
                .anyMatch(kind -> calculate("PER_SECOND").details().stream()
                        .anyMatch(row -> kind.key().equals(row.type())));
        assertFalse(tokenKindUsed, "按秒计费的明细不该借用 token 明细类型词表");
    }

    /**
     * TOKEN 模式三档单位：{@code price} 恒为规则配置的原始单价，
     * 金额分母由同行 {@code unit} 推导（1 / 1e3 / 1e6），{@code unit} 不许写死。
     *
     * <p>反向探针价值：若有人把 {@code unit} 写死成 {@code 1M_TOKENS}，
     * 或把复算分母写回固定的 1e6，{@code PER_TOKEN} / {@code 1K_TOKENS} 两档会立刻变红。</p>
     */
    @Test
    void tokenRuleUnitsShouldKeepOriginalPriceAndDriveDenominator() {
        Map<String, String> expectedTotal = Map.of(
                "PER_TOKEN", "3600",
                "1K_TOKENS", "3.6",
                "1M_TOKENS", "0.0036");

        for (Map.Entry<String, String> expected : expectedTotal.entrySet()) {
            String unit = expected.getKey();
            BillingRuleRef rule = new BillingRuleRef("TOKEN", TOKEN_CONFIG, "USD", "FIXED", unit, "v1");
            TokenBillingUsage usage = new TokenBillingUsage(1_000L, 200L, 1_200L, 0L, 0L, 0L, 0L);
            NormalizedBillingAmount amount = tokenCalculator.calculate(
                    new NormalizedUsageContext(rule, usage, Map.of(), AT));

            assertEquals(0, new BigDecimal(expected.getValue()).compareTo(new BigDecimal(amount.totalCost())),
                    unit + " 下总价必须由 unitSize 决定，不许写死 1e6");
            for (NormalizedUsageDetail row : amount.details()) {
                assertEquals(unit, row.unit(), "unit 必须跟随规则: " + row.type());
                BigDecimal recalculated = BigDecimal.valueOf(row.quantity())
                        .multiply(new BigDecimal(row.price()))
                        .divide(BigDecimal.valueOf(BillingUnitEnum.unitSizeOf(row.unit())), MathContext.DECIMAL128);
                assertEquals(0, recalculated.compareTo(new BigDecimal(row.cost())),
                        unit + " / " + row.type() + " 复算不闭合");
            }
            NormalizedUsageDetail inputRow = amount.details().stream()
                    .filter(row -> "uncachedInput".equals(row.type()))
                    .findFirst().orElseThrow();
            assertEquals(0, new BigDecimal("2").compareTo(new BigDecimal(inputRow.price())),
                    unit + " 下 price 必须是配置原值 2，不随单位放大");
        }
    }

    private NormalizedBillingAmount calculate(String unit) {
        BillingRuleRef rule = new BillingRuleRef("VIDEO", CONFIG, "USD", "FIXED", unit, "v1");
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .outputVideoMillis(6_000L)
                .inputVideoMillis(3_000L)
                .inputImageCount(3)
                .outputResolution("720P")
                .sound(true)
                .referenceVideo(false)
                .build();
        return calculator.calculate(new NormalizedUsageContext(rule, usage, Map.of(), AT));
    }
}
