package org.chobit.knot.svc.dto.routing;

/**
 * 计费事实主体：当次被测调用的身份与形态。
 *
 * @param modelCode   被测模型业务码
 * @param protocol    接口协议
 * @param stream      是否流式
 * @param ruleSource  计费规则命中层级（与 {@link BillingFactRuleRef#matchedBy()} 同值，便于自检区直接读取）
 */
public record BillingFactSubject(String modelCode, String protocol, boolean stream, BillingRuleSource ruleSource) {
}
