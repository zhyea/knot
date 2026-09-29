package org.chobit.knot.gateway.usage.calculator;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class AbstractBillingModeCalculator implements BillingModeCalculator {

    protected BillingRuleEntity rule(NormalizedUsageContext context) {
        return context.rule();
    }

    protected int unitSize(BillingRuleEntity rule) {
        BillingUnitEnum unitEnum = BillingUnitEnum.fromCode(normalizeUnit(rule.getUnit()));
        if (unitEnum == null) {
            return 1_000;
        }
        return switch (unitEnum) {
            case ONE_M_TOKENS -> 1_000_000;
            case PER_TOKEN, PER_REQUEST, PER_IMAGE, PER_SECOND, CUSTOM -> 1;
            case PER_MINUTE -> 60;
            default -> 1_000;
        };
    }

    protected BigDecimal safeMoney(BigDecimal value) {
        return ObjectUtils.defaultIfNull(value, BigDecimal.ZERO);
    }

    protected BigDecimal cost(long amount, BigDecimal unitPrice, int unitSize) {
        if (amount <= 0 || unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amount)
                .multiply(unitPrice)
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    protected NormalizedUsageDetail detail(String type,
                                           long tokens,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           int unitSize) {
        return new NormalizedUsageDetail(
                type,
                tokens,
                safeMoney(cost),
                pricePerMillion(safeMoney(unitPrice), unitSize)
        );
    }

    protected BigDecimal pricePerMillion(BigDecimal unitPrice, int unitSize) {
        if (unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return unitPrice
                .multiply(BigDecimal.valueOf(1_000_000L))
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    protected String normalizeDetailType(String itemType, BillingModeEnum mode) {
        if (StringUtils.isNotBlank(itemType)) {
            return StringUtils.trim(itemType);
        }
        return mode == null ? BillingModeEnum.CUSTOM.code() : mode.code();
    }

    protected String normalizeUnit(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trim(value));
        return StringUtils.defaultIfBlank(normalized, BillingUnitEnum.ONE_K_TOKENS.code());
    }

    protected long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
    }
}
