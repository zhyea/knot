package org.chobit.knot.gateway.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
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
        TokenBillingUsage token = normalizedUsage.tokenUsage();
        long totalTokens = token.totalTokens() > 0
                ? token.totalTokens()
                : token.inputTokens() + token.outputTokens();
        // amount.totalCost() 已是最终对外文本（普通十进制、不设小数位上限），原样透传，不再二次转换
        return new NormalizedUsage(
                totalTokens,
                amount.totalCost(),
                rule.getCurrency(),
                billingVersion(rule),
                amount.details()
        );
    }

    private static BillingUsage normalizedUsage(BillingUsage usage, BillingModeEnum modeEnum) {
        if (BillingModeEnum.TOKEN != modeEnum || !(usage instanceof TokenBillingUsage token)) {
            return usage;
        }
        long totalTokens = token.totalTokens() > 0
                ? token.totalTokens()
                : token.inputTokens() + token.outputTokens();
        return new TokenBillingUsage(
                token.inputTokens(),
                token.outputTokens(),
                totalTokens,
                token.cacheReadTokens(),
                token.cacheWriteTokens(),
                token.cacheWrite5mTokens(),
                token.cacheWrite1hTokens()
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
