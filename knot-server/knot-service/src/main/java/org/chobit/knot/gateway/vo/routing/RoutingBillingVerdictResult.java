package org.chobit.knot.gateway.vo.routing;

import org.chobit.knot.gateway.dto.routing.BillingCheckResult;
import org.chobit.knot.gateway.dto.routing.BillingFactAmount;
import org.chobit.knot.gateway.dto.routing.BillingFactRuleRef;

import java.util.List;
import java.util.Map;

/**
 * 路由规则测试·计费裁判结果（v7 §2 / §9 输出）：三区一站式聚合。
 *
 * <p>被告链（producedUsage / producedBilling / upstreamUsage / upstreamCost / ruleRef）供「计费事实区」；
 * {@code checks} 供「自检区」七项；{@code verdict} 供「裁判结论区」。自检与裁判互不可见（双盲），
 * 故本对象同时承载两者但互不引用对方结论。</p>
 *
 * <p>被告账与影子账同型（{@link BillingFactAmount}），便于前端直接逐项对照；
 * {@link RoutingBillingVerdict#isAvailable()} 为 false 时仅 {@link RoutingBillingVerdict#getReason()}
 * 非空，不抛异常炸链路。</p>
 *
 * @param producedUsage   被告用量：knot_extend.usage 原样
 * @param producedBilling 被告账：knot_extend.billing（已转 BillingFactAmount，与影子账同型）
 * @param ruleRef         命中规则快照（matchedBy 供自检区直接读取）
 * @param upstreamUsage   上游 usage 原文（证据）
 * @param upstreamCost    上游返回的 cost（可空 → 前端显示「上游未返回」）
 * @param shadowBilling   影子重算账（可空：未命中规则）
 * @param checks          七项自检结果（三态 PASS / FAIL / SKIP）
 * @param verdict         JEV 裁判结论
 */
public record RoutingBillingVerdictResult(Map<String, Object> producedUsage,
                                           BillingFactAmount producedBilling,
                                           BillingFactRuleRef ruleRef,
                                           Map<String, Object> upstreamUsage,
                                           String upstreamCost,
                                           BillingFactAmount shadowBilling,
                                           List<BillingCheckResult> checks,
                                           RoutingBillingVerdict verdict) {
}
