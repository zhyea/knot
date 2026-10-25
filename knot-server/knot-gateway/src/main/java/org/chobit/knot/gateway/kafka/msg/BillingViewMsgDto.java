package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenUsageDetailKind;

import java.util.List;

/**
 * 响应层的计费视图：命中的规则、币种与总价，外加逐类用量的明细行。
 *
 * <p>与对外 {@code knot_extend.billing} 是同一份结果，但这里额外带上估价来源
 * （规则业务码 / 版本 / 计费模式 / 定价方案），因为数仓要按「哪条规则算的」做归因；
 * 而对外输出不必暴露这么多内部信息。</p>
 *
 * <p>金额一律走 {@code String}（8 位小定标）：Jackson 对 {@link java.math.BigDecimal} 默认写
 * {@code toString()}，小额会变成 {@code 1E-8}，数仓解析口径不统一。这里是唯一出口，必须钉死形态。</p>
 *
 * @param totalCost 总价（十进制普通字符串）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BillingViewMsgDto(String ruleCode,
                                String ruleVersion,
                                String billingMode,
                                String pricingPlan,
                                long totalTokens,
                                String totalCost,
                                String currency,
                                List<BillingDetailMsgDto> detail) {

    /**
     * 由汇聚计费结果构建；{@code null}（未命中任何计费规则）返回 {@code null}——
     * 「免费」与「没配置计费」在数仓里必须是两件事。
     */
    public static BillingViewMsgDto of(NormalizedUsage billing, String ruleCode, String pricingPlan) {
        if (billing == null) {
            return null;
        }
        List<BillingDetailMsgDto> details = billing.detail() == null
            ? List.of()
            : billing.detail().stream().map(BillingDetailMsgDto::of).toList();
        return new BillingViewMsgDto(
            ruleCode,
            billing.billingVersion(),
            resolveMode(billing),
            pricingPlan,
            billing.totalTokens() == null ? 0L : billing.totalTokens(),
            billing.totalCost(),
            billing.currency(),
            details);
    }

    /**
     * 计费模式：命中细化 token 明细（uncachedInput / cachedRead / output…）时为 TOKEN，
     * 其余场景无法从结果反推，留 {@code null} 交由 {@code billingVersion} 关联规则补齐。
     */
    private static String resolveMode(NormalizedUsage billing) {
        if (billing.detail() == null || billing.detail().isEmpty()) {
            return null;
        }
        for (NormalizedUsageDetail item : billing.detail()) {
            for (TokenUsageDetailKind kind : TokenUsageDetailKind.values()) {
                if (kind.key().equals(item.type())) {
                    return "TOKEN";
                }
            }
        }
        return null;
    }

    /**
     * 计费明细行：一类计费量的金额结果。
     *
     * @param cost  金额，十进制普通字符串（8 位小定标）
     * @param price 折算到百万单位的单价，同样为字符串
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BillingDetailMsgDto(String type,
                                      Long tokens,
                                      String cost,
                                      String price) {

        /**
         * 由内部明细行构建；{@code null} 返回 {@code null}。
         */
        public static BillingDetailMsgDto of(NormalizedUsageDetail detail) {
            if (detail == null) {
                return null;
            }
            return new BillingDetailMsgDto(
                detail.type(),
                detail.tokens(),
                detail.cost(),
                detail.price());
        }
    }
}
