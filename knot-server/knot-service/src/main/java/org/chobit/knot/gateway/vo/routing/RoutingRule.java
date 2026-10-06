package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.RetryPolicy;

import java.util.List;

public record RoutingRule(
        Long id,
        @Size(max = 32) String ruleCode,
        @NotBlank String name,
        @Size(max = 128) String appScenario,
        List<Long> consumerIds,
        List<String> consumerNames,
        Long appId,
        String appName,
        Long userId,
        String userName,
        boolean enabled,
        @Valid List<RoutingRuleTargetItem> targets,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy,
        /** 规则级失败重试策略；null 表示未配置，运行时按内置默认（开启）处理 */
        RetryPolicy retryPolicy
) {
}
