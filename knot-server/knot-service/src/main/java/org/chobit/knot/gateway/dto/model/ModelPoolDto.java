package org.chobit.knot.gateway.dto.model;

import java.util.List;

public record ModelPoolDto(
        Long id,
        String poolCode,
        String name,
        String modelType,
        String selectionStrategy,
        boolean enabled,
        String remark,
        List<ModelPoolItemDto> items
) {
}
