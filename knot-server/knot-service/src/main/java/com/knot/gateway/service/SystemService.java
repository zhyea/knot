package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.SystemConverter;
import com.knot.gateway.dto.system.BackupTaskDto;
import com.knot.gateway.dto.system.NodeDto;
import com.knot.gateway.dto.system.OperationLogDetailDto;
import com.knot.gateway.dto.system.OperationLogDto;
import com.knot.gateway.dto.system.UserDto;
import com.knot.gateway.entity.BackupJobEntity;
import com.knot.gateway.entity.OperationLogDetailEntity;
import com.knot.gateway.entity.OperationLogEntity;
import com.knot.gateway.entity.UserEntity;
import com.knot.gateway.mapper.SystemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemService {
    private final SystemMapper systemMapper;
    private final SystemConverter systemConverter;

    public SystemService(SystemMapper systemMapper, SystemConverter systemConverter) {
        this.systemMapper = systemMapper;
        this.systemConverter = systemConverter;
    }

    public PageResult<UserDto> listUsers(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<UserEntity> pageInfo = new PageInfo<>(systemMapper.listUsers());
        return PageResult.fromPage(pageInfo, systemConverter::toUserDtoList, pageRequest);
    }

    @Transactional
    public UserDto createUser(UserDto request) {
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setStatus(request.status());
        systemMapper.insertUser(entity);
        return systemConverter.toUserDto(entity);
    }

    @Transactional
    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = systemMapper.getUserById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        entity.setStatus(status);
        systemMapper.updateUserStatus(entity);
        return systemConverter.toUserDto(entity);
    }

    public PageResult<OperationLogDto> listOperationLogs(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<OperationLogEntity> pageInfo = new PageInfo<>(systemMapper.listOperationLogs());
        return PageResult.fromPage(pageInfo, systemConverter::toOperationLogDtoList, pageRequest);
    }

    public OperationLogDetailDto getOperationLogDetail(Long id) {
        OperationLogDetailEntity detail = systemMapper.getOperationLogDetail(id);
        if (detail == null) {
            return new OperationLogDetailDto(id, "{}", "{}");
        }
        return systemConverter.toOperationLogDetailDto(detail);
    }

    public List<NodeDto> listNodes() {
        return systemMapper.listNodes().stream()
                .map(systemConverter::toNodeDto)
                .toList();
    }

    @Transactional
    public BackupTaskDto createBackupTask() {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setJobCode("backup-" + System.currentTimeMillis());
        entity.setStatus("RUNNING");
        entity.setSnapshotRef("snapshot-" + System.currentTimeMillis());
        systemMapper.insertBackupJob(entity);
        return systemConverter.toBackupTaskDto(entity);
    }

    @Transactional
    public BackupTaskDto restoreBackup(String backupCode) {
        BackupJobEntity entity = new BackupJobEntity();
        entity.setJobCode(backupCode);
        entity.setStatus("RESTORING");
        systemMapper.updateBackupJobStatus(entity);
        return new BackupTaskDto(backupCode, "RESTORING");
    }

}
