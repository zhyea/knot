package org.chobit.knot.svc.dto.routing;

public record RoutingRuleTargetDto(
        String targetType,
        Long targetId,
        String targetCode,
        /** 发往上游时写入请求体 model 参数的模型标识（kb_models.upstream_model） */
        String upstreamModelCode,
        String targetName,
        String modelType,
        String providerAccountCode,
        int priority,
        boolean primary,
        /**
         * 来源模型池业务码：目标为模型池时取池 code（{@code kb_model_pools.pool_code}），直挂模型为 {@code null}。
         * 池展开后 {@code targetCode} 已是池内模型 code，池 code 只从本字段带出，数仓据此把池内模型归因到具体池。
         */
        String poolCode
) {
}
