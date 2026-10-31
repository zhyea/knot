package org.chobit.knot.svc.dto.system;

import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;

import java.util.List;

/**
 * 操作日志分页响应：一页日志 + 三个筛选下拉的候选项。
 *
 * <p>⚠ 这里原先直接持有 {@code List<OperationLogEntity>}——「装响应的壳里塞实体」是实体外泄
 * 最容易漏的一处：controller 的返回类型看着人畜无害（{@code OperationLogListResult}），
 * 但 JSON 里吐的仍是实体。现在改成 {@link OperationLogDetailItem}，与
 * {@code OperationLogController} 其余几个方法同一种载体。</p>
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
