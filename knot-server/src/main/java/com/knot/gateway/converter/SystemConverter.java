package com.knot.gateway.converter;

import com.knot.gateway.controller.SystemController;
import com.knot.gateway.entity.BackupJobEntity;
import com.knot.gateway.entity.GatewayNodeEntity;
import com.knot.gateway.entity.OperationLogDetailEntity;
import com.knot.gateway.entity.OperationLogEntity;
import com.knot.gateway.entity.UserEntity;
import com.knot.gateway.service.SystemService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface SystemConverter {

    // ==================== Entity ↔ DTO ====================

    SystemService.UserDto toUserDto(UserEntity entity);

    SystemService.OperationLogDto toOperationLogDto(OperationLogEntity entity);

    @Mapping(source = "logId", target = "id")
    SystemService.OperationLogDetailDto toOperationLogDetailDto(OperationLogDetailEntity entity);

    SystemService.NodeDto toNodeDto(GatewayNodeEntity entity);

    @Mapping(source = "jobCode", target = "taskId")
    SystemService.BackupTaskDto toBackupTaskDto(BackupJobEntity entity);

    List<SystemService.UserDto> toUserDtoList(List<UserEntity> entities);

    List<SystemService.OperationLogDto> toOperationLogDtoList(List<OperationLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    SystemController.UserItem toUserVO(SystemService.UserDto dto);

    SystemController.OperationLogItem toOperationLogVO(SystemService.OperationLogDto dto);

    SystemController.OperationLogDetail toOperationLogDetailVO(SystemService.OperationLogDetailDto dto);

    SystemController.NodeItem toNodeVO(SystemService.NodeDto dto);

    SystemController.BackupTaskResult toBackupTaskVO(SystemService.BackupTaskDto dto);

    List<SystemController.UserItem> toUserVOList(List<SystemService.UserDto> dtos);

    List<SystemController.OperationLogItem> toOperationLogVOList(List<SystemService.OperationLogDto> dtos);

    List<SystemController.NodeItem> toNodeVOList(List<SystemService.NodeDto> dtos);
}
