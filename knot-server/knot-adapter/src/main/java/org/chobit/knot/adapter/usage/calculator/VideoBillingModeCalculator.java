package org.chobit.knot.adapter.usage.calculator;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.BillingUnitEnum;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.BillingConfig;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VIDEO 模式计算器：按「分量」计价，各分量独立取价后求和。
 * 总价 = Σ 分量(量 × 单价 ÷ 单位基数) + Σ 附加费；分量为输出视频（合成量 OUTPUT / SUM / OUTPUT_PLUS_MAX，按维度键分档）、
 * 输入视频、输入图片（二者仅在对应分档价或分量价存在时启用）。
 * 维度分档键为组合键并逐级降级：{@code 720P:REFERENCE:AUDIO} → {@code 720P:REFERENCE} → {@code 720P} → 默认价，分档价统一走 {@code PricingPlan.resolveDimensionPrice}。
 * PROGRESSIVE 阶梯按单位序号逐段累加，不是「总量落一档」。
 */
@Slf4j
@Component
public class VideoBillingModeCalculator extends AbstractBillingModeCalculator {

    /** 无分辨率键时用于查表的空候选（退化到默认价） */
    private static final List<String> NO_DIMENSION = List.of();

    private static final String SUFFIX_PROGRESSIVE = ":PROGRESSIVE";
    private static final String TYPE_INPUT_VIDEO = "VIDEO_INPUT_VIDEO";
    private static final String TYPE_INPUT_IMAGE = "VIDEO_INPUT_IMAGE";
    private static final String TYPE_SURCHARGE = "VIDEO_SURCHARGE";

    /** MiniMax 图片免费张数默认值：官方「每次任务前 5 张免费」，配置未显式声明时按此兜底 */
    private static final long MINIMAX_FREE_INPUT_IMAGES = 5L;

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.VIDEO;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleRef rule = rule(context);
        BillingUsage usage = context.usage();
        BillingConfig config = configOf(rule);
        int unitSize = unitSize(rule, mode());
        String unitCode = unitCode(rule, mode());

        if (!(usage instanceof VideoBillingUsage video)) {
            // 类型错配：VIDEO 规则绑到了非视频提取器。量无从取——不出账并留下告警，
            // 不再按 1 秒硬计（那是第四条静默多收路径）。
            return noCharge("VIDEO 规则吃到非视频用量，已跳过计费：" + usage.getClass().getSimpleName());
        }

        // 厂商定制方案走专属口径：MiniMax 价表的结构（分量拆分方式 / 「有输入」的表达位置）通用路径表达不了
        if (context.pricingPlan() == PricingPlanEnum.VENDOR_MINIMAX) {
            return calculateVendorMiniMax(context, rule, config, video, unitSize);
        }

        BillingConfig.VideoConfig videoConfig = config == null ? null : config.video();
        String resolution = video.outputResolution();
        List<String> outputKeys = dimensionKeys(resolution, video.hasInputVideo(), video.sound());
        BillingConfig.PricingPlan plan = config == null ? null : planOf(config, rule);
        // 量纲由单位声明：1M_TOKENS 等 token 单位取内嵌 token 量（豆包 Seedance 口径），
        // PER_SECOND 取秒（并可走合成量公式）
        boolean tokenQuantity = tokenQuantity(rule, mode());

        List<NormalizedUsageDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // ---- 分量 1：输出视频（合成量 + 维度分档价 + 可选 PROGRESSIVE） ----
        long outputAmount = tokenQuantity
                ? tokenAmount(video.tokenUsage())
                : outputAmountSeconds(video, videoConfig);
        BigDecimal outputCost = componentCost(plan, outputKeys, outputAmount, unitSize, context, config);
        BigDecimal outputPrice = componentUnitPrice(plan, outputKeys, outputAmount, unitSize, context, config);
        total = total.add(outputCost);
        details.add(detail(detailType(resolution, config), outputAmount, outputCost, outputPrice, unitCode));

        // ---- 分量 2：输入视频（按秒，独立分档键前缀） ----
        // ⚠ token 量纲下不启用：一条规则只有一个单位基数（分母），秒/张分量会被同一个分母除错
        if (!tokenQuantity && videoConfig != null && videoConfig.billsInputVideo()
                && video.inputSeconds() > 0) {
            List<String> inputKeys = dimensionKeys(
                    componentKey(BillingConfig.VideoConfig.INPUT_VIDEO_PREFIX, resolution),
                    video.hasInputVideo(), video.sound());
            BigDecimal fallbackPrice = videoConfig.inputVideoPerSecond();
            BigDecimal price = firstDimensionPrice(plan, inputKeys, context, fallbackPrice);
            BigDecimal cost = cost(video.inputSeconds(), price, unitSize);
            total = total.add(cost);
            details.add(detail(TYPE_INPUT_VIDEO, video.inputSeconds(), cost, price, unitCode));
        }

