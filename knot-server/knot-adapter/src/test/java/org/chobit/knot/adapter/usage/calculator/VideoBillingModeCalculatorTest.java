package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视频计费规则的业界口径回归（2026-10-25，批次 A+B+C）。
 *
 * <p>每条断言都锚定一家真实厂商的公开价格表，避免「自己写的规则自己验」：</p>
 * <ul>
 *   <li>豆包 Seedance 2.5 —— 维度组合键分档（分辨率 × 是否含视频输入）</li>
 *   <li>Kling 可灵 4.0 —— 合成量三档（OUTPUT_PLUS_MAX 一条公式覆盖）</li>
 *   <li>MiniMax Hailuo —— 多分量求和（输出秒 + 输入秒 + 输入图片张，含免费额度）</li>
 *   <li>Vidu Q2 —— PROGRESSIVE 分段累加（首秒阶梯，非线性）</li>
 * </ul>
 *
 * <p>金额断言用字符串全等：对外契约不设小数位上限，{@code compareTo} 会把
 * {@code 1} 与 {@code 1.00} 判成相等，放过精度回退。</p>
 */
class VideoBillingModeCalculatorTest {

    private final VideoBillingModeCalculator calculator = new VideoBillingModeCalculator();

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-25T02:30:00Z");

    // ==================== P2 维度分档（豆包 Seedance 2.5） ====================

    /** 豆包 480p/720p：不含视频输入 70 元/百万token，含视频输入 42；1080p 对应 77 / 46 */
    private static final String DOUBAO = """
            {
              "defaultUnitPrice": 70,
              "resolutionPrices": {
                "720P": 70,
                "720P:REFERENCE": 42,
                "1080P": 77,
                "1080P:REFERENCE": 46
              }
            }
            """;

    @Test
    void doubaoShouldBillHigherWithoutReferenceInput() {
        // 输入不含视频：720P -> 70；10 秒 × 70
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("700", amount.totalCost(), "10 秒 × 720P 无参考输入价 70");
    }

