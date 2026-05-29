package org.chobit.knot.gateway.dto.model;

public record ProviderModelMappingDto(
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
