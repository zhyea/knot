package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.model.PageRequest;

public record OperationLogQuery(
        Integer pageNum,
        Integer pageSize,
        String module,
        String operation,
        String status,
        String keyword
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