    @Test
    void doubaoShouldBillLowerWithReferenceInput() {
        // 输入含视频：720P:REFERENCE -> 42；10 秒 × 42
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, false, true)));

        assertEquals("420", amount.totalCost(), "组合键 720P:REFERENCE 命中 42，而不是 720P 的 70");
    }

    @Test
    void doubao1080PShouldBillByItsOwnTier() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED"),
                video(10L, 0L, 0L, 1920L, 1080L, false, true)));

        assertEquals("460", amount.totalCost(), "1080P:REFERENCE -> 46");
    }

    // ==================== Q4 Token 化（豆包 Seedance 2.5，单位元/百万token） ====================

    /**
     * 豆包 Seedance 2.5 是「视频模型按 token 计价」：量纲由规则单位 {@code 1M_TOKENS} 声明，
     * 分辨率与「是否含视频输入」退化为<b>分档键</b>而不是计费量。
     */
    @Test
    void doubaoShouldBillByTokenQuantityWhenUnitIsMillionTokens() {
        // 100 万 token、720P、输入不含视频 -> 70 元/百万token
        VideoBillingUsage usage = new VideoBillingUsage(
                new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L),
                5L, 0L, 0L, 921_600L, 1280L, 720L, false, false);

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", "1M_TOKENS"), usage));

        assertEquals("70", amount.totalCost(), "1,000,000 token ÷ 1e6 × 70");
    }

    @Test
    void doubaoShouldApplyReferenceTierUnderTokenQuantity() {
        // 同量，输入含视频 -> 720P:REFERENCE 42 元/百万token
        VideoBillingUsage usage = new VideoBillingUsage(
                new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L),
                5L, 3L, 0L, 921_600L, 1280L, 720L, false, true);

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", "1M_TOKENS"), usage));

        assertEquals("42", amount.totalCost(), "分档键在 token 量纲下照常生效");
    }

    @Test
    void doubaoShouldIgnoreInputComponentsUnderTokenQuantity() {
        // token 量纲下只有一条单位基数（分母），秒/张分量会被同一个分母除错，故不启用
        String config = """
                {
                  "defaultUnitPrice": 70,
                  "video": {"inputVideoPerSecond": 0.5, "inputImagePerImage": 0.2, "freeInputImages": 5}
                }
                """;
        VideoBillingUsage usage = new VideoBillingUsage(
                new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L),
                5L, 4L, 8L, 921_600L, 1280L, 720L, false, false);

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED", "1M_TOKENS"), usage));

        assertEquals("70", amount.totalCost(), "输入侧分量在 token 量纲下不参与");
        assertEquals(1, amount.details().size(), "只出输出分量一条明细");
    }

    @Test
    void shouldStillBillBySecondsWhenUnitIsPerSecond() {
        // 同一份豆包配置，单位改回 PER_SECOND 就按秒——量纲完全由单位决定
        VideoBillingUsage usage = new VideoBillingUsage(
                new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L),
                5L, 0L, 0L, 921_600L, 1280L, 720L, false, false);

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", "PER_SECOND"), usage));

        assertEquals("350", amount.totalCost(), "5 秒 × 70，token 不再参与");
    }

    @Test
    void shouldFallbackToSecondsWhenUnitMissing() {
        // 单位缺失 -> 模式默认 PER_SECOND（不是 1K_TOKENS），与管理端表单同源
        VideoBillingUsage usage = new VideoBillingUsage(
                new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L),
                5L, 0L, 0L, 921_600L, 1280L, 720L, false, false);

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", null), usage));

        assertEquals("350", amount.totalCost(), "单位缺失按秒，不是按 token");
    }

    // ==================== 分辨率档位键（厂商档位名不可枚举） ====================

    /**
     * MiniMax Hailuo 的档位是 768P / 2K / 480P——不在标准尺寸表里。
     * 回归意义：分辨率键必须优先取<b>请求体声明的档位名</b>，
     * 靠宽高反推只能认标准尺寸，768P 会退化成裸串或空值，配了价也吃不到。
     */
    @Test
    void shouldUseDeclaredResolutionNameFromRequest() {
        String config = """
                {"defaultUnitPrice": 9, "resolutionPrices": {"768P": 0.50, "2K": 0.80}}
                """;
        NormalizedUsageContext ctx = new NormalizedUsageContext(
                rule(config, "FIXED"),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L, 0L, 0L, 0L, false, false),
                Map.of("resolution", "768P"), OCCURRED_AT);

        NormalizedBillingAmount amount = calculator.calculate(ctx);

        assertEquals("5.00", amount.totalCost(), "10 秒 × 768P 价 0.50（不是默认价 9）");
        assertEquals("VIDEO:768P", amount.details().get(0).type());
    }

    @Test
    void shouldNormalizeDeclaredResolutionCaseAndSeparators() {
        // 厂商写法五花八门：768p / 768P / "768 P" / 768-P 都应落到同一个键
        String config = "{\"defaultUnitPrice\": 9, \"resolutionPrices\": {\"768P\": 0.50}}";
        for (String raw : new String[]{"768p", "768P", "768 P", "768-P", "768_p"}) {
            NormalizedUsageContext ctx = new NormalizedUsageContext(
                    rule(config, "FIXED"),
                    new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L, 0L, 0L, 0L, false, false),
                    Map.of("resolution", raw), OCCURRED_AT);

            assertEquals("5.00", calculator.calculate(ctx).totalCost(), "写法 " + raw + " 应归一到 768P");
        }
    }

    @Test
    void shouldFallBackToWidthHeightWhenResolutionNotDeclared() {
        // 没有文本声明时才由宽高反推（1366x768 -> 768P）
        String config = "{\"defaultUnitPrice\": 9, \"resolutionPrices\": {\"768P\": 0.50}}";
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L,
                        1_049_088L, 1366L, 768L, false, false)));

        assertEquals("5.00", amount.totalCost(), "宽高反推 1366x768 -> 768P");
    }

    @Test
    void declaredResolutionWinsOverWidthHeight() {
        // 请求体声明优先于宽高：声明 2K 时即便宽高是 768 也按 2K 计价
        String config = "{\"defaultUnitPrice\": 9, \"resolutionPrices\": {\"768P\": 0.50, \"2K\": 0.80}}";
        NormalizedUsageContext ctx = new NormalizedUsageContext(
                rule(config, "FIXED"),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 0L, 0L,
                        1_049_088L, 1366L, 768L, false, false),
                Map.of("resolution", "2K"), OCCURRED_AT);

        assertEquals("8.00", calculator.calculate(ctx).totalCost(), "声明的 2K 优先于宽高反推的 768P");
    }

    @Test
    void shouldDegradeToBareResolutionWhenCombinationNotConfigured() {
        // 只配了 720P / 1080P 的老配置：reference=true 也没有组合键可命中，必须回落 720P 而不是报错或归零
        String legacy = "{\"defaultUnitPrice\": 0.02, \"resolutionPrices\": {\"720P\": 0.05, \"1080P\": 0.1}}";
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(legacy, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, true, true)));

        assertEquals("0.50", amount.totalCost(), "组合键未配置时逐级降级到 720P（老配置零改动）");
    }

    // ==================== Q2 合成量（Kling 可灵 4.0） ====================

    private static final String KLING = "{\"defaultUnitPrice\": 1.0, \"video\": {\"quantityMode\": \"OUTPUT_PLUS_MAX\"}}";

    @Test
    void klingShouldBillOutputOnlyWhenNoInputVideo() {
        // 无视频输入：总时长 = 输出时长
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(KLING, "FIXED"),
                video(5L, 0L, 0L, 1920L, 1080L, false, false)));

        assertEquals("5.0", amount.totalCost(), "无输入 -> 输出 5 秒（不是 5+5=10）");
    }

    @Test
    void klingShouldDoubleOutputWhenInputShorterThanOutput() {
        // 输入 3 ≤ 输出 5：总时长 = 输出 × 2 = 10
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(KLING, "FIXED"),
                video(5L, 3L, 0L, 1920L, 1080L, false, false)));

        assertEquals("10.0", amount.totalCost(), "输入 ≤ 输出 -> 输出×2 = 10");
    }

    @Test
    void klingShouldSumWhenInputLongerThanOutput() {
        // 输入 8 > 输出 5：总时长 = 输入 + 输出 = 13
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(KLING, "FIXED"),
                video(5L, 8L, 0L, 1920L, 1080L, false, false)));

        assertEquals("13.0", amount.totalCost(), "输入 > 输出 -> 输入+输出 = 13");
    }

    @Test
    void sumQuantityModeShouldAddInputSeconds() {
        String sum = "{\"defaultUnitPrice\": 0.5, \"video\": {\"quantityMode\": \"SUM\"}}";
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(sum, "FIXED"),
                video(10L, 4L, 0L, 1280L, 720L, false, false)));

        assertEquals("7.0", amount.totalCost(), "(10+4) 秒 × 0.5");
    }

    // ==================== Q3 多分量求和（MiniMax Hailuo） ====================

    /**
     * MiniMax-H3 768P：输出视频 0.50/秒、输入视频 0.50/秒、输入图片 0.20/张（前 5 张免费）。
     */
    private static final String MINIMAX = """
            {
              "defaultUnitPrice": 0.50,
              "video": {
                "inputVideoPerSecond": 0.50,
                "inputImagePerImage": 0.20,
                "freeInputImages": 5
              }
            }
            """;

    @Test
    void minimaxShouldSumThreeComponentsWithFreeImageQuota() {
        // 输出 10 秒 × 0.50 = 5.0；输入视频 4 秒 × 0.50 = 2.0；输入图片 (8-5) 张 × 0.20 = 0.6
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(MINIMAX, "FIXED"),
                video(10L, 4L, 8L, 1280L, 720L, false, false)));

        assertEquals("7.60", amount.totalCost(), "5.00 + 2.00 + 0.60");
        assertEquals(3, amount.details().size(), "三个分量各出一条明细");
        assertEquals("VIDEO:720P", amount.details().get(0).type());
        assertEquals("VIDEO_INPUT_VIDEO", amount.details().get(1).type());
        assertEquals("VIDEO_INPUT_IMAGE", amount.details().get(2).type());
        assertEquals(3L, amount.details().get(2).tokens(), "8 张扣掉 5 张免费额度 -> 计费 3 张");
    }

    /**
     * 分辨率取不到时，输入侧分量的键必须退化成<b>不带分辨率</b>的分量键（{@code INPUT_VIDEO}），
     * 而不是拼出 {@code INPUT_VIDEO:null} 这种脏键——脏键配不出来也查不到，
     * 只会静默回落到分量默认价，让「不分档的输入视频价」永远配不上。
     */
    @Test
    void inputComponentsShouldUseResolutionAgnosticKeyWhenResolutionUnknown() {
        String config = """
                {
                  "defaultUnitPrice": 0.5,
                  "video": {"inputVideoPerSecond": 0.9, "inputImagePerImage": 0.7},
                  "resolutionPrices": {"INPUT_VIDEO": 0.3, "INPUT_IMAGE": 0.1}
                }
                """;

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"),
                new VideoBillingUsage(TokenBillingUsage.empty(), 10L, 4L, 2L, 0L, 0L, 0L, false, false)));

        assertEquals("6.4", amount.totalCost(),
                "输出 10×0.5 + 输入视频 4×0.3(INPUT_VIDEO) + 输入图片 2×0.1(INPUT_IMAGE)，不是分量默认价 0.9 / 0.7");
    }

    @Test
    void minimaxShouldNotBillInputVideoWhenAbsent() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(MINIMAX, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("5.00", amount.totalCost(), "无输入时只剩输出分量");
        assertEquals(1, amount.details().size());
    }

    // ==================== VENDOR_MINIMAX 专属口径（MiniMax H3 官方价表） ====================
    //
    // 官方规则（2026-10 截图）：
    //   输出视频每秒 = 输入视频每秒（同档位成对相等，如 768P 都是 0.50、2K 都是 0.80）
    //   输入图片每张固定价（H3/H3-Highspeed 0.20、Lite 0.13），不随分辨率变，前 5 张免费
    //   视频输入按输入时长及所选模型的输出分辨率计费；音频输入免费

    private static final String MINIMAX_H3 = """
            {
              "defaultUnitPrice": 0.50,
              "resolutionPrices": {"2K": 0.80, "768P": 0.50},
              "video": {"inputImagePerImage": 0.20, "freeInputImages": 5}
            }
            """;

    @Test
    void vendorMiniMaxShouldSumThreeComponentsOnOfficialPrices() {
        // 输出 10 秒 × 0.50 = 5.00；输入 4 秒 × 0.50（同档位输出价回落）= 2.00；
        // 图片 (8-5) 张 × 0.20 = 0.60  => 7.60
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(MINIMAX_H3, "VENDOR_MINIMAX"),
                video(10L, 4L, 8L, 1366L, 768L, false, false)));

        assertEquals("7.60", amount.totalCost(), "5.00 + 2.00 + 0.60");
        assertEquals(3, amount.details().size(), "输出 / 输入视频 / 输入图片 各一条");
        assertEquals("VIDEO:768P", amount.details().get(0).type());
        assertEquals("VIDEO_INPUT_VIDEO", amount.details().get(1).type());
        assertEquals("VIDEO_INPUT_IMAGE", amount.details().get(2).type());
        assertEquals(3L, amount.details().get(2).tokens(), "8 张扣 5 张免费 -> 计费 3 张");
    }

    @Test
    void vendorMiniMaxShouldPickTierPriceByOutputResolution() {
        // 2K 档：输出 10 秒 × 0.80 = 8.00；输入 4 秒 × 0.80 = 3.20；图片 0 张 => 11.20
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(MINIMAX_H3, "VENDOR_MINIMAX"),
                video(10L, 4L, 0L, 2560L, 1440L, false, false)));

        assertEquals("11.20", amount.totalCost(), "2K 档输出/输入均 0.80");
        assertEquals("VIDEO:2K", amount.details().get(0).type(), "2560x1440 归一到 2K");
    }

    @Test
    void vendorMiniMaxShouldHonorInputVideoPriceOverride() {
        // INPUT_VIDEO:768P 单配 0.30：输出 10×0.50=5.00 + 输入 4×0.30=1.20 => 6.20
        String config = """
                {
                  "defaultUnitPrice": 0.50,
                  "resolutionPrices": {"768P": 0.50, "INPUT_VIDEO:768P": 0.30},
                  "video": {"inputImagePerImage": 0.20, "freeInputImages": 5}
                }
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(10L, 4L, 0L, 1366L, 768L, false, false)));

        assertEquals("6.20", amount.totalCost(), "输入价覆盖生效，不是回落输出价 5.00+2.00");
    }

    @Test
    void vendorMiniMaxShouldDefaultFreeImagesToFive() {
        // 未配 freeInputImages -> 按 MiniMax 默认 5 张免费：3 张全免、8 张只收 3 张
        String config = """
                {"defaultUnitPrice": 0.50, "video": {"inputImagePerImage": 0.13}}
                """;
        NormalizedBillingAmount threeImages = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(10L, 0L, 3L, 1366L, 768L, false, false)));
        assertEquals("5.00", threeImages.totalCost(), "3 张未超 5 张免费额度");
        assertEquals(1, threeImages.details().size(), "无图片明细");

        NormalizedBillingAmount eightImages = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(10L, 0L, 8L, 1366L, 768L, false, false)));
        assertEquals("5.39", eightImages.totalCost(), "输出 5.00 + 图片 3×0.13=0.39");
    }

    @Test
    void vendorMiniMaxShouldNotChargeAnySurchargeForAudio() {
        // 配了 AUDIO 附加费也不收：MiniMax 音频输入免费
        String config = """
                {
                  "defaultUnitPrice": 0.50,
                  "video": {"inputImagePerImage": 0.20},
                  "surcharges": [{"code": "AUDIO", "trigger": "AUDIO", "amount": 0.30}]
                }
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(10L, 0L, 0L, 1366L, 768L, true, false)));

        assertEquals("5.00", amount.totalCost(), "只有输出分量，音频免费不加 0.30");
        assertEquals(1, amount.details().size(), "无附加费明细");
    }

    @Test
    void vendorMiniMaxShouldNeverFoldInputSecondsIntoOutput() {
        // 即使配了 quantityMode=SUM，也不得把输入秒折进输出量（否则与输入视频分量重复计费）
        String config = """
                {"defaultUnitPrice": 0.50, "video": {"quantityMode": "SUM", "inputImagePerImage": 0.20}}
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(5L, 3L, 0L, 1366L, 768L, false, false)));

        assertEquals(5L, amount.details().get(0).tokens(), "输出分量仍是 5 秒，没有被 SUM 折成 8");
        assertEquals(3L, amount.details().get(1).tokens(), "输入秒单独作为分量");
        assertEquals("4.00", amount.totalCost(), "输出 2.50 + 输入 1.50");
    }

    // ==================== S2 PROGRESSIVE 分段累加（Vidu Q2） ====================

    /** Vidu Q2-turbo 720P：第 1 秒 8，第 2 秒 10，第 3 秒起 +10/秒 */
    private static final String VIDU_Q2_TURBO_720P = """
            {
              "tierMode": "PROGRESSIVE",
              "tier": [
                {"condition": {"from": 1, "to": 1}, "unitPrices": {"input": 8, "output": 0, "cacheRead": 0, "cacheWrite": 0, "cacheWrite5m": 0, "cacheWrite1h": 0}},
                {"condition": {"from": 2, "to": 2}, "unitPrices": {"input": 10, "output": 0, "cacheRead": 0, "cacheWrite": 0, "cacheWrite5m": 0, "cacheWrite1h": 0}},
                {"condition": {"from": 3},               "unitPrices": {"input": 10, "output": 0, "cacheRead": 0, "cacheWrite": 0, "cacheWrite5m": 0, "cacheWrite1h": 0}}
              ]
            }
            """;

    @Test
    void viduProgressiveShouldAccumulatePerSecond() {
        // 8 秒 -> 8 + 10 + 10×6 = 78
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(VIDU_Q2_TURBO_720P, "TIERED"),
                video(8L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("78", amount.totalCost(), "第1秒8 + 第2秒10 + 第3~8秒各10 = 78");
    }

    @Test
    void viduProgressiveOneSecondOnlyChargesFirstTier() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(VIDU_Q2_TURBO_720P, "TIERED"),
                video(1L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("8", amount.totalCost(), "1 秒只走第 1 档");
    }

    @Test
    void viduProgressiveShouldReportAveragePriceAndTaggedType() {
        // PROGRESSIVE 下没有唯一单价：明细报本笔单位均价，type 带 :PROGRESSIVE 提示下游别按「量×单价」复算
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(VIDU_Q2_TURBO_720P, "TIERED"),
                video(8L, 0L, 0L, 1280L, 720L, false, false)));

        NormalizedUsageDetail detail = amount.details().get(0);
        assertEquals("VIDEO:720P:PROGRESSIVE", detail.type());
        assertEquals("9750000.00", detail.price(), "明细 price 是「每百万单位单价」口径（78÷8=9.75 → ×1e6）");
    }

    /** Vidu Q2-pro 720P：第 1 秒 15，之后 +10/秒；5 秒 -> 15 + 10×4 = 55 */
    @Test
    void viduProgressiveTwoTierShouldAccumulate() {
        String config = """
                {
                  "tierMode": "PROGRESSIVE",
                  "tier": [
                    {"condition": {"from": 1, "to": 1}, "unitPrices": {"input": 15, "output": 0, "cacheRead": 0, "cacheWrite": 0, "cacheWrite5m": 0, "cacheWrite1h": 0}},
                    {"condition": {"from": 2},               "unitPrices": {"input": 10, "output": 0, "cacheRead": 0, "cacheWrite": 0, "cacheWrite5m": 0, "cacheWrite1h": 0}}
                  ]
                }
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "TIERED"),
                video(5L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("55", amount.totalCost(), "第1秒15 + 第2~5秒各10 = 55");
    }

    @Test
    void progressiveShouldNotApplyWhenPlanIsNotTiered() {
        // tierMode 只在 TIERED 下生效（校验层已拒绝该组合保存），计算层不因它改道
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("{\"defaultUnitPrice\": 2}", "FIXED"),
                video(8L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("16", amount.totalCost(), "FIXED 下按量×单价");
    }

    // ==================== S3 附加费（Vidu 音频直出） ====================

    @Test
    void surchargeShouldAddFlatAmountWhenAudioRequested() {
        String config = """
                {
                  "defaultUnitPrice": 2,
                  "surcharges": [{"code": "AUDIO", "trigger": "AUDIO", "amount": 15}]
                }
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, true, false)));

        assertEquals("35", amount.totalCost(), "10 秒 × 2 + 音频附加费 15");
        assertEquals("VIDEO_SURCHARGE", amount.details().get(1).type());
        assertEquals("15", amount.details().get(1).cost());
    }

    @Test
    void surchargeShouldNotApplyWhenNotTriggered() {
        String config = """
                {
                  "defaultUnitPrice": 2,
                  "surcharges": [{"code": "AUDIO", "trigger": "AUDIO", "amount": 15}]
                }
                """;
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"),
                video(10L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("20", amount.totalCost(), "没要声音就不收附加费");
        assertEquals(1, amount.details().size());
    }

    // ==================== 方案层打通（此前分辨率价绕过 PricingPlan） ====================

    /** 低峰窗口 01:00-04:00（周一），倍率 0.5 */
    private static final String PEAK_OFF_PEAK = """
            {
              "defaultUnitPrice": 0.02,
              "resolutionPrices": {"720P": 0.05, "1080P": 0.1},
              "pricing": {
                "rateMode": "MULTIPLIER", "timezone": "UTC+0",
                "phases": [
                  {"condition": {"weekdays": [1], "windows": [{"start": "01:00", "end": "04:00"}]},
                   "phase": "PEAK", "multiplier": 1},
                  {"condition": {"type": "DEFAULT"}, "phase": "OFF_PEAK", "multiplier": 0.5}
                ]
              }
            }
            """;

    @Test
    void resolutionPriceShouldAlsoBeDiscountedByOffPeak() {
        // 2026-10-25 是周日 -> 兜底低峰，倍率 0.5；720P 价 0.05 × 0.5 = 0.025
        // 回归意义：此前命中 resolutionPrices 后直接 return，绕过方案层，错峰对视频完全不生效
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(PEAK_OFF_PEAK, "PEAK_OFF_PEAK"),
                video(10L, 0L, 0L, 1280L, 720L, false, false)));

        assertEquals("0.250", amount.totalCost(), "10 秒 × (0.05 × 0.5)");
    }

    @Test
    void resolutionPriceShouldStayFullDuringPeak() {
        // UTC+0 周一 02:30 落在高峰窗口，倍率 1
        Instant mondayPeak = Instant.parse("2026-10-19T02:30:00Z");
        NormalizedBillingAmount amount = calculator.calculate(new NormalizedUsageContext(
                rule(PEAK_OFF_PEAK, "PEAK_OFF_PEAK"),
                video(10L, 0L, 0L, 1280L, 720L, false, false),
                Map.of(), mondayPeak));

        assertEquals("0.50", amount.totalCost(), "10 秒 × 0.05 × 1");
    }

    // ==================== 兜底语义 ====================

    @Test
    void shouldBillAtLeastOneSecondWhenDurationMissing() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("{\"defaultUnitPrice\": 0.5}", "FIXED"),
                VideoBillingUsage.empty()));

        assertEquals("0.5", amount.totalCost(), "秒数缺失按 1 秒兜底");
    }

    @Test
    void shouldFallbackToRequestBodySizeWhenUsageIsNotVideo() {
        NormalizedBillingAmount amount = calculator.calculate(new NormalizedUsageContext(
                rule("{\"defaultUnitPrice\": 0.02, \"resolutionPrices\": {\"720P\": 0.05}}", "FIXED"),
                new TokenBillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                Map.of("size", "1280x720"), OCCURRED_AT));

        assertEquals("0.05", amount.totalCost(), "类型错配：按 1 秒计，分辨率仍取请求体 size");
        assertTrue(amount.details().get(0).type().startsWith("VIDEO:720P"));
    }

    // ==================== helpers ====================

    private VideoBillingUsage video(long outputSeconds,
                                    long inputSeconds,
                                    long imageCount,
                                    long width,
                                    long height,
                                    boolean sound,
                                    boolean reference) {
        return new VideoBillingUsage(TokenBillingUsage.empty(), outputSeconds, inputSeconds, imageCount,
                width * height, width, height, sound, reference);
    }

    private NormalizedUsageContext context(BillingRuleRef rule, VideoBillingUsage usage) {
        return new NormalizedUsageContext(rule, usage, Map.of(), OCCURRED_AT);
    }

    private BillingRuleRef rule(String config, String plan) {
        return rule(config, plan, "PER_SECOND");
    }

    private BillingRuleRef rule(String config, String plan, String unit) {
        return new BillingRuleRef("VIDEO", config, "USD", plan, unit, "v1");
    }
}
