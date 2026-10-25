package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;

/**
 * 计费明细行（与网关 {@link NormalizedUsageDetail} 同构，金额为十进制普通字符串契约）。
 *
 * <p>{@code price} 是「每百万单位单价」（与网关 {@code detail()} 同口径，见
 * {@code AbstractBillingModeCalculator#pricePerMillion}）：复算公式 {@code cost = tokens × price ÷ 1e6}
 * 对任意单位基数都成立，无需再传 {@code unitSize}。</p>
 *
 * @param type  明细类型（TOKEN: UNCACHED_INPUT / CACHED_READ / CACHED_WRITE / OUTPUT；媒体: 模式名）
 * @param tokens 计费量（token 数 / 张数 / 秒数）
 * @param cost  本行金额（十进制普通字符串）
 * @param price 每百万单位单价（十进制普通字符串）
 */
public record BillingFactAmountLine(String type, Long tokens, String cost, String price) {

    public static BillingFactAmountLine from(NormalizedUsageDetail detail) {
        return new BillingFactAmountLine(detail.type(), detail.tokens(), detail.cost(), detail.price());
    }
}
