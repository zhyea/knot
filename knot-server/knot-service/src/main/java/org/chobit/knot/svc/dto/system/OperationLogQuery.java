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
     * null 安全：请求体未传时给一个「只带默认分页、无任何筛选」的查询体。
     *
     * <p>替掉 controller 里 {@code new OperationLogQuery(1, 20, null × 6)} 那种字面量构造——
     * 加一个筛选字段就要改调用点，且默认页大小会被写死两遍（{@link PageRequest} 里已经有一份）。</p>
     */
    public static OperationLogQuery orDefault(OperationLogQuery query) {
        return query == null
                ? new OperationLogQuery(null, null, null, null, null, null, null, null)
                : query;
    }
}
