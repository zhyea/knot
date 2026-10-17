package org.chobit.knot.gateway.dto.routing;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计费事实：自检层（{@link BillingFactChecks}）的单一输入。
 *
 * <p>由 {@link BillingFactAssembler} 组装，包含四类信息：
 * <ul>
 *   <li>{@code subject} / {@code ruleRef}：被测调用身份与命中规则（被告链依据）；</li>
 *   <li>{@code usage}：影子提取的用量（未计价）；</li>
 *   <li>{@code shadowAmount}：同口径重算账（离线复算，与生产同逻辑）；</li>
 *   <li>{@code gatewayBilling}：被告账（网关实际注入的 knot_extend.billing）；</li>
 *   <li>{@code upstreamCost}：上游返回的 cost（可空 → 触发 SKIP）。</li>
 * </ul>
 *
 * <p>自检层只比对结构内的数值，不触达生产热路径，也不读 JEV。</p>
 *
 * @param subject        主体
 * @param usage          影子用量视图
 * @param ruleRef        规则快照
 * @param shadowAmount   影子重算账（可空：未命中规则）
 * @param gatewayBilling 被告账（可空：响应未返回 knot_extend.billing）
 * @param upstreamCost   上游 cost（可空：上游未返回）
 */
public record BillingFact(BillingFactSubject subject,
                          BillingFactUsage usage,
                          BillingFactRuleRef ruleRef,
                          BillingFactAmount shadowAmount,
                          BillingFactAmount gatewayBilling,
                          BigDecimal upstreamCost) {
}
