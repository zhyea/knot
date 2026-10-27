package org.chobit.knot.adapter.model.usage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 归一化后的计费视图，即 {@code knot_extend.billing} 的载荷。
 *
 * <p>{@code totalCost} 与明细 {@link NormalizedUsageDetail#cost()} 同为**十进制普通字符串**契约，
 * 理由与转换实现见 {@link NormalizedBillingAmount}：绝不出科学计数法，且不设小数位上限
 * （单价 6 位小数 ÷ 百万量级需要 12 位以上才不归零）。</p>
 *
 * <p>⭐ <b>空值整项省略</b>（{@link JsonInclude.Include#NON_NULL}）：{@code billingVersion}
 * 由规则版本号 {@code trimToNull} 而来、{@code currency} 亦可能因存量数据为空，
 * 不声明的默认行为是写出 {@code "billingVersion": null}。而同一 {@code knot_extend} 里的
 * {@code usage} 走 {@link ModelUsage} 的 {@code NON_DEFAULT}（空值省略）——一段响应两套空值口径，
 * 下游按「字段缺席」判空的逻辑会在 {@code billing} 段失效。此处与 {@code BillingViewMsgDto}
 * 的 {@code NON_NULL} 同口径：计费视图无论去向（响应还是数仓）都只给有值的字段。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedUsage(Long totalTokens,
                              String totalCost,
                              String currency,
                              String billingVersion,
                              List<NormalizedUsageDetail> detail) {
}
