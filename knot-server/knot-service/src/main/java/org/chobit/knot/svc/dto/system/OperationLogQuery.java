package org.chobit.knot.svc.dto.system;

import org.chobit.knot.common.model.PageRequest;

public record OperationLogQuery(
        Integer pageNum,
        Integer pageSize,
        String module,
        String operation,
        Integer status,
        String keyword,
        String entityType,
        Long entityId
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
