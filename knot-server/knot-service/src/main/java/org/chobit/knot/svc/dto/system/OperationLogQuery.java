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

    /**
     * null 安全：请求体未传时返回「只带默认分页、无任何筛选」的查询体。
     */
    public static OperationLogQuery orDefault(OperationLogQuery query) {
        return query == null
                ? new OperationLogQuery(null, null, null, null, null, null, null, null)
                : query;
    }
}
