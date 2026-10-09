package org.chobit.knot.gateway.dto.routing;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.RetryPolicy;

import java.util.List;

public record RoutingRuleDto(
        Long id,
        String ruleCode,
        String name,
        String appScenario,
        List<Long> consumerIds,
        List<String> consumerNames,
        Long appId,
        String appName,
        /** 归属用户登录名（业务码，非主键 id）；路由规则按 username 绑定用户 */
        String username,
        boolean enabled,
        List<RoutingRuleTargetDto> targets,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy,
        /** 规则级失败重试策略；null 表示未配置，运行时按内置默认（开启）处理 */
        RetryPolicy retryPolicy
) {
}
