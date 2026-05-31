package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 鎿嶄綔鏃ュ織鎺у埗鍣?
 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * Constructs a new instance.
     */
    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /**
     * 鍒嗛〉鏌ヨ鎿嶄綔鏃ュ織
     */
    @PostMapping("/list")
    public List<OperationLogEntity> list(@RequestBody PageQuery query) {
        // TODO: 瀹炵幇鍒嗛〉鏌ヨ
        List<OperationLogEntity> logs = operationLogService.listByModule(null, null, null, null);
        return logs;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    /**
     * 鏍规嵁ID鏌ヨ鎿嶄綔鏃ュ織璇︽儏
     */
    @GetMapping("/{id}")
    public OperationLogEntity getById(@PathVariable Long id) {
        OperationLogEntity log = operationLogService.getById(id);
        return log;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    /**
     * 鏍规嵁妯″潡鏌ヨ鎿嶄綔鏃ュ織
     */
    @GetMapping("/module/{module}")
    public List<OperationLogEntity> getByModule(
            @PathVariable String module,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long operatorId) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, entityType, entityId, operatorId);
        return logs;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    /**
     * 鏍规嵁鎿嶄綔浜烘煡璇㈡搷浣滄棩蹇?
     */
    @GetMapping("/operator/{operatorId}")
    public List<OperationLogEntity> getByOperator(
            @PathVariable Long operatorId,
            @RequestParam(required = false) String module) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, null, null, operatorId);
        return logs;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    /**
     * 鏍规嵁瀹炰綋鏌ヨ鎿嶄綔鏃ュ織
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public List<OperationLogEntity> getByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(required = false) String module) {
        List<OperationLogEntity> logs = operationLogService.listByModule(module, entityType, entityId, null);
        return logs;
    }
}
