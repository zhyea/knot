package org.chobit.knot.svc.dto.system;

import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;

import java.util.List;

/**
 * 操作日志分页响应：一页日志 + 三个筛选下拉的候选项，日志元素为 {@link OperationLogDetailItem}。
 */
public record OperationLogListResult(
        List<OperationLogDetailItem> list,
        long total,
        int pageNum,
        int pageSize,
        int pages,
        List<String> moduleOptions,
        List<String> operationOptions,
        /** 执行结果（OperationLogStatusEnum）：1-成功 2-失败 */
        List<Integer> statusOptions
) {
    public static OperationLogListResult of(PageResult<OperationLogDetailItem> page,
                                            List<String> moduleOptions,
                                            List<String> operationOptions,
                                            List<Integer> statusOptions) {
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
