package org.chobit.knot.gateway.vo.model;

import java.util.List;
import java.time.LocalDateTime;

public record LogicalModelItem(
        Long id,
        String modelCode,
        String modelName,
        String modelType,
        String modelFamily,
        String displayName,
        String description,
        List<String> tags,
        List<String> useCases,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        List<String> languages,
        String visibility,
        /** 发布状态（LogicalModelPublishStatusEnum）：1-DRAFT 2-PUBLISHED 3-ARCHIVED */
        Integer publishStatus,
        boolean enabled,
        boolean deleted,
        Integer sortOrder,
        boolean featured,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProviderModelMappingItem> mappings
) {
}
