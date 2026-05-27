package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoutingConsumer(
        Long id,
        @Size(max = 32) String consumerCode,
        @NotBlank String name,
        Long userId,
        String userName,
        String secretKey,
        boolean enabled,
        Long ruleCount
) {
}
