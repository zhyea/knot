package org.chobit.knot.gateway.dto.model;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

public record LogicalModelDto(
        Long id,
        String modelCode,
        String modelName,
        String modelType,
        String modelFamily,
        String version,
        String displayName,
        String tagline,
        String description,
        String logoUrl,
        String coverUrl,
        List<String> tags,
        List<String> useCases,
        Map<String, Object> capabilities,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        List<String> languages,
        Map<String, Object> defaultParams,
        Map<String, Object> paramSchema,
        Map<String, Object> safetyPolicy,
        String visibility,
        String publishStatus,
        boolean enabled,
        Integer sortOrder,
        boolean featured,
        Long ownerUserId,
        String ownerTeam,
        String qualityLevel,
        String latencyLevel,
        String costLevel,
        String pricingSummary,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProviderModelMappingDto> mappings
) {
}
