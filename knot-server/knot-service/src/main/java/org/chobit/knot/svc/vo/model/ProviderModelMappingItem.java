package org.chobit.knot.svc.vo.model;

public record ProviderModelMappingItem(
        Long id,
        String logicalModelCode,
        String logicalModelName,
        String providerAccountCode,
        String providerName,
        Long modelId,
        String modelCode,
        String modelName,
        String providerModelName,
        boolean enabled,
        Integer priority
) {
}
