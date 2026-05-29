package org.chobit.knot.gateway.vo.model;

public record ModelPoolItem(
        Long id,
        Long modelId,
        String modelCode,
        String modelName,
        String modelType,
        Long providerId,
        String providerName,
        Integer weight,
        Integer priority,
        Boolean enabled
) {
}
