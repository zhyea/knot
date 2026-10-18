package org.chobit.knot.gateway.dto.routing;

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
         * 来源模型池业务码：目标为模型池时，本候选是池内展开出的模型，此值为池 code
         * （kb_model_pools.pool_code）；规则直挂模型目标为 {@code null}。
         * 池展开后 targetCode 已替换为池内模型 code，池 code 只能从这里带出——
         * 数仓据此把「池内被使用的模型」归因到具体池。
         */
        String poolCode
) {
}
