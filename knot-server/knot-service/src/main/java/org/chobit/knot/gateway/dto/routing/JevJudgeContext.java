package org.chobit.knot.gateway.dto.routing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * {@link JevBillingJudge} 的裁判输入载体：仅携带判定所需的「已产出 + 底层依据」，
 * <b>不含</b>影子重算账（shadowAmount）、解析用量视图（parsedUsage）与任何自检结论——
 * 这是 v7 双盲硬约束（§4.2 / §5.2）：JEV 是独立裁判，不得读取别的老师的阅卷结果。
 *
 * @param subject        主体（被测调用身份）
 * @param gatewayUsage   被告用量：网关实际注入的 {@code knot_extend.usage} 原样（非影子视图）
 * @param gatewayBilling 被告账：网关实际注入的 {@code knot_extend.billing}
 * @param upstreamUsage  上游 usage 原文（证据，仅数值键入 state）
 * @param upstreamCost   上游返回的 cost（可空 → 触发 SKIP）
 * @param ruleRef        命中规则快照（证据）
 * @param requestBody    请求体（证据：仅抽取白名单请求属性如 size）
 */
public record JevJudgeContext(BillingFactSubject subject,
                              Map<String, Object> gatewayUsage,
                              BillingFactAmount gatewayBilling,
                              Map<String, Object> upstreamUsage,
                              BigDecimal upstreamCost,
                              BillingFactRuleRef ruleRef,
                              Map<String, Object> requestBody) {
}
