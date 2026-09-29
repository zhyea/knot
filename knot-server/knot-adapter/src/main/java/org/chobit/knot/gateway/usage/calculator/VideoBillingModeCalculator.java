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
import java.util.Map;

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
        BigDecimal unitPrice = resolveUnitPrice(rule, resolution);
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

    private BigDecimal resolveUnitPrice(BillingRuleEntity rule, String resolution) {
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        if (config == null || resolution == null) {
            return config == null ? BigDecimal.ZERO
                    : config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan())).resolveDefaultPrice(BigDecimal.ZERO);
        }
        BigDecimal fallback = config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan())).resolveDefaultPrice(BigDecimal.ZERO);
        Map<String, BigDecimal> prices = config.resolutionPrices();
        if (prices == null || prices.isEmpty()) {
            return fallback;
        }
        BigDecimal matched = prices.get(resolution);
        if (matched == null) {
            matched = prices.get(StringUtils.upperCase(resolution));
        }
        if (matched == null) {
            matched = prices.get(StringUtils.lowerCase(resolution));
        }
        return matched == null ? fallback : matched;
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
