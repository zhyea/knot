package org.chobit.knot.svc.vo.gateway;

import java.time.LocalDateTime;

/**
 * 网关配置快照：路由规则（目标选择策略、重试与摘除策略）。
 * 字段逐字平移 {@code RoutingRuleEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record RoutingRuleConfigVo(
        Long id,
        String ruleCode,
        String name,
        String appScenario,
        String appCode,
        Long appId,
        String appName,
        String username,
        String userRealName,
        String consumerCode,
        String consumerName,
        String logicalModelCode,
        String logicalModelName,
        String retryPolicy,
        String ejectionPolicy,
        String selectionStrategy,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
