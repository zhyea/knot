package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.SystemConverter;
import org.chobit.knot.gateway.dto.system.BackupTaskDto;
import org.chobit.knot.gateway.dto.system.NodeDto;
import org.chobit.knot.gateway.dto.system.OperationLogDetailDto;
import org.chobit.knot.gateway.dto.system.OperationLogDto;
import org.chobit.knot.gateway.entity.BackupJobEntity;
import org.chobit.knot.gateway.entity.OperationLogDetailEntity;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.mapper.SystemMapper;
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
