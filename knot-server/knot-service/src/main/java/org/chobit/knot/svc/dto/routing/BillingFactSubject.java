package org.chobit.knot.svc.dto.routing;

/**
 * 计费事实主体：当次被测调用的身份与形态。
 *
 * @param modelCode   被测模型业务码
 * @param protocol    接口协议
 * @param stream      是否流式
 * @param ruleSource  计费规则命中层级（与 {@link BillingFactRuleRef#matchedBy()} 同值，便于自检区直接读取）
 * @param taskStatus  异步任务的终态词（{@code completed} / {@code failed} / 在途词）；同步链路为
 *                    {@code null}。⭐ 用<b>对外词</b>而不是库里的 0~5 码：拿到它的地方是网关查询响应，
 *                    再造一份码表就是把 {@code AsyncTaskViewSupport.displayStatus} 抄第二遍
 */
public record BillingFactSubject(String modelCode, String protocol, boolean stream,
                                 BillingRuleSource ruleSource, String taskStatus) {
}
