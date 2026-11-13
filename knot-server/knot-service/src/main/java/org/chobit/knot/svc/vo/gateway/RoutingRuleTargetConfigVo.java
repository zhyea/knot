package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：路由目标（候选链顺序与权重）。
 * 字段逐字平移 {@code RoutingRuleTargetEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record RoutingRuleTargetConfigVo(
        Long id,
        String ruleCode,
        String targetType,
        Long targetId,
        Integer priority,
        Boolean primary,
        String targetCode,
        String targetName,
        String modelType,
        String providerAccountCode
) {
}
