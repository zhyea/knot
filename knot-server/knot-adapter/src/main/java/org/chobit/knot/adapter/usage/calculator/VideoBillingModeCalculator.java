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
import lombok.extern.slf4j.Slf4j;

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
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.configJson());
        int unitSize = unitSize(rule, mode());

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
        details.add(detail(detailType(resolution, config), outputAmount, outputCost, outputPrice, unitSize));

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
            details.add(detail(TYPE_INPUT_VIDEO, video.inputSeconds(), cost, price, unitSize));
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
                details.add(detail(TYPE_INPUT_IMAGE, billable, cost, price, unitSize));
            }
        }

        // ---- 附加费：与用量无关的常数项 ----
        BigDecimal surcharge = config == null
                ? BigDecimal.ZERO
                : config.surchargeTotal(video.sound(), video.hasInputVideo());
        if (surcharge.signum() != 0) {
            total = total.add(surcharge);
            // ⭐ 附加费也走 detail()，不许自己拼 NormalizedUsageDetail：tokens=1、单位基数=1，
            // price 由 pricePerMillion 折算成「每百万单位单价」= surcharge × 1e6。
            // 若像 v1 那样把总额直接塞进 price，下游按 tokens × price ÷ 1e6 复算会差 1e6 倍——
            // 而该行 type/cost 全对，静态检查和肉眼都看不出，只有复算式守门能抓住。
            details.add(detail(TYPE_SURCHARGE, 1L, surcharge, surcharge, 1));
        }

        return NormalizedBillingAmount.of(usage.tokenUsage().totalTokens(), total, details);
    }

    /**
     * VENDOR_MINIMAX 专属口径：MiniMax H3 系列「三分量求和」。
     *
     * <pre>
     * 总价 = 输出秒数 × 档位输出单价
     *      + 输入秒数 × 档位输入单价（未单独配置 INPUT_VIDEO:档位 时回落到同档位输出单价）
     *      + max(0, 输入图片张数 − 免费张数) × 每张固定单价
     * </pre>
     *
     * <p><b>与通用分量路径的三点差异</b>（这三点就是本方法存在的理由，不是转发壳）：</p>
     * <ol>
     *   <li><b>输出量只取输出秒，绝不折叠输入秒</b>：MiniMax 把输入秒当独立分量计费，
     *       若再走 {@code quantity(SUM/OUTPUT_PLUS_MAX)} 就会与分量 2 重复计费。</li>
     *   <li><b>输入视频单价默认回落同档位输出单价</b>：官方「视频输入按输入时长及所选模型的输出分辨率计费」；
     *       {@code INPUT_VIDEO:档位} 可单独覆盖（未来某档不同价）。</li>
     *   <li><b>图片单价不随分辨率变化 + 免费额度默认 5 + 音频输入免费</b>：图片按张走固定标量价，
     *       官方文档明确音频输入免费，故不收取任何附加费。</li>
     * </ol>
     */
    private NormalizedBillingAmount calculateVendorMiniMax(NormalizedUsageContext context,
                                                           BillingRuleRef rule,
                                                           BillingConfig config,
                                                           VideoBillingUsage video,
                                                           int unitSize) {
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
        details.add(detail(detailType(resolution, config), outputAmount, outputCost, outputPrice, unitSize));

        // ---- 分量 2：输入视频 —— 输入秒 × 档位输入单价，未单配时回落同档位输出单价 ----
        long inputAmount = video.inputSeconds();
        if (inputAmount > 0) {
            List<String> inputKeys = dimensionKeys(
                    componentKey(BillingConfig.VideoConfig.INPUT_VIDEO_PREFIX, resolution), false, false);
            BigDecimal inputPrice = firstDimensionPrice(plan, inputKeys, context, outputPrice);
            BigDecimal inputCost = cost(inputAmount, inputPrice, unitSize);
            total = total.add(inputCost);
            details.add(detail(TYPE_INPUT_VIDEO, inputAmount, inputCost, inputPrice, unitSize));
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
                details.add(detail(TYPE_INPUT_IMAGE, billable, imageCost, imagePrice, unitSize));
            }
        }

        // 音频输入免费：MiniMax 不收取附加费，故无分量 4
        return NormalizedBillingAmount.of(video.tokenUsage().totalTokens(), total, details);
    }

    /**
     * 不出账：用量不足以支撑计价时的统一出口（规则与提取器类型错配）。
     *
     * <p>返回<b>零额 + 空明细</b>，不再按 1 秒硬计——不知道量就不该收钱。明细留空让对账侧
     * 一眼看出「这笔没有计费依据」，告警带原因，避免静默。</p>
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
     * 输出分量的计费量（秒）：按 {@code video.quantityMode} 把输入秒折进输出秒。
     *
     * <p>默认 {@code OUTPUT}（仅输出秒）与历史行为完全一致，未配 {@code video} 的配置不受影响。</p>
     *
     * <p>⭐ <b>「至少一秒」兜底收口在这里</b>：v1 它在两处各写一遍（通用路径与 MiniMax 分支），
     * 改一处漏一处。语义是「<b>确实出片了但时长字段缺失</b>」（提取器给出了档位名或 token 量），
     * 此时按 1 秒计合理；连档位名都没有的用量由 {@code VideoBillingUsage#isEmpty()} 表达，
     * 那是另一条链路的口径，不在本方法里判。
     * 另：毫秒精度下 {@code 0.5s} 不再被截成 0 再兜成 1 秒——它经 {@code outputSeconds()}
     * 向上取整为 1 秒，与旧值恰好一致，但 {@code 5.9s} 会正确计 6 秒而非 5 秒。</p>
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
