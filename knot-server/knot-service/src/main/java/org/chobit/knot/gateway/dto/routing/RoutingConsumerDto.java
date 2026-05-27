package org.chobit.knot.gateway.dto.routing;

public record RoutingConsumerDto(
        Long id,
        String consumerCode,
        String name,
        Long userId,
        String userName,
        String secretKey,
        boolean enabled,
        Long ruleCount
) {
}
