package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record RoutingTestRequest(
        @NotBlank String secretKey,
        String prompt,
        String model,
        String protocol,
        String targetType,
        Long targetId,
        Map<String, Object> requestBody
) {
}
