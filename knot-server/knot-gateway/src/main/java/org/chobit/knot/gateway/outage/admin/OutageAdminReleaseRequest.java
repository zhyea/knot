package org.chobit.knot.gateway.outage.admin;

/**
 * 手动恢复请求：解除「规则 × 模型」的摘除状态，目标立即重新参与候选链。
 *
 * @param ruleCode  路由规则业务码
 * @param modelCode 供应商模型业务码
 */
public record OutageAdminReleaseRequest(
        String ruleCode,
        String modelCode
) {
}
