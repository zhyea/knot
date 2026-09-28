package org.chobit.knot.gateway.vo.model;

public record ModelPoolItem(
        Long id,
        String modelCode,
        String modelName,
        String modelType,
        String providerAccountCode,
        String providerName,
        Integer weight,
        Integer priority,
        Boolean enabled
) {
}
