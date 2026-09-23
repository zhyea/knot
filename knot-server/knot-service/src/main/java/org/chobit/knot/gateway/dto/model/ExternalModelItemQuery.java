package org.chobit.knot.gateway.dto.model;

import org.chobit.knot.gateway.model.PageRequest;

import java.util.List;

public record ExternalModelItemQuery(
        Integer pageNum,
        Integer pageSize,
        String sourceCode,
        String syncStatus,
        String keyword,
        String modelType,
        List<Long> ids
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
