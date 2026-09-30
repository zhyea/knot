package org.chobit.knot.gateway.vo.billing;

/**
 * 计费方案试算请求。
 *
 * <p>两个入参都是可选：不给 {@code occurredAt} 用「现在」，不给 {@code usageAmount} 视为 0
 * （阶梯命中失效，单价回到基础价，但峰谷判定照常）。
 */
public record PricingPreviewRequest(String occurredAt,
                                    Long usageAmount) {
}
