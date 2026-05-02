package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.SystemConverter;
import com.knot.gateway.service.SystemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final SystemService systemService;
    private final SystemConverter systemConverter;

    public SystemController(SystemService systemService, SystemConverter systemConverter) {
        this.systemService = systemService;
        this.systemConverter = systemConverter;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleItem>> roles() {
        return ApiResponse.ok(List.of(
                new RoleItem("SUPER_ADMIN", "超级管理员"),
                new RoleItem("OPS_ADMIN", "运维管理员"),
                new RoleItem("BIZ_ADMIN", "业务管理员"),
                new RoleItem("USER", "普通用户")
        ));
    }

    @GetMapping("/log-types")
    public ApiResponse<List<String>> logTypes() {
        return ApiResponse.ok(List.of("access", "operation", "error", "system"));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserItem>> users(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<SystemService.UserDto> page = systemService.listUsers(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(systemConverter::toUserVOList));
    }

    @PostMapping("/users")
    public ApiResponse<UserItem> createUser(@RequestBody @Valid UserItem request) {
        SystemService.UserDto created = systemService.createUser(new SystemService.UserDto(
                null, request.username(), request.realName(), request.status()
        ));
        return ApiResponse.ok(systemConverter.toUserVO(created));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<UserItem> updateUserStatus(@PathVariable Long id, @RequestBody @Valid UpdateUserStatusRequest request) {
        SystemService.UserDto updated = systemService.updateUserStatus(id, request.status());
        return ApiResponse.ok(systemConverter.toUserVO(updated));
    }

    @GetMapping("/logs")
    public ApiResponse<List<SystemLogItem>> logs() {
        // 返回最近的系统日志，从操作日志中提取
        return ApiResponse.ok(systemService.listOperationLogs(PageRequest.of(1, 10)).list().stream()
                .map(dto -> new SystemLogItem(dto.moduleCode(), "INFO", dto.actionCode()))
                .toList());
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResult<OperationLogItem>> operationLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<SystemService.OperationLogDto> page = systemService.listOperationLogs(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(systemConverter::toOperationLogVOList));
    }

    @GetMapping("/operation-logs/{id}")
    public ApiResponse<OperationLogDetail> operationLogDetail(@PathVariable Long id) {
        SystemService.OperationLogDetailDto detail = systemService.getOperationLogDetail(id);
        return ApiResponse.ok(systemConverter.toOperationLogDetailVO(detail));
    }

    @GetMapping("/nodes")
    public ApiResponse<List<NodeItem>> nodes() {
        return ApiResponse.ok(systemConverter.toNodeVOList(systemService.listNodes()));
    }

    @PostMapping("/backups")
    public ApiResponse<BackupTaskResult> createBackupTask() {
        SystemService.BackupTaskDto task = systemService.createBackupTask();
        return ApiResponse.ok(systemConverter.toBackupTaskVO(task));
    }

    @PostMapping("/backups/{id}/restore")
    public ApiResponse<BackupTaskResult> restoreBackup(@PathVariable String id) {
        SystemService.BackupTaskDto task = systemService.restoreBackup(id);
        return ApiResponse.ok(systemConverter.toBackupTaskVO(task));
    }

    public record RoleItem(String code, String name) {
    }

    public record UserItem(Long id, String username, String realName, String status) {
    }

    public record UpdateUserStatusRequest(String status) {
    }

    public record SystemLogItem(String type, String level, String message) {
    }

    public record OperationLogItem(Long id, String moduleCode, String actionCode, String targetId, String resultStatus) {
    }

    public record OperationLogDetail(Long id, String beforeJson, String afterJson) {
    }

    public record NodeItem(String nodeId, String host, String status) {
    }

    public record BackupTaskResult(String taskId, String status) {
    }
}
