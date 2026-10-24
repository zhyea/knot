package org.chobit.knot.gateway.usage.calculator;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.model.BillingConfig;
import org.chobit.knot.gateway.model.NormalizedBillingAmount;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.TokenUsageDetailKind;
import org.chobit.knot.gateway.usage.BillingRuleRef;
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

    /**
     * 按上下文携带的定价方案分发到对应计费方法：
     * 固定价直出 / 阶梯价按量命中档位 / 高低峰按发生时点判相位。
     * 方案来源为 {@link NormalizedUsageContext#pricingPlan()}（规则列解析，空或未知回落 FIXED）。
     */
    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        if (!(context.usage() instanceof TokenBillingUsage usage)) {
            // 非 token 用量（媒体记录）到达本计算器 = 提取器与规则模式错配：
            // 热路径不抛异常阻断响应，按零用量出账，交由计费规则配置侧修正
            return NormalizedBillingAmount.of(0L, BigDecimal.ZERO, List.of());
        }
        return switch (context.pricingPlan()) {
            case FIXED -> calculateFixed(context, usage);
            case TIERED -> calculateTiered(context, usage);
            case PEAK_OFF_PEAK -> calculatePeakOffPeak(context, usage);
        };
    }

    /** 固定价：basePrices 直出 -> defaultUnitPrice -> 0；不读用量、不读时点 */
    private NormalizedBillingAmount calculateFixed(NormalizedUsageContext context, TokenBillingUsage usage) {
        return assemble(context.rule(), usage,
                planOf(context, PricingPlanEnum.FIXED),
                BillingConfig.PricingContext.ofAmount(0L));
    }

    /** 阶梯价：按本次请求总 token 命中档位（整笔命中一个档位，不拆段），时点不参与 */
    private NormalizedBillingAmount calculateTiered(NormalizedUsageContext context, TokenBillingUsage usage) {
        return assemble(context.rule(), usage,
                planOf(context, PricingPlanEnum.TIERED),
                BillingConfig.PricingContext.ofAmount(totalTokensOf(usage)));
    }

    /**
     * 高低峰：按发生时点判相位——MULTIPLIER 在模式层基础价上乘倍率，
     * ABSOLUTE 取命中相位自身单价；时间由上下文兜底，不会退化成不调整。
     */
    private NormalizedBillingAmount calculatePeakOffPeak(NormalizedUsageContext context, TokenBillingUsage usage) {
        return assemble(context.rule(), usage,
                planOf(context, PricingPlanEnum.PEAK_OFF_PEAK),
                new BillingConfig.PricingContext(totalTokensOf(usage), context.occurredAt()));
    }

    /** 方案领域对象：config_json 解析失败（null）时各分项价按 0 出账 */
    private static BillingConfig.PricingPlan planOf(NormalizedUsageContext context, PricingPlanEnum plan) {
        BillingConfig config = BillingConfig.fromJsonOrNull(context.rule().configJson());
        return config == null ? null : config.pricingPlan(plan);
    }

    private static long totalTokensOf(TokenBillingUsage usage) {
        long total = usage.totalTokens();
        return total > 0 ? total : usage.inputTokens() + usage.outputTokens();
    }

    /**
     * 三个方案共用的出账组装：解析六个分项单价 -> 算钱 -> 出明细。
     * 单价解析全部委托方案领域对象（{@link BillingConfig.PricingPlan}），此处不重复实现价逻辑。
     */
    private NormalizedBillingAmount assemble(BillingRuleRef rule,
                                             TokenBillingUsage usage,
                                             BillingConfig.PricingPlan plan,
                                             BillingConfig.PricingContext pricingContext) {
        int unitSize = unitSize(rule, mode());
        BigDecimal zero = BigDecimal.ZERO;
        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        long totalTokens = totalTokensOf(usage);
        long cacheReadTokens = usage.cacheReadTokens();
        // 缓存写：ttl 明细（5m / 1h）优先，只有明细都为 0 时才是单字段口径
        boolean ttlSplit = usage.cacheWriteTtlSplit();
        long cacheWriteTokens = ttlSplit ? 0L : usage.cacheWriteTokens();
        long cacheWrite5mTokens = ttlSplit ? usage.cacheWrite5mTokens() : 0L;
        long cacheWrite1hTokens = ttlSplit ? usage.cacheWrite1hTokens() : 0L;
        BigDecimal inputPrice = priceOf(plan, BillingConfig.PriceKind.INPUT, pricingContext, zero);
        BigDecimal outputPrice = priceOf(plan, BillingConfig.PriceKind.OUTPUT, pricingContext, zero);
        BigDecimal cacheReadPrice = priceOf(plan, BillingConfig.PriceKind.CACHE_READ, pricingContext, zero);
        // 缓存写未配分项价时回落 input 价（既有口径）
        BigDecimal cacheWritePrice = priceOf(plan, BillingConfig.PriceKind.CACHE_WRITE, pricingContext, inputPrice);
        BigDecimal cacheWrite5mPrice = priceOf(plan, BillingConfig.PriceKind.CACHE_WRITE_5M, pricingContext, inputPrice);
        BigDecimal cacheWrite1hPrice = priceOf(plan, BillingConfig.PriceKind.CACHE_WRITE_1H, pricingContext, inputPrice);
        long uncachedInputTokens = Math.max(0L, inputTokens - cacheReadTokens - usage.cacheWriteTotal());
        BigDecimal inputCost = cost(uncachedInputTokens, inputPrice, unitSize);
        BigDecimal outputCost = cost(outputTokens, outputPrice, unitSize);
        BigDecimal cacheReadCost = cost(cacheReadTokens, cacheReadPrice, unitSize);
        BigDecimal cacheWriteCost = cost(cacheWriteTokens, cacheWritePrice, unitSize);
        BigDecimal cacheWrite5mCost = cost(cacheWrite5mTokens, cacheWrite5mPrice, unitSize);
        BigDecimal cacheWrite1hCost = cost(cacheWrite1hTokens, cacheWrite1hPrice, unitSize);
        List<NormalizedUsageDetail> details = ttlSplit
                ? List.of(
                detail(TokenUsageDetailKind.UNCACHED_INPUT, uncachedInputTokens, inputCost, inputPrice, unitSize),
                detail(TokenUsageDetailKind.CACHED_READ, cacheReadTokens, cacheReadCost, cacheReadPrice, unitSize),
                detail(TokenUsageDetailKind.CACHED_WRITE_5M, cacheWrite5mTokens, cacheWrite5mCost, cacheWrite5mPrice, unitSize),
                detail(TokenUsageDetailKind.CACHED_WRITE_1H, cacheWrite1hTokens, cacheWrite1hCost, cacheWrite1hPrice, unitSize),
                detail(TokenUsageDetailKind.OUTPUT, outputTokens, outputCost, outputPrice, unitSize))
                : List.of(
                detail(TokenUsageDetailKind.UNCACHED_INPUT, uncachedInputTokens, inputCost, inputPrice, unitSize),
                detail(TokenUsageDetailKind.CACHED_READ, cacheReadTokens, cacheReadCost, cacheReadPrice, unitSize),
                detail(TokenUsageDetailKind.CACHED_WRITE, cacheWriteTokens, cacheWriteCost, cacheWritePrice, unitSize),
                detail(TokenUsageDetailKind.OUTPUT, outputTokens, outputCost, outputPrice, unitSize));
        return NormalizedBillingAmount.of(
                totalTokens,
                inputCost.add(outputCost)
                        .add(cacheReadCost)
                        .add(cacheWriteCost)
                        .add(cacheWrite5mCost)
                        .add(cacheWrite1hCost),
                details
        );
    }

    private static BigDecimal priceOf(BillingConfig.PricingPlan plan,
                                      BillingConfig.PriceKind kind,
                                      BillingConfig.PricingContext pricingContext,
                                      BigDecimal fallback) {
        return plan == null ? fallback : plan.resolvePrice(kind, pricingContext, fallback);
    }
}
