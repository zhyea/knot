package org.chobit.knot.svc.dto.model;

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
