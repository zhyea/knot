package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.svc.dto.system.OperationLogListResult;
import org.chobit.knot.svc.dto.system.OperationLogQuery;
import org.chobit.knot.svc.service.OperationLogService;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志控制器。
 */
@RestController
@RequestMapping("/api/operation-logs")
@AuthCheck
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志。
     */
    @PostMapping("/list")
    public OperationLogListResult list(@RequestBody(required = false) OperationLogQuery query) {
        OperationLogQuery request = OperationLogQuery.orDefault(query);
        var page = operationLogService.list(
                request.toPageRequest(),
                request.module(),
                request.operation(),
                request.status(),
                request.keyword(),
                request.entityType(),
                request.entityId(),
                request.entityNamePrefix()
        );
        return OperationLogListResult.of(
                page,
                operationLogService.listModules(),
                operationLogService.listOperations(),
                operationLogService.listStatuses()
        );
    }

    /**
     * 根据 ID 查询操作日志详情。
     */
    @GetMapping("/{id}")
    public OperationLogDetailItem getById(@PathVariable Long id) {
        return operationLogService.getById(id);
    }
}
