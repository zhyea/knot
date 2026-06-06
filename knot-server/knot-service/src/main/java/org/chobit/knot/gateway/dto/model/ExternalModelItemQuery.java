package org.chobit.knot.gateway.dto.model;

import org.chobit.knot.gateway.model.PageRequest;

public record ExternalModelItemQuery(
        Integer pageNum,
        Integer pageSize,
        String sourceCode,
        String syncStatus,
        String keyword,
        String modelType
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
