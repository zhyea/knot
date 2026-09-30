package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.BillingConfig;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TokenBillingModeCalculator extends AbstractBillingModeCalculator {

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.TOKEN;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleEntity rule = rule(context);
        BillingUsage usage = context.usage();
        int unitSize = unitSize(rule);
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.getConfigJson());
        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        long totalTokens = usage.totalTokens() > 0 ? usage.totalTokens() : inputTokens + outputTokens;
        long cacheReadTokens = usage.cacheReadTokens();
        long cacheWriteTokens = usage.cacheWriteTokens();
        long ladderAmount = totalTokens;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal inputPrice;
        BigDecimal outputPrice;
        BigDecimal cacheReadPrice;
        BigDecimal cacheWritePrice;
        if (config == null) {
            inputPrice = zero;
            outputPrice = zero;
            cacheReadPrice = zero;
            cacheWritePrice = zero;
        } else {
            BillingConfig.PricingPlan pricing = config.pricingPlan(PricingPlanEnum.fromCode(rule.getPricingPlan()));
            // 带发生时点 → 高低峰等按时间定价的方案才能在热路径真正生效
            BillingConfig.PricingContext pricingContext = new BillingConfig.PricingContext(ladderAmount, context.occurredAt());
            inputPrice = pricing.resolvePrice(BillingConfig.PriceKind.INPUT, pricingContext, zero);
            outputPrice = pricing.resolvePrice(BillingConfig.PriceKind.OUTPUT, pricingContext, zero);
            cacheReadPrice = pricing.resolvePrice(BillingConfig.PriceKind.CACHE_READ, pricingContext, zero);
            cacheWritePrice = pricing.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, pricingContext, inputPrice);
        }
        long uncachedInputTokens = Math.max(0L, inputTokens - cacheReadTokens - cacheWriteTokens);
        BigDecimal inputCost = cost(uncachedInputTokens, inputPrice, unitSize);
        BigDecimal outputCost = cost(outputTokens, outputPrice, unitSize);
        BigDecimal cacheReadCost = cost(cacheReadTokens, cacheReadPrice, unitSize);
        BigDecimal cacheWriteCost = cost(cacheWriteTokens, cacheWritePrice, unitSize);
        List<NormalizedUsageDetail> details = List.of(
                detail("uncachedInput", uncachedInputTokens, inputCost, inputPrice, unitSize),
                detail("cachedRead", cacheReadTokens, cacheReadCost, cacheReadPrice, unitSize),
                detail("cachedWrite", cacheWriteTokens, cacheWriteCost, cacheWritePrice, unitSize),
                detail("output", outputTokens, outputCost, outputPrice, unitSize)
        );
        return new NormalizedBillingAmount(
                totalTokens,
                inputCost.add(outputCost).add(cacheReadCost).add(cacheWriteCost),
                details
        );
    }
}
