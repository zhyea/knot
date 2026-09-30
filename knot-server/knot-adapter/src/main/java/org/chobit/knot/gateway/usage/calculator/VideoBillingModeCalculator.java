package org.chobit.knot.gateway.usage.calculator;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingConfig;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class VideoBillingModeCalculator extends AbstractBillingModeCalculator {

    private static final String REQUEST_SIZE = "size";
    private static final String FALLBACK_SIZE = "resolution";

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.VIDEO;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleEntity rule = rule(context);
        BillingUsage usage = context.usage();
        long amount = firstAmount(usage.amount(), 1L);
        String resolution = normalizeResolution(context.requestBody().get(REQUEST_SIZE));
        if (resolution == null) {
            resolution = normalizeResolution(context.requestBody().get(FALLBACK_SIZE));
        }
        // 带发生时点传给方案层，简单价分支才能吃到相位倍率
        BillingConfig.PricingContext pricingContext = new BillingConfig.PricingContext(amount, context.occurredAt());
        BigDecimal unitPrice = resolveUnitPrice(rule, resolution, pricingContext);
        int unitSize = unitSize(rule);
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        String detailType = resolution == null
                ? normalizeDetailType(rule.getBillingMode(), BillingModeEnum.VIDEO)
                : normalizeDetailType(rule.getBillingMode(), BillingModeEnum.VIDEO) + ":" + resolution;
        return new NormalizedBillingAmount(
                usage.totalTokens(),
                totalCost,
                List.of(detail(detailType, amount, totalCost, unitPrice, unitSize))
        );
    }

    /**
     * 分辨率单价 -&gt; defaultUnitPrice -&gt; 0。
     *
     * <p>⚠ 已知缺口：命中的分辨率价直接返回，**未过** {@code PricingPlan} 倍率链路
     * （方案文档 §八 风险 ①）。首期 PEAK_OFF_PEAK 仅支持 TOKEN 模式，VIDEO 拿不到该方案，
     * 故无实际影响；放开到非 TOKEN 前必须先处理这里。
     */
    private BigDecimal resolveUnitPrice(BillingRuleEntity rule,
                                        String resolution,
                                        BillingConfig.PricingContext pricingContext) {
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        if (config == null || resolution == null) {
            return config == null ? BigDecimal.ZERO
                    : planOf(config, rule).resolveDefaultPrice(pricingContext, BigDecimal.ZERO);
        }
        BillingConfig.PricingPlan plan = planOf(config, rule);
        BigDecimal fallback = plan.resolveDefaultPrice(pricingContext, BigDecimal.ZERO);
        BigDecimal matched = config.resolutionPrice(resolution);
        if (matched == null) {
            matched = config.resolutionPrice(StringUtils.upperCase(resolution));
        }
        if (matched == null) {
            matched = config.resolutionPrice(StringUtils.lowerCase(resolution));
        }
        return matched == null ? fallback : matched;
    }

    private static BillingConfig.PricingPlan planOf(BillingConfig config, BillingRuleEntity rule) {
        return config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan()));
    }

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
            case "720", "720p", "1280x720" -> "720P";
            case "1080", "1080p", "1920x1080" -> "1080P";
            default -> StringUtils.upperCase(text);
        };
    }
}
