package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
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
                video(10L, 0L, 0L, "720P", false, false)));

        assertEquals("700", amount.totalCost(), "10 秒 × 720P 无参考输入价 70");
    }

    @Test
    void doubaoShouldBillLowerWithReferenceInput() {
        // 输入含视频：720P:REFERENCE -> 42；10 秒 × 42
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED"),
                video(10L, 0L, 0L, "720P", false, true)));

        assertEquals("420", amount.totalCost(), "组合键 720P:REFERENCE 命中 42，而不是 720P 的 70");
    }

    @Test
    void doubao1080PShouldBillByItsOwnTier() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED"),
                video(10L, 0L, 0L, "1080P", false, true)));

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
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .outputResolution("720P")
                .build();

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", "1M_TOKENS"), usage));

        assertEquals("70", amount.totalCost(), "1,000,000 token ÷ 1e6 × 70");
    }

    @Test
    void doubaoShouldApplyReferenceTierUnderTokenQuantity() {
        // 同量，输入含视频 -> 720P:REFERENCE 42 元/百万token
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .inputVideoMillis(3_000L)
                .outputResolution("720P")
                .referenceVideo(true)
                .build();

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
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .inputVideoMillis(4_000L)
                .inputImageCount(8L)
                .outputResolution("720P")
                .build();

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED", "1M_TOKENS"), usage));

        assertEquals("70", amount.totalCost(), "输入侧分量在 token 量纲下不参与");
        assertEquals(1, amount.details().size(), "只出输出分量一条明细");
    }

    @Test
    void shouldStillBillBySecondsWhenUnitIsPerSecond() {
        // 同一份豆包配置，单位改回 PER_SECOND 就按秒——量纲完全由单位决定
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .outputResolution("720P")
                .build();

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", "PER_SECOND"), usage));

        assertEquals("350", amount.totalCost(), "5 秒 × 70，token 不再参与");
    }

    @Test
    void shouldFallbackToSecondsWhenUnitMissing() {
        // 单位缺失 -> 模式默认 PER_SECOND（不是 1K_TOKENS），与管理端表单同源
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(new TokenBillingUsage(800_000L, 200_000L, 1_000_000L, 0L, 0L, 0L, 0L))
                .outputVideoMillis(5_000L)
                .outputResolution("720P")
                .build();

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(DOUBAO, "FIXED", null), usage));

        assertEquals("350", amount.totalCost(), "单位缺失按秒，不是按 token");
    }

    // ==================== 分辨率档位键（厂商档位名不可枚举） ====================

    /**
     * MiniMax Hailuo 的档位是 768P / 2K / 480P——不在标准尺寸表里。
     * 回归意义：维度键取的是<b>提取器归一化好的档位名</b>（{@code outputResolution}），不是宽高。
     * 宽高是「由档位名反推」的中间产物，768P 这类非标档反推不出来，配了价也吃不到。
     */
    @Test
    void shouldUseResolutionTierNameAsDimensionKey() {
        String config = """
                {"defaultUnitPrice": 9, "resolutionPrices": {"768P": 0.50, "2K": 0.80}}
                """;

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"), video(10L, 0L, 0L, "768P", false, false)));

        assertEquals("5.00", amount.totalCost(), "10 秒 × 768P 价 0.50（不是默认价 9）");
        assertEquals("VIDEO:768P", amount.details().get(0).type());
    }

    @Test
    void shouldDegradeToDefaultPriceWhenResolutionMissing() {
        // 档位名缺失 -> 没有任何档位键可命中 -> 回落 defaultUnitPrice。
        // 归一化（768p / "768 P" / 768-P -> 768P）在提取层完成，不在计算器里做二遍。
        String config = "{\"defaultUnitPrice\": 9, \"resolutionPrices\": {\"768P\": 0.50}}";

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"), video(10L, 0L, 0L, "", false, false)));

        assertEquals("90", amount.totalCost(), "无档位名时按默认价 9 × 10 秒");
        assertEquals("VIDEO", amount.details().get(0).type(), "无档位时分档类型不带后缀");
    }

    @Test
    void shouldDegradeToBareResolutionWhenCombinationNotConfigured() {
        // 只配了 720P / 1080P 的老配置：reference=true 也没有组合键可命中，必须回落 720P 而不是报错或归零
        String legacy = "{\"defaultUnitPrice\": 0.02, \"resolutionPrices\": {\"720P\": 0.05, \"1080P\": 0.1}}";
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(legacy, "FIXED"),
                video(10L, 0L, 0L, "720P", true, true)));

        assertEquals("0.50", amount.totalCost(), "组合键未配置时逐级降级到 720P（老配置零改动）");
    }

    // ==================== 合成量：OUTPUT_PLUS_MAX（输入秒折进输出量） ====================

    private static final String OUTPUT_PLUS_MAX_CONFIG = "{\"defaultUnitPrice\": 1.0, \"video\": {\"quantityMode\": \"OUTPUT_PLUS_MAX\"}}";

    @Test
    void outputPlusMaxShouldBillOutputOnlyWhenNoInputVideo() {
        // 无视频输入：总时长 = 输出时长
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(OUTPUT_PLUS_MAX_CONFIG, "FIXED"),
                video(5L, 0L, 0L, "1080P", false, false)));

        assertEquals("5.0", amount.totalCost(), "无输入 -> 输出 5 秒（不是 5+5=10）");
    }

    @Test
    void outputPlusMaxShouldDoubleOutputWhenInputShorterThanOutput() {
        // 输入 3 ≤ 输出 5：总时长 = 输出 × 2 = 10
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(OUTPUT_PLUS_MAX_CONFIG, "FIXED"),
                video(5L, 3L, 0L, "1080P", false, false)));

        assertEquals("10.0", amount.totalCost(), "输入 ≤ 输出 -> 输出×2 = 10");
    }

    @Test
    void outputPlusMaxShouldSumWhenInputLongerThanOutput() {
        // 输入 8 > 输出 5：总时长 = 输入 + 输出 = 13
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(OUTPUT_PLUS_MAX_CONFIG, "FIXED"),
                video(5L, 8L, 0L, "1080P", false, false)));

        assertEquals("13.0", amount.totalCost(), "输入 > 输出 -> 输入+输出 = 13");
    }

    @Test
    void sumQuantityModeShouldAddInputSeconds() {
        String sum = "{\"defaultUnitPrice\": 0.5, \"video\": {\"quantityMode\": \"SUM\"}}";
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(sum, "FIXED"),
                video(10L, 4L, 0L, "720P", false, false)));

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
                video(10L, 4L, 8L, "720P", false, false)));

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
                VideoBillingUsage.builder()
                        .outputVideoMillis(10_000L).inputVideoMillis(4_000L).inputImageCount(2L)
                        .build()));

        assertEquals("6.4", amount.totalCost(),
                "输出 10×0.5 + 输入视频 4×0.3(INPUT_VIDEO) + 输入图片 2×0.1(INPUT_IMAGE)，不是分量默认价 0.9 / 0.7");
    }

    @Test
    void minimaxShouldNotBillInputVideoWhenAbsent() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(MINIMAX, "FIXED"),
                video(10L, 0L, 0L, "720P", false, false)));

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
                video(10L, 4L, 8L, "768P", false, false)));

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
                video(10L, 4L, 0L, "2K", false, false)));

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
                video(10L, 4L, 0L, "768P", false, false)));

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
                video(10L, 0L, 3L, "768P", false, false)));
        assertEquals("5.00", threeImages.totalCost(), "3 张未超 5 张免费额度");
        assertEquals(1, threeImages.details().size(), "无图片明细");

        NormalizedBillingAmount eightImages = calculator.calculate(context(
                rule(config, "VENDOR_MINIMAX"),
                video(10L, 0L, 8L, "768P", false, false)));
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
                video(10L, 0L, 0L, "768P", true, false)));

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
                video(5L, 3L, 0L, "768P", false, false)));

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
                video(8L, 0L, 0L, "720P", false, false)));

        assertEquals("78", amount.totalCost(), "第1秒8 + 第2秒10 + 第3~8秒各10 = 78");
    }

    @Test
    void viduProgressiveOneSecondOnlyChargesFirstTier() {
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(VIDU_Q2_TURBO_720P, "TIERED"),
                video(1L, 0L, 0L, "720P", false, false)));

        assertEquals("8", amount.totalCost(), "1 秒只走第 1 档");
    }

    @Test
    void viduProgressiveShouldReportAveragePriceAndTaggedType() {
        // PROGRESSIVE 下没有唯一单价：明细报本笔单位均价，type 带 :PROGRESSIVE 提示下游别按「量×单价」复算
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(VIDU_Q2_TURBO_720P, "TIERED"),
                video(8L, 0L, 0L, "720P", false, false)));

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
                video(5L, 0L, 0L, "720P", false, false)));

        assertEquals("55", amount.totalCost(), "第1秒15 + 第2~5秒各10 = 55");
    }

    @Test
    void progressiveShouldNotApplyWhenPlanIsNotTiered() {
        // tierMode 只在 TIERED 下生效（校验层已拒绝该组合保存），计算层不因它改道
        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("{\"defaultUnitPrice\": 2}", "FIXED"),
                video(8L, 0L, 0L, "720P", false, false)));

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
                video(10L, 0L, 0L, "720P", true, false)));

        assertEquals("35", amount.totalCost(), "10 秒 × 2 + 音频附加费 15");
        assertEquals("VIDEO_SURCHARGE", amount.details().get(1).type());
        assertEquals("15", amount.details().get(1).cost());
        // price 是「每百万单位单价」，不是总额：15 × 1e6，复算 1 × price ÷ 1e6 才回到 15
        assertEquals("15000000", amount.details().get(1).price(),
                "附加费行也必须遵守统一单价口径，否则下游按 tokens×price÷1e6 复算差 1e6 倍");
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
                video(10L, 0L, 0L, "720P", false, false)));

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
                video(10L, 0L, 0L, "720P", false, false)));

        assertEquals("0.250", amount.totalCost(), "10 秒 × (0.05 × 0.5)");
    }

    @Test
    void resolutionPriceShouldStayFullDuringPeak() {
        // UTC+0 周一 02:30 落在高峰窗口，倍率 1
        Instant mondayPeak = Instant.parse("2026-10-19T02:30:00Z");
        NormalizedBillingAmount amount = calculator.calculate(new NormalizedUsageContext(
                rule(PEAK_OFF_PEAK, "PEAK_OFF_PEAK"),
                video(10L, 0L, 0L, "720P", false, false),
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
    void shouldNotChargeWhenUsageIsNotVideo() {
        // 类型错配（VIDEO 规则绑到了非视频提取器）：量无从取，**不出账**。
        // v1 会按 1 秒硬计 + 用请求体 size 取档位价——那是第四条静默多收路径。
        NormalizedBillingAmount amount = calculator.calculate(new NormalizedUsageContext(
                rule("{\"defaultUnitPrice\": 0.02, \"resolutionPrices\": {\"720P\": 0.05}}", "FIXED"),
                new TokenBillingUsage(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                Map.of("size", "1280x720"), OCCURRED_AT));

        assertEquals("0", amount.totalCost(), "不知道量就不收钱");
        assertTrue(amount.details().isEmpty(), "无计费依据时明细留空，便于对账识别");
    }

    @Test
    void shouldCeilFractionalSecondsInsteadOfTruncating() {
        // 毫秒精度的意义：5.9 秒不再被截成 5 秒（少收 15%），按秒向上取整为 6
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .outputVideoMillis(5_900L)
                .build();

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule("{\"defaultUnitPrice\": 0.5}", "FIXED"), usage));

        assertEquals("3.0", amount.totalCost(), "6 秒 × 0.5（v1 会算成 5 秒 → 2.5）");
    }

    // ==================== 维度分档价（DIMENSION_TIERED） ====================

    /**
     * 阿里云万相 wan2.6-i2v-flash「图生视频-基于首帧」：输出分辨率 × 是否输出声音，仅按输出秒计费
     * （首帧图完全不计费）。有声 720P 0.3 / 1080P 0.5，无声 720P 0.15 / 1080P 0.25。
     */
    private static final String WANXIANG_I2V = """
            {
              "defaultUnitPrice": 0.30,
              "resolutionPrices": {
                "720P": 0.15,
                "720P:AUDIO": 0.30,
                "1080P": 0.25,
                "1080P:AUDIO": 0.50
              }
            }
            """;

    @Test
    void dimensionTieredShouldPriceByResolutionAndSoundTier() {
        // 10 秒 720P 有声 -> 720P:AUDIO 0.3
        assertEquals("3.00", calculator.calculate(context(
                rule(WANXIANG_I2V, "DIMENSION_TIERED"),
                video(10L, 0L, 0L, "720P", true, false))).totalCost());
        // 10 秒 720P 无声 -> 720P 0.15
        assertEquals("1.50", calculator.calculate(context(
                rule(WANXIANG_I2V, "DIMENSION_TIERED"),
                video(10L, 0L, 0L, "720P", false, false))).totalCost());
        // 10 秒 1080P 有声 -> 1080P:AUDIO 0.5
        assertEquals("5.00", calculator.calculate(context(
                rule(WANXIANG_I2V, "DIMENSION_TIERED"),
                video(10L, 0L, 0L, "1080P", true, false))).totalCost());
    }

    @Test
    void dimensionTieredShouldFallBackToDefaultPriceForUnconfiguredTier() {
        // 厂商档位不可枚举，总有配不到的档：未命中必须回落 defaultUnitPrice，而不是 0 元出账
        assertEquals("3.00", calculator.calculate(context(
                rule(WANXIANG_I2V, "DIMENSION_TIERED"),
                video(10L, 0L, 0L, "4K", true, false))).totalCost(),
                "4K:AUDIO / 4K 都没配 -> 回落默认价 0.3");
    }

    @Test
    void dimensionTieredShouldTreatReportedInputSecondsAsReferenceTier() {
        // ⭐ 主探针：请求体没带 reference_* 键（Kling 传 video / image），但上游回传了输入时长 3 秒。
        // 若「有输入视频」只看 reference 标志，:REFERENCE 档位价恒不命中 —— 每条请求都按高价多收。
        String config = """
                {
                  "defaultUnitPrice": 1.0,
                  "resolutionPrices": {"720P": 1.0, "720P:REFERENCE": 0.6}
                }
                """;
        assertEquals("6.0", calculator.calculate(context(
                rule(config, "DIMENSION_TIERED"),
                video(10L, 3L, 0L, "720P", false, false))).totalCost(),
                "输入时长 3 秒即「有输入视频」：10 秒 × 720P:REFERENCE 0.6，而不是 720P 的 1.0");
    }

    @Test
    void dimensionTieredShouldPreferMostSpecificReferenceAudioTier() {
        String config = """
                {
                  "defaultUnitPrice": 1.0,
                  "resolutionPrices": {"720P": 1.0, "720P:AUDIO": 1.2,
                                       "720P:REFERENCE": 0.6, "720P:REFERENCE:AUDIO": 0.9}
                }
                """;
        assertEquals("9.0", calculator.calculate(context(
                rule(config, "DIMENSION_TIERED"),
                video(10L, 3L, 0L, "720P", true, false))).totalCost(),
                "含输入 + 有声 -> 命中最具体的 720P:REFERENCE:AUDIO 0.9");
        assertEquals("6.0", calculator.calculate(context(
                rule(config, "DIMENSION_TIERED"),
                video(10L, 3L, 0L, "720P", false, false))).totalCost(),
                "含输入 + 无声 -> 720P:REFERENCE 0.6");
        assertEquals("12.0", calculator.calculate(context(
                rule(config, "DIMENSION_TIERED"),
                video(10L, 0L, 0L, "720P", true, false))).totalCost(),
                "无输入 + 有声 -> 720P:AUDIO 1.2");
    }

    @Test
    void dimensionTieredShouldFoldInputSecondsWhenQuantityModeSaysSo() {
        // 声档与量纲是两根独立的轴：有无输入决定「取哪一档价」，口径决定「量是多少」
        String config = """
                {
                  "defaultUnitPrice": 1.0,
                  "resolutionPrices": {"720P": 1.0, "720P:REFERENCE": 0.6},
                  "video": {"quantityMode": "OUTPUT_PLUS_MAX"}
                }
                """;
        // 输入 3 ≤ 输出 10 -> 量 = 10 + max(3,10) = 20，单价 0.6 -> 12.0
        assertEquals("12.0", calculator.calculate(context(
                rule(config, "DIMENSION_TIERED"),
                video(10L, 3L, 0L, "720P", false, false))).totalCost());
    }

    // ==================== 明细行契约守门 ====================

    @Test
    void everyDetailRowShouldSatisfyAmountTimesPriceFormula() {
        // 契约：每行都满足 cost = tokens × price ÷ 1e6（price 是「每百万单位单价」）。
        // 用全行遍历而不是逐行断言：SURCHARGE 曾把总额直接塞进 price，该行 type/cost 全对，
        // 肉眼与静态检查都看不出，只有复算式能抓住；遍历写法让未来新增的明细行自动被覆盖。
        // 此处比较用 compareTo 而非字符串全等——这是数值恒等式（除法会改变 scale），
        // 与「对外金额文本全等」是两件事。
        String config = """
                {
                  "defaultUnitPrice": 2,
                  "video": {"inputVideoPerSecond": 0.5, "inputImagePerImage": 0.1, "freeInputImages": 1},
                  "surcharges": [{"code": "AUDIO", "trigger": "AUDIO", "amount": 15}]
                }
                """;

        NormalizedBillingAmount amount = calculator.calculate(context(
                rule(config, "FIXED"),
                video(10L, 4L, 3L, "720P", true, true)));

        assertEquals(4, amount.details().size(), "输出 + 输入视频 + 输入图片 + 附加费");
        BigDecimal sum = BigDecimal.ZERO;
        for (NormalizedUsageDetail row : amount.details()) {
            BigDecimal recalculated = BigDecimal.valueOf(row.tokens())
                    .multiply(new BigDecimal(row.price()))
                    .divide(BigDecimal.valueOf(1_000_000L), MathContext.DECIMAL128);
            assertEquals(0, recalculated.compareTo(new BigDecimal(row.cost())),
                    "明细行 " + row.type() + " 复算不闭合：tokens×price÷1e6 = "
                            + recalculated.toPlainString() + "，但 cost = " + row.cost());
            sum = sum.add(new BigDecimal(row.cost()));
        }
        assertEquals(0, sum.compareTo(new BigDecimal(amount.totalCost())),
                "总价必须等于明细行 cost 之和");
    }

    // ==================== helpers ====================

    private VideoBillingUsage video(long outputSeconds,
                                    long inputSeconds,
                                    long imageCount,
                                    String resolution,
                                    boolean sound,
                                    boolean reference) {
        return VideoBillingUsage.builder()
                .outputVideoMillis(outputSeconds * 1_000L)
                .inputVideoMillis(inputSeconds * 1_000L)
                .inputImageCount(imageCount)
                .outputResolution(resolution)
                .sound(sound)
                .referenceVideo(reference)
                .build();
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
