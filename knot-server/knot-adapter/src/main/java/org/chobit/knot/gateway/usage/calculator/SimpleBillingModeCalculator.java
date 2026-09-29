package org.chobit.knot.gateway.usage.calculator;

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
public class SimpleBillingModeCalculator extends AbstractBillingModeCalculator {

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.CUSTOM;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleEntity rule = rule(context);
        BillingUsage usage = context.usage();
        BillingModeEnum mode = resolveMode(rule);
        long amount = switch (mode) {
            case EMBEDDING -> firstAmount(usage.inputTokens(), usage.totalTokens());
            case REQUEST -> 1L;
            case IMAGE, AUDIO, VIDEO -> firstAmount(usage.amount(), 1L);
            default -> firstAmount(usage.totalTokens(), usage.inputTokens());
        };
        if (amount <= 0 && (BillingModeEnum.IMAGE == mode || BillingModeEnum.REQUEST == mode)) {
            amount = 1L;
        }
        int unitSize = unitSize(rule);
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        BigDecimal unitPrice = config == null
                ? BigDecimal.ZERO
                : config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan())).resolveDefaultPrice(BigDecimal.ZERO);
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        return new NormalizedBillingAmount(
                usage.totalTokens(),
                totalCost,
                List.of(detail(normalizeDetailType(rule.getBillingMode(), mode), amount, totalCost, unitPrice, unitSize))
        );
    }

    private BillingModeEnum resolveMode(BillingRuleEntity rule) {
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(rule == null ? null : rule.getBillingMode());
        return modeEnum == null ? BillingModeEnum.CUSTOM : modeEnum;
    }
}
