package org.chobit.knot.gateway.vo.model;

public record ProviderModelMappingItem(
        Long id,
        Long logicalModelId,
        String logicalModelCode,
        String logicalModelName,
        Long providerId,
        String providerName,
        Long modelId,
        String modelCode,
        String modelName,
        String providerModelName,
        boolean enabled,
        Integer priority
) {
}
