package org.chobit.knot.gateway.dto.model;

public record ModelPoolItemDto(
        Long id,
        String modelCode,
        String modelName,
        String modelType,
        String providerAccountCode,
        String providerName,
        Integer weight,
        Integer priority,
        boolean enabled
) {
}
