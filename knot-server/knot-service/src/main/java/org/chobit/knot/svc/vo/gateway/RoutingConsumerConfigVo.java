package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：路由消费者（secretKey 鉴权、配额与限流）。
 * 字段逐字平移 {@code RoutingConsumerEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record RoutingConsumerConfigVo(
        Long id,
        String consumerCode,
        String name,
        String userRealName,
        String userUsername,
        String secretKey,
        Boolean returnUsageDetail,
        Long rateLimitPolicyId,
        Long quotaPolicyId,
        Integer status
) {
}
