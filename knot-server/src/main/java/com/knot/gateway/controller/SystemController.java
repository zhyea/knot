package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.SystemService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
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
    public ApiResponse<List<UserItem>> users() {
        List<UserItem> users = systemService.listUsers().stream()
                .map(u -> new UserItem(u.id(), u.username(), u.realName(), u.status()))
                .toList();
        return ApiResponse.ok(users);
    }

    @PostMapping("/users")
    public ApiResponse<UserItem> createUser(@RequestBody UserItem request) {
        SystemService.UserDto created = systemService.createUser(new SystemService.UserDto(
                null, request.username(), request.realName(), request.status()
        ));
        return ApiResponse.ok(new UserItem(created.id(), created.username(), created.realName(), created.status()));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<UserItem> updateUserStatus(@PathVariable Long id, @RequestBody UpdateUserStatusRequest request) {
        SystemService.UserDto updated = systemService.updateUserStatus(id, request.status());
        return ApiResponse.ok(new UserItem(updated.id(), updated.username(), updated.realName(), updated.status()));
    }

    @GetMapping("/logs")
    public ApiResponse<List<SystemLogItem>> logs() {
        return ApiResponse.ok(List.of(
                new SystemLogItem("system", "INFO", "gateway started"),
                new SystemLogItem("operation", "INFO", "admin updated provider config")
        ));
    }

    @GetMapping("/operation-logs")
    public ApiResponse<List<OperationLogItem>> operationLogs() {
        List<OperationLogItem> logs = systemService.listOperationLogs().stream()
                .map(l -> new OperationLogItem(l.id(), l.moduleCode(), l.actionCode(), l.targetId(), l.resultStatus()))
                .toList();
        return ApiResponse.ok(logs);
    }

    @GetMapping("/operation-logs/{id}")
    public ApiResponse<OperationLogDetail> operationLogDetail(@PathVariable Long id) {
        SystemService.OperationLogDetailDto detail = systemService.getOperationLogDetail(id);
        return ApiResponse.ok(new OperationLogDetail(detail.id(), detail.beforeJson(), detail.afterJson()));
    }

    @GetMapping("/nodes")
    public ApiResponse<List<NodeItem>> nodes() {
        List<NodeItem> nodes = systemService.listNodes().stream()
                .map(n -> new NodeItem(n.nodeId(), n.host(), n.status()))
                .toList();
        return ApiResponse.ok(nodes);
    }

    @PostMapping("/backups")
    public ApiResponse<BackupTaskResult> createBackupTask() {
        SystemService.BackupTaskDto task = systemService.createBackupTask();
        return ApiResponse.ok(new BackupTaskResult(task.taskId(), task.status()));
    }

    @PostMapping("/backups/{id}/restore")
    public ApiResponse<BackupTaskResult> restoreBackup(@PathVariable String id) {
        SystemService.BackupTaskDto task = systemService.restoreBackup(id);
        return ApiResponse.ok(new BackupTaskResult(task.taskId(), task.status()));
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