        // ---- 分量 3：输入图片（按张，扣免费额度）；同 token 量纲下不启用 ----
        if (!tokenQuantity && videoConfig != null && videoConfig.billsInputImage()) {
            long billable = videoConfig.billableImageCount(video.inputImageCount());
            if (billable > 0) {
                List<String> imageKeys = dimensionKeys(
                        componentKey(BillingConfig.VideoConfig.INPUT_IMAGE_PREFIX, resolution),
                        video.hasInputVideo(), video.sound());
                BigDecimal fallbackPrice = videoConfig.inputImagePerImage();
                BigDecimal price = firstDimensionPrice(plan, imageKeys, context, fallbackPrice);
                BigDecimal cost = cost(billable, price, unitSize);
                total = total.add(cost);
                details.add(detail(TYPE_INPUT_IMAGE, billable, cost, price, unitCode));
            }
        }

        // ---- 附加费：与用量无关的常数项 ----
        BigDecimal surcharge = config == null
                ? BigDecimal.ZERO
                : config.surchargeTotal(video.sound(), video.hasInputVideo());
        if (surcharge.signum() != 0) {
            total = total.add(surcharge);
            // ⭐ 附加费也走 detail()，不许自己拼 NormalizedUsageDetail：quantity=1、unit=PER_REQUEST（基数 1），
            // price 就是附加费本身（每次价）。此时 price 与 cost 数值相同是常数项的自然结果：
            // cost 是总额、price 是单价，quantity × price ÷ 1 恰好闭合，不是重复赋值。
            // 若把别的东西塞进 price，该行 type/cost 全对，静态检查和肉眼都看不出，只有复算式守门能抓住。
            details.add(detail(TYPE_SURCHARGE, 1L, surcharge, surcharge,
                    BillingUnitEnum.PER_REQUEST.code()));
        }

