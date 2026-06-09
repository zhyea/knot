package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.converter.SystemConverter;
import org.chobit.knot.gateway.dto.system.OperationLogDetailDto;
import org.chobit.knot.gateway.dto.system.OperationLogDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.SystemService;
import org.chobit.knot.gateway.vo.system.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final SystemService systemService;
    private final SystemConverter systemConverter;

    /**
     * Constructs a new instance.
     */
    public SystemController(SystemService systemService, SystemConverter systemConverter) {
        this.systemService = systemService;
        this.systemConverter = systemConverter;
    }

    /**
     * Executes the public operation. Executes the public operation.
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
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/log-types")
    public List<String> logTypes() {
        return List.of("access", "operation", "error", "system");
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/logs")
    public List<SystemLogItem> logs() {
        // 杩斿洖鏈€杩戠殑绯荤粺鏃ュ織锛屼粠鎿嶄綔鏃ュ織涓彁鍙?
        return systemService.listOperationLogs(PageRequest.of(1, 10)).list().stream()
                .map(dto -> new SystemLogItem(dto.moduleCode(), "INFO", dto.actionCode()))
                .toList();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @AuthCheck
    @PostMapping("/operation-logs")
    public PageResult<OperationLogItem> operationLogs(@RequestBody(required = false) PageQuery query) {
        PageResult<OperationLogDto> page = systemService.listOperationLogs(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(systemConverter::toOperationLogVOList);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @AuthCheck
    @PostMapping("/operation-logs/{id}")
    public OperationLogDetail operationLogDetail(@PathVariable Long id) {
        OperationLogDetailDto detail = systemService.getOperationLogDetail(id);
        return systemConverter.toOperationLogDetailVO(detail);
    }

}
