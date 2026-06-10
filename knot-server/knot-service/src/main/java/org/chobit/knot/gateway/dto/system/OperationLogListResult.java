package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.model.PageResult;

import java.util.List;

public record OperationLogListResult(
        List<OperationLogEntity> list,
        long total,
        int pageNum,
        int pageSize,
        int pages,
        List<String> moduleOptions,
        List<String> operationOptions,
        List<String> statusOptions
) {
    public static OperationLogListResult of(PageResult<OperationLogEntity> page,
                                            List<String> moduleOptions,
                                            List<String> operationOptions,
                                            List<String> statusOptions) {
        return new OperationLogListResult(
                page.list(),
                page.total(),
                page.pageNum(),
                page.pageSize(),
                page.pages(),
                moduleOptions,
                operationOptions,
                statusOptions
        );
    }
}
