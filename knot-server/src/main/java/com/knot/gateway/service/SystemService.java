package com.knot.gateway.service;

import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.entity.BackupJobEntity;
import com.knot.gateway.entity.OperationLogDetailEntity;
import com.knot.gateway.entity.UserEntity;
import com.knot.gateway.mapper.SystemMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemService {
    private final SystemMapper systemMapper;

    public SystemService(SystemMapper systemMapper) {
        this.systemMapper = systemMapper;
    }

    public List<UserDto> listUsers() {
        return systemMapper.listUsers().stream()
                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getRealName(), u.getStatus()))
                .toList();
    }

    public UserDto createUser(UserDto request) {
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setStatus(request.status());
        systemMapper.insertUser(entity);
        return new UserDto(entity.getId(), entity.getUsername(), entity.getRealName(), entity.getStatus());
    }

    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = systemMapper.getUserById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        entity.setStatus(status);
        systemMapper.updateUserStatus(entity);
        return new UserDto(entity.getId(), entity.getUsername(), entity.getRealName(), entity.getStatus());
    }

    public List<OperationLogDto> listOperationLogs() {
        return systemMapper.listOperationLogs().stream()
                .map(l -> new OperationLogDto(l.getId(), l.getModuleCode(), l.getActionCode(), l.getTargetId(), l.getResultStatus()))
                .toList();
    }

    public OperationLogDetailDto getOperationLogDetail(Long id) {
        OperationLogDetailEntity detail = systemMapper.getOperationLogDetail(id);
        if (detail == null) {
            return new OperationLogDetailDto(id, "{}", "{}");
        }
        return new OperationLogDetailDto(detail.getLogId(), detail.getBeforeJson(), detail.getAfterJson());
    }

    public List<NodeDto> listNodes() {
        return systemMapper.listNodes().stream()
                .map(n -> new NodeDto(n.getNodeId(), n.getHost(), n.getStatus()))
                .toList();
    }

    public BackupTaskDto createBackupTask() {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setJobCode("backup-" + System.currentTimeMillis());
        entity.setStatus("RUNNING");
        entity.setSnapshotRef("snapshot-" + System.currentTimeMillis());
        systemMapper.insertBackupJob(entity);
        return new BackupTaskDto(entity.getJobCode(), entity.getStatus());
    }

    public BackupTaskDto restoreBackup(String backupCode) {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setJobCode(backupCode);
        entity.setStatus("RESTORING");
        systemMapper.updateBackupJobStatus(entity);
        return new BackupTaskDto(backupCode, "RESTORING");
    }

    public record UserDto(Long id, String username, String realName, String status) {}
    public record OperationLogDto(Long id, String moduleCode, String actionCode, String targetId, String resultStatus) {}
    public record OperationLogDetailDto(Long id, String beforeJson, String afterJson) {}
    public record NodeDto(String nodeId, String host, String status) {}
    public record BackupTaskDto(String taskId, String status) {}
}
