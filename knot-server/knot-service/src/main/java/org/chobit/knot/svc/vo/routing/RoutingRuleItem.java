package org.chobit.knot.svc.vo.routing;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 路由规则列表行：只承载列表页真正渲染的字段。
 *
 * <p>限流 / 额度、失败重试、异常摘除与目标选择策略只在编辑窗口使用，
 * 打开编辑窗口时按 id 走详情接口（{@code GET /api/routing-rules/{id}}）再取——
 * 列表既不为它们多发一次 {@code kb_resource_traffic_policies} 批查询，也不把它们塞进响应体。</p>
 *
 * <p>目标行仍随列表下发：列表的「路由目标」列要展示，且它是同一批 join 出来的派生字段、
 * 不额外增加查询次数。</p>
 */
public record RoutingRuleItem(
        Long id,
        String ruleCode,
        String name,
        String appScenario,
        String consumerCode,
        String consumerName,
        Long appId,
        String appName,
        String username,
        String userRealName,
        String logicalModelCode,
        String logicalModelName,
        boolean enabled,
        List<RoutingRuleTargetItem> targets,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
