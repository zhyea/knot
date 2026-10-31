package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.svc.dto.system.OperationLogListResult;
import org.chobit.knot.svc.dto.system.OperationLogQuery;
import org.chobit.knot.svc.service.OperationLogService;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志控制器。
 */
@RestController
@RequestMapping("/api/operation-logs")
@AuthCheck
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * Constructs a new instance.
     */
    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志。
     */
    @PostMapping("/list")
    public OperationLogListResult list(@RequestBody(required = false) OperationLogQuery query) {
        OperationLogQuery request = query == null ? new OperationLogQuery(1, 20, null, null, null, null, null, null) : query;
        var page = operationLogService.list(
                request.toPageRequest(),
                request.module(),
                request.operation(),
                request.status(),
                request.keyword(),
                request.entityType(),
                request.entityId()
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

    /**
     * 根据模块查询操作日志。
     */
    @GetMapping("/module/{module}")
    public List<OperationLogDetailItem> getByModule(
            @PathVariable String module,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long operatorId) {
        return operationLogService.listByModule(module, entityType, entityId, operatorId);
    }

    /**
     * 根据操作人查询操作日志。
     */
    @GetMapping("/operator/{operatorId}")
    public List<OperationLogDetailItem> getByOperator(
            @PathVariable Long operatorId,
            @RequestParam(required = false) String module) {
        return operationLogService.listByModule(module, null, null, operatorId);
    }

    /**
     * 根据实体查询操作日志。
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public List<OperationLogDetailItem> getByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(required = false) String module) {
        return operationLogService.listByModule(module, entityType, entityId, null);
    }
}
