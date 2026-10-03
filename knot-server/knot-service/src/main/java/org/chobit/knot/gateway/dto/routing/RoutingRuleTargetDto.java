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
        boolean primary
) {
}
