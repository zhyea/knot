package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.gateway.adapter.model.usage.NormalizedUsage;

import java.util.List;

/**
 * 一份计费账（同口径）：既承载影子重算账，也承载被告账（knot_extend.billing）。
 *
 * <p>两者用同一类型，{@code GATEWAY_RECOMPUTE_MATCH} 直接按类型对齐比较，不混用两套结构。</p>
 *
 * @param totalTokens token 总量（媒体模式为 0）
 * @param totalCost  总价（十进制普通字符串）
 * @param currency   币种
 * @param details    明细行
 */
public record BillingFactAmount(long totalTokens, String totalCost, String currency, List<BillingFactAmountLine> details) {

    public static BillingFactAmount from(NormalizedUsage usage) {
        if (usage == null) {
            return null;
        }
        List<BillingFactAmountLine> lines = usage.detail() == null
                ? List.of()
                : usage.detail().stream().map(BillingFactAmountLine::from).toList();
        long total = usage.totalTokens() == null ? 0L : usage.totalTokens();
        return new BillingFactAmount(total, usage.totalCost(), usage.currency(), lines);
    }
}
