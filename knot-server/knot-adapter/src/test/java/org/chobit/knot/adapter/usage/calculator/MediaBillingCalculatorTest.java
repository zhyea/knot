package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.adapter.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 媒体用量记录进入计费计算器的行为：按模式摄取（张数 / 秒数）、
 * 视频分辨率价键由宽高推导、错配时零出账不抛异常。
 *
 * <p>金额断言用字符串全等：对外契约不设小数位上限，{@code compareTo} 会把
 * {@code 1} 与 {@code 1.00} 判成相等，放过精度回退。</p>
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

        assertEquals("1.5", amount.totalCost(), "3 张 × 0.5");
        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("IMAGE", detail.type());
        assertEquals(3L, detail.tokens());
    }

    @Test
    void shouldBillAtLeastOneImageWhenCountMissing() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("IMAGE", "PER_IMAGE", SIMPLE_CONFIG),
                ImageBillingUsage.empty()));

        assertEquals("0.5", amount.totalCost(), "上游未上报张数按 1 张兜底");
    }

    @Test
    void shouldBillAudioBySeconds() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("AUDIO", "PER_SECOND", SIMPLE_CONFIG),
                new AudioBillingUsage(TokenBillingUsage.empty(), 120L)));

        assertEquals("60.0", amount.totalCost(), "120 秒 × 0.5（PER_MINUTE 已于 2026-10-17 退役，音频统一按秒）");
        assertEquals("AUDIO", amount.details().get(0).type());
        assertEquals(120L, amount.details().get(0).tokens());
    }

    @Test
    void shouldBillVideoBySecondsFromVideoUsage() {
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("VIDEO", "PER_SECOND", SIMPLE_CONFIG),
                VideoBillingUsage.builder().outputVideoMillis(10_000L).build()));

        assertEquals("5.0", amount.totalCost(), "10 秒 × 0.5");
        assertEquals("VIDEO", amount.details().get(0).type());
    }

    @Test
    void shouldBillPerSecondWhenVideoRuleMissesUnit() {
        // 回归：单位缺失时旧的兜底是 1K_TOKENS（分母 1000），每秒价会被再除 1000 少收三个数量级。
        // 现在一律按模式默认单位（VIDEO -> PER_SECOND，分母 1）归一，与管理端表单同源。
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("VIDEO", null, SIMPLE_CONFIG),
                VideoBillingUsage.builder().outputVideoMillis(10_000L).build()));

        assertEquals("5.0", amount.totalCost(), "10 秒 × 0.5 ÷ 1（不是 ÷ 1000 的 0.005）");
    }

    @Test
    void shouldBillAtLeastOneSecondWhenTokenUsageMeetsVideoMode() {
        // 类型错配：token 用量没有秒数口径，按 1 秒兜底（atLeastOne）
        NormalizedBillingAmount amount = simple.calculate(context(
                rule("VIDEO", "PER_SECOND", SIMPLE_CONFIG),
                new TokenBillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L)));

        assertEquals("0.5", amount.totalCost(), "1 秒 × 0.5");
    }

    @Test
    void shouldUseResolutionPriceDerivedFromVideoSize() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                VideoBillingUsage.builder().outputVideoMillis(10_000L).outputResolution("720P").build()));

        assertEquals("0.50", amount.totalCost(), "10 秒 × 720P 价 0.05");
        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("VIDEO:720P", detail.type());
    }

    @Test
    void shouldUse1080PPriceForFullHdVideo() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                VideoBillingUsage.builder()
                        .outputVideoMillis(10_000L).outputResolution("1080P")
                        .sound(true).referenceVideo(true).build()));

        assertEquals("1.0", amount.totalCost(), "10 秒 × 1080P 价 0.1");
        assertEquals("VIDEO:1080P", amount.details().get(0).type());
    }

    @Test
    void shouldFallBackToDefaultPriceForUnknownResolution() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                VideoBillingUsage.builder().outputVideoMillis(10_000L).outputResolution("480P").build()));

        assertEquals("0.20", amount.totalCost(), "未配置分辨率 → 默认价 0.02");
        // 2026-10-25：854x480 归一成 480P（480P 是业界标准档位，豆包/Vidu/MiniMax 都有这一档），
        // 此前会落成裸尺寸串 854X480，导致 480P 档位永远配不上、只能吃默认价。
        // 配置里仍只有 720P/1080P，故这里依旧回落默认价，变的只是 detail 上的键名。
        assertEquals("VIDEO:480P", amount.details().get(0).type());
    }

    @Test
    void shouldUseDefaultPriceWhenSizeMissing() {
        NormalizedBillingAmount amount = videoCalculator.calculate(context(
                rule("VIDEO", "PER_SECOND", VIDEO_CONFIG),
                VideoBillingUsage.builder().outputVideoMillis(10_000L).build()));

        assertEquals("0.20", amount.totalCost());
        assertEquals("VIDEO", amount.details().get(0).type(), "无分辨率时 detail 不带后缀");
    }

    /**
     * 同一条 VIDEO + token 单位的规则，绑 VideoBillingModeCalculator（模式专属）与走
     * SimpleBillingModeCalculator（通用兜底）必须给出同一金额。
     *
     * <p>探针：上游只上报输出 token（total=0、output=100 万）时，通用兜底若回落<b>输入</b>量
     * 会算出 0 金额——静默漏收，且编译与单测全绿。</p>
     */
    @Test
    void videoTokenRuleShouldBillSameAmountOnBothCalculatorPaths() {
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(0L, 1_000_000L, 0L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .outputResolution("720P")
                .build();
        BillingRuleRef rule = rule("VIDEO", "1M_TOKENS", "{\"defaultUnitPrice\": 70}");

        NormalizedBillingAmount viaVideo = videoCalculator.calculate(context(rule, usage));
        NormalizedBillingAmount viaSimple = simple.calculate(context(rule, usage));

        assertEquals("70", viaVideo.totalCost(), "100 万输出 token ÷ 1e6 × 70");
        assertEquals(viaVideo.totalCost(), viaSimple.totalCost(), "两条路径对同一条规则必须同额");
    }

    @Test
    void shouldReportZeroWhenTokenCalculatorGetsMediaUsage() {
        // 提取器与规则模式错配：热路径不抛异常，按零用量出账
        NormalizedBillingAmount amount = tokenCalculator.calculate(context(
                rule("TOKEN", "1K_TOKENS", SIMPLE_CONFIG),
                new ImageBillingUsage(TokenBillingUsage.empty(), 3L, 0L, 0L, 0L)));

        assertEquals("0", amount.totalCost());
        assertTrue(amount.details().isEmpty());
    }

    private NormalizedUsageContext context(BillingRuleRef rule, BillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, Map.of(), Instant.parse("2026-10-12T02:30:00Z"));
    }

    private BillingRuleRef rule(String mode, String unit, String config) {
        return new BillingRuleRef(mode, config, "USD", "FIXED", unit, "v1");
    }
}
