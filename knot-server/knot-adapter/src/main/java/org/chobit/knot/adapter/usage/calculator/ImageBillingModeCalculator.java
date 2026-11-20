package org.chobit.knot.adapter.usage.calculator;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.BillingConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * IMAGE 模式计算器：总价 = 出图张数 × 输出图片单价 + 输入图片张数 × 每张单价。
 *
 * 输出单价按像素档落定：{@code image.tiers} 命中档位价 → 方案层倍率链；未配档位表或输出像素未知（
 * {@code pixels <= 0}）时取规则默认单价（含阶梯与相位调整）。档位表非空、像素已知却无档覆盖即抛
 * {@link ErrorCode#VALIDATION_ERROR}。输入图片张数先扣 {@code image.freeInputImages}，单价过方案层分量链。
 */
@Slf4j
@Component
public class ImageBillingModeCalculator extends AbstractBillingModeCalculator {

    /** 输入图片分量不按字符串维度键查表，候选键恒为空 */
    private static final List<String> NO_DIMENSION = List.of();

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.IMAGE;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleRef rule = rule(context);
        BillingUsage usage = context.usage();
        if (!(usage instanceof ImageBillingUsage image)) {
            log.warn("IMAGE 计费跳过：规则吃到非图片用量，已不出账：{}", usage.getClass().getSimpleName());
            return NormalizedBillingAmount.of(0L, BigDecimal.ZERO, List.of());
        }
        BillingConfig config = configOf(rule);
        BillingConfig.ImageConfig imageConfig = config == null ? null : config.image();
        BillingConfig.PricingPlan plan = config == null
                ? null
                : config.pricingPlan(PricingPlanEnum.fromCode(rule.pricingPlan()));
        int unitSize = unitSize(rule, mode());
        String unitCode = unitCode(rule, mode());

        long count = firstAmount(image.count(), 1L);
        BillingConfig.PricingContext outputContext =
                new BillingConfig.PricingContext(count, context.occurredAt());
        BigDecimal outputPrice = outputUnitPrice(plan, imageConfig, image.pixels(), outputContext, config);
        BigDecimal outputCost = cost(count, outputPrice, unitSize);

        List<NormalizedUsageDetail> details = new ArrayList<>();
        BigDecimal total = outputCost;
        details.add(detail(normalizeDetailType("", BillingModeEnum.IMAGE), count, outputCost, outputPrice, unitCode));

        if (imageConfig != null && imageConfig.billsInputImage()) {
            long billable = imageConfig.billableImageCount(image.inputImageCount());
            if (billable > 0) {
                BigDecimal inputPrice = inputImageUnitPrice(plan, imageConfig, context);
                BigDecimal inputCost = cost(billable, inputPrice, unitSize);
                total = total.add(inputCost);
                details.add(detail(BillingConfig.ImageConfig.DETAIL_INPUT_IMAGE,
                        billable, inputCost, inputPrice, unitCode));
            }
        }
        return NormalizedBillingAmount.of(usage.tokenUsage().totalTokens(), total, details);
    }

    /**
     * 输出图片单价：像素档命中价 → 方案层倍率链；否则走默认单价（阶梯与相位调整同链）。
     *
     * 档位表非空且像素已知时，无档覆盖即抛——静默回落默认单价会让高档位的图按低档价出账。
     */
    private BigDecimal outputUnitPrice(BillingConfig.PricingPlan plan,
                                       BillingConfig.ImageConfig imageConfig,
                                       long pixels,
                                       BillingConfig.PricingContext pricingContext,
                                       BillingConfig config) {
        if (plan == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal tierPrice = imageConfig == null ? null : imageConfig.priceFor(pixels);
        if (tierPrice != null) {
            BigDecimal adjusted = plan.resolveConfiguredPrice(tierPrice, pricingContext);
            return adjusted == null ? tierPrice : adjusted;
        }
        boolean tiered = imageConfig != null && imageConfig.tiers() != null && !imageConfig.tiers().isEmpty();
        if (tiered && pixels > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "no image pixel tier covers " + pixels + " pixels"
                            + " (add an open-ended tier or extend the tier table)");
        }
        BigDecimal fallback = config == null ? null : config.defaultUnitPrice();
        BigDecimal resolved = plan.resolveDefaultPrice(pricingContext, fallback);
        return resolved == null ? BigDecimal.ZERO : resolved;
    }

    /**
     * 输入图片每张单价：过方案层分量链（分量默认价优先于规则默认价，高低峰 MULTIPLIER 乘相位倍率）。
     */
    private BigDecimal inputImageUnitPrice(BillingConfig.PricingPlan plan,
                                           BillingConfig.ImageConfig imageConfig,
                                           NormalizedUsageContext context) {
        BigDecimal componentDefault = imageConfig.inputImagePerImage();
        if (plan == null) {
            return componentDefault == null ? BigDecimal.ZERO : componentDefault;
        }
        BigDecimal resolved = plan.resolveComponentPrice(NO_DIMENSION, componentDefault,
                new BillingConfig.PricingContext(1L, context.occurredAt()), BigDecimal.ZERO);
        return resolved == null ? BigDecimal.ZERO : resolved;
    }
}
