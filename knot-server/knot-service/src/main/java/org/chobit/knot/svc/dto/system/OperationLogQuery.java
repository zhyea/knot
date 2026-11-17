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
        Long entityId,
        /**
         * 实体名前缀过滤：命中 {@code entity_name = 前缀} 或 {@code 前缀/...}。
         * 枚举分类日志按「分类」聚合，就是靠它把 {@code 分类} 与 {@code 分类/编码} 一并取出。
         */
        String entityNamePrefix
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }

    /**
     * null 安全：请求体未传时返回「只带默认分页、无任何筛选」的查询体。
     */
    public static OperationLogQuery orDefault(OperationLogQuery query) {
        return query == null
                ? new OperationLogQuery(null, null, null, null, null, null, null, null, null)
                : query;
    }
}
