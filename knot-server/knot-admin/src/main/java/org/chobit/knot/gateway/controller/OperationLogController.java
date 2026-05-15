package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志控制器
 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志
     */
    @PostMapping("/list")
    public ApiResponse<List<OperationLogEntity>> list(@RequestBody PageQuery query) {
        // TODO: 实现分页查询
        List<OperationLogEntity> logs = operationLogService.listByModule(null, null, null, null);
        return ApiResponse.ok(logs);
    }

    /**
     * 根据ID查询操作日志详情
     */
    @GetMapping("/{id}")
    public ApiResponse<OperationLogEntity> getById(@PathVariable Long id) {
        OperationLogEntity log = operationLogService.getById(id);
        return ApiResponse.ok(log);
    }

    /**
     * 根据模块查询操作日志
     */
    @GetMapping("/module/{module}")
    public ApiResponse<List<OperationLogEntity>> getByModule(
            @PathVariable String module,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long operatorId) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, entityType, entityId, operatorId);
        return ApiResponse.ok(logs);
    }

    /**
     * 根据操作人查询操作日志
     */
    @GetMapping("/operator/{operatorId}")
    public ApiResponse<List<OperationLogEntity>> getByOperator(
            @PathVariable Long operatorId,
            @RequestParam(required = false) String module) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, null, null, operatorId);
        return ApiResponse.ok(logs);
    }

    /**
     * 根据实体查询操作日志
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ApiResponse<List<OperationLogEntity>> getByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(required = false) String module) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, entityType, entityId, null);
        return ApiResponse.ok(logs);
    }
}
