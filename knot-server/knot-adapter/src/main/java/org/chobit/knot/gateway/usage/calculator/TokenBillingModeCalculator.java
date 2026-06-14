package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
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
        BigDecimal unitPrice = safeMoney(rule.getUnitPrice());
        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        long totalTokens = usage.totalTokens() > 0 ? usage.totalTokens() : inputTokens + outputTokens;
        long cacheReadTokens = usage.cacheReadTokens();
        long cacheWriteTokens = usage.cacheWriteTokens();
        BigDecimal inputPrice = jsonDecimal(rule.getConfigJson(), "inputUnitPrice", unitPrice);
        BigDecimal outputPrice = jsonDecimal(rule.getConfigJson(), "outputUnitPrice", unitPrice);
        BigDecimal cacheReadPrice = jsonDecimal(rule.getConfigJson(), "cacheReadUnitPrice", BigDecimal.ZERO);
        BigDecimal cacheWritePrice = jsonDecimal(rule.getConfigJson(), "cacheWriteUnitPrice", inputPrice);
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
