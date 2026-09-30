package org.chobit.knot.gateway.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;

import java.time.Instant;
import java.util.Map;

public final class UsageNormalizationSupport {

    private UsageNormalizationSupport() {
    }


    /**
     * @param occurredAt 用量发生时点；null 由 {@link NormalizedUsageContext} 兜底成 {@code Instant.now()}
     */
    public static NormalizedUsage normalize(BillingUsage usage,
                                            BillingRuleEntity rule,
                                            Map<String, Object> requestBody,
                                            Instant occurredAt,
                                            BillingModeCalculator calculator) {
        if (usage == null || usage.isEmpty() || rule == null) {
            return null;
        }
        String mode = normalizeBillingMode(rule.getBillingMode());
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(mode);
        if (modeEnum == null) {
            modeEnum = BillingModeEnum.CUSTOM;
        }
        BillingUsage normalizedUsage = normalizedUsage(usage, modeEnum);
        if (calculator == null) {
            throw new IllegalStateException("UsageExtractor calculator is required");
        }
        NormalizedBillingAmount amount =
                calculator.calculate(new NormalizedUsageContext(rule, normalizedUsage, requestBody, occurredAt));
        long totalTokens = normalizedUsage.totalTokens() > 0
                ? normalizedUsage.totalTokens()
                : normalizedUsage.inputTokens() + normalizedUsage.outputTokens();
        return new NormalizedUsage(
                totalTokens,
                amount.totalCost(),
                rule.getCurrency(),
                billingVersion(rule),
                amount.details()
        );
    }

    private static BillingUsage normalizedUsage(BillingUsage usage, BillingModeEnum modeEnum) {
        if (BillingModeEnum.TOKEN != modeEnum) {
            return usage;
        }
        long totalTokens = usage.totalTokens() > 0
                ? usage.totalTokens()
                : usage.inputTokens() + usage.outputTokens();
        return new BillingUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                totalTokens,
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                usage.amount()
        );
    }

    private static String billingVersion(BillingRuleEntity rule) {
        return StringUtils.trimToNull(rule.getVersionCode());
    }

    private static String normalizeBillingMode(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trim(value));
        return StringUtils.defaultIfBlank(normalized, BillingModeEnum.TOKEN.code());
    }
}
