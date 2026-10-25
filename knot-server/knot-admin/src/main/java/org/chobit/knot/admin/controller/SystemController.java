package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.svc.converter.OperationLogConverter;
import org.chobit.knot.svc.dto.system.OperationLogDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.OperationLogService;
import org.chobit.knot.svc.vo.system.OperationLogItem;
import org.chobit.knot.svc.vo.system.RoleItem;
import org.chobit.knot.svc.vo.system.SystemLogItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final OperationLogService operationLogService;
    private final OperationLogConverter operationLogConverter;

    /**
     * Constructs a new instance.
     */
    public SystemController(OperationLogService operationLogService, OperationLogConverter operationLogConverter) {
        this.operationLogService = operationLogService;
        this.operationLogConverter = operationLogConverter;
    }

    /**
     * Returns static role options.
     */
    @AuthCheck
    @PostMapping("/roles")
    public List<RoleItem> roles() {
        return List.of(
                new RoleItem("SUPER_ADMIN", "超级管理员"),
                new RoleItem("OPS_ADMIN", "运维管理员"),
                new RoleItem("BIZ_ADMIN", "业务管理员"),
                new RoleItem("USER", "普通用户")
        );
    }

    /**
     * Returns system log type options.
     */
    @PostMapping("/log-types")
    public List<String> logTypes() {
        return List.of("access", "operation", "error", "system");
    }

    /**
     * Returns recent system logs assembled from operation logs.
     */
    @PostMapping("/logs")
    public List<SystemLogItem> logs() {
        return operationLogService.listOperationLogs(PageRequest.of(1, 10)).list().stream()
                .map(dto -> new SystemLogItem(dto.moduleCode(), "INFO", dto.actionCode()))
                .toList();
    }

    /**
     * Lists operation logs with pagination.
     */
    @AuthCheck
    @PostMapping("/operation-logs")
    public PageResult<OperationLogItem> operationLogs(@RequestBody(required = false) PageQuery query) {
        PageResult<OperationLogDto> page = operationLogService.listOperationLogs(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest()
        );
        return page.mapList(operationLogConverter::toOperationLogVOList);
    }
}
