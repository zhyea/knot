package org.chobit.knot.gateway.adapter.model.usage;

import java.util.List;

/**
 * 归一化后的计费视图，即 {@code knot_extend.billing} 的载荷。
 *
 * <p>{@code totalCost} 与明细 {@link NormalizedUsageDetail#cost()} 同为**十进制普通字符串**契约，
 * 理由与转换实现见 {@link NormalizedBillingAmount}：绝不出科学计数法，且不设小数位上限
 * （单价 6 位小数 ÷ 百万量级需要 12 位以上才不归零）。</p>
 */
public record NormalizedUsage(Long totalTokens,
                              String totalCost,
                              String currency,
                              String billingVersion,
                              List<NormalizedUsageDetail> detail) {
}
