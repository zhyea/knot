package org.chobit.knot.gateway.dto.model;

import org.chobit.knot.gateway.model.PageRequest;

public record ExternalModelItemQuery(
        Integer pageNum,
        Integer pageSize,
        String sourceCode,
        String syncStatus
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
