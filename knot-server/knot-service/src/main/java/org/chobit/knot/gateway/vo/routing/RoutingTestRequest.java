package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.constraints.NotBlank;

public record RoutingTestRequest(
        @NotBlank String secretKey,
        String prompt,
        String model
) {
}