        return NormalizedBillingAmount.of(usage.tokenUsage().totalTokens(), total, details);
    }

    /**
     * VENDOR_MINIMAX 专属口径：MiniMax H3 系列「三分量求和」。
     * 总价 = 输出秒数 × 档位输出单价 + 输入秒数 × 档位输入单价（未单独配置 {@code INPUT_VIDEO:档位} 时回落同档位输出单价）
     * + max(0, 输入图片张数 − 免费张数) × 每张固定单价。
     * 输出量只取输出秒、不折叠输入秒；图片单价不随分辨率变化，音频输入免费。
     */
    private NormalizedBillingAmount calculateVendorMiniMax(NormalizedUsageContext context,
                                                           BillingRuleRef rule,
                                                           BillingConfig config,
                                                           VideoBillingUsage video,
                                                           int unitSize) {
        String unitCode = unitCode(rule, mode());
        BillingConfig.VideoConfig videoConfig = config == null ? null : config.video();
        BillingConfig.PricingPlan plan = config == null ? null : planOf(config, rule);
        String resolution = video.outputResolution();
        // MiniMax 无 reference / audio 折扣档，维度键只取纯档位名
        List<String> outputKeys = dimensionKeys(resolution, false, false);

        List<NormalizedUsageDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // ---- 分量 1：输出视频 —— 仅输出秒（不折叠输入秒，输入秒是独立分量） ----
        long outputAmount = firstAmount(video.outputSeconds(), 1L);
        BigDecimal outputPrice = componentUnitPrice(plan, outputKeys, outputAmount, unitSize, context, config);
        BigDecimal outputCost = componentCost(plan, outputKeys, outputAmount, unitSize, context, config);
        total = total.add(outputCost);
        details.add(detail(detailType(resolution, config), outputAmount, outputCost, outputPrice, unitCode));

        // ---- 分量 2：输入视频 —— 输入秒 × 档位输入单价，未单配时回落同档位输出单价 ----
        long inputAmount = video.inputSeconds();
        if (inputAmount > 0) {
            List<String> inputKeys = dimensionKeys(
                    componentKey(BillingConfig.VideoConfig.INPUT_VIDEO_PREFIX, resolution), false, false);
            BigDecimal inputPrice = firstDimensionPrice(plan, inputKeys, context, outputPrice);
            BigDecimal inputCost = cost(inputAmount, inputPrice, unitSize);
            total = total.add(inputCost);
            details.add(detail(TYPE_INPUT_VIDEO, inputAmount, inputCost, inputPrice, unitCode));
        }

        // ---- 分量 3：输入图片 —— 超出免费额度的张数 × 固定每张价（不随分辨率变） ----
        if (videoConfig != null && video.inputImageCount() > 0) {
            long free = videoConfig.freeInputImages() == null
                    ? MINIMAX_FREE_INPUT_IMAGES
                    : Math.max(videoConfig.freeInputImages(), 0L);
            long billable = Math.max(video.inputImageCount() - free, 0L);
            if (billable > 0) {
                BigDecimal imagePrice = videoConfig.inputImagePerImage() == null
                        ? BigDecimal.ZERO : videoConfig.inputImagePerImage();
                BigDecimal imageCost = cost(billable, imagePrice, unitSize);
                total = total.add(imageCost);
                details.add(detail(TYPE_INPUT_IMAGE, billable, imageCost, imagePrice, unitCode));
            }
        }

        // 音频输入免费：MiniMax 不收取附加费，故无分量 4
        return NormalizedBillingAmount.of(video.tokenUsage().totalTokens(), total, details);
    }

    /**
     * 不出账：用量不足以支撑计价（规则与提取器类型错配）时的统一出口，返回零额 + 空明细。
     */
    private NormalizedBillingAmount noCharge(String reason) {
        log.warn("VIDEO 计费跳过：{}", reason);
        return NormalizedBillingAmount.of(0L, BigDecimal.ZERO, List.of());
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
        return cost(amount, strictOutputPrice(plan, keys, pricingContext, config), unitSize);
    }

    /**
     * 明细里报的单价：PROGRESSIVE 下报本笔单位均价（总额 ÷ 量），且 type 带 {@code :PROGRESSIVE} 后缀，下游不可按「量 × 单价」复算。
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
        return strictOutputPrice(plan, keys, pricingContext, config);
    }

    /**
     * 输出视频分量的单价：严格取档位价——配了档位表必须命中，未配则用 {@code defaultUnitPrice}，两者都没有时抛异常。
     */
    private BigDecimal strictOutputPrice(BillingConfig.PricingPlan plan,
                                         List<String> keys,
                                         BillingConfig.PricingContext pricingContext,
                                         BillingConfig config) {
        BigDecimal strict = plan.resolveDimensionPriceStrict(keys, pricingContext, "output video");
        if (strict != null) {
            return strict;
        }
        BigDecimal fallback = config == null ? null : config.defaultUnitPrice();
        if (fallback == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "video rule has neither tier price nor defaultUnitPrice configured");
        }
        return fallback;
    }

    /**
     * 输入侧分量的单价：分档键未命中时用分量自带的 {@code componentDefault}，不走 {@code resolveDimensionPrice}
     * （后者会回落到输出视频的 {@code defaultUnitPrice}）。本分量不参与严格化：{@code componentDefault} 在此是业务口径，
     * 如输入视频未单配价时按同档位输出价、输入图片按张固定价。
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
     * 维度分档键：从最具体到最宽泛排序，供方案层取第一个命中；顺序固定为 分辨率 → {@code :REFERENCE} → {@code :AUDIO}，逐级去掉最右一段。
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
     * 输入侧分量的维度键名：分辨率已知时为 {@code 前缀:分辨率}，未知时退化为不带分辨率的分量键（如 {@code INPUT_VIDEO}，即不分档价）。
     */
    private static String componentKey(String prefix, String resolution) {
        return StringUtils.isBlank(resolution)
                ? StringUtils.removeEnd(prefix, ":")
                : prefix + resolution.trim();
    }

    // ==================== 合成量 ====================

    /**
     * 输出分量的计费量（秒）：按 {@code video.quantityMode}（默认 {@code OUTPUT}）把输入秒折进输出秒。
     * 「至少一秒」兜底收口在这里：确实出片但时长字段缺失时按 1 秒计；时长经 {@code outputSeconds()} 向上取整到秒。
     */
    private long outputAmountSeconds(VideoBillingUsage video, BillingConfig.VideoConfig videoConfig) {
        long output = firstAmount(video.outputSeconds(), 1L);
        return videoConfig == null ? output : videoConfig.quantity(output, video.inputSeconds());
    }

    // ==================== 明细类型 ====================

    private String detailType(String resolution, BillingConfig config) {
        String base = normalizeDetailType("", BillingModeEnum.VIDEO);
        String type = StringUtils.isBlank(resolution) ? base : base + ":" + resolution;
        return config != null && config.progressive() ? type + SUFFIX_PROGRESSIVE : type;
    }

    private static BillingConfig.PricingPlan planOf(BillingConfig config, BillingRuleRef rule) {
        return config.pricingPlan(PricingPlanEnum.fromCode(rule.pricingPlan()));
    }
}
