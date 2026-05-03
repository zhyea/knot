package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.system.*;
import org.chobit.knot.gateway.entity.*;
import org.chobit.knot.gateway.vo.system.*;
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
