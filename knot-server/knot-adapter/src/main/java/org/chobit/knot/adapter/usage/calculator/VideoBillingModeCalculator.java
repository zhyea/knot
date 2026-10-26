package org.chobit.knot.adapter.usage.calculator;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VIDEO 模式计算器：按「分量」计价，各分量独立取价后求和。
 *
 * <p>2026-10-25 重写（批次 A+B+C）。此前只支持「输出秒数 × 分辨率单价」一条直线，
 * 覆盖不了业界任何一种主流视频计费。现在的模型：</p>
 * <pre>
 * 总价 = Σ 分量(量 × 单价 ÷ 单位基数) + Σ 附加费
 *
 * 分量：
 *   1. 输出视频 —— 量为合成量（OUTPUT / SUM / OUTPUT_PLUS_MAX），单价走维度键分档
 *   2. 输入视频 —— 仅 video.inputVideoPerSecond 或 INPUT_VIDEO:* 分档价存在时启用
 *   3. 输入图片 —— 仅 video.inputImagePerImage 或 INPUT_IMAGE:* 分档价存在时启用
 * </pre>
 *
 * <p><b>三个关键点</b>：</p>
 * <ol>
 *   <li><b>维度分档键是组合键 + 逐级降级</b>：{@code 720P:REFERENCE:AUDIO} → {@code 720P:REFERENCE}
 *       → {@code 720P} → 默认价。豆包的「分辨率 × 是否含视频输入」二维定价天然可表达，
 *       而只配了 {@code 720P} 的老配置零改动继续生效（2026-10-25 前 {@code sound}/{@code reference}
 *       已被提取器解析出来，但计价链路完全没消费它们）。</li>
 *   <li><b>分档价必须过方案层</b>：全部走 {@code PricingPlan.resolveDimensionPrice}，
 *       不再有「命中分辨率价直接 return」的旁路。此前那条旁路让 VIDEO 拿不到阶梯与错峰
 *       （代码注释自标「风险 ①」），而 Vidu 的错峰半价正是这个需求。</li>
 *   <li><b>PROGRESSIVE 阶梯是分段函数不是阶梯价</b>：Vidu Q2「第 1 秒 8、第 2 秒 10、第 3 秒起 +10」
 *       按单位序号逐秒落档累加，与「总量落一档」是两套算法，混用会算错钱。</li>
 * </ol>
 */
@Component
public class VideoBillingModeCalculator extends AbstractBillingModeCalculator {

    /**
     * 请求体里声明分辨率的字段名，按优先级排列（与 {@code MediaUsageReaders.sizeOf} 同口径）。
     *
     * <p>⭐ 为什么计算器要自己读一遍请求体：厂商的分辨率档位名<b>不可枚举</b>——
     * MiniMax 用 768P，Vidu 用 540P，豆包用 480p/720p/1080p，还有 2K、4K 各种写法。
     * 「从宽高反推档位名」只能覆盖标准尺寸（1280x720→720P），遇到 768P 这类非标档
     * 会退化成裸尺寸串或空值，配了价也吃不到。厂商自己声明的档位名才是最权威的键。</p>
     */
    private static final List<String> REQUEST_SIZE_KEYS = List.of("resolution", "size", "dimensions");

    /** 无分辨率键时用于查表的空候选（退化到默认价） */
    private static final List<String> NO_DIMENSION = List.of();

