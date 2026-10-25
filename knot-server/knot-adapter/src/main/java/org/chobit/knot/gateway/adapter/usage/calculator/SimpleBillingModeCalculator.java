package org.chobit.knot.gateway.adapter.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.model.BillingConfig;
import org.chobit.knot.gateway.adapter.model.usage.BillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.gateway.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.gateway.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.gateway.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.gateway.adapter.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SimpleBillingModeCalculator extends AbstractBillingModeCalculator {

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.CUSTOM;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleRef rule = rule(context);
        BillingUsage usage = context.usage();
        BillingModeEnum mode = resolveMode(rule);
        long amount = billingAmount(usage, mode);
        int unitSize = unitSize(rule, mode);
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.configJson());
        BigDecimal unitPrice = config == null
                ? BigDecimal.ZERO
                : config.pricingPlan(PricingPlanEnum.fromCode(rule.pricingPlan()))
                        .resolveDefaultPrice(new BillingConfig.PricingContext(amount, context.occurredAt()), BigDecimal.ZERO);
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        return NormalizedBillingAmount.of(
                usage.tokenUsage().totalTokens(),
                totalCost,
                List.of(detail(normalizeDetailType(rule.billingMode(), mode), amount, totalCost, unitPrice, unitSize))
        );
    }

    /**
     * 按计费模式取计费量：token 类模式取 token 维度，媒体模式取各自的计量
     * （IMAGE 张数 / AUDIO、VIDEO 秒数），类型即契约。
     */
    private long billingAmount(BillingUsage usage, BillingModeEnum mode) {
        return switch (mode) {
            case EMBEDDING -> tokenAmount(usage, true);
            case REQUEST -> 1L;
            case IMAGE -> atLeastOne(imageCount(usage));
            case AUDIO, VIDEO -> atLeastOne(mediaSeconds(usage));
            default -> tokenAmount(usage, false);
        };
    }

    /** token 用量取数：媒体记录经内嵌部分取，非 token 来源为 0 */
    private long tokenAmount(BillingUsage usage, boolean inputFirst) {
        TokenBillingUsage token = usage.tokenUsage();
        return inputFirst
                ? firstAmount(token.inputTokens(), token.totalTokens())
                : firstAmount(token.totalTokens(), token.inputTokens());
    }

    private long imageCount(BillingUsage usage) {
        return usage instanceof ImageBillingUsage image ? image.count() : 0L;
    }

    /**
     * 媒体模式的秒数：媒体记录取各自时长；token 用量与图像记录无时长口径，为 0
     * （由 {@link #atLeastOne} 兜底成 1）。
     */
    private long mediaSeconds(BillingUsage usage) {
        if (usage instanceof VideoBillingUsage video) {
            return video.durationSeconds();
        }
        if (usage instanceof AudioBillingUsage audio) {
            return audio.durationSeconds();
        }
        return 0L;
    }

    private long atLeastOne(long amount) {
        return amount > 0 ? amount : 1L;
    }

    private BillingModeEnum resolveMode(BillingRuleRef rule) {
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(rule == null ? null : rule.billingMode());
        return modeEnum == null ? BillingModeEnum.CUSTOM : modeEnum;
    }
}
