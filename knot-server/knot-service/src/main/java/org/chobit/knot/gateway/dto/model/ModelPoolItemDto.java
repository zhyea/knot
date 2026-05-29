package org.chobit.knot.gateway.dto.model;

public record ModelPoolItemDto(
        Long id,
        Long modelId,
        String modelCode,
        String modelName,
        String modelType,
        Long providerId,
        String providerName,
        Integer weight,
        Integer priority,
        boolean enabled
) {
}
