package org.chobit.knot.svc.dto.routing;

/**
 * 计费事实主体：当次被测调用的身份与形态。
 * @param modelCode   被测模型业务码
 * @param protocol    接口协议
 * @param stream      是否流式
 * @param ruleSource  计费规则命中层级（与 {@link BillingFactRuleRef#matchedBy()} 同值，便于自检区直接读取）
 * @param taskStatus  异步任务的状态码（{@code AsyncTaskStatusEnum} 的 0~5，与网关查询响应同一套）；
 *                    同步链路为 {@code null}。用码而不是「对外词」：词表曾被单独维护一份
 *                    （gateway 的投影里做映射），一份词散在多端、改一处漏一处；码只有一个来源，
 *                    label 展示由前端按码查 `AsyncTaskStatusEnum`
 */
public record BillingFactSubject(String modelCode, String protocol, boolean stream,
                                 BillingRuleSource ruleSource, Integer taskStatus) {
}