    private static final String SUFFIX_PROGRESSIVE = ":PROGRESSIVE";
    private static final String TYPE_INPUT_VIDEO = "VIDEO_INPUT_VIDEO";
    private static final String TYPE_INPUT_IMAGE = "VIDEO_INPUT_IMAGE";
    private static final String TYPE_SURCHARGE = "VIDEO_SURCHARGE";

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.VIDEO;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleRef rule = rule(context);
        BillingUsage usage = context.usage();
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.configJson());
        int unitSize = unitSize(rule, mode());

        if (!(usage instanceof VideoBillingUsage video)) {
            // 类型错配：用量不是 VIDEO 提取器产出——秒数无从取，按 1 秒兜底，分辨率仍取请求体
            return legacy(context, rule, config, unitSize);
        }

        BillingConfig.VideoConfig videoConfig = config == null ? null : config.video();
        String resolution = resolutionOf(video, context);
        List<String> outputKeys = dimensionKeys(resolution, video.reference(), video.sound());
        BillingConfig.PricingPlan plan = config == null ? null : planOf(config, rule);
        // 量纲由单位声明：1M_TOKENS 等 token 单位取内嵌 token 量（豆包 Seedance 口径），
        // PER_SECOND 取秒（并可走合成量公式）
        boolean tokenQuantity = tokenQuantity(rule, mode());

        List<NormalizedUsageDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // ---- 分量 1：输出视频（合成量 + 维度分档价 + 可选 PROGRESSIVE） ----
        long outputAmount = tokenQuantity
                ? tokenAmount(video.tokenUsage())
                : quantity(video.durationSeconds(), video.inputDurationSeconds(), videoConfig);
        BigDecimal outputCost = componentCost(plan, outputKeys, outputAmount, unitSize, context, config);
        BigDecimal outputPrice = componentUnitPrice(plan, outputKeys, outputAmount, unitSize, context, config);
        total = total.add(outputCost);
        details.add(detail(detailType(resolution, config), outputAmount, outputCost, outputPrice, unitSize));

        // ---- 分量 2：输入视频（按秒，独立分档键前缀） ----
        // ⚠ token 量纲下不启用：一条规则只有一个单位基数（分母），秒/张分量会被同一个分母除错
        if (!tokenQuantity && videoConfig != null && videoConfig.billsInputVideo()
                && video.inputDurationSeconds() > 0) {
            List<String> inputKeys = dimensionKeys(
                    componentKey(BillingConfig.VideoConfig.INPUT_VIDEO_PREFIX, resolution),
                    video.reference(), video.sound());
            BigDecimal fallbackPrice = videoConfig.inputVideoPerSecond();
            BigDecimal price = firstDimensionPrice(plan, inputKeys, context, fallbackPrice);
            BigDecimal cost = cost(video.inputDurationSeconds(), price, unitSize);
            total = total.add(cost);
            details.add(detail(TYPE_INPUT_VIDEO, video.inputDurationSeconds(), cost, price, unitSize));
        }

        // ---- 分量 3：输入图片（按张，扣免费额度）；同 token 量纲下不启用 ----
        if (!tokenQuantity && videoConfig != null && videoConfig.billsInputImage()) {
            long billable = videoConfig.billableImageCount(video.imageCount());
            if (billable > 0) {
                List<String> imageKeys = dimensionKeys(
                        componentKey(BillingConfig.VideoConfig.INPUT_IMAGE_PREFIX, resolution),
                        video.reference(), video.sound());
                BigDecimal fallbackPrice = videoConfig.inputImagePerImage();
                BigDecimal price = firstDimensionPrice(plan, imageKeys, context, fallbackPrice);
                BigDecimal cost = cost(billable, price, unitSize);
                total = total.add(cost);
                details.add(detail(TYPE_INPUT_IMAGE, billable, cost, price, unitSize));
            }
        }

        // ---- 附加费：与用量无关的常数项 ----
        BigDecimal surcharge = config == null ? BigDecimal.ZERO : config.surchargeTotal(video.sound(), video.reference());
        if (surcharge.signum() != 0) {
            total = total.add(surcharge);
            details.add(new NormalizedUsageDetail(TYPE_SURCHARGE, 1L,
                    NormalizedBillingAmount.plainMoney(surcharge),
                    NormalizedBillingAmount.plainMoney(surcharge)));
        }

        return NormalizedBillingAmount.of(usage.tokenUsage().totalTokens(), total, details);
    }

    /**
     * 类型错配时的兜底：秒数取不到，按 1 秒计，分辨率从请求体文本解析。
     *
     * <p>与旧实现行为一致（VIDEO 规则吃到 token 用量不抛异常、按 1 秒出账），
     * 只是单价改走方案层。</p>
     */
    private NormalizedBillingAmount legacy(NormalizedUsageContext context,
                                           BillingRuleRef rule,
                                           BillingConfig config,
                                           int unitSize) {
        String resolution = declaredResolution(context.requestBody());
        long amount = 1L;
        BillingConfig.PricingContext pricingContext = new BillingConfig.PricingContext(amount, context.occurredAt());
        List<String> keys = dimensionKeys(resolution, false, false);
        BigDecimal unitPrice = config == null ? BigDecimal.ZERO
                : planOf(config, rule).resolveDimensionPrice(keys, pricingContext, BigDecimal.ZERO);
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        return NormalizedBillingAmount.of(
                0L,
                totalCost,
                List.of(detail(detailType(resolution, config), amount, totalCost, unitPrice, unitSize))
        );
    }

    // ==================== 分量取价 ====================

    /**
     * 单个分量的金额：PROGRESSIVE 时按逐单位累加和计，否则「量 × 单价 ÷ 基数」。
     */
    private BigDecimal componentCost(BillingConfig.PricingPlan plan,
                                     List<String> keys,
                                     long amount,
                                     int unitSize,
                                     NormalizedUsageContext context,
                                     BillingConfig config) {
        if (plan == null) {
            return BigDecimal.ZERO;
        }
        BillingConfig.PricingContext pricingContext = new BillingConfig.PricingContext(amount, context.occurredAt());
        BigDecimal progressiveSum = config != null && config.progressive()
                ? plan.resolveProgressiveSum(BillingConfig.PriceKind.INPUT, amount, pricingContext, BigDecimal.ZERO)
                : null;
        if (progressiveSum != null) {
            // 逐单位累加和已是「单价域」的总额，除以单位基数即得金额（amount=1 代入复用同一公式）
            return cost(1L, progressiveSum, unitSize);
        }
        return cost(amount, plan.resolveDimensionPrice(keys, pricingContext, BigDecimal.ZERO), unitSize);
    }

    /**
     * 明细里报的单价：PROGRESSIVE 下不存在唯一单价，报<b>本笔单位均价</b>
     * （总额 ÷ 量），与 robin 2026-10-25 拍板口径一致；type 带 {@code :PROGRESSIVE}
     * 后缀提示下游不要按「量 × 单价」复算。
     */
    private BigDecimal componentUnitPrice(BillingConfig.PricingPlan plan,
                                          List<String> keys,
                                          long amount,
                                          int unitSize,
                                          NormalizedUsageContext context,
                                          BillingConfig config) {
        if (plan == null || amount <= 0) {
            return BigDecimal.ZERO;
        }
        BillingConfig.PricingContext pricingContext = new BillingConfig.PricingContext(amount, context.occurredAt());
        if (config != null && config.progressive()) {
            BigDecimal sum = plan.resolveProgressiveSum(BillingConfig.PriceKind.INPUT, amount, pricingContext, BigDecimal.ZERO);
            if (sum != null) {
                return sum.divide(BigDecimal.valueOf(amount), MONEY_DIVISION);
            }
        }
        return plan.resolveDimensionPrice(keys, pricingContext, BigDecimal.ZERO);
    }

    /**
     * 输入侧分量的单价：分档键 -&gt; 分量自带默认价。
     *
     * <p>走 {@code resolveComponentPrice} 而不是 {@code resolveDimensionPrice}：后者在键未命中时
     * 会回落到规则的 {@code defaultUnitPrice}（那是<b>输出视频</b>的价），
     * 把「输入图片 0.20/张」算成「输出视频 0.50/秒」。</p>
     */
    private BigDecimal firstDimensionPrice(BillingConfig.PricingPlan plan,
                                           List<String> keys,
                                           NormalizedUsageContext context,
                                           BigDecimal componentDefault) {
        if (plan == null) {
            return BigDecimal.ZERO;
        }
        return plan.resolveComponentPrice(keys, componentDefault,
                new BillingConfig.PricingContext(1L, context.occurredAt()), BigDecimal.ZERO);
    }

    // ==================== 维度键 ====================

    /**
     * 维度分档键：从最具体到最宽泛排序，供方案层「取第一个命中」。
     *
     * <p>顺序固定为 分辨率 → {@code :REFERENCE} → {@code :AUDIO}，
     * 逐级去掉最右一段。只配了 {@code 720P} 的老配置因此零改动继续生效。</p>
     */
    private List<String> dimensionKeys(String resolution, boolean reference, boolean sound) {
        if (resolution == null || resolution.isBlank()) {
            return NO_DIMENSION;
        }
        List<String> keys = new ArrayList<>();
        if (reference && sound) {
            keys.add(resolution + ":REFERENCE:AUDIO");
        }
        if (reference) {
            keys.add(resolution + ":REFERENCE");
        }
        if (sound) {
            keys.add(resolution + ":AUDIO");
        }
        keys.add(resolution);
        return keys;
    }

    /**
     * 输入侧分量的维度键名：分辨率未知时用「不带分辨率」的分量键。
     *
     * <p>⭐ 不能直接写成 {@code 前缀 + resolution}：分辨率取不到时会拼出
     * {@code INPUT_VIDEO:null} 这种脏键——配不出来也查不到，只能静默回落分量默认价。
     * 而 {@code INPUT_VIDEO} 本身是合法键（「不分档的输入视频价」），
     * 留着这条降级路径，配置才表达得出来。</p>
     */
    private static String componentKey(String prefix, String resolution) {
        return StringUtils.isBlank(resolution)
                ? StringUtils.removeEnd(prefix, ":")
                : prefix + resolution.trim();
    }

    // ==================== 合成量 ====================

    /**
     * 输出分量的计费量：按 {@code video.quantityMode} 把输入秒折进输出秒。
     *
     * <p>默认 {@code OUTPUT}（仅输出秒）与历史行为完全一致，未配 {@code video} 的配置不受影响。</p>
     */
    private long quantity(long outputSeconds, long inputSeconds, BillingConfig.VideoConfig videoConfig) {
        long output = outputSeconds > 0 ? outputSeconds : 1L;
        return videoConfig == null ? output : videoConfig.quantity(output, inputSeconds);
    }

    // ==================== 明细类型 ====================

    private String detailType(String resolution, BillingConfig config) {
        String base = normalizeDetailType("", BillingModeEnum.VIDEO);
        String type = resolution == null ? base : base + ":" + resolution;
        return config != null && config.progressive() ? type + SUFFIX_PROGRESSIVE : type;
    }

    private static BillingConfig.PricingPlan planOf(BillingConfig config, BillingRuleRef rule) {
        return config.pricingPlan(PricingPlanEnum.fromCode(rule.pricingPlan()));
    }

    /**
     * 分辨率键：**请求体声明的档位名优先**，其次由用量宽高推导。
     *
     * <p>顺序不能反：{@code resolution: "768P"} 是厂商自己的档位名，最权威；
     * 而宽高反推只认标准尺寸表，MiniMax 的 768P 反推不出来（会退化成裸串或空值）。
     * 两者都没有时返回 {@code null}，计默认价。</p>
     */
    private String resolutionOf(VideoBillingUsage video, NormalizedUsageContext context) {
        String declared = declaredResolution(context.requestBody());
        if (declared != null) {
            return declared;
        }
        if (video.width() <= 0 || video.height() <= 0) {
            return null;
        }
        return normalizeResolution(video.width() + "x" + video.height());
    }

    /** 请求体里声明的分辨率文本：任一字段命中即用，空值继续找下一个 */
    private String declaredResolution(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return null;
        }
        for (String key : REQUEST_SIZE_KEYS) {
            String normalized = normalizeResolution(requestBody.get(key));
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * 分辨率文本归一化：标准尺寸映射到档位名，**未识别的写法原样大写返回**。
     *
     * <p>原样返回是刻意的——档位名不可枚举（768P、540P、2K、4K 及各厂商自造名），
     * 硬塞进白名单只会让新档位配不上价。{@code auto} 之类的非档位值会落成 {@code AUTO}，
     * 配不上就自然回落默认价，不会误命中。</p>
     */
    private String normalizeResolution(Object value) {
        String text = value == null ? null : StringUtils.trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        String normalized = StringUtils.lowerCase(text)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
        return switch (normalized) {
            case "480", "480p", "854x480" -> "480P";
            case "540", "540p", "960x540" -> "540P";
            case "720", "720p", "1280x720" -> "720P";
            case "768", "768p", "1366x768", "1024x768" -> "768P";
            case "1080", "1080p", "1920x1080" -> "1080P";
            case "1440", "1440p", "2k", "qhd", "2560x1440" -> "2K";
            case "2160", "2160p", "4k", "uhd", "3840x2160" -> "4K";
            default -> StringUtils.upperCase(text);
        };
    }
}
