package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;

import java.util.List;

public record ResolvedRouting(Long ruleId,
                              String ruleCode,
                              Long consumerId,
                              String secretKey,
                              boolean returnUsageDetail,
                              List<RoutingRuleTargetDto> candidateModels,
                              GatewayRoutingInfo routingInfo,
                              /** 规则级失败重试策略；为 null 时运行时按 {@link RetryPolicy#DEFAULT} 处理 */
                              RetryPolicy retryPolicy,
                              /** 规则级异常摘除策略；为 null 时运行时按 {@link EjectionPolicy#DEFAULT} 处理 */
                              EjectionPolicy ejectionPolicy) {

    /**
     * 有效重试策略：未配置时退回内置默认（默认开启）。
     */
    public RetryPolicy effectiveRetryPolicy() {
        return retryPolicy == null ? RetryPolicy.DEFAULT : retryPolicy;
    }

    /**
     * 有效摘除策略：未配置时退回内置默认（默认关闭）。
     */
    public EjectionPolicy effectiveEjectionPolicy() {
        return ejectionPolicy == null ? EjectionPolicy.DEFAULT : ejectionPolicy;
    }
}
