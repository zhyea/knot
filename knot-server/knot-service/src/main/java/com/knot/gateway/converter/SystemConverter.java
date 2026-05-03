package com.knot.gateway.converter;

import com.knot.gateway.dto.system.BackupTaskDto;
import com.knot.gateway.vo.system.*;
import com.knot.gateway.dto.system.NodeDto;
import com.knot.gateway.dto.system.OperationLogDetailDto;
import com.knot.gateway.dto.system.OperationLogDto;
import com.knot.gateway.dto.system.UserDto;
import com.knot.gateway.entity.BackupJobEntity;
import com.knot.gateway.entity.GatewayNodeEntity;
import com.knot.gateway.entity.OperationLogDetailEntity;
import com.knot.gateway.entity.OperationLogEntity;
import com.knot.gateway.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface SystemConverter {

    // ==================== Entity ↔ DTO ====================

    UserDto toUserDto(UserEntity entity);

    OperationLogDto toOperationLogDto(OperationLogEntity entity);

    @Mapping(source = "logId", target = "id")
    OperationLogDetailDto toOperationLogDetailDto(OperationLogDetailEntity entity);

    NodeDto toNodeDto(GatewayNodeEntity entity);

    @Mapping(source = "jobCode", target = "taskId")
    BackupTaskDto toBackupTaskDto(BackupJobEntity entity);

    List<UserDto> toUserDtoList(List<UserEntity> entities);

    List<OperationLogDto> toOperationLogDtoList(List<OperationLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    UserItem toUserVO(UserDto dto);

    OperationLogItem toOperationLogVO(OperationLogDto dto);

    OperationLogDetail toOperationLogDetailVO(OperationLogDetailDto dto);

    NodeItem toNodeVO(NodeDto dto);

    BackupTaskResult toBackupTaskVO(BackupTaskDto dto);

    List<UserItem> toUserVOList(List<UserDto> dtos);

    List<OperationLogItem> toOperationLogVOList(List<OperationLogDto> dtos);

    List<NodeItem> toNodeVOList(List<NodeDto> dtos);
}
